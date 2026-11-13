package com.kaori.browser.ai.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

/**
 * Typed access to the AI configuration stored in the default preferences.
 *
 * <p>Only non-secret values live here. The API key is stored separately through
 * {@link com.kaori.browser.ai.security.ApiKeyStore}.</p>
 */
public final class AiSettings {

    public static final String KEY_ENABLED = "ai_enabled";
    public static final String KEY_PROVIDER = "ai_provider";
    public static final String KEY_BASE_URL = "ai_base_url";
    public static final String KEY_MODEL = "ai_model";
    public static final String KEY_TIMEOUT = "ai_timeout_seconds";
    public static final String KEY_MAX_TOKENS = "ai_max_tokens";
    public static final String KEY_MAX_STEPS = "ai_max_steps";
    public static final String KEY_TEMPERATURE = "ai_temperature";
    public static final String KEY_STREAM = "ai_stream";
    public static final String KEY_NATIVE_TOOLS = "ai_native_tools";
    public static final String KEY_PERMISSION_MODE = "ai_permission_mode";
    public static final String KEY_REPORT_LANGUAGE = "ai_report_language";

    public static final String PROVIDER_OPENCODE = "opencode_go";
    public static final String PROVIDER_CUSTOM = "custom";

    public static final String OPENCODE_CHAT_URL =
            "https://opencode.ai/inference/go/openai/v1/chat/completions";
    public static final String OPENCODE_MODELS_URL =
            "https://opencode.ai/inference/go/v1/models";
    public static final String OPENCODE_BASE_URL =
            "https://opencode.ai/inference/go/openai/v1";

    public static final String DEFAULT_BASE_URL = OPENCODE_BASE_URL;
    public static final String DEFAULT_MODEL = "deepseek-v4.1-flash";

    public static final int DEFAULT_TIMEOUT_SECONDS = 0;
    public static final int DEFAULT_MAX_TOKENS = 8192;
    public static final int DEFAULT_MAX_STEPS = 20;
    public static final float DEFAULT_TEMPERATURE = 0.7f;

    public static final String PERMISSION_ASK_ALL = "ask_all";
    public static final String PERMISSION_SAFE = "safe";
    public static final String PERMISSION_AUTO = "auto";
    public static final String DEFAULT_PERMISSION_MODE = PERMISSION_SAFE;

    /** Default language for research reports; editable in AI settings. */
    public static final String DEFAULT_REPORT_LANGUAGE = "简体中文";


    public static final String USER_AGENT = "KaoriBrowser-AI/1.0 (Android; browser-agent)";

    private static final String CHAT_SUFFIX = "/chat/completions";
    private static final String MODELS_SUFFIX = "/models";

    private AiSettings() {
    }

    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    /** Research report language, falling back to Simplified Chinese. */
    public static String reportLanguage(Context context) {
        String value = trim(prefs(context).getString(KEY_REPORT_LANGUAGE, DEFAULT_REPORT_LANGUAGE));
        return value.isEmpty() ? DEFAULT_REPORT_LANGUAGE : value;
    }

    /**
     * One-time migration: the old default read timeout (120 s) aborted long translations,
     * so an untouched value is switched to "no limit". A value the user changed on purpose
     * is left alone.
     */
    private static void migrateTimeout(Context context) {
        SharedPreferences preferences = prefs(context);
        if (preferences.getBoolean("ai_timeout_migrated_v1", false)) {
            return;
        }
        String current = trim(preferences.getString(KEY_TIMEOUT, ""));
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean("ai_timeout_migrated_v1", true);
        if (current.isEmpty() || "120".equals(current)) {
            editor.putString(KEY_TIMEOUT, "0");
        }
        editor.apply();
    }

    public static Snapshot load(Context context) {
        migrateTimeout(context);
        return from(prefs(context));
    }

    public static Snapshot from(SharedPreferences preferences) {
        String provider = trim(preferences.getString(KEY_PROVIDER, PROVIDER_OPENCODE));
        if (provider.isEmpty()) {
            provider = PROVIDER_OPENCODE;
        }

        String baseUrl = trim(preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL));
        if (baseUrl.isEmpty()) {
            baseUrl = DEFAULT_BASE_URL;
        }

        String model = trim(preferences.getString(KEY_MODEL, DEFAULT_MODEL));
        int timeout = readInt(preferences, KEY_TIMEOUT, DEFAULT_TIMEOUT_SECONDS, 0, 3600);
        int maxTokens = readInt(preferences, KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS, 128, 65536);
        int maxSteps = readInt(preferences, KEY_MAX_STEPS, DEFAULT_MAX_STEPS, 1, 60);

