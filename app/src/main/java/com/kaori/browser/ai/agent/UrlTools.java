package com.kaori.browser.ai.agent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Pure URL policy helpers shared by the validator, safety manager and tools. */
public final class UrlTools {

    private UrlTools() {
    }

    public static String trim(String url) {
        return url == null ? "" : url.trim();
    }

    public static String schemeOf(String url) {
        String value = trim(url);
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return "";
        }
        return value.substring(0, colon).toLowerCase(Locale.US);
    }

    public static boolean isHttpUrl(String url) {
        String scheme = schemeOf(url);
        return ("http".equals(scheme) || "https".equals(scheme)) && trim(url).length() > scheme.length() + 3;
    }

    /** Schemes the agent may never navigate to. */
    public static boolean isDangerousScheme(String url) {
        String scheme = schemeOf(url);
        return "javascript".equals(scheme) || "file".equals(scheme) || "content".equals(scheme)
                || "data".equals(scheme) || "intent".equals(scheme) || "android-app".equals(scheme)
                || "about".equals(scheme) || "blob".equals(scheme) || "chrome".equals(scheme)
                || "vbscript".equals(scheme) || "ws".equals(scheme) || "wss".equals(scheme);
    }

    public static String stripFragment(String url) {
        String value = trim(url);
        int hash = value.indexOf('#');
        return hash < 0 ? value : value.substring(0, hash);
    }

    /** Normalised key used for de-duplicating visited pages and sources. */
    public static String normalizeForDedupe(String url) {
        String value = stripFragment(url);
        if (value.endsWith("/") && value.length() > 8) {
            value = value.substring(0, value.length() - 1);
        }
        return value.toLowerCase(Locale.US);
    }

    public static String hostOf(String url) {
        try {
            URI uri = new URI(trim(url));
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (URISyntaxException e) {
            return "";
        }
    }

    public static boolean sameSite(String first, String second) {
        String a = hostOf(first);
        String b = hostOf(second);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a);
    }

    /** Fills in a missing scheme so a model may answer with "example.com". */
    public static String ensureScheme(String url) {
        String value = trim(url);
        if (value.isEmpty()) {
            return "";
        }
        if (schemeOf(value).isEmpty()) {
            return "https://" + value;
        }
        return value;
    }
}
