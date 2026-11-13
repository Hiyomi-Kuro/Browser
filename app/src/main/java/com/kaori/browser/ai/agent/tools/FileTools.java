package com.kaori.browser.ai.agent.tools;

import android.webkit.CookieManager;
import android.webkit.WebView;

import com.kaori.browser.ai.agent.UrlTools;
import com.kaori.browser.ai.reader.PageSnapshot;
import com.kaori.browser.ai.storage.ExportWriter;
import com.kaori.browser.ai.storage.FileNames;
import com.kaori.browser.export.PdfExporter;
import com.kaori.browser.view.NinjaWebView;

import org.json.JSONObject;

/** browser.download / save_markdown / save_pdf. */
public final class FileTools {

    private FileTools() {
    }

    public static final class Download implements BrowserTool {
        @Override
        public String name() {
            return "browser.download";
        }

        @Override
        public String description() {
            return "Download a public file through the system download manager. Asks the "
                    + "user first.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "url", ToolSchemas.stringProperty("Absolute http(s) file URL"),
                    "filename", ToolSchemas.stringProperty("Optional file name")), "url");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            String url = UrlTools.ensureScheme(arguments.optString("url", ""));
            if (!UrlTools.isHttpUrl(url)) {
                return "Only http:// and https:// URLs can be downloaded.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            String url = UrlTools.ensureScheme(arguments.optString("url", ""));
            String requested = arguments.optString("filename", "");

            final String[] userAgent = new String[]{""};
            final String[] referer = new String[]{""};
            final String[] cookie = new String[]{""};
            context.onMain(() -> {
                WebView webView = context.webView();
                if (webView != null) {
                    userAgent[0] = webView.getSettings().getUserAgentString();
                    referer[0] = webView.getUrl();
                }
                cookie[0] = CookieManager.getInstance().getCookie(url);
            });

            String fileName = FileNames.safeFileName(
                    requested.isEmpty() ? FileNames.fromUrl(url) : requested);
            if (fileName.isEmpty()) {
                fileName = "download.bin";
            }

            try {
                BrowserDownloads.Queued queued = BrowserDownloads.enqueue(
                        context.appContext, url, fileName, userAgent[0], referer[0], cookie[0]);
                context.recordSource(context.currentTitle(), url,
                        "download #" + queued.id + " " + queued.fileName, "download");
                return ToolResult.ok("Download queued as Download/Browser/" + queued.fileName
                        + " (id " + queued.id + ")");
            } catch (Exception e) {
                return ToolResult.fail("The download could not be started: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }

        @Override
        public Risk risk() {
            return Risk.HIGH;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }

    public static final class SaveMarkdown implements BrowserTool {
        @Override
        public String name() {
            return "browser.save_markdown";
        }

        @Override
        public String description() {
            return "Save the current page as a Markdown file in Download/Browser.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "filename", ToolSchemas.stringProperty("Optional file name")));
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PageReading.Result read = PageReading.read(context.host,
                    Math.max(8000, context.toolTimeoutMs));
            if (!read.ok()) {
                return ToolResult.fail(read.error);
            }
            PageSnapshot snapshot = read.snapshot;
            String requested = arguments.optString("filename", "");
            String base = requested.isEmpty() ? snapshot.getTitle() : requested;
            String fileName = FileNames.normalize(base, ".md");
            String content = "<!-- Source: " + snapshot.getUrl() + " -->\n\n"
                    + snapshot.getMarkdown() + "\n";
            try {
                ExportWriter.Saved saved = ExportWriter.saveText(
                        context.appContext, fileName, "text/markdown", content);
                return ToolResult.ok("Saved to " + saved.describe());
            } catch (Exception e) {
                return ToolResult.fail("Saving Markdown failed: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }

        @Override
        public Risk risk() {
            return Risk.MEDIUM;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }

    public static final class SavePdf implements BrowserTool {
        @Override
        public String name() {
            return "browser.save_pdf";
        }

        @Override
        public String description() {
            return "Open the browser's existing PDF/print export for the current page. The "
                    + "user still confirms in the system dialog.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties());
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            WebView webView = context.webView();
            if (!(webView instanceof NinjaWebView)) {
                return ToolResult.fail("PDF export needs an open browser tab.");
            }
            final NinjaWebView target = (NinjaWebView) webView;
            boolean posted = context.onMain(() -> {
                if (context.host.activity() != null) {
                    PdfExporter.export(context.host.activity(), target);
                }
            });
            if (!posted) {
                return ToolResult.fail("The browser did not accept the PDF export request.");
            }
            ToolResult result = ToolResult.ok("The system print/PDF dialog was opened; the "
                    + "user completes the export there.");
            result.userVisibleOnly = true;
            return result;
        }

        @Override
        public Risk risk() {
            return Risk.HIGH;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }
}
