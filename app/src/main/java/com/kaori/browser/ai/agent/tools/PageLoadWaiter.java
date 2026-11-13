package com.kaori.browser.ai.agent.tools;

import android.webkit.WebView;

import com.kaori.browser.ai.agent.BrowserHost;

import java.util.concurrent.atomic.AtomicInteger;

/** Waits for WebView navigation to settle without blocking the main thread. */
public final class PageLoadWaiter {

    private static final long POLL_MS = 250;

    private PageLoadWaiter() {
    }

    /** @return true when loading finished and stayed finished for two polls. */
    public static boolean awaitSettle(BrowserHost host, long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(1000, timeoutMs);
        int stable = 0;
        while (System.currentTimeMillis() < deadline) {
            int progress = progress(host);
            if (progress >= 100) {
                stable++;
                if (stable >= 2) {
                    return true;
                }
            } else {
                stable = 0;
            }
            if (!sleep(POLL_MS)) {
                return false;
            }
        }
        return stable > 0;
    }

    /** @return true when the URL changed away from {@code urlBefore} and loading settled. */
    public static boolean awaitNavigation(BrowserHost host, String urlBefore, long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(1000, timeoutMs);
        boolean changed = false;
        while (System.currentTimeMillis() < deadline) {
            String url = host.currentUrl();
            if (url != null && !url.equals(urlBefore)) {
                changed = true;
            }
            if (changed && progress(host) >= 100) {
                return awaitSettle(host, Math.min(4000, Math.max(1000, timeoutMs)));
            }
            if (!sleep(POLL_MS)) {
                return false;
            }
        }
        return changed;
    }

    /** Short quiet period so a DOM update triggered by a click can appear. */
    public static void settleQuietly(long milliseconds) {
        sleep(Math.max(0, Math.min(2000, milliseconds)));
    }

    private static int progress(BrowserHost host) {
        final AtomicInteger value = new AtomicInteger(-1);
        boolean posted = host.postAndWait(() -> {
            WebView webView = host.currentWebView();
            value.set(webView == null ? -1 : webView.getProgress());
        }, 2000);
        return posted ? value.get() : -1;
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }
}
