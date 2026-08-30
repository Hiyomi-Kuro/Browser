package com.kaori.browser.unit;

import android.app.Activity;
import android.webkit.WebView;

/**
 * @deprecated Use {@link com.kaori.browser.export.MarkdownExporter}.
 */
@Deprecated
public final class MarkdownExporter {

    private MarkdownExporter() {
    }

    public static void export(Activity activity, WebView webView) {
        com.kaori.browser.export.MarkdownExporter.export(activity, webView);
    }
}
