package com.kaori.browser.session;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.kaori.browser.browser.AlbumController;
import com.kaori.browser.browser.BrowserContainer;
import com.kaori.browser.unit.BrowserUnit;
import com.kaori.browser.view.NinjaWebView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SessionManager {

    private static final String KEY_OPEN_TABS = "openTabs";
    private static final String KEY_OPEN_TAB_SETTINGS = "openTabSettings";
    private static final String SEPARATOR = "‚‗‚";

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
}
