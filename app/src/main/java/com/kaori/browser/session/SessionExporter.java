package com.kaori.browser.session;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import com.kaori.browser.R;
import com.kaori.browser.reader.ArticleExtractor;
import com.kaori.browser.view.NinjaWebView;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SessionExporter {

    private SessionExporter() {
    }

    public static void export(
            Activity activity,
            BrowserSession session,
            List<NinjaWebView> webViews
    ) {
        if (activity == null || session == null || session.isEmpty()) {
            return;
        }

        Toast.makeText(activity, R.string.session_exporting, Toast.LENGTH_SHORT).show();
        ArrayList<String> pages = new ArrayList<>(
                Collections.nCopies(session.size(), "")
        );
        extractNext(activity, session, webViews, pages, 0);
    }

    private static void extractNext(
            Activity activity,
            BrowserSession session,
            List<NinjaWebView> webViews,
            ArrayList<String> pages,
            int index
    ) {
        if (index >= session.size()) {
            new Thread(() -> writeSession(activity, session, pages)).start();
            return;
        }

        NinjaWebView webView = index < webViews.size() ? webViews.get(index) : null;
        if (webView == null || webView.getUrl() == null) {
            pages.set(index, unavailableMarkdown(session, index));
            extractNext(activity, session, webViews, pages, index + 1);
            return;
        }

        ArticleExtractor.extract(webView, new ArticleExtractor.Callback() {
            @Override
            public void onExtracted(String html) {
                new Thread(() -> {
                    pages.set(index, convertToMarkdown(session, index, html));
                    activity.runOnUiThread(() ->
                            extractNext(activity, session, webViews, pages, index + 1)
                    );
                }).start();
            }

            @Override
            public void onError() {
                pages.set(index, unavailableMarkdown(session, index));
                extractNext(activity, session, webViews, pages, index + 1);
            }
        });
    }

    private static String convertToMarkdown(
            BrowserSession session,
            int index,
            String html
    ) {
        try {
            String body = FlexmarkHtmlConverter
                    .builder()
                    .build()
                    .convert(html)
                    .trim();
            if (body.isEmpty()) {
                return unavailableMarkdown(session, index);
            }
            return markdownHeader(session, index) + body + "\n";
        } catch (Exception ignored) {
            return unavailableMarkdown(session, index);
        }
    }

    private static String unavailableMarkdown(BrowserSession session, int index) {
        return markdownHeader(session, index)
                + "_Page content could not be extracted. Open the source URL to view it._\n";
    }

    private static String markdownHeader(BrowserSession session, int index) {
        return "<!-- Source: " + session.getUrl(index) + " -->\n\n"
                + "# " + session.getTitle(index) + "\n\n";
    }

    private static void writeSession(
            Activity activity,
            BrowserSession session,
            List<String> pages
    ) {
        String folder = makeSafePathSegment(session.getName());
        String relativePath = Environment.DIRECTORY_DOWNLOADS
                + "/Browser/Sessions/"
                + folder;
        ArrayList<Uri> created = new ArrayList<>();

        try {
            created.add(writeFile(
                    activity,
                    relativePath,
                    "session.json",
                    "application/json",
                    SessionManager.toJson(session) + "\n"
            ));

            for (int i = 0; i < session.size(); i++) {
                String fileName = String.format(
                        Locale.US,
                        "%02d_%s.md",
                        i + 1,
                        makeSafeFileName(session.getTitle(i))
                );
                created.add(writeFile(
                        activity,
                        relativePath,
                        fileName,
                        "text/markdown",
                        pages.get(i)
                ));
            }

            created.add(writeFile(
                    activity,
                    relativePath,
                    "sources.md",
                    "text/markdown",
                    buildSources(session)
            ));

            activity.runOnUiThread(() -> Toast.makeText(
                    activity,
                    activity.getString(
                            R.string.session_export_done,
                            "Download/Browser/Sessions/" + folder
                    ),
                    Toast.LENGTH_LONG
            ).show());
        } catch (Exception e) {
            for (Uri uri : created) {
                try {
                    activity.getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            String message = e.getMessage() == null ? "unknown error" : e.getMessage();
            activity.runOnUiThread(() -> Toast.makeText(
                    activity,
                    activity.getString(R.string.session_export_failed, message),
                    Toast.LENGTH_LONG
            ).show());
        }
    }

    private static Uri writeFile(
            Activity activity,
            String relativePath,
            String fileName,
            String mimeType,
            String content
    ) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = activity.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
        );
        if (uri == null) {
            throw new IllegalStateException("Unable to create " + fileName);
        }

        try {
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) {
                    throw new IllegalStateException("Unable to open " + fileName);
                }
                output.write(content.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            ContentValues complete = new ContentValues();
            complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
            activity.getContentResolver().update(uri, complete, null, null);
            return uri;
        } catch (Exception e) {
            try {
                activity.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private static String buildSources(BrowserSession session) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Sources — ").append(session.getName()).append("\n\n");
        for (int i = 0; i < session.size(); i++) {
            builder.append(i + 1)
                    .append(". [")
                    .append(escapeMarkdownLabel(session.getTitle(i)))
                    .append("](<")
                    .append(session.getUrl(i).replace(">", "%3E"))
                    .append(">)\n");
        }
        return builder.toString();
    }

    private static String escapeMarkdownLabel(String value) {
        return value.replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    private static String makeSafePathSegment(String value) {
        String name = value == null ? "" : value
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._]+|[._]+$", "")
                .trim();
        if (name.isEmpty()) {
            name = "Session";
        }
        if (name.length() > 80) {
            name = name.substring(0, 80);
        }
        return name;
    }

    private static String makeSafeFileName(String value) {
        String name = makeSafePathSegment(value);
        if (name.length() > 70) {
            name = name.substring(0, 70);
        }
        return name;
    }
}
