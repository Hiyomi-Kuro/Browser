package com.kaori.browser.ai.storage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Deterministic, filesystem-safe names for exported AI artefacts. */
public final class FileNames {

    private static final int MAX_BASE = 80;

    private FileNames() {
    }

    public static String normalize(String title, String extension) {
        String base = title == null ? "" : title
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (base.isEmpty()) {
            base = "AI export";
        }
        base = base.replace('.', '_').trim();
        if (base.isEmpty()) {
            base = "AI export";
        }
        if (base.length() > MAX_BASE) {
            base = base.substring(0, MAX_BASE).trim();
        }
        String ext = extension == null ? "" : extension.trim();
        if (!ext.isEmpty() && !ext.startsWith(".")) {
            ext = "." + ext;
        }
        return base + ext;
    }

    /** Keeps the caller's extension; only illegal characters are replaced. */
    public static String safeFileName(String name) {
        if (name == null) {
            return "";
        }
        String value = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (value.length() > 90) {
            int dot = value.lastIndexOf('.');
            String extension = dot > 0 && value.length() - dot <= 8
                    ? value.substring(dot) : "";
            value = value.substring(0, 90 - extension.length()) + extension;
        }
        return value;
    }

    public static String timestamped(String title, String extension) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        return normalize(title + " " + stamp, extension);
    }

    /** Last path segment of a URL, decoded and sanitised, or "" when unusable. */
    public static String fromUrl(String url) {
        if (url == null) {
            return "";
        }
        String value = url;
        int query = value.indexOf('?');
        if (query > 0) {
            value = value.substring(0, query);
        }
        int hash = value.indexOf('#');
        if (hash > 0) {
            value = value.substring(0, hash);
        }
        if (value.endsWith("/")) {
            return "";
        }
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        try {
            name = java.net.URLDecoder.decode(name, "UTF-8");
        } catch (Exception ignored) {
            // Keep the raw name when it is not valid percent-encoding.
        }
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.length() > MAX_BASE) {
            name = name.substring(0, MAX_BASE);
        }
        return name;
    }
}
