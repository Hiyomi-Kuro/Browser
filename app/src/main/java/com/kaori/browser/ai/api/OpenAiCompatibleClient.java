package com.kaori.browser.ai.api;

import com.kaori.browser.ai.config.AiSettings;
import com.kaori.browser.ai.model.ChatRequest;
import com.kaori.browser.ai.model.ChatResponse;
import com.kaori.browser.ai.model.SessionIds;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLException;

/**
 * HTTPS OpenAI-compatible chat client.
 *
 * <p>Supports streaming SSE and plain JSON bodies, native tool declarations, stable
 * {@code x-opencode-session} routing, cooperative cancellation and classified failures.
 * The client never logs the API key.</p>
 */
public final class OpenAiCompatibleClient implements AiClient {

    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int MAX_BODY_CHARS = 8 * 1024 * 1024;
    private static final int MAX_ERROR_CHARS = 2000;

    /** Above this configured budget a truncation retry would only exceed provider caps. */
    private static final int MAX_BOOST_TRIGGER = 16384;
    private static final int BOOST_CAP = 32768;

    private final AiSettings.Snapshot settings;
    private final String apiKey;

    public OpenAiCompatibleClient(AiSettings.Snapshot settings, String apiKey) {
        this.settings = settings;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public ChatResponse chat(ChatRequest request, CancelToken token, StreamListener listener)
            throws AiException {
        if (token == null) {
            token = new CancelToken();
        }
        try {
            return executeChat(request, token, listener, true);
        } catch (AiException e) {
            // Some reasoning models reject an explicit temperature; retry once without it.
            if (isReasoningTruncation(e) && !token.isCancelled()
                    && settings.maxTokens < MAX_BOOST_TRIGGER) {
                int boosted = boostedTokenBudget(settings.maxTokens);
                try {
                    return executeChat(request.withMaxTokens(boosted), token, listener, true);
                } catch (AiException second) {
                    if (isReasoningTruncation(second)) {
                        throw new AiException(AiException.Kind.EMPTY_RESPONSE, 200,
                                "The model spent the whole output budget on reasoning even after "
                                        + "raising max tokens to " + boosted
                                        + ". Use a non-reasoning model or raise the max output "
                                        + "tokens in AI settings.", second.getFinishReason(), second);
                    }
                    throw second;
                }
            }
            if (e.getKind() == AiException.Kind.BAD_REQUEST
                    && mentionsTemperature(e) && !token.isCancelled()) {
                return executeChat(request, token, listener, false);
            }
            throw e;
        }
    }

    private ChatResponse executeChat(ChatRequest request, CancelToken token,
                                     StreamListener listener, boolean includeTemperature)
            throws AiException {
        requireKey();
        token.throwIfCancelled();

        if (request == null || request.getModel().trim().isEmpty()) {
            throw new AiException(AiException.Kind.NOT_CONFIGURED, 0,
                    "No model selected. Choose a model in AI settings.");
        }

        JSONObject payload;
        try {
            payload = request.toJson(includeTemperature);
        } catch (JSONException e) {
            throw new AiException(AiException.Kind.BAD_REQUEST, 0,
                    "Unable to build the request body", e);
        }

        HttpURLConnection connection = open(settings.chatUrl(), "POST", token, request.getSessionId());
        try {
            writeBody(connection, payload.toString());
            int status = resolveStatus(connection, token);
            if (status >= 400) {
                throw httpError(status, readBody(connection.getErrorStream()));
            }
            if (request.isStream()) {
                return readStream(connection, token, listener);
            }
            return AiResponseParser.parseChat(readBody(inputStreamOf(connection)));
        } finally {
            disconnect(connection);
            token.clearAbortHook();
        }
    }

    @Override
    public List<String> listModels(CancelToken token) throws AiException {
        if (token == null) {
            token = new CancelToken();
        }
        requireKey();
        token.throwIfCancelled();

        String url = settings.modelsUrl();
        HttpURLConnection connection = open(url, "GET", token, null);
        try {
            int status = resolveStatus(connection, token);
            if (status >= 400) {
                throw httpError(status, readBody(connection.getErrorStream()));
            }
            return AiResponseParser.parseModels(readBody(inputStreamOf(connection)));
        } finally {
            disconnect(connection);
            token.clearAbortHook();
        }
    }

    @Override
    public ChatResponse testConnection(CancelToken token) throws AiException {
        if (token == null) {
            token = new CancelToken();
        }
        requireKey();
        if (settings.model == null || settings.model.trim().isEmpty()) {
            listModels(token);
            ChatResponse response = new ChatResponse();
            response.setContent("Models endpoint reachable");
            return response;
        }
        ChatRequest request = new ChatRequest(
                settings.model, -1, 64, false, SessionIds.newSessionId(), null);
        request.addSystem("You are a connectivity probe. Reply with the single word: pong.");
        request.addUser("ping");
        return chat(request, token, null);
    }

    private static boolean isReasoningTruncation(AiException e) {
        return e != null && e.getKind() == AiException.Kind.EMPTY_RESPONSE
                && e.isLengthTruncated();
    }

    /** One automatic budget boost: at least 8192, otherwise double, capped at 32768. */
    private static int boostedTokenBudget(int configured) {
        int base = configured <= 0 ? 8192 : Math.max(8192, configured * 2);
        return Math.min(BOOST_CAP, base);
    }

    private void requireKey() throws AiException {
        if (apiKey.trim().isEmpty()) {
            throw new AiException(AiException.Kind.NO_API_KEY, 0,
                    "No API key configured. Add one in AI settings.");
        }
    }

    private HttpURLConnection open(String url, String method, CancelToken token, String sessionId)
            throws AiException {
        if (url == null || url.trim().isEmpty()) {
            throw new AiException(AiException.Kind.NOT_CONFIGURED, 0, "No API endpoint configured");
        }
        URL target;
        try {
            target = new URL(url.trim());
        } catch (MalformedURLException e) {
            throw new AiException(AiException.Kind.BAD_REQUEST, 0, "Invalid API URL: " + url, e);
        }

        String protocol = target.getProtocol() == null
                ? "" : target.getProtocol().toLowerCase(Locale.US);
        if (!"https".equals(protocol) && !"http".equals(protocol)) {
            throw new AiException(AiException.Kind.BAD_REQUEST, 0,
                    "Unsupported API scheme: " + protocol);
        }

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) target.openConnection();
        } catch (IOException e) {
            throw new AiException(AiException.Kind.NETWORK, 0,
                    "Unable to open a connection to the API", e);
        }

        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        // 0 means infinite: a long translation must not be cut off by a read timeout.
        connection.setReadTimeout(settings.timeoutSeconds <= 0
                ? 0 : settings.timeoutSeconds * 1000);
        connection.setUseCaches(false);
        try {
            connection.setRequestMethod(method);
        } catch (IOException e) {
            throw new AiException(AiException.Kind.BAD_REQUEST, 0, "Unsupported HTTP method", e);
        }
        connection.setDoInput(true);
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
        }
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        connection.setRequestProperty("User-Agent", AiSettings.USER_AGENT);
        if (sessionId != null && !sessionId.isEmpty()) {
            connection.setRequestProperty("x-opencode-session", sessionId);
        }

        final HttpURLConnection reference = connection;
        token.setAbortHook(() -> {
            try {
                reference.disconnect();
            } catch (RuntimeException ignored) {
                // Disconnecting twice is harmless.
            }
        });

        return connection;
    }



    private void writeBody(HttpURLConnection connection, String body) throws AiException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
            output.flush();
        } catch (IOException e) {
            throw ioFailure(e, null);
        }
    }

    private int resolveStatus(HttpURLConnection connection, CancelToken token) throws AiException {
        try {
            return connection.getResponseCode();
        } catch (IOException e) {
            throw ioFailure(e, token);
        }
    }

    private InputStream inputStreamOf(HttpURLConnection connection) throws AiException {
        try {
            return connection.getInputStream();
        } catch (IOException e) {
            throw ioFailure(e, null);
        }
    }

    private ChatResponse readStream(HttpURLConnection connection, CancelToken token,
                                    StreamListener listener) throws AiException {
        ChatResponse response = new ChatResponse();
        StreamAccumulator accumulator = new StreamAccumulator();
        StringBuilder raw = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                token.throwIfCancelled();
                if (raw.length() < MAX_BODY_CHARS) {
                    raw.append(line).append('\n');
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                accumulator.accept(payload, response, listener);
            }
        } catch (IOException e) {
            throw ioFailure(e, token);
        } catch (AiException e) {
            throw e;
        }

        if (!response.hasContent() && !response.hasToolCalls()) {
            String body = raw.toString().trim();
            if (body.startsWith("{")) {
                return AiResponseParser.parseChat(body);
            }
            throw emptyStreamError(response);
        }
        accumulator.finish(response);
        return response;
    }

    /**
     * Classifies a stream that produced no visible text. The finish reason is preserved so
     * the caller can retry with a bigger budget when the model spent it all on reasoning.
     */
    private static AiException emptyStreamError(ChatResponse response) {
        String finish = response.getFinishReason();
        int reasoningChars = response.getReasoning().length();
        String detail;
        if (("length".equalsIgnoreCase(finish) || "max_tokens".equalsIgnoreCase(finish))
                && reasoningChars > 0) {
            detail = "Model exhausted its output budget while reasoning: no visible text was "
                    + "produced (reasoning " + reasoningChars + " characters, finish_reason="
                    + finish + ").";
        } else {
            detail = "Streaming response contained no content (finish_reason="
                    + (finish.isEmpty() ? "-" : finish) + ", reasoning_chars="
                    + reasoningChars + ").";
        }
        return new AiException(AiException.Kind.EMPTY_RESPONSE, 200, detail, finish);
    }

    private String readBody(InputStream input) throws AiException {
        if (input == null) {
            return "";
        }
        try (InputStream stream = input;
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            int total = 0;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                total += read;
                if (total > MAX_BODY_CHARS) {
                    break;
                }
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AiException(AiException.Kind.NETWORK, 0,
                    "Unable to read the API response", e);
        }
    }

    private AiException httpError(int status, String body) {
        AiException.Kind kind = AiException.fromHttpStatus(status);
        String message = "";

        if (body != null && !body.trim().isEmpty()) {
            try {
                JSONObject root = new JSONObject(body.trim());
                message = AiResponseParser.errorMessage(root);
                if (message.isEmpty()) {
                    message = root.optString("message", "");
                }
                AiException.Kind classified = AiResponseParser.classify(message);
                if (classified != AiException.Kind.UNKNOWN) {
                    kind = classified;
                }
            } catch (JSONException ignored) {
                message = body.trim();
            }
        }

        if (message.length() > MAX_ERROR_CHARS) {
            message = message.substring(0, MAX_ERROR_CHARS);
        }
        String prefix;
        switch (status) {
            case 401:
                prefix = "Authentication failed (401)";
                break;
            case 403:
                prefix = "Access forbidden (403)";
                break;
            case 429:
                prefix = "Rate limited (429)";
                break;
            case 404:
                prefix = "Endpoint or model not found (404)";
                break;
            default:
                prefix = status >= 500 ? "Provider server error (" + status + ")"
                        : "Request rejected (" + status + ")";
                break;
        }
        if (message.isEmpty()) {
            message = prefix;
        } else {
            message = prefix + ": " + message;
        }
        return new AiException(kind, status, message);
    }

    private AiException ioFailure(IOException e, CancelToken token) {
        if (token != null && token.isCancelled()) {
            return new AiException(AiException.Kind.CANCELLED, 0, "Request cancelled by user", e);
        }
        if (e instanceof SocketTimeoutException) {
            return new AiException(AiException.Kind.TIMEOUT, 0,
                    "Request timed out after " + Math.max(10, settings.timeoutSeconds)
                            + "s. Increase the timeout in AI settings or try again.", e);
        }
        if (e instanceof UnknownHostException) {
            return new AiException(AiException.Kind.NETWORK, 0,
                    "Cannot resolve the API host. Check the base URL and your connection.", e);
        }
        if (e instanceof ConnectException) {
            return new AiException(AiException.Kind.NETWORK, 0,
                    "Cannot connect to the API endpoint.", e);
        }
        if (e instanceof SSLException) {
            return new AiException(AiException.Kind.NETWORK, 0,
                    "TLS handshake failed. Check the API URL and the device clock.", e);
        }
        return new AiException(AiException.Kind.NETWORK, 0,
                "Network error: " + (e.getMessage() == null ? "unknown" : e.getMessage()), e);
    }

    private static boolean mentionsTemperature(AiException e) {
        String text = (e.getDetail() == null ? "" : e.getDetail()).toLowerCase(Locale.US);
        if (text.contains("temperature")) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause != null && cause.getMessage() != null
                && cause.getMessage().toLowerCase(Locale.US).contains("temperature");
    }

    private void disconnect(HttpURLConnection connection) {
        try {
            connection.disconnect();
        } catch (RuntimeException ignored) {
            // Ignore; the connection may already be closed.
        }
    }

    /** Convenience for callers that need an empty model result list. */
    public static List<String> emptyModels() {
        return new ArrayList<>();
    }
}
