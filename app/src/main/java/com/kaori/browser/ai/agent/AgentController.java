package com.kaori.browser.ai.agent;

import android.content.Context;
import android.util.Log;

import com.kaori.browser.ai.AiExecutors;
import com.kaori.browser.ai.api.AiClient;
import com.kaori.browser.ai.api.AiClientFactory;
import com.kaori.browser.ai.api.AiException;
import com.kaori.browser.ai.api.CancelToken;
import com.kaori.browser.ai.agent.executor.AgentExecutor;
import com.kaori.browser.ai.agent.observation.BrowserObservation;
import com.kaori.browser.ai.agent.observation.ElementInfo;
import com.kaori.browser.ai.agent.observation.PageObserver;
import com.kaori.browser.ai.agent.planner.AgentDecision;
import com.kaori.browser.ai.agent.planner.AgentPlanner;
import com.kaori.browser.ai.agent.tools.PageLoadWaiter;
import com.kaori.browser.ai.agent.tools.ToolContext;
import com.kaori.browser.ai.agent.tools.ToolRegistry;
import com.kaori.browser.ai.agent.tools.ToolResult;
import com.kaori.browser.ai.config.AiSettings;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs the Observe → Plan → Act → Verify loop for one agent task.
 *
 * <p>The loop runs on its own thread. Every WebView access is posted to the main thread
 * and awaited, so the UI stays responsive and the loop can never block Android. The
 * loop is bounded by the step limit, the time limit, user cancellation and a repeated
 * failing action guard; it cannot spin forever.</p>
 */
public final class AgentController {

    public interface Listener {
        void onStatus(AgentStatus status, String message, int step, int maxSteps);

        void onStep(int step, String thought, String tool, String argsSummary);

        void onToolResult(int step, String tool, boolean ok, String message, String url);

        void onFinal(AgentStatus status, String summary, String errorMessage);
    }

    public interface ConfirmationHandler {
        /** Called on the main thread. Exactly one of prompt.allow()/deny() must follow. */
        void requestConfirmation(int step, String toolName, String description,
                                 String reason, ConfirmationPrompt prompt);
    }

    /** Blocks the agent thread until the user answers. */
    public static final class ConfirmationPrompt {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile boolean approved;

        public void allow() {
            approved = true;
            latch.countDown();
        }

        public void deny() {
            approved = false;
            latch.countDown();
        }

