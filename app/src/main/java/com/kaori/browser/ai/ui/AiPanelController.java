package com.kaori.browser.ai.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import com.kaori.browser.R;
import com.kaori.browser.ai.agent.AgentController;
import com.kaori.browser.ai.agent.AgentSession;
import com.kaori.browser.ai.agent.AgentStatus;
import com.kaori.browser.ai.agent.BrowserHost;
import com.kaori.browser.ai.api.AiException;
import com.kaori.browser.ai.chat.AiChatService;
import com.kaori.browser.ai.chat.AiChatSession;
import com.kaori.browser.ai.chat.AiChatPrompt;
import com.kaori.browser.ai.chat.ChatMessage;
import com.kaori.browser.ai.chat.ChatTool;
import com.kaori.browser.ai.config.AiSettings;
import com.kaori.browser.ai.model.ChatResponse;
import com.kaori.browser.ai.reader.PageContentExtractor;
import com.kaori.browser.ai.reader.PageSnapshot;
import com.kaori.browser.ai.research.ResearchEngine;
import com.kaori.browser.ai.research.ResearchOptions;
import com.kaori.browser.ai.research.ResearchTask;
import com.kaori.browser.ai.research.StructuredTable;
import com.kaori.browser.ai.storage.AgentTaskStore;
import com.kaori.browser.ai.storage.ChatToolStore;
import com.kaori.browser.ai.storage.ExportWriter;
import com.kaori.browser.ai.storage.FileNames;

import java.util.List;

/**
 * The AI bottom sheet: chat, agent control and deep research in one panel.
 *
 * <p>Owned by {@code BrowserActivity}, so switching tabs or pages keeps the conversation
 * and the running task. All long work is delegated to {@link AiChatService},
 * {@link AgentController} and {@link ResearchEngine}.</p>
 */
