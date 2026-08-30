package com.kaori.browser.export;

import android.app.Activity;
import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;

import com.kaori.browser.unit.HelperUnit;
import com.kaori.browser.view.NinjaWebView;

import java.util.Objects;

public final class PdfExporter {

    private PdfExporter() {
    }

    public static void export(Context context, NinjaWebView webView) {
        if (context == null || webView == null) {
            return;
        }

        ((Activity) context).runOnUiThread(() -> {
            String title = HelperUnit.guessFileName(webView.getUrl(), null, null);
            PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
            PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(title);
            Objects.requireNonNull(printManager).print(
                    title,
                    printAdapter,
                    new PrintAttributes.Builder().build()
            );
        });
    }
}
