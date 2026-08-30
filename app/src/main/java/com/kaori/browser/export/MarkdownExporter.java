package com.kaori.browser.export;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.WebView;
import android.widget.Toast;

import com.kaori.browser.reader.ArticleExtractor;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MarkdownExporter {

    private static final String MARKOR_PACKAGE = "net.gsantner.markor";

    private MarkdownExporter() {
    }

    public static void export(Activity activity, WebView webView) {
        if (activity == null || webView == null || webView.getUrl() == null) {
            return;
        }

        final String pageTitle = webView.getTitle() == null
                ? "Web page"
                : webView.getTitle();
        final String pageUrl = webView.getUrl();

        ArticleExtractor.extract(webView, new ArticleExtractor.Callback() {
            @Override
            public void onExtracted(String html) {
                new Thread(() -> convertAndSave(
                        activity,
                        pageTitle,
                        pageUrl,
                        html
                )).start();
            }

            @Override
            public void onError() {
                showToast(activity, "Unable to extract page content");
            }
        });
    }

    private static void convertAndSave(
            Activity activity,
            String pageTitle,
            String pageUrl,
            String html
    ) {
        Uri uri = null;

        try {
            String markdownBody = FlexmarkHtmlConverter
                    .builder()
                    .build()
                    .convert(html)
                    .trim();

            if (markdownBody.isEmpty()) {
                throw new IllegalStateException("Markdown output is empty");
            }

            String title = pageTitle
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .trim();

            String markdown =
                    "<!-- Source: " + pageUrl + " -->\n\n" +
                    markdownBody +
                    "\n";

            String fileName = makeSafeFileName(title) + ".md";

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown");
            values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Browser"
            );
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            uri = activity.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
            );

            if (uri == null) {
                throw new IllegalStateException("Unable to create Markdown file");
            }

            try (OutputStream output =
                         activity.getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) {
                    throw new IllegalStateException("Unable to open Markdown file");
                }

                output.write(markdown.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            ContentValues complete = new ContentValues();
            complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
            activity.getContentResolver().update(uri, complete, null, null);

            Uri finalUri = uri;
            activity.runOnUiThread(() -> {
                Toast.makeText(
                        activity,
                        "Saved to Download/Browser/" + fileName,
                        Toast.LENGTH_LONG
                ).show();

                openInMarkor(activity, finalUri, fileName);
            });

        } catch (Exception e) {
            if (uri != null) {
                try {
                    activity.getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }

            activity.runOnUiThread(() ->
                    Toast.makeText(
                            activity,
                            "Failed to save Markdown: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show()
            );
        }
    }

    private static void openInMarkor(Activity activity, Uri uri, String fileName) {
        Intent intent = new Intent(Intent.ACTION_EDIT);
        intent.setDataAndType(uri, "text/markdown");
        intent.setPackage(MARKOR_PACKAGE);
        intent.setClipData(ClipData.newRawUri(fileName, uri));
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                    activity,
                    "Markdown saved. Markor is not installed or cannot open this file.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private static String makeSafeFileName(String title) {
        String name = title
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ")
                .trim();

        if (name.isEmpty()) {
            name = "Web page";
        }

        if (name.length() > 120) {
            name = name.substring(0, 120).trim();
        }

        return name;
    }

    private static void showToast(Activity activity, String message) {
        activity.runOnUiThread(() ->
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        );
    }
}
