package com.kaori.browser.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BrowserSession {

    private final String name;
    private final List<String> titles;
    private final List<String> urls;
    private final List<String> settings;
    private final int activeIndex;

    BrowserSession(List<String> urls, List<String> settings) {
        this("", urls, urls, settings, urls.isEmpty() ? -1 : 0);
    }

    BrowserSession(
            String name,
            List<String> titles,
            List<String> urls,
            List<String> settings,
            int activeIndex
    ) {
        int size = Math.min(titles.size(), Math.min(urls.size(), settings.size()));
        this.name = name == null ? "" : name;
        this.titles = Collections.unmodifiableList(
                new ArrayList<>(titles.subList(0, size))
        );
        this.urls = Collections.unmodifiableList(
                new ArrayList<>(urls.subList(0, size))
        );
        this.settings = Collections.unmodifiableList(
                new ArrayList<>(settings.subList(0, size))
        );
        if (size == 0) {
            this.activeIndex = -1;
        } else {
            this.activeIndex = Math.max(0, Math.min(activeIndex, size - 1));
        }
    }

    public boolean isEmpty() {
        return urls.isEmpty();
    }

    public String getName() {
        return name;
    }

    public int size() {
        return urls.size();
    }

    public String getTitle(int index) {
        String title = titles.get(index);
        if (title == null || title.trim().isEmpty()) {
            return urls.get(index);
        }
        return title;
    }

    public String getUrl(int index) {
        return urls.get(index);
    }

    public String getSettings(int index) {
        return settings.get(index);
    }

    public int getActiveIndex() {
        return activeIndex;
    }
}
