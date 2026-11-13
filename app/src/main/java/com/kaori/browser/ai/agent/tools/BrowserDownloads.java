package com.kaori.browser.ai.agent.tools;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

/** Enqueues an agent download through the system DownloadManager. */
public final class BrowserDownloads {

    public static final class Queued {
        public final long id;
        public final String fileName;

        Queued(long id, String fileName) {
            this.id = id;
            this.fileName = fileName;
        }
    }

    private BrowserDownloads() {
    }

    public static Queued enqueue(Context context, String url, String fileName, String userAgent,
                                 String referer, String cookie) throws Exception {
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("DownloadManager is unavailable");
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(fileName);
        request.setDescription(url);
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, "Browser/" + fileName);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(false);
        if (userAgent != null && !userAgent.isEmpty()) {
            request.addRequestHeader("User-Agent", userAgent);
        }
        if (referer != null && !referer.isEmpty()) {
            request.addRequestHeader("Referer", referer);
        }
        if (cookie != null && !cookie.isEmpty()) {
            request.addRequestHeader("Cookie", cookie);
        }
        long id = manager.enqueue(request);
        if (id < 0) {
            throw new IllegalStateException("The system refused the download request");
        }
        return new Queued(id, fileName);
    }
}
