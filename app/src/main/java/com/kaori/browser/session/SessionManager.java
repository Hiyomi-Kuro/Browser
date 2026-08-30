package com.kaori.browser.session;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.kaori.browser.browser.AlbumController;
import com.kaori.browser.browser.BrowserContainer;
import com.kaori.browser.unit.BrowserUnit;
import com.kaori.browser.view.NinjaWebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SessionManager {

    private static final String KEY_OPEN_TABS = "openTabs";
    private static final String KEY_OPEN_TAB_SETTINGS = "openTabSettings";
    private static final String KEY_NAMED_SESSIONS = "namedSessionsV1";
    private static final String KEY_LAST_NAMED_SESSION = "lastNamedSession";
    private static final String SEPARATOR = "‚‗‚";
    private static final int NAMED_SESSION_SCHEMA = 1;

    private SessionManager() {
    }

    public static void save(
            SharedPreferences preferences,
            AlbumController currentAlbumController
    ) {
        ArrayList<String> openTabs = new ArrayList<>();
        ArrayList<String> openTabSettings = new ArrayList<>();

        for (int i = 0; i < BrowserContainer.size(); i++) {
            AlbumController controller = BrowserContainer.get(i);
            NinjaWebView webView = (NinjaWebView) controller;
            String url = webView.getUrl();
            String settings = webView.getSettingsBackup();

            if (url == null || BrowserUnit.URL_ABOUT_BLANK.equals(url)) {
                continue;
            }

            if (currentAlbumController == controller) {
                openTabs.add(0, url);
                openTabSettings.add(0, settings);
            } else {
                openTabs.add(url);
                openTabSettings.add(settings);
            }
        }

        preferences.edit()
                .putString(KEY_OPEN_TABS, TextUtils.join(SEPARATOR, openTabs))
                .putString(KEY_OPEN_TAB_SETTINGS, TextUtils.join(SEPARATOR, openTabSettings))
                .apply();
    }

    public static BrowserSession restore(SharedPreferences preferences) {
        List<String> urls = Arrays.asList(TextUtils.split(
                preferences.getString(KEY_OPEN_TABS, ""),
                SEPARATOR
        ));
        List<String> settings = Arrays.asList(TextUtils.split(
                preferences.getString(KEY_OPEN_TAB_SETTINGS, ""),
                SEPARATOR
        ));
        return new BrowserSession(urls, settings);
    }

    public static void clearSavedTabs(SharedPreferences preferences) {
        preferences.edit().putString(KEY_OPEN_TABS, "").apply();
    }

    public static BrowserSession capture(
            String name,
            AlbumController currentAlbumController
    ) {
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> urls = new ArrayList<>();
        ArrayList<String> settings = new ArrayList<>();
        int activeIndex = -1;

        for (int i = 0; i < BrowserContainer.size(); i++) {
            AlbumController controller = BrowserContainer.get(i);
            NinjaWebView webView = (NinjaWebView) controller;
            String url = webView.getUrl();
            if (url == null) {
                continue;
            }

            if (controller == currentAlbumController) {
                activeIndex = urls.size();
            }

            String title = webView.getTitle();
            titles.add(TextUtils.isEmpty(title) ? url : title);
            urls.add(url);
            settings.add(webView.getSettingsBackup());
        }

        if (activeIndex < 0 && !urls.isEmpty()) {
            activeIndex = 0;
        }

        return new BrowserSession(
                normalizeName(name),
                titles,
                urls,
                settings,
                activeIndex
        );
    }

    public static void saveNamed(
            SharedPreferences preferences,
            BrowserSession session
    ) {
        if (session == null || session.isEmpty() || TextUtils.isEmpty(session.getName())) {
            return;
        }

        JSONArray existing = readNamedArray(preferences);
        JSONArray updated = new JSONArray();
        boolean replaced = false;

        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item == null) {
                continue;
            }

            if (session.getName().equals(item.optString("name"))) {
                updated.put(toJsonObject(session));
                replaced = true;
            } else {
                updated.put(item);
            }
        }

        if (!replaced) {
            updated.put(toJsonObject(session));
        }

        preferences.edit()
                .putString(KEY_NAMED_SESSIONS, updated.toString())
                .putString(KEY_LAST_NAMED_SESSION, session.getName())
                .apply();
    }

    public static List<BrowserSession> listNamed(SharedPreferences preferences) {
        JSONArray stored = readNamedArray(preferences);
        ArrayList<BrowserSession> sessions = new ArrayList<>();

        for (int i = 0; i < stored.length(); i++) {
            JSONObject item = stored.optJSONObject(i);
            BrowserSession session = fromJsonObject(item);
            if (session != null && !session.isEmpty()) {
                sessions.add(session);
            }
        }

        return Collections.unmodifiableList(sessions);
    }

    public static void deleteNamed(
            SharedPreferences preferences,
            String name
    ) {
        String normalized = normalizeName(name);
        JSONArray existing = readNamedArray(preferences);
        JSONArray updated = new JSONArray();

        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item != null && !normalized.equals(item.optString("name"))) {
                updated.put(item);
            }
        }

        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_NAMED_SESSIONS, updated.toString());
        if (normalized.equals(preferences.getString(KEY_LAST_NAMED_SESSION, ""))) {
            editor.remove(KEY_LAST_NAMED_SESSION);
        }
        editor.apply();
    }

    public static String getLastNamedSessionName(SharedPreferences preferences) {
        return preferences.getString(KEY_LAST_NAMED_SESSION, "");
    }

    public static String toJson(BrowserSession session) {
        JSONObject object = toJsonObject(session);
        try {
            return object.toString(2);
        } catch (JSONException ignored) {
            return object.toString();
        }
    }

    private static JSONArray readNamedArray(SharedPreferences preferences) {
        String raw = preferences.getString(KEY_NAMED_SESSIONS, "[]");
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private static JSONObject toJsonObject(BrowserSession session) {
        JSONObject object = new JSONObject();
        JSONArray tabs = new JSONArray();

        try {
            object.put("schemaVersion", NAMED_SESSION_SCHEMA);
            object.put("name", session.getName());
            object.put("activeIndex", session.getActiveIndex());

            for (int i = 0; i < session.size(); i++) {
                JSONObject tab = new JSONObject();
                tab.put("index", i);
                tab.put("title", session.getTitle(i));
                tab.put("url", session.getUrl(i));
                tab.put("settings", session.getSettings(i));
                tabs.put(tab);
            }
            object.put("tabs", tabs);
        } catch (JSONException ignored) {
        }

        return object;
    }

    private static BrowserSession fromJsonObject(JSONObject object) {
        if (object == null) {
            return null;
        }

        String name = normalizeName(object.optString("name"));
        JSONArray tabs = object.optJSONArray("tabs");
        if (TextUtils.isEmpty(name) || tabs == null) {
            return null;
        }

        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> urls = new ArrayList<>();
        ArrayList<String> settings = new ArrayList<>();

        for (int i = 0; i < tabs.length(); i++) {
            JSONObject tab = tabs.optJSONObject(i);
            if (tab == null) {
                continue;
            }

            String url = tab.optString("url", "");
            if (TextUtils.isEmpty(url)) {
                continue;
            }
            titles.add(tab.optString("title", url));
            urls.add(url);
            settings.add(tab.optString("settings", "00000000"));
        }

        if (urls.isEmpty()) {
            return null;
        }

        return new BrowserSession(
                name,
                titles,
                urls,
                settings,
                object.optInt("activeIndex", 0)
        );
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80).trim();
        }
        return normalized;
    }
}