        boolean await(long timeoutMs) {
            try {
                latch.await(Math.max(1000, timeoutMs), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return approved;
        }
    }

    private static final int OBSERVE_FAILURE_LIMIT = 3;
    private static final int MODEL_RETRY_LIMIT = 2;
    private static final int REPEAT_FAILURE_LIMIT = 3;
    private static final long CONFIRMATION_TIMEOUT_MS = 120000L;

    private static final String TAG = "AgentController";

    private final Context appContext;
    private final AgentSession session;
    private final Listener listener;
    private final ConfirmationHandler confirmationHandler;

    private final Object pauseLock = new Object();

    private volatile Thread worker;
    private volatile boolean paused;
    private volatile boolean stopped;
    private volatile CancelToken activeToken;
    private volatile int currentStep;

    public AgentController(Context context, AgentSession session, Listener listener,
                           ConfirmationHandler confirmationHandler) {
        this.appContext = context.getApplicationContext();
        this.session = session;
        this.listener = listener;
        this.confirmationHandler = confirmationHandler;
    }

    public AgentSession getSession() {
        return session;
    }

    public boolean isRunning() {
        Thread thread = worker;
        return thread != null && thread.isAlive();
    }

    public boolean isPaused() {
        return paused;
    }

    public synchronized boolean start(BrowserHost host) {
        if (isRunning()) {
            return false;
        }
        stopped = false;
        paused = false;
        currentStep = 0;
        session.setStatus(AgentStatus.RUNNING);
        Thread thread = new Thread(() -> {
            try {
                runLoop(host);
            } catch (Throwable t) {
                Log.e(TAG, "Agent loop terminated unexpectedly", t);
                finish(host, AgentStatus.FAILED, "",
                        "The agent stopped unexpectedly: " + t.getClass().getSimpleName());
            }
        }, "kaori-agent");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
        return true;
    }

    public void pause() {
        if (!isRunning()) {
            return;
        }
        paused = true;
        publishStatus(AgentStatus.PAUSED, "Paused", currentStep);
    }

    public void resume() {
        if (!isRunning()) {
            return;
        }
        paused = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        publishStatus(AgentStatus.RUNNING, "Resumed", currentStep);
    }

    public void stop() {
        stopped = true;
        paused = false;
        CancelToken token = activeToken;
        if (token != null) {
            token.cancel();
        }
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runLoop(BrowserHost host) {
        ToolRegistry registry = new ToolRegistry();
        PageObserver observer = new PageObserver();
        AgentPlanner planner = new AgentPlanner(appContext, registry);
        AgentExecutor executor = new AgentExecutor(registry);

        AiSettings.Snapshot settings = AiSettings.load(appContext);
        long toolTimeout = settings.timeoutSeconds <= 0
                ? 30000L : Math.max(8000, Math.min(30000, settings.timeoutSeconds * 500L));
        ToolContext toolContext = new ToolContext(host, session, appContext, observer, toolTimeout);
        String permissionMode = settings.permissionMode;

        String lastError = "";
        int attempts = 0;
        int observeFailures = 0;
        int modelRetries = 0;
        int repeatFailures = 0;
        String lastSignature = "";

        try {
            AiClient client = AiClientFactory.create(appContext);
            publishStatus(AgentStatus.RUNNING, "Starting", 0);

            while (true) {
                if (stopped) {
                    finish(host, AgentStatus.CANCELLED, "", "Stopped by the user");
                    return;
                }
                if (session.getMaxSteps() > 0 && attempts >= session.getMaxSteps()) {
                    finish(host, AgentStatus.STEP_LIMIT, "", "The step limit of "
                            + session.getMaxSteps() + " was reached before the task finished.");
                    return;
                }
                if (session.isDeadlineExceeded()) {
                    finish(host, AgentStatus.TIMEOUT, "",
                            "The time limit was reached before the task finished.");
                    return;
                }

                awaitResume();
                if (stopped) {
                    finish(host, AgentStatus.CANCELLED, "", "Stopped by the user");
                    return;
                }

                BrowserObservation observation = observer.observe(host, 9000);
                if (observation == null) {
                    observeFailures++;
                    lastError = "The page could not be observed; it may still be loading.";
                    publishStatus(AgentStatus.RUNNING, lastError, currentStep);
                    if (observeFailures >= OBSERVE_FAILURE_LIMIT) {
                        finish(host, AgentStatus.FAILED, "",
                                "The page could not be observed after "
                                        + OBSERVE_FAILURE_LIMIT + " attempts.");
                        return;
                    }
                    PageLoadWaiter.settleQuietly(1200);
                    continue;
                }
                observeFailures = 0;
                session.getMemory().addObservation(observation);
                publishStatus(AgentStatus.RUNNING, "Planning the next step", currentStep + 1);

                CancelToken token = new CancelToken();
                activeToken = token;
                AgentDecision decision;
                try {
                    decision = planner.decide(client, token, session, observation, lastError);
                    modelRetries = 0;
                } catch (AiException e) {
                    if (stopped || e.getKind() == AiException.Kind.CANCELLED) {
                        finish(host, AgentStatus.CANCELLED, "", "Stopped by the user");
                        return;
                    }
                    if (e.isRetryable() && modelRetries < MODEL_RETRY_LIMIT) {
                        modelRetries++;
                        lastError = e.getDetail();
                        session.getLogger().log(0, "model", "retry",
                                e.getKind() + ": " + e.getDetail(), observation.url);
                        PageLoadWaiter.settleQuietly(1500);
                        continue;
                    }
                    finish(host, AgentStatus.FAILED, "", describe(e));
                    return;
                } finally {
                    activeToken = null;
                }

                if (decision.protocolError) {
                    attempts++;
                    lastError = decision.protocolMessage;
                    session.getLogger().log(attempts, "protocol", "invalid",
                            PromptGuard.sanitizeForLog(decision.protocolMessage, 300),
                            observation.url);
                    publishStatus(AgentStatus.RUNNING, decision.protocolMessage, attempts);
                    continue;
                }

                if (decision.isFinal) {
                    finish(host, AgentStatus.COMPLETED, decision.finalAnswer, "");
                    return;
                }

                attempts++;
                currentStep = attempts;
                publishStep(attempts, decision.thought, decision.tool,
                        decision.argumentsSummary());

                String toolName = decision.tool;
                JSONObject arguments = decision.arguments;
                ElementInfo target = observation.findElement(arguments.optString("element_id", ""));
                boolean sensitiveTarget = target != null && target.isPassword();
                boolean agentOwnedTab = "browser.close_tab".equals(toolName)
                        && host.isAgentTab(arguments.optInt("index", -1));

                AgentExecutor.Execution preparation = executor.prepare(
                        toolName, arguments, permissionMode, agentOwnedTab, sensitiveTarget);

                boolean confirmed = false;
                if (preparation.outcome == AgentExecutor.Outcome.REQUIRES_CONFIRMATION) {
                    confirmed = true;
                    if (!askUser(attempts, toolName, arguments, preparation.message)) {
                        lastError = "The user denied " + toolName + ".";
                        session.getMemory().addStep(new AgentStep(attempts, decision.thought,
                                toolName, decision.argumentsSummary(), false, lastError,
                                observation.url, 0, true));
                        session.getLogger().log(attempts, toolName, "denied", lastError,
                                observation.url);
                        publishToolResult(attempts, toolName, false, lastError, observation.url);
                        continue;
                    }
                } else if (!preparation.canRun()) {
                    lastError = preparation.message;
                    session.getMemory().addStep(new AgentStep(attempts, decision.thought,
                            toolName, decision.argumentsSummary(), false, lastError,
                            observation.url, 0, false));
                    session.getLogger().log(attempts, toolName,
                            preparation.outcome == AgentExecutor.Outcome.DENIED ? "denied" : "invalid",
                            lastError, observation.url);
                    publishToolResult(attempts, toolName, false, lastError, observation.url);
                    String signature = repeatSignature(toolName, arguments);
                    if (signature.equals(lastSignature)) {
                        repeatFailures++;
                        if (repeatFailures >= REPEAT_FAILURE_LIMIT) {
                            finish(host, AgentStatus.FAILED, "",
                                    "The agent repeated the same rejected action "
                                            + REPEAT_FAILURE_LIMIT + " times.");
                            return;
                        }
                    } else {
                        repeatFailures = 0;
                        lastSignature = signature;
                    }
                    continue;
                }

                if (stopped) {
                    finish(host, AgentStatus.CANCELLED, "", "Stopped by the user");
                    return;
                }

                long startedAt = System.currentTimeMillis();
                ToolResult result = executor.run(toolContext, preparation.tool, arguments);
                long elapsed = System.currentTimeMillis() - startedAt;
                String urlAfter = result.urlAfter.isEmpty()
                        ? (observation == null ? "" : observation.url) : result.urlAfter;

                session.getMemory().addStep(new AgentStep(attempts, decision.thought, toolName,
                        decision.argumentsSummary(), result.ok, result.message, urlAfter,
                        elapsed, confirmed));
                session.getLogger().log(attempts, toolName, result.ok ? "ok" : "failed",
                        result.message, urlAfter);
                publishToolResult(attempts, toolName, result.ok, result.message, urlAfter);
                lastError = result.ok ? "" : result.message;

                if (result.pageChanged) {
                    PageLoadWaiter.awaitSettle(host, 9000);
                }

                String signature = repeatSignature(toolName, arguments);
                if (!result.ok && signature.equals(lastSignature)) {
                    repeatFailures++;
                    if (repeatFailures >= REPEAT_FAILURE_LIMIT) {
                        finish(host, AgentStatus.FAILED, "",
                                "The agent repeated the same failing action "
                                        + REPEAT_FAILURE_LIMIT + " times and stopped.");
                        return;
                    }
                } else {
                    repeatFailures = 0;
                    lastSignature = signature;
                }
            }
        } catch (AiException e) {
            finish(host, AgentStatus.FAILED, "", describe(e));
        } catch (RuntimeException e) {
            finish(host, AgentStatus.FAILED, "",
                    "The agent stopped unexpectedly: "
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            activeToken = null;
        }
    }

    private boolean askUser(int step, String toolName, JSONObject arguments, String reason) {
        if (confirmationHandler == null) {
            return false;
        }
        ConfirmationPrompt prompt = new ConfirmationPrompt();
        final String description = AgentSafetyManager.describeAction(toolName, arguments);
        publishStatus(AgentStatus.WAITING_CONFIRMATION, reason, step);
        AiExecutors.main().execute(() -> confirmationHandler.requestConfirmation(
                step, toolName, description, reason, prompt));
        boolean approved = prompt.await(CONFIRMATION_TIMEOUT_MS);
        publishStatus(AgentStatus.RUNNING, approved ? "Action approved" : "Action denied", step);
        return approved;
    }

    private void awaitResume() {
        synchronized (pauseLock) {
            while (paused && !stopped) {
                try {
                    pauseLock.wait(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void finish(BrowserHost host, AgentStatus status, String summary, String error) {
        try {
            session.setStatus(status);
            if (summary != null && !summary.isEmpty()) {
                session.setResultSummary(summary);
            }
            if (error != null && !error.isEmpty()) {
                session.setErrorMessage(error);
            }
            session.getLogger().log(currentStep, "agent", status.name(),
                    session.getResultSummary().isEmpty() ? error : session.getResultSummary(),
                    lastKnownUrl());

            if (host != null) {
                host.postAndWait(host::closeAllAgentTabs, 3000);
            }
        } catch (Throwable t) {
            // Cleanup must never kill the background thread (and with it the process).
            Log.w(TAG, "Agent cleanup failed", t);
        }

        final AgentStatus finalStatus = status;
        final String finalSummary = session.getResultSummary();
        final String finalError = session.getErrorMessage();
        AiExecutors.main().execute(() -> {
            if (listener != null) {
                listener.onStatus(finalStatus,
                        finalError.isEmpty() ? finalSummary : finalError,
                        currentStep, session.getMaxSteps());
                listener.onFinal(finalStatus, finalSummary, finalError);
            }
        });
    }

    /** Last URL observed on the page; never touches a WebView, so any thread may call it. */
    private String lastKnownUrl() {
        BrowserObservation observation = session.getMemory().latestObservation();
        return observation == null ? "" : observation.url;
    }

    private void publishStatus(AgentStatus status, String message, int step) {
        final int maxSteps = session.getMaxSteps();
        AiExecutors.main().execute(() -> {
            if (listener != null) {
                listener.onStatus(status, message, step, maxSteps);
            }
        });
    }

    private void publishStep(int step, String thought, String tool, String arguments) {
        AiExecutors.main().execute(() -> {
            if (listener != null) {
                listener.onStep(step, thought, tool, arguments);
            }
        });
    }

    private void publishToolResult(int step, String tool, boolean ok, String message, String url) {
        AiExecutors.main().execute(() -> {
            if (listener != null) {
                listener.onToolResult(step, tool, ok, message, url);
            }
        });
    }

    private static String repeatSignature(String tool, JSONObject arguments) {
        return tool + "|" + (arguments == null ? "{}" : arguments.toString());
    }

    public static String describe(AiException e) {
        if (e == null) {
            return "Unknown AI error";
        }
        return e.getKind().name() + (e.getHttpStatus() > 0 ? " (" + e.getHttpStatus() + ")" : "")
                + ": " + e.getDetail();
    }
}
