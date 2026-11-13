package com.kaori.browser.ai.chat;

import android.content.Context;
import android.content.SharedPreferences;

import com.kaori.browser.ai.config.AiSettings;

/** Session-level call statistics shown in AI settings. */
public final class AiUsageStats {

    private static final String KEY_REQUESTS = "ai_stats_requests";
    private static final String KEY_PROMPT = "ai_stats_prompt_tokens";
    private static final String KEY_COMPLETION = "ai_stats_completion_tokens";
    private static final String KEY_LAST_ERROR = "ai_stats_last_error";
    private static final String KEY_LAST_AT = "ai_stats_last_at";

    private AiUsageStats() {
    }

    public static void recordSuccess(Context context, int promptTokens, int completionTokens) {
        SharedPreferences preferences = AiSettings.prefs(context);
        preferences.edit()
                .putInt(KEY_REQUESTS, preferences.getInt(KEY_REQUESTS, 0) + 1)
                .putInt(KEY_PROMPT, preferences.getInt(KEY_PROMPT, 0) + Math.max(0, promptTokens))
                .putInt(KEY_COMPLETION,
                        preferences.getInt(KEY_COMPLETION, 0) + Math.max(0, completionTokens))
                .putLong(KEY_LAST_AT, System.currentTimeMillis())
                .apply();
    }

    public static void recordError(Context context, String kind) {
        AiSettings.prefs(context).edit()
                .putString(KEY_LAST_ERROR, kind == null ? "" : kind)
                .putLong(KEY_LAST_AT, System.currentTimeMillis())
                .apply();
    }

    public static int requests(Context context) {
        return AiSettings.prefs(context).getInt(KEY_REQUESTS, 0);
    }

    public static int promptTokens(Context context) {
        return AiSettings.prefs(context).getInt(KEY_PROMPT, 0);
    }

    public static int completionTokens(Context context) {
        return AiSettings.prefs(context).getInt(KEY_COMPLETION, 0);
    }

    public static String lastError(Context context) {
        return AiSettings.prefs(context).getString(KEY_LAST_ERROR, "");
    }

    public static void reset(Context context) {
        AiSettings.prefs(context).edit()
                .remove(KEY_REQUESTS)
                .remove(KEY_PROMPT)
                .remove(KEY_COMPLETION)
                .remove(KEY_LAST_ERROR)
                .remove(KEY_LAST_AT)
                .apply();
    }
}
