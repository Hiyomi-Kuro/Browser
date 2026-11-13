package com.kaori.browser.ai.agent.tools;

import android.content.Context;
import android.webkit.WebView;

import com.kaori.browser.ai.agent.AgentSession;
import com.kaori.browser.ai.agent.BrowserHost;
import com.kaori.browser.ai.agent.observation.BrowserObservation;
import com.kaori.browser.ai.agent.observation.PageObserver;

/**
 * Everything a tool may use. Tool code runs on the agent thread, never on the main
 * thread, and reaches the WebView through {@link JsBridge} / {@link BrowserHost}.
 */
public final class ToolContext {

    public final BrowserHost host;
    public final AgentSession session;
    public final Context appContext;
    public final PageObserver observer;
    public final long toolTimeoutMs;

    public ToolContext(BrowserHost host, AgentSession session, Context appContext,
                       PageObserver observer, long toolTimeoutMs) {
        this.host = host;
        this.session = session;
        this.appContext = appContext;
        this.observer = observer;
        this.toolTimeoutMs = toolTimeoutMs <= 0 ? 15000 : toolTimeoutMs;
    }

    public WebView webView() {
        return host.currentWebView();
    }

    public BrowserObservation latestObservation() {
        return session.getMemory().latestObservation();
    }

    public String currentUrl() {
        return host.currentUrl();
    }

    public String currentTitle() {
        return host.currentTitle();
    }

    /** Runs UI-thread work and waits for it to complete. */
    public boolean onMain(Runnable runnable) {
        return host.postAndWait(runnable, Math.max(3000, Math.min(toolTimeoutMs, 15000)));
    }

    /** Fresh snapshot; used by tools that must verify the current DOM. */
    public BrowserObservation observeNow() {
        return observer.observe(host, Math.min(toolTimeoutMs, 8000));
    }

    public void recordSource(String title, String url, String excerpt, String status) {
        session.getMemory().recordSource(url, title, excerpt, status);
    }
}
