package com.kaori.browser.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BrowserSession {

    private final List<String> urls;
    private final List<String> settings;

    BrowserSession(List<String> urls, List<String> settings) {
        int size = Math.min(urls.size(), settings.size());
        this.urls = Collections.unmodifiableList(
                new ArrayList<>(urls.subList(0, size))
        );
        this.settings = Collections.unmodifiableList(
                new ArrayList<>(settings.subList(0, size))
        );
    }

    public boolean isEmpty() {
        return urls.isEmpty();
    }

    public int size() {
        return urls.size();
    }

    public String getUrl(int index) {
        return urls.get(index);
    }

    public String getSettings(int index) {
        return settings.get(index);
    }
}
