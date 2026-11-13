package com.kaori.browser.ai.agent.tools;

import android.webkit.WebView;

import com.kaori.browser.ai.agent.BrowserHost;
import com.kaori.browser.ai.reader.PageContentExtractor;
import com.kaori.browser.ai.reader.PageSnapshot;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Blocking wrapper around {@link PageContentExtractor} for the agent thread. */
public final class PageReading {

    public static final class Result {
        public final PageSnapshot snapshot;
        public final String error;

        Result(PageSnapshot snapshot, String error) {
            this.snapshot = snapshot;
            this.error = error;
        }

        public boolean ok() {
            return snapshot != null;
        }
    }

    private PageReading() {
    }

    public static Result read(BrowserHost host, long timeoutMs) {
        WebView webView = host.currentWebView();
        if (webView == null) {
            return new Result(null, "No browser tab is open");
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<PageSnapshot> snapshot = new AtomicReference<>(null);
        final AtomicReference<String> error = new AtomicReference<>("");

        boolean posted = host.postAndWait(() -> PageContentExtractor.extract(webView,
                new PageContentExtractor.Callback() {
                    @Override
                    public void onExtracted(PageSnapshot value) {
                        snapshot.set(value);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message == null ? "Unable to read the page" : message);
                        latch.countDown();
                    }
                }), 5000);

        if (!posted) {
            return new Result(null, "The browser did not accept the read request");
        }
        try {
            if (!latch.await(Math.max(20000, timeoutMs), TimeUnit.MILLISECONDS)) {
                return new Result(null, "Reading the page timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(null, "Reading the page was interrupted");
        }
        if (snapshot.get() == null) {
            return new Result(null, error.get().isEmpty() ? "Unable to read the page" : error.get());
        }
        return new Result(snapshot.get(), "");
    }
}