public final class AiPanelController implements AgentController.Listener,

        ResearchEngine.Listener, AgentController.ConfirmationHandler {

    private enum Mode {
        CHAT,
        AGENT,
        RESEARCH
    }

    private final Activity activity;
    private final BrowserHost host;

    private BottomSheetDialog dialog;
    private RecyclerView messageList;
    private AiMessageAdapter adapter;
    private EditText input;
    private ImageButton sendButton;
    private ImageButton stopButton;
    private TextView statusView;
    private TextView modelChip;
    private View controlBar;

    /** Root of the panel layout; receives the IME bottom inset so the input stays visible. */
    private View panelRoot;
    private Button startButton;
    private Button pauseButton;
    private Button stopAgentButton;
    private ChipGroup chipGroup;
    private TabLayout modeTabs;

    private final AiChatSession chatSession = new AiChatSession();
    private final AiChatService chatService = new AiChatService();

    private AgentController agentController;
    private AgentSession agentSession;
    private ResearchEngine researchEngine;

    private Mode mode = Mode.CHAT;
    private ChatMessage streamingMessage;
    private int streamingIndex = -1;

    public AiPanelController(Activity activity, BrowserHost host) {
        this.activity = activity;
        this.host = host;
    }

    // ---------------------------------------------------------------- lifecycle

    public boolean isVisible() {
        return dialog != null && dialog.isShowing();
    }

    public void show() {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (!AiSettings.load(activity).enabled) {
            promptToEnable();
            return;
        }
        if (dialog == null) {
            build();
        }
        refreshModelChip();
        if (!dialog.isShowing()) {
            dialog.show();
        }
        attachKeyboardInsets();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    /** Called from the activity's onDestroy: stops work and releases the sheet. */
    public void destroy() {
        chatService.cancel();
        if (agentController != null) {
            agentController.stop();
        }
        if (researchEngine != null) {
            researchEngine.stop();
        }
        if (dialog != null) {
            dialog.setOnShowListener(null);
            dialog.dismiss();
        }
        dialog = null;
    }

    private void promptToEnable() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ai_settings_title)
                .setMessage(R.string.ai_disabled)
                .setPositiveButton(R.string.app_ok, (dialogInterface, which) -> {
                    android.content.Intent intent = new android.content.Intent(activity,
                            com.kaori.browser.activity.Settings_Ai.class);
                    activity.startActivity(intent);
                })
                .setNegativeButton(R.string.app_cancel, null)
                .show();
    }

    /**
     * Lifts the panel above the on-screen keyboard.
     *
     * <p>{@link BottomSheetDialog} draws edge to edge (it turns decor fitting off), so
     * {@code SOFT_INPUT_ADJUST_RESIZE} alone never moves the sheet and the keyboard used to
     * cover the input row. The panel pads itself by the IME inset instead, which works on
     * every supported API level and does not fight the sheet's own insets.</p>
     */
    private void attachKeyboardInsets() {
        if (dialog == null || panelRoot == null || dialog.getWindow() == null) {
            return;
        }
        applyKeyboardInsets(dialog.getWindow().getDecorView(), panelRoot);
    }

    private static void applyKeyboardInsets(View decorView, View content) {
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(bars.bottom, ime.bottom);
            if (content.getPaddingBottom() != bottom) {
                content.setPadding(content.getPaddingLeft(), content.getPaddingTop(),
                        content.getPaddingRight(), bottom);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(decorView);
    }

    private void build() {
        dialog = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ai_panel, null);
        dialog.setContentView(view);
        panelRoot = view;

        if (dialog.getWindow() != null) {
            // The sheet is laid out edge to edge and lifts itself with IME insets, so the
            // window must not resize as well or the keyboard offset would apply twice.
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }

        dialog.setOnShowListener(d -> {
            if (dialog == null || dialog.getWindow() == null) {
                return;
            }
            View sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet instanceof FrameLayout) {
                android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
                FrameLayout frame = (FrameLayout) sheet;
                frame.getLayoutParams().height = (int) (metrics.heightPixels * 0.92f);
                frame.requestLayout();
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(frame);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        messageList = view.findViewById(R.id.ai_messages);
        adapter = new AiMessageAdapter(activity);
        LinearLayoutManager manager = new LinearLayoutManager(activity);
        manager.setStackFromEnd(true);
        messageList.setLayoutManager(manager);
        messageList.setAdapter(adapter);

        input = view.findViewById(R.id.ai_input);
        sendButton = view.findViewById(R.id.ai_send);
        stopButton = view.findViewById(R.id.ai_stop_generation);
        statusView = view.findViewById(R.id.ai_status);
        modelChip = view.findViewById(R.id.ai_model_chip);
        controlBar = view.findViewById(R.id.ai_control_bar);
        chipGroup = view.findViewById(R.id.ai_chips);
        modeTabs = view.findViewById(R.id.ai_mode_tabs);

        startButton = view.findViewById(R.id.ai_btn_start);
        pauseButton = view.findViewById(R.id.ai_btn_pause);
        stopAgentButton = view.findViewById(R.id.ai_btn_stop);
        ImageButton closeButton = view.findViewById(R.id.ai_close);

        sendButton.setOnClickListener(v -> onSendClicked());
        stopButton.setOnClickListener(v -> stopAll());
        closeButton.setOnClickListener(v -> dismiss());
        startButton.setOnClickListener(v -> {
            if (mode == Mode.RESEARCH) {
                startResearch(input.getText().toString());
            } else {
                startAgent(input.getText().toString());
            }
        });
        pauseButton.setOnClickListener(v -> togglePause());
        stopAgentButton.setOnClickListener(v -> stopAll());

        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && !event.isShiftPressed();
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                onSendClicked();
                return true;
            }
            return false;
        });

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // Make sure the newest message is visible once the keyboard takes space.
                messageList.postDelayed(this::scrollToBottom, 250);
            }
        });

        modeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mode = tab.getPosition() == 1 ? Mode.AGENT
                        : tab.getPosition() == 2 ? Mode.RESEARCH : Mode.CHAT;
                applyMode();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        applyMode();
        refreshModelChip();
        restoreConversation();
    }

    private void restoreConversation() {
        for (ChatMessage message : chatSession.getMessages()) {
            adapter.add(message);
        }
        if (chatSession.getPage() != null) {
            adapter.add(new ChatMessage(ChatMessage.Kind.SYSTEM,
                    activity.getString(R.string.ai_page_loaded,
                            chatSession.getPage().getTitle(),
                            chatSession.getPage().getCharCount())));
        }
        if (!adapter.isEmpty()) {
            scrollToBottom();
        }
    }

    // ---------------------------------------------------------------- modes

    private void applyMode() {
        if (controlBar == null) {
            return;
        }
        boolean showAgentBar = mode != Mode.CHAT;
        controlBar.setVisibility(showAgentBar ? View.VISIBLE : View.GONE);
        input.setHint(mode == Mode.CHAT ? R.string.ai_input_hint
                : mode == Mode.AGENT ? R.string.ai_input_hint : R.string.ai_research_topic_hint);
        rebuildChips();
        updateAgentButtons();
        if (showAgentBar && statusView != null && statusView.getText().length() == 0) {
            statusView.setText(R.string.ai_agent_status_idle);
        }
    }

    private void rebuildChips() {
        if (chipGroup == null) {
            return;
        }
        chipGroup.removeAllViews();
        if (mode == Mode.CHAT) {
            // Every chat-only action lives behind one Tools button; the buttons
            // themselves are editable in AI settings.
            addChip(R.string.ai_tools, v -> showToolChooser());
        }
        addChip(R.string.ai_tasks, v -> showTaskHistory());
        addChip(R.string.ai_clear, v -> clearConversation());
        addChip(R.string.ai_copy, v -> copyLast());
        addChip(R.string.ai_save, v -> saveArtifact());
    }

    private void showToolChooser() {
        final List<ChatTool> tools = ChatToolStore.load(activity);
        if (tools.isEmpty()) {
            toast(R.string.ai_tools_empty);
            return;
        }
        CharSequence[] labels = new CharSequence[tools.size()];
        for (int i = 0; i < tools.size(); i++) {
            labels[i] = tools.get(i).getName();
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ai_tools_choose)
                .setItems(labels, (dialog, which) -> runTool(tools.get(which)))
                .show();
    }

    private void runTool(final ChatTool tool) {
        if (chatService.isBusy()) {
            toast(R.string.ai_thinking);
            return;
        }
        if (tool.isReadPage() && !tool.hasPrompt()) {
            readPage(null);
            return;
        }
        if (tool.usesSelection()) {
            captureSelection(selection -> {
                if (selection.trim().isEmpty()) {
                    toast(R.string.ai_selection_empty);
                    return;
                }
                String prompt = tool.resolvePrompt(selection, targetLanguage());
                readPage(() -> sendChat(prompt));
            });
            return;
        }
        String prompt = tool.resolvePrompt("", targetLanguage());
        if (prompt.trim().isEmpty()) {
            readPage(null);
        } else {
            readPage(() -> sendChat(prompt));
        }
    }

    private void captureSelection(java.util.function.Consumer<String> callback) {
        WebView webView = host.currentWebView();
        if (webView == null) {
            toast(R.string.ai_no_page);
            return;
        }
        webView.evaluateJavascript(
                "(function(){var s=window.getSelection();return s?String(s.toString()):'';})()",
                value -> callback.accept(unwrapJsString(value)));
    }
    private String targetLanguage() {
        String language = java.util.Locale.getDefault().getLanguage();
        return "zh".equalsIgnoreCase(language) ? "English" : "Simplified Chinese";
    }

    private void addChip(int textRes, View.OnClickListener listener) {
        Chip chip = new Chip(activity);
        chip.setText(textRes);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(listener);
        chipGroup.addView(chip);
    }

    // ---------------------------------------------------------------- chat

    private void onSendClicked() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (mode == Mode.CHAT) {
            sendChat(text);
        } else if (mode == Mode.AGENT) {
            startAgent(text);
        } else {
            startResearch(text);
        }
    }

    private void sendChat(String text) {
        if (chatService.isBusy()) {
            toast(R.string.ai_thinking);
            return;
        }
        AiSettings.Snapshot settings = AiSettings.load(activity);
        if (!settings.enabled) {
            toast(R.string.ai_disabled);
            return;
        }
        if (!settings.hasModel()) {
            toast(R.string.ai_need_model);
            return;
        }

        input.setText("");
        append(new ChatMessage(ChatMessage.Kind.USER, text));
        chatSession.add(new ChatMessage(ChatMessage.Kind.USER, text));

        ChatMessage placeholder = new ChatMessage(ChatMessage.Kind.ASSISTANT, "");
        streamingMessage = placeholder;
        append(placeholder);
        streamingIndex = adapter.getItemCount() - 1;
        setChatBusy(true);

        chatService.send(activity, chatSession, text, chatSession.getPage() != null,
                new AiChatService.Listener() {
                    @Override
                    public void onStarted() {
                        setChatBusy(true);
                    }

                    @Override
                    public void onText(String delta) {
                        streamingMessage = streamingMessage.append(delta);
                        adapter.updateAt(streamingIndex, streamingMessage);
                        scrollToBottom();
                    }

                    @Override
                    public void onComplete(String fullText, ChatResponse response) {
                        ChatMessage done = new ChatMessage(ChatMessage.Kind.ASSISTANT,
                                fullText, response.getModelName());
                        streamingMessage = done;
                        adapter.updateAt(streamingIndex, done);
                        chatSession.add(done);
                        chatSession.recordUsage(response.getPromptTokens(),
                                response.getCompletionTokens());
                        setChatBusy(false);
                        scrollToBottom();
                    }

                    @Override
                    public void onProgress(String message) {
                        append(new ChatMessage(ChatMessage.Kind.SYSTEM, message));
                    }

                    public void onError(AiException error) {
                        if (streamingMessage != null
                                && !streamingMessage.getText().trim().isEmpty()) {
                            // Keep whatever was already produced and report the failure
                            // separately, so a late error never erases the answer.
                            append(new ChatMessage(ChatMessage.Kind.ERROR, friendly(error)));
                        } else {
                            adapter.updateAt(streamingIndex,
                                    new ChatMessage(ChatMessage.Kind.ERROR, friendly(error)));
                        }
                        setChatBusy(false);
                        scrollToBottom();
                    }
                });
    }

    private void setChatBusy(boolean busy) {
        if (sendButton == null) {
            return;
        }
        sendButton.setVisibility(busy ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void readPage(Runnable afterRead) {
        if (host.currentWebView() == null) {
            toast(R.string.ai_no_page);
            return;
        }
        PageContentExtractor.extract(host.currentWebView(), new PageContentExtractor.Callback() {
            @Override
            public void onExtracted(PageSnapshot snapshot) {
                chatSession.setPage(snapshot);
                append(new ChatMessage(ChatMessage.Kind.PAGE,
                        activity.getString(R.string.ai_page_loaded, snapshot.getTitle(),
                                snapshot.getCharCount()), snapshot.getTitle()));
                if (afterRead != null) {
                    afterRead.run();
                }
            }

            @Override
            public void onError(String message) {
                append(new ChatMessage(ChatMessage.Kind.ERROR, message));
            }
        });
    }

    private void runPageAction(String instruction) {
        if (chatService.isBusy()) {
            toast(R.string.ai_thinking);
            return;
        }
        readPage(() -> sendChat(instruction));
    }

    private void explainSelection() {
        if (host.currentWebView() == null) {
            toast(R.string.ai_no_page);
            return;
        }
        host.currentWebView().evaluateJavascript(
                "(function(){var s=window.getSelection();return s?String(s.toString()):'';})()",
                value -> {
                    String selection = unwrapJsString(value);
                    if (selection.trim().isEmpty()) {
                        toast(R.string.ai_selection_empty);
                        return;
                    }
                    readPage(() -> sendChat(AiChatPrompt.explainInstruction(selection)));
                });
    }

    private static String unwrapJsString(String value) {
        if (value == null || "null".equals(value)) {
            return "";
        }
        try {
            Object parsed = new org.json.JSONTokener(value).nextValue();
            return parsed instanceof String ? (String) parsed : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- agent

    private void startAgent(String task) {
        if (task.trim().isEmpty()) {
            toast(R.string.ai_agent_need_task);
            return;
        }
        if (agentController != null && agentController.isRunning()) {
            toast(R.string.ai_agent_status_idle);
            return;
        }
        AiSettings.Snapshot settings = AiSettings.load(activity);
        if (!settings.enabled) {
            toast(R.string.ai_disabled);
            return;
        }
        if (!settings.hasModel()) {
            toast(R.string.ai_need_model);
            return;
        }

        input.setText("");
        append(new ChatMessage(ChatMessage.Kind.USER, task));

        agentSession = new AgentSession(task);
        // Work mode has no step or time limit; the planner prompt requires convergence.
        agentSession.setMaxSteps(0);
        agentSession.setDeadlineMs(0);

        agentController = new AgentController(activity, agentSession, this, this);
        if (!agentController.start(host)) {
            append(new ChatMessage(ChatMessage.Kind.ERROR,
                    activity.getString(R.string.ai_agent_status_failed, "start")));
            return;
        }
        statusView.setText(activity.getString(R.string.ai_agent_status_running, 0,
                settings.maxSteps));
        updateAgentButtons();
    }

    private void togglePause() {
        if (agentController == null || !agentController.isRunning()) {
            return;
        }
        if (agentController.isPaused()) {
            agentController.resume();
        } else {
            agentController.pause();
        }
        updateAgentButtons();
    }

    private void stopAll() {
        chatService.cancel();
        if (agentController != null) {
            agentController.stop();
        }
        if (researchEngine != null) {
            researchEngine.stop();
        }
        setChatBusy(false);
        updateAgentButtons();
    }

    private void updateAgentButtons() {
        if (startButton == null) {
            return;
        }
        boolean running = (agentController != null && agentController.isRunning())
                || (researchEngine != null && researchEngine.isRunning());
        boolean paused = agentController != null && agentController.isPaused();
        startButton.setVisibility(running ? View.GONE : View.VISIBLE);
        pauseButton.setVisibility(running ? View.VISIBLE : View.GONE);
        stopAgentButton.setVisibility(running ? View.VISIBLE : View.GONE);
        pauseButton.setText(paused ? R.string.ai_agent_resume : R.string.ai_agent_pause);
    }

    @Override
    public void requestConfirmation(int step, String toolName, String description,
                                    String reason, AgentController.ConfirmationPrompt prompt) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            prompt.deny();
            return;
        }
        AlertDialog confirm = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ai_agent_confirm_title)
                .setMessage(activity.getString(R.string.ai_agent_confirm_message,
                        description, reason))
                .setPositiveButton(R.string.app_ok, (d, w) -> prompt.allow())
                .setNegativeButton(R.string.app_cancel, (d, w) -> prompt.deny())
                .setCancelable(true)
                .create();
        confirm.setOnCancelListener(d -> prompt.deny());
        confirm.show();
    }

    // ---------------------------------------------------------------- research

    private void startResearch(String topic) {
        if (topic.trim().isEmpty()) {
            toast(R.string.ai_agent_need_task);
            return;
        }
        if (researchEngine != null && researchEngine.isRunning()) {
            toast(R.string.ai_research_done);
            return;
        }
        AiSettings.Snapshot settings = AiSettings.load(activity);
        if (!settings.enabled) {
            toast(R.string.ai_disabled);
            return;
        }
        if (!settings.hasModel()) {
            toast(R.string.ai_need_model);
            return;
        }

        input.setText("");
        append(new ChatMessage(ChatMessage.Kind.USER, topic));
        append(new ChatMessage(ChatMessage.Kind.SYSTEM,
                activity.getString(R.string.ai_report_language) + ": "
                        + AiSettings.reportLanguage(activity)));

        if (researchEngine == null) {
            researchEngine = new ResearchEngine(activity, host, this, this);
        }
        if (!researchEngine.start(topic)) {
            append(new ChatMessage(ChatMessage.Kind.ERROR,
                    activity.getString(R.string.ai_agent_status_failed, "start")));
            return;
        }
        statusView.setText(activity.getString(R.string.ai_agent_status_running, 0, 0));
        updateAgentButtons();
    }
    private static int parseInt(CharSequence text, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(text).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String textOf(CharSequence text, String fallback) {
        String value = text == null ? "" : String.valueOf(text).trim();
        return value.isEmpty() ? fallback : value;
    }

    // ---------------------------------------------------------------- agent callbacks

    @Override
    public void onStatus(AgentStatus status, String message, int step, int maxSteps) {
        if (statusView == null) {
            return;
        }
        switch (status) {
            case PAUSED:
                statusView.setText(activity.getString(R.string.ai_agent_status_paused, step));
                break;
            case WAITING_CONFIRMATION:
                statusView.setText(message);
                break;
            case COMPLETED:
                statusView.setText(activity.getString(R.string.ai_agent_status_completed, step));
                break;
            case FAILED:
                statusView.setText(activity.getString(R.string.ai_agent_status_failed, message));
                break;
            case CANCELLED:
                statusView.setText(R.string.ai_agent_status_cancelled);
                break;
            case STEP_LIMIT:
                statusView.setText(activity.getString(R.string.ai_agent_status_step_limit, step));
                break;
            case TIMEOUT:
                statusView.setText(R.string.ai_agent_status_timeout);
                break;
            default:
                statusView.setText(activity.getString(R.string.ai_agent_status_running,
                        step, maxSteps));
                break;
        }
        updateAgentButtons();
    }

    @Override
    public void onStep(int step, String thought, String tool, String argsSummary) {
        StringBuilder builder = new StringBuilder();
        if (thought != null && !thought.trim().isEmpty()) {
            builder.append(thought.trim()).append("\n\n");
        }
        builder.append(tool).append(' ').append(argsSummary);
        append(new ChatMessage(ChatMessage.Kind.STEP, builder.toString(),
                String.valueOf(step)));
    }

    @Override
    public void onToolResult(int step, String tool, boolean ok, String message, String url) {
        StringBuilder builder = new StringBuilder();
        builder.append(ok ? "OK — " : "FAILED — ").append(message);
        if (url != null && !url.isEmpty()) {
            builder.append("\n").append(url);
        }
        append(new ChatMessage(ChatMessage.Kind.TOOL, builder.toString(), tool));
    }

    @Override
    public void onFinal(AgentStatus status, String summary, String errorMessage) {
        if (status == AgentStatus.COMPLETED && summary != null && !summary.trim().isEmpty()) {
            append(new ChatMessage(ChatMessage.Kind.ASSISTANT, summary));
        } else if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            append(new ChatMessage(ChatMessage.Kind.ERROR, errorMessage));
        }
        persistAgentTask(status, summary, errorMessage);
        updateAgentButtons();
    }

    /** Saves the finished agent run so it appears in the task history. */
    private void persistAgentTask(AgentStatus status, String summary, String error) {
        if (agentSession == null) {
            return;
        }
        AgentTaskStore.TaskRecord record = new AgentTaskStore.TaskRecord();
        record.id = agentSession.getId();
        record.kind = AgentTaskStore.KIND_AGENT;
        record.task = agentSession.getTask();
        record.status = status == null ? "UNKNOWN" : status.name();
        record.createdAt = agentSession.getCreatedAt();
        record.finishedAt = agentSession.getFinishedAt();
        record.summary = summary == null ? "" : summary;
        record.error = error == null ? "" : error;
        record.steps = agentSession.getMemory().stepCount();
        record.maxSteps = agentSession.getMaxSteps();
        record.log = agentSession.getLogger().toJson();
        org.json.JSONArray sources = new org.json.JSONArray();
        for (com.kaori.browser.ai.research.SourceRecord source
                : agentSession.getMemory().sources()) {
            org.json.JSONObject object = new org.json.JSONObject();
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
        AgentTaskStore.save(activity, record);
    }

    // ---------------------------------------------------------------- research callbacks

    @Override
    public void onProgress(int step, int maxSteps, String message) {
        if (statusView != null) {
            statusView.setText(activity.getString(R.string.ai_agent_status_running, step, maxSteps)
                    + " · " + message);
        }
    }

    @Override
    public void onFinished(ResearchTask task, AgentSession session, AgentStatus status,
                           String error) {
        updateAgentButtons();
        if (task == null) {
            return;
        }
        if (task.getReport() != null && !task.getReport().trim().isEmpty()) {
            append(new ChatMessage(ChatMessage.Kind.REPORT, task.getReport(),
                    activity.getString(R.string.ai_research_report)));
            saveResearchReport(task);
        } else if (error != null && !error.isEmpty()) {
            append(new ChatMessage(ChatMessage.Kind.ERROR, error));
        }
        if (statusView != null) {
            statusView.setText(activity.getString(R.string.ai_research_done,
                    task.getSources().size()));
        }
    }

    private void saveResearchReport(ResearchTask task) {
        try {
            String fileName = FileNames.timestamped("AI research", ".md");
            ExportWriter.Saved saved = ExportWriter.saveText(activity, fileName,
                    "text/markdown", task.getReport());
            append(new ChatMessage(ChatMessage.Kind.SYSTEM,
                    activity.getString(R.string.ai_saved_to, saved.describe())));
        } catch (Exception e) {
            append(new ChatMessage(ChatMessage.Kind.SYSTEM,
                    "Report not saved: " + (e.getMessage() == null
                            ? e.getClass().getSimpleName() : e.getMessage())));
        }
    }

    // ---------------------------------------------------------------- actions

    private void clearConversation() {
        chatService.cancel();
        chatSession.reset();
        adapter.clear();
        streamingMessage = null;
        setChatBusy(false);
        toast(R.string.app_done);
    }

    private void copyLast() {
        String text = adapter.lastText();
        if (text.trim().isEmpty()) {
            toast(R.string.list_empty);
            return;
        }
        ClipboardManager manager =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("AI", text));
        }
        toast(R.string.ai_copied);
    }

    private void saveArtifact() {
        String text = adapter.lastText();
        if (text.trim().isEmpty()) {
            toast(R.string.list_empty);
            return;
        }
        StructuredTable table = StructuredTable.parseMarkdownTable(text);
        if (table == null || table.rowCount() == 0) {
            saveMarkdown(text);
            return;
        }
        table.dedupe();
        final StructuredTable parsed = table;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ai_save)
                .setItems(new CharSequence[]{"Markdown", "CSV", "JSON"}, (d, which) -> {
                    if (which == 1) {
                        saveText(FileNames.timestamped("AI data", ".csv"), "text/csv",
                                parsed.toCsv());
                    } else if (which == 2) {
                        saveText(FileNames.timestamped("AI data", ".json"), "application/json",
                                parsed.toJson());
                    } else {
                        saveMarkdown(text);
                    }
                })
                .show();
    }

    private void saveMarkdown(String text) {
        saveText(FileNames.timestamped("AI result", ".md"), "text/markdown", text);
    }

    private void saveText(String fileName, String mime, String content) {
        try {
            ExportWriter.Saved saved = ExportWriter.saveText(activity, fileName, mime, content);
            toast(activity.getString(R.string.ai_saved_to, saved.describe()));
        } catch (Exception e) {
            toast("Save failed: " + (e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private void showTaskHistory() {
        List<AgentTaskStore.TaskRecord> records = AgentTaskStore.list(activity);
        if (records.isEmpty()) {
            toast(R.string.ai_tasks_empty);
            return;
        }
        CharSequence[] labels = new CharSequence[records.size()];
        for (int i = 0; i < records.size(); i++) {
            AgentTaskStore.TaskRecord record = records.get(i);
            labels[i] = record.title() + "\n" + activity.getString(R.string.ai_task_summary,
                    record.kind, record.status, record.steps);
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ai_tasks_title)
                .setItems(labels, (d, which) -> showTaskDetails(records.get(which)))
                .show();
    }

    private void showTaskDetails(AgentTaskStore.TaskRecord record) {
        StringBuilder body = new StringBuilder();
        body.append(record.status).append(" · ").append(record.steps).append('/')
                .append(record.maxSteps).append('\n');
        if (!record.error.isEmpty()) {
            body.append(record.error).append('\n');
        }
        if (!record.summary.isEmpty()) {
            String summary = record.summary;
            body.append(summary.length() > 400 ? summary.substring(0, 400) + "…" : summary);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(record.title())
                .setMessage(body.toString())
                .setPositiveButton(R.string.ai_task_view,
                        (d, w) -> showReportText(record.title(), record.report))
                .setNeutralButton(R.string.ai_task_delete, (d, w) -> {
                    AgentTaskStore.delete(activity, record.id);
                    toast(R.string.ai_task_deleted);
                });
        if (AgentTaskStore.KIND_RESEARCH.equals(record.kind)
                && !"COMPLETED".equals(record.status)) {
            builder.setNegativeButton(R.string.ai_task_resume, (d, w) -> confirmResume(record));
        } else {
            builder.setNegativeButton(R.string.app_cancel, null);
        }
        builder.show();
    }

    private void confirmResume(AgentTaskStore.TaskRecord record) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ai_task_resume)
                .setMessage(R.string.ai_task_resume_notice)
                .setPositiveButton(R.string.app_ok, (d, w) -> startResearch(record.task))
                .setNegativeButton(R.string.app_cancel, null)
                .show();
    }

    private void showReportText(String title, String report) {
        ScrollView scroll = new ScrollView(activity);
        TextView text = new TextView(activity);
        text.setText(report == null || report.isEmpty() ? activity.getString(R.string.list_empty)
                : report);
        text.setTextIsSelectable(true);
        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        scroll.addView(text);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(R.string.app_ok, null)
                .show();
    }

    // ---------------------------------------------------------------- helpers

    private void append(ChatMessage message) {
        if (adapter == null) {
            return;
        }
        adapter.add(message);
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (messageList != null && adapter != null && adapter.getItemCount() > 0) {
            messageList.post(() -> messageList.scrollToPosition(adapter.getItemCount() - 1));
        }
    }

    private void refreshModelChip() {
        if (modelChip == null) {
            return;
        }
        AiSettings.Snapshot settings = AiSettings.load(activity);
        modelChip.setText(settings.hasModel() ? settings.model
                : activity.getString(R.string.ai_need_model));
    }

    private String friendly(AiException error) {
        if (error == null) {
            return activity.getString(R.string.ai_error_generic);
        }
        int res;
        switch (error.getKind()) {
            case AUTHENTICATION:
                res = R.string.ai_error_auth;
                break;
            case FORBIDDEN:
                res = R.string.ai_error_forbidden;
                break;
            case RATE_LIMIT:
                res = R.string.ai_error_rate;
                break;
            case TIMEOUT:
                res = R.string.ai_error_timeout;
                break;
            case NETWORK:
                res = R.string.ai_error_network;
                break;
            case PARSE:
                res = R.string.ai_error_parse;
                break;
            case EMPTY_RESPONSE:
                res = R.string.ai_error_empty;
                break;
            case CANCELLED:
                res = R.string.ai_error_cancelled;
                break;
            case NO_API_KEY:
                res = R.string.ai_need_key;
                break;
            case NOT_CONFIGURED:
                res = R.string.ai_need_model;
                break;
            default:
                res = R.string.ai_error_generic;
                break;
        }
        String detail = error.getDetail();
        return activity.getString(res) + (detail.isEmpty() ? "" : "\n" + detail);
    }

    private void toast(int res) {
        Toast.makeText(activity, res, Toast.LENGTH_SHORT).show();
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }

    private void hideKeyboard() {
        if (input == null) {
            return;
        }
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }
}
