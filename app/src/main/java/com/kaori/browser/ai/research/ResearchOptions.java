package com.kaori.browser.ai.research;

import org.json.JSONObject;

/** Research limits. A value of 0 means "no limit". */
public final class ResearchOptions {

    public static final int UNLIMITED = 0;
    public static final String DEFAULT_REPORT_LANGUAGE = "简体中文";

    public final int maxResults;
    public final int maxPages;
    public final int maxDepth;
    public final int timeoutSeconds;
    public final int maxSteps;
    public final String reportLanguage;

    public ResearchOptions(int maxResults, int maxPages, int maxDepth, int timeoutSeconds,
                           int maxSteps, String reportLanguage) {
        this.maxResults = Math.max(0, maxResults);
        this.maxPages = Math.max(0, maxPages);
        this.maxDepth = Math.max(0, maxDepth);
        this.timeoutSeconds = Math.max(0, timeoutSeconds);
        this.maxSteps = Math.max(0, maxSteps);
        String language = reportLanguage == null ? "" : reportLanguage.trim();
        this.reportLanguage = language.isEmpty() ? DEFAULT_REPORT_LANGUAGE : language;
    }

    /** No result, page, depth, step or time limit. */
    public static ResearchOptions unlimited(String language) {
        return new ResearchOptions(UNLIMITED, UNLIMITED, UNLIMITED, UNLIMITED, UNLIMITED, language);
    }

    public static ResearchOptions defaults() {
        return unlimited(DEFAULT_REPORT_LANGUAGE);
    }

    public boolean hasStepLimit() {
        return maxSteps > 0;
    }

    public boolean hasTimeLimit() {
        return timeoutSeconds > 0;
    }

    public String describeLimits() {
        return "结果数 " + describe(maxResults) + "，网页数 " + describe(maxPages)
                + "，深度 " + describe(maxDepth) + "，时间 " + describe(timeoutSeconds)
                + " 秒，步数 " + describe(maxSteps);
    }

    public String describe(int value) {
        return value <= 0 ? "不限制" : String.valueOf(value);
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("maxResults", maxResults);
            object.put("maxPages", maxPages);
            object.put("maxDepth", maxDepth);
            object.put("timeoutSeconds", timeoutSeconds);
            object.put("maxSteps", maxSteps);
            object.put("reportLanguage", reportLanguage);
        } catch (Exception ignored) {
        }
        return object;
    }

    public static ResearchOptions fromJson(JSONObject object) {
        if (object == null) {
            return defaults();
        }
        return new ResearchOptions(
                object.optInt("maxResults", UNLIMITED),
                object.optInt("maxPages", UNLIMITED),
                object.optInt("maxDepth", UNLIMITED),
                object.optInt("timeoutSeconds", UNLIMITED),
                object.optInt("maxSteps", UNLIMITED),
                object.optString("reportLanguage", DEFAULT_REPORT_LANGUAGE));
    }
}
