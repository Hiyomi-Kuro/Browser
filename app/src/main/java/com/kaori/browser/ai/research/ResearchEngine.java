package com.kaori.browser.ai.research;

import android.content.Context;

import com.kaori.browser.ai.agent.AgentController;
import com.kaori.browser.ai.agent.AgentSession;
import com.kaori.browser.ai.agent.AgentStatus;
import com.kaori.browser.ai.agent.BrowserHost;
import com.kaori.browser.ai.config.AiSettings;
import com.kaori.browser.ai.storage.AgentTaskStore;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Drives a deep-research run.
 *
 * <p>Research reuses the agent engine with a research prompt. There are deliberately no
 * result, page, depth, step or time limits; the prompt instead requires the model to pick
 * the fastest and most token-efficient path. The final report is assembled locally from
 * the sources the tools actually read.</p>
 */
public final class ResearchEngine implements AgentController.Listener {

    public interface Listener {
        void onProgress(int step, int maxSteps, String message);

        void onStep(int step, String thought, String tool, String args);

        void onToolResult(int step, String tool, boolean ok, String message, String url);

        void onFinished(ResearchTask task, AgentSession session, AgentStatus status, String error);
    }

    private final Context appContext;
    private final BrowserHost host;
    private final Listener listener;
    private final AgentController.ConfirmationHandler confirmationHandler;

    private ResearchTask task;
    private AgentSession session;
    private AgentController controller;

    public ResearchEngine(Context context, BrowserHost host, Listener listener,
                          AgentController.ConfirmationHandler confirmationHandler) {
        this.appContext = context.getApplicationContext();
        this.host = host;
        this.listener = listener;
        this.confirmationHandler = confirmationHandler;
    }

    public ResearchTask getTask() {
        return task;
    }

    public AgentSession getSession() {
        return session;
    }

    public boolean isRunning() {
        return controller != null && controller.isRunning();
    }

    public boolean start(String topic) {
        if (isRunning()) {
            return false;
        }
        String language = AiSettings.reportLanguage(appContext);
        ResearchOptions options = ResearchOptions.unlimited(language);

        task = new ResearchTask(topic, options);
        task.setStatus(AgentStatus.RUNNING);

        session = new AgentSession(buildResearchPrompt(topic, language));
        session.setResearch(true);
        session.setMaxSteps(ResearchOptions.UNLIMITED);
        session.setDeadlineMs(0);

        controller = new AgentController(appContext, session, this, confirmationHandler);
        return controller.start(host);
    }

    public void pause() {
        if (controller != null) {
            controller.pause();
        }
    }

    public void resume() {
        if (controller != null) {
            controller.resume();
        }
    }

    public void stop() {
        if (controller != null) {
            controller.stop();
        }
    }

    public static String buildResearchPrompt(String topic, String language) {
        String target = language == null || language.trim().isEmpty()
                ? ResearchOptions.DEFAULT_REPORT_LANGUAGE : language.trim();
        return "研究任务：" + topic + "\n\n"
                + "开始之前，先想清楚最快、最省 token 的路线；每执行一步之前，"
                + "用一句话说明为什么这是当前最优的一步。\n\n"
                + "工作方式：\n"
                + "1. 优先直接打开已知或可推断的 URL，避免逐层点击导航。\n"
                + "2. 一个页面只需要读一次：用 browser.extract_markdown 拿到正文即可，"
                + "不要用 get_text / get_dom 反复观察同一页面。\n"
                + "3. 每次动作都要有明确目的；信息足够就立刻收敛，不要为了“多看几个”而浪费 token。\n"
                + "4. 只记录真实读到的内容：标题、URL、页面写明的日期、任务要求的字段。\n"
                + "5. 如果页面打不开、需要登录、只有摘要或付费墙，直接说明，不要编造；"
                + "搜索摘要不能当作全文。\n"
                + "6. 不同来源之间要比较：指出重复、矛盾与缺失的数据。\n\n"
                + "没有结果数、网页数、深度、步数或时间上限，但这不意味着可以漫无目的地试探；"
                + "请在能高质量完成任务的前提下尽快结束。\n\n"
                + "最后用 final_answer 输出报告：简短引言、带“来源”列的 Markdown 对比表、"
                + "关键发现、局限性说明。报告语言：" + target + "。\n"
                + "禁止编造 DOI、论文标题、发表日期或研究数据。\n";
    }

    @Override
    public void onStatus(AgentStatus status, String message, int step, int maxSteps) {
        if (listener != null) {
            listener.onProgress(step, maxSteps, message);
        }
    }

    @Override
    public void onStep(int step, String thought, String tool, String argsSummary) {
        if (listener != null) {
            listener.onStep(step, thought, tool, argsSummary);
        }
    }

    @Override
    public void onToolResult(int step, String tool, boolean ok, String message, String url) {
        if (listener != null) {
            listener.onToolResult(step, tool, ok, message, url);
        }
    }

    @Override
    public void onFinal(AgentStatus status, String summary, String errorMessage) {
        if (task == null) {
            return;
        }
        task.setStatus(status);
        task.setErrorMessage(errorMessage);
        if (session != null) {
            for (SourceRecord source : session.getMemory().sources()) {
                task.addSource(source);
            }
        }
        String report = ReportGenerator.build(task, session, summary);
        task.setReport(report);
        persist();
        if (listener != null) {
            listener.onFinished(task, session, status, errorMessage);
        }
    }

    private void persist() {
        AgentTaskStore.TaskRecord record = new AgentTaskStore.TaskRecord();
        record.id = task.getId();
        record.kind = AgentTaskStore.KIND_RESEARCH;
        record.task = task.getTopic();
        record.status = task.getStatus().name();
        record.createdAt = task.getCreatedAt();
        record.finishedAt = task.getFinishedAt();
        record.summary = "";
        record.error = task.getErrorMessage();
        record.report = task.getReport();
        record.steps = session == null ? 0 : session.getMemory().stepCount();
        record.maxSteps = 0;
        record.options = task.getOptions().toJson();
        if (session != null) {
            record.log = session.getLogger().toJson();
        }
        JSONArray sources = new JSONArray();
        for (SourceRecord source : task.getSources()) {
            JSONObject object = new JSONObject();
            try {
                object.put("url", source.url);
                object.put("title", source.title);
                object.put("excerpt", source.excerpt);
                object.put("status", source.status);
                object.put("accessedAt", source.accessedAt);
            } catch (Exception ignored) {
            }
            sources.put(object);
        }
        record.sources = sources;
        AgentTaskStore.save(appContext, record);
    }
}