        float temperature;
        try {
            temperature = Float.parseFloat(
                    trim(preferences.getString(KEY_TEMPERATURE, String.valueOf(DEFAULT_TEMPERATURE))));
        } catch (NumberFormatException e) {
            temperature = DEFAULT_TEMPERATURE;
        }
        if (Float.isNaN(temperature) || temperature < 0f || temperature > 2f) {
            temperature = DEFAULT_TEMPERATURE;
        }

        String permissionMode = trim(preferences.getString(KEY_PERMISSION_MODE, DEFAULT_PERMISSION_MODE));
        if (!PERMISSION_ASK_ALL.equals(permissionMode)
                && !PERMISSION_SAFE.equals(permissionMode)
                && !PERMISSION_AUTO.equals(permissionMode)) {
            permissionMode = DEFAULT_PERMISSION_MODE;
        }

        return new Snapshot(
                preferences.getBoolean(KEY_ENABLED, false),
                provider,
                baseUrl,
                model,
                timeout,
                maxTokens,
                maxSteps,
                temperature,
                preferences.getBoolean(KEY_STREAM, true),
                preferences.getBoolean(KEY_NATIVE_TOOLS, true),
                permissionMode
        );
    }

    private static int readInt(SharedPreferences preferences, String key, int fallback, int min, int max) {
        String raw = trim(preferences.getString(key, String.valueOf(fallback)));
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            value = fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** Normalises user input into an absolute chat-completions endpoint. */
    public static String normalizeChatUrl(String raw) {
        String value = trim(raw);
        if (value.isEmpty()) {
            value = DEFAULT_BASE_URL;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith(CHAT_SUFFIX)) {
            return value;
        }
        return value + CHAT_SUFFIX;
    }

    /**
     * Resolves the models endpoint for a chat endpoint.
     *
     * <p>The OpenCode Go console documents a models endpoint that is not derived from
     * the OpenAI-compatible chat path, so it is special-cased.</p>
     */
    public static String modelsUrlFor(String provider, String chatUrl) {
        if (PROVIDER_OPENCODE.equals(provider)) {
            return OPENCODE_MODELS_URL;
        }
        String value = normalizeChatUrl(chatUrl);
        if (value.endsWith(CHAT_SUFFIX)) {
            value = value.substring(0, value.length() - CHAT_SUFFIX.length());
        }
        return value + MODELS_SUFFIX;
    }

    /** Immutable configuration snapshot used by a single request or agent run. */
    public static final class Snapshot {
        public final boolean enabled;
        public final String provider;
        public final String baseUrl;
        public final String model;
        public final int timeoutSeconds;
        public final int maxTokens;
        public final int maxSteps;
        public final float temperature;
        public final boolean stream;
        public final boolean nativeTools;
        public final String permissionMode;

        public Snapshot(boolean enabled, String provider, String baseUrl, String model,
                        int timeoutSeconds, int maxTokens, int maxSteps, float temperature,
                        boolean stream, boolean nativeTools, String permissionMode) {
            this.enabled = enabled;
            this.provider = provider;
            this.baseUrl = baseUrl;
            this.model = model;
            this.timeoutSeconds = timeoutSeconds;
            this.maxTokens = maxTokens;
            this.maxSteps = maxSteps;
            this.temperature = temperature;
            this.stream = stream;
            this.nativeTools = nativeTools;
            this.permissionMode = permissionMode;
        }

        public boolean isOpencode() {
            return PROVIDER_OPENCODE.equals(provider);
        }

        public String chatUrl() {
            return normalizeChatUrl(baseUrl);
        }

        public String modelsUrl() {
            return modelsUrlFor(provider, baseUrl);
        }

        public boolean hasModel() {
            return !TextUtils.isEmpty(model);
        }

        public Snapshot withModel(String newModel) {
            return new Snapshot(enabled, provider, baseUrl, newModel, timeoutSeconds,
                    maxTokens, maxSteps, temperature, stream, nativeTools, permissionMode);
        }

        public Snapshot withStream(boolean newStream) {
            return new Snapshot(enabled, provider, baseUrl, model, timeoutSeconds,
                    maxTokens, maxSteps, temperature, newStream, nativeTools, permissionMode);
        }

        public Snapshot withProvider(String newProvider, String newBaseUrl) {
            return new Snapshot(true, newProvider, newBaseUrl, model, timeoutSeconds,
                    maxTokens, maxSteps, temperature, stream, nativeTools, permissionMode);
        }
    }
}
