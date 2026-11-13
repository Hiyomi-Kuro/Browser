package com.kaori.browser.ai.agent.tools;

import android.webkit.WebView;

import com.kaori.browser.ai.agent.BrowserHost;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs WebView JavaScript from a background thread and waits for its value. */
public final class JsBridge {

    private JsBridge() {
    }

    /** @return the raw evaluateJavascript value, or {@code null} on timeout/failure. */
    public static String evaluate(BrowserHost host, WebView webView, String script, long timeoutMs) {
        if (host == null || webView == null || script == null) {
            return null;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> holder = new AtomicReference<>(null);

        boolean posted = host.postAndWait(
                () -> webView.evaluateJavascript(script, value -> {
                    holder.set(value);
                    latch.countDown();
                }),
                5000);
        if (!posted) {
            return null;
        }
        try {
            if (!latch.await(Math.max(1000, timeoutMs), TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return holder.get();
    }

    /** Parses the JS return value as a JSON object. */
    public static JSONObject evaluateJson(BrowserHost host, WebView webView, String script,
                                          long timeoutMs) {
        String value = evaluate(host, webView, script, timeoutMs);
        if (value == null) {
            return null;
        }
        try {
            Object parsed = new JSONTokener(value).nextValue();
            if (parsed instanceof String) {
                String text = ((String) parsed).trim();
                if (text.isEmpty() || "null".equals(text)) {
                    return null;
                }
                return new JSONObject(text);
            }
            if (parsed instanceof JSONObject) {
                return (JSONObject) parsed;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /** JSON-quotes untrusted text so it becomes a safe JavaScript string literal. */
    public static String quote(String value) {
        return JSONObject.quote(value == null ? "" : value);
    }
}
