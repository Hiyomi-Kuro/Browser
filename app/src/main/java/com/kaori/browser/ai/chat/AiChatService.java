package com.kaori.browser.ai.chat;

import android.content.Context;

import com.kaori.browser.R;
import com.kaori.browser.ai.AiExecutors;
import com.kaori.browser.ai.api.AiClient;
import com.kaori.browser.ai.api.AiClientFactory;
import com.kaori.browser.ai.api.AiException;
import com.kaori.browser.ai.api.CancelToken;
import com.kaori.browser.ai.config.AiSettings;
import com.kaori.browser.ai.model.AiMessage;
import com.kaori.browser.ai.model.ChatRequest;
import com.kaori.browser.ai.model.ChatResponse;
import com.kaori.browser.ai.reader.ContentBudget;
import com.kaori.browser.ai.reader.PageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs one reading-assistant request at a time, off the main thread.
 *
 * <p>Long answers are produced in batches. Whenever the provider stops because it reached
 * the output cap, the service asks it to continue from where it stopped and appends the
 * text, with no fixed number of rounds: it keeps going until the model finishes on its own,
 * the user cancels, or a continuation returns nothing at all. A page larger than
 * {@link #SINGLE_PASS_CHARS} is read segment by segment first, so the middle of a long
 * article is never dropped.</p>
 */
public final class AiChatService {

    /** Largest page block sent in one request. */
    private static final int SINGLE_PASS_CHARS = 60000;
    /** Segment size for a page that must be read in parts. */
    private static final int SEGMENT_CHARS = 20000;
    /** Upper bound for one segment's notes inside the final synthesis. */
    private static final int SEGMENT_NOTE_CHARS = 3000;

    private static final String CONTINUE_INSTRUCTION =
            "Continue exactly where your previous message stopped. Do not repeat text that "
                    + "is already written, do not restart the answer, do not comment on the "
                    + "fact that you are continuing. Output only the remaining content, in the "
                    + "same language and format as before.";

    public interface Listener {
        void onStarted();

        void onText(String delta);

        void onComplete(String fullText, ChatResponse response);

        void onError(AiException error);

        /** Optional progress note, e.g. long-page segmentation or an answer continuation. */
        default void onProgress(String message) {
        }
    }

    private final AtomicReference<CancelToken> active = new AtomicReference<>(null);
    private volatile Context appContext;

    public boolean isBusy() {
        return active.get() != null;
    }

    public void cancel() {
        CancelToken token = active.getAndSet(null);
        if (token != null) {
            token.cancel();
        }
    }

    /**
     * Sends the conversation. The caller must already have appended the user turn to the
     * session. Callbacks are delivered on the main thread.
     */
    public void send(Context context, AiChatSession session, String userPrompt,
                     boolean includePage, Listener listener) {
        if (session == null || listener == null) {
            return;
        }
        cancel();

        final CancelToken token = new CancelToken();
        active.set(token);

        final Context contextRef = context.getApplicationContext();
        this.appContext = contextRef;
        final List<AiMessage> history = new ArrayList<>(AiChatPrompt.history(
                session.getMessages(), AiChatPrompt.MAX_HISTORY_MESSAGES,
                AiChatPrompt.MAX_HISTORY_CHARS));
        final PageSnapshot page = includePage ? session.getPage() : null;

        listener.onStarted();

        AiExecutors.background().execute(() -> {
            AiException failure = null;
            String fullText = "";
            ChatResponse response = null;
            try {
                AiSettings.Snapshot settings = AiSettings.load(contextRef);
                AiClient client = AiClientFactory.create(contextRef);
                if (!settings.hasModel()) {
                    throw new AiException(AiException.Kind.NOT_CONFIGURED, 0,
                            "No model selected. Choose a model in AI settings.");
                }

                if (page != null && page.getMarkdown().length() > SINGLE_PASS_CHARS) {
                    response = segmentedRead(client, settings, token, session, page, userPrompt,
                            listener);
                } else {
                    response = singlePass(client, settings, token, session, history, page,
                            userPrompt, listener);
                }
                fullText = response.getContent();
                AiUsageStats.recordSuccess(contextRef,
                        response.getPromptTokens(), response.getCompletionTokens());
            } catch (AiException e) {
                failure = e;
                AiUsageStats.recordError(contextRef, e.getKind().name());
            } catch (RuntimeException e) {
                failure = new AiException(AiException.Kind.UNKNOWN, 0,
                        e.getMessage() == null ? "Unexpected failure" : e.getMessage(), e);
                AiUsageStats.recordError(contextRef, "UNKNOWN");
            }

            final AiException error = failure;
            final String text = fullText;
            final ChatResponse completed = response;
            AiExecutors.main().execute(() -> {
                active.compareAndSet(token, null);
                if (error != null) {
                    if (error.getKind() != AiException.Kind.CANCELLED) {
                        listener.onError(error);
                    }
                } else if (completed != null) {
                    listener.onComplete(text, completed);
                }
            });
        });
    }

    private ChatResponse singlePass(AiClient client, AiSettings.Snapshot settings,
                                    CancelToken token, AiChatSession session,
                                    List<AiMessage> messages, PageSnapshot page,
                                    String userPrompt, Listener listener) throws AiException {
        List<AiMessage> turns = new ArrayList<>(messages);
        String pageBlock = page == null ? "" : page.toPromptBlock() + "\n\n";

        int lastUser = -1;
        for (int i = turns.size() - 1; i >= 0; i--) {
            if (AiMessage.ROLE_USER.equals(turns.get(i).getRole())) {
                lastUser = i;
                break;
            }
        }
        if (lastUser >= 0) {
            turns.set(lastUser, new AiMessage(AiMessage.ROLE_USER,
                    pageBlock + turns.get(lastUser).getContent()));
        } else {
            turns.add(new AiMessage(AiMessage.ROLE_USER,
                    pageBlock + (userPrompt == null ? "" : userPrompt)));
        }

        List<AiMessage> complete = new ArrayList<>();
        complete.add(AiMessage.system(AiChatPrompt.systemPrompt(null)));
        complete.addAll(turns);
        return chatUntilComplete(client, settings, token, session, complete, listener);
    }

    /**
     * Reads the whole page in order: one request per segment to extract everything
     * relevant, then one streaming request that synthesises the notes.
     */
    /**
     * Reads the whole page in order, applying the user's actual task to each segment
     * (translation stays a translation, extraction stays an extraction), then joins the
     * segment results in order. Nothing is dropped and no paraphrasing pass is applied.
     */
    /**
     * Reads the whole page in order, applying the user's actual task to each segment
     * (translation stays a translation, extraction stays an extraction).
     *
     * <p>Segments are independent: a segment that fails is retried once, then noted and
     * skipped so the text produced by the other segments is kept instead of being thrown
     * away. Only when no segment produced anything is the whole request reported as failed.</p>
     */
    private ChatResponse segmentedRead(AiClient client, AiSettings.Snapshot settings,
                                       CancelToken token, AiChatSession session,
                                       PageSnapshot page, String userPrompt, Listener listener)
            throws AiException {
        List<String> segments = ContentBudget.segments(page.getMarkdown(), SEGMENT_CHARS);
        if (segments.isEmpty()) {
            segments = new ArrayList<>();
            segments.add(page.getMarkdown());
        }
        progress(listener, appContext == null ? ""
                : appContext.getString(R.string.ai_reading_segments, segments.size()));

        StringBuilder answer = new StringBuilder();
        List<String> failures = new ArrayList<>();
        String finish = "";
        int promptTokens = 0;
        int completionTokens = 0;
        String firstError = null;

        for (int i = 0; i < segments.size(); i++) {
            token.throwIfCancelled();

            ChatResponse part = null;
            AiException failure = null;
            for (int attempt = 0; attempt < 2 && part == null; attempt++) {
                try {
                    part = readSegment(client, settings, token, session, page, userPrompt,
                            segments.get(i), i, segments.size(), listener);
                } catch (AiException e) {
                    if (e.getKind() == AiException.Kind.CANCELLED) {
                        throw e;
                    }
                    failure = e;
                    if (firstError == null) {
                        firstError = e.getDetail();
                    }
                    if (!e.isRetryable()) {
                        break;
                    }
                }
            }

            if (part == null) {
                failures.add("第 " + (i + 1) + " 段：" 
                        + (failure == null ? "未知原因" : failure.getDetail()));
                progress(listener, appContext == null ? ""
                        : appContext.getString(R.string.ai_segment_failed,
                        i + 1, segments.size()));
                continue;
            }

            String text = part.getContent() == null ? "" : part.getContent().trim();
            if (!text.isEmpty()) {
                if (answer.length() > 0) {
                    answer.append("\n\n");
                }
                answer.append(text);
            }
            promptTokens += part.getPromptTokens();
            completionTokens += part.getCompletionTokens();
            finish = part.getFinishReason();
            progress(listener, appContext == null ? ""
                    : appContext.getString(R.string.ai_segment_done, i + 1, segments.size()));
        }

        if (answer.length() == 0) {
            throw new AiException(AiException.Kind.EMPTY_RESPONSE, 200,
                    firstError == null ? "No segment produced any content." : firstError);
        }
        if (!failures.isEmpty()) {
            answer.append("\n\n---\n\n> 以下段落未能完成，结果可能不完整：\n");
            for (String record : failures) {
                answer.append("> - ").append(record).append('\n');
            }
        }

        ChatResponse result = new ChatResponse();
        result.setContent(answer.toString());
        result.setFinishReason(finish);
        result.setPromptTokens(promptTokens);
        result.setCompletionTokens(completionTokens);
        return result;
    }

    private ChatResponse readSegment(AiClient client, AiSettings.Snapshot settings,
                                     CancelToken token, AiChatSession session, PageSnapshot page,
                                     String userPrompt, String segment, int index, int total,
                                     Listener listener) throws AiException {
        List<AiMessage> turns = new ArrayList<>();
        turns.add(AiMessage.system("你在处理一个长网页的第 " + (index + 1) + "/" + total
                + " 段。请只针对本段内容完成下面的任务；不要涉及其他段落，不要编造，"
                + "本段与任务无关时只回复“本段无相关内容”。\n"
                + "任务：" + (userPrompt == null ? "" : userPrompt)));
        turns.add(AiMessage.user("标题：" + page.getTitle() + "\nURL：" + page.getUrl()
                + "\n\n<untrusted_page_content>\n" + segment
                + "\n</untrusted_page_content>"));
        return chatUntilComplete(client, settings, token, session, turns, listener);
    }

    /**
     * Sends {@code turns} and keeps asking the model to continue while it stops on the
     * output cap with new text produced. There is no round limit: the loop ends when the
     * model finishes normally, the user cancels, or a continuation adds no new text (which
     * would otherwise repeat the same request forever).
     */
    private ChatResponse chatUntilComplete(AiClient client, AiSettings.Snapshot settings,
                                           CancelToken token, AiChatSession session,
                                           List<AiMessage> turns, Listener listener)
            throws AiException {
        boolean stream = listener != null;
        ChatResponse response = send(client, settings, session, turns, token, listener, stream);
        if (!response.hasContent() && !response.hasToolCalls()) {
            throw new AiException(AiException.Kind.EMPTY_RESPONSE, 200,
                    "The model returned no visible text (finish_reason="
                            + (response.getFinishReason().isEmpty() ? "-"
                            : response.getFinishReason()) + ").",
                    response.getFinishReason());
        }

        StringBuilder combined = new StringBuilder(response.getContent());
        int promptTokens = response.getPromptTokens();
        int completionTokens = response.getCompletionTokens();
        String finish = response.getFinishReason();
        String modelName = response.getModelName();
        int rounds = 0;

        while (isLengthStop(finish) && combined.length() > 0 && !token.isCancelled()) {
            rounds++;
            progress(listener, appContext == null ? ""
                    : appContext.getString(R.string.ai_continuing, rounds));

            List<AiMessage> continuation = new ArrayList<>(turns);
            continuation.add(AiMessage.assistant(combined.toString()));
            continuation.add(AiMessage.user(CONTINUE_INSTRUCTION));

            ChatResponse part = send(client, settings, session, continuation, token, listener,
                    stream);
            promptTokens += part.getPromptTokens();
            completionTokens += part.getCompletionTokens();
            finish = part.getFinishReason();
            if (!part.hasContent()) {
                // No new text: another round would repeat this request with the same result.
                break;
            }
            combined.append(part.getContent());
        }

        ChatResponse result = new ChatResponse();
        result.setContent(combined.toString());
        result.setFinishReason(finish);
        result.setModelName(modelName);
        result.setPromptTokens(promptTokens);
        result.setCompletionTokens(completionTokens);
        return result;
    }

    private ChatResponse send(AiClient client, AiSettings.Snapshot settings, AiChatSession session,
                              List<AiMessage> turns, CancelToken cancelToken, Listener listener,
                              boolean stream) throws AiException {
        ChatRequest request = new ChatRequest(settings.model, settings.temperature,
                settings.maxTokens, stream && settings.stream, session.getId(), null);
        for (AiMessage message : turns) {
            request.add(message);
        }
        return client.chat(request, cancelToken, streamListener(listener));
    }

    private static boolean isLengthStop(String finishReason) {
        return "length".equalsIgnoreCase(finishReason)
                || "max_tokens".equalsIgnoreCase(finishReason);
    }

    private AiClient.StreamListener streamListener(final Listener listener) {
        if (listener == null) {
            return null;
        }
        return new AiClient.StreamListener() {
            @Override
            public void onTextDelta(String text) {
                AiExecutors.main().execute(() -> listener.onText(text));
            }

            @Override
            public void onReasoningDelta(String text) {
                // Reasoning is not rendered as the answer.
            }
        };
    }

    private void progress(final Listener listener, final String message) {
        if (listener == null) {
            return;
        }
        AiExecutors.main().execute(() -> listener.onProgress(message));
    }
}
