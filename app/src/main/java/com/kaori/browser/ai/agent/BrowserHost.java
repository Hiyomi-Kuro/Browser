package com.kaori.browser.ai.agent;

import android.app.Activity;
import android.webkit.WebView;

import java.util.List;

/**
 * The narrow surface the agent needs from the browser UI.
 *
 * <p>Implemented by {@code BrowserActivity}. Keeping this as an interface stops the
 * agent from growing a second copy of tab management and lets the tool layer stay
 * testable and free of Activity code.</p>
 */
public interface BrowserHost {

    Activity activity();

    WebView currentWebView();

    String currentUrl();

    String currentTitle();

    boolean canGoBack();

    boolean canGoForward();

    /** Opens a URL in the current tab or in a new tab that is marked agent-created. */
    void openUrl(String url, boolean newTab);

    void goBack();

    void goForward();

    void reload();

    void stopLoading();

    int tabCount();

    int currentTabIndex();

    List<TabInfo> listTabs();

    boolean switchToTab(int index);

    boolean isAgentTab(int index);

    /** @return false when the tab is user-owned and may not be closed. */
    boolean closeAgentTab(int index);

    void closeAllAgentTabs();

    /** Posts work to the main thread and waits at most {@code timeoutMs}. */
    boolean postAndWait(Runnable runnable, long timeoutMs);
}
