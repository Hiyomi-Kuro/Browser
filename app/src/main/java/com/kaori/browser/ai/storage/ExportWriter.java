package com.kaori.browser.ai.storage;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writes AI reports and exports into {@code Download/Browser} through MediaStore.
 *
 * <p>Uses the pending flag so a failed write never leaves a half file behind.</p>
 */
public final class ExportWriter {

    public static final String FOLDER = "Browser";

    public static final class Saved {
        public final Uri uri;
        public final String displayName;
        public final String relativePath;

        Saved(Uri uri, String displayName, String relativePath) {
            this.uri = uri;
            this.displayName = displayName;
            this.relativePath = relativePath;
        }

        public String describe() {
            return "Download/" + relativePath + "/" + displayName;
        }
    }

    private ExportWriter() {
    }

    public static Saved saveText(Context context, String fileName, String mimeType, String content)
            throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = context.getContentResolver()
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("Unable to create " + fileName);
        }

        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IllegalStateException("Unable to open " + fileName);
            }
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Exception e) {
            try {
                context.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
                // Cleanup is best effort.
            }
            throw e;
        }

        ContentValues complete = new ContentValues();
        complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(uri, complete, null, null);

        return new Saved(uri, fileName, FOLDER);
    }

    public static void share(Context context, Uri uri, String mimeType, String subject) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(intent, subject);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }
}
