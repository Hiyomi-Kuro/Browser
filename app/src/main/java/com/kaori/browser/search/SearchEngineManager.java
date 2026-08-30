package com.kaori.browser.search;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SearchEngineManager {

    private static final String SEARCH_ENGINE_GOOGLE = "https://www.google.com/search?q=";
    private static final String SEARCH_ENGINE_DUCKDUCKGO = "https://duckduckgo.com/?q=";
    private static final String SEARCH_ENGINE_STARTPAGE = "https://startpage.com/do/search?query=";
    private static final String SEARCH_ENGINE_BING = "https://www.bing.com/search?q=";
    private static final String SEARCH_ENGINE_BAIDU = "https://www.baidu.com/s?wd=";
    private static final String SEARCH_ENGINE_QWANT = "https://www.qwant.com/?q=";
    private static final String SEARCH_ENGINE_ECOSIA = "https://www.ecosia.org/search?q=";
    private static final String SEARCH_ENGINE_METAGER = "https://metager.org/meta/meta.ger3?eingabe=";
    private static final String SEARCH_ENGINE_STARTPAGE_DE = "https://startpage.com/do/search?lui=deu&language=deutsch&query=";
    private static final String SEARCH_ENGINE_SEARX = "https://searx.be/?q=";
    private static final String SEARCH_ENGINE_BRAVE = "https://search.brave.com/search?q=";

    private SearchEngineManager() {
    }

    public static boolean isUrl(String url) {
        if (url == null) {
            return false;
        }

        String normalized = url.toLowerCase(Locale.getDefault());
        if (normalized.startsWith("about:")
                || normalized.startsWith("mailto:")
                || normalized.startsWith("file://")
                || normalized.startsWith("content://")
                || normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("view-source:")
                || normalized.startsWith("ftp://")
                || normalized.startsWith("intent://")) {
            return true;
        }

        String regex = "^((ftp|http|https|intent)?://)"
                + "?(([0-9a-z_!~*'().&=+$%-]+: )?[0-9a-z_!~*'().&=+$%-]+@)?"
                + "(([0-9]{1,3}\\.){3}[0-9]{1,3}"
                + "|"
                + "([0-9a-z_!~*'()-]+\\.)*"
                + "([0-9a-z][0-9a-z-]{0,61})?[0-9a-z]\\."
                + "[a-z]{2,6})"
                + "(:[0-9]{1,4})?"
                + "((/?)|"
                + "(/[0-9a-z_!~*'().;?:@&=+$,%#-]+)+/?)$";
        return Pattern.compile(regex).matcher(normalized).matches();
    }

    public static String resolve(Context context, String query) {
        if (isUrl(query)) {
            if (query.startsWith("about:") || query.startsWith("mailto:")) {
                return query;
            }
            if (!query.contains("://")) {
                return "https://" + query;
            }
            return query;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String customSearchEngine = preferences.getString("sp_search_engine_custom", "");
        if (preferences.getBoolean("searchEngineSwitch", false)) {
            return customSearchEngine + query;
        }

        int engine = Integer.parseInt(Objects.requireNonNull(
                preferences.getString("sp_search_engine", "3")
        ));
        switch (engine) {
            case 1: return SEARCH_ENGINE_STARTPAGE_DE + query;
            case 2: return SEARCH_ENGINE_BAIDU + query;
            case 3: return SEARCH_ENGINE_BING + query;
            case 4: return SEARCH_ENGINE_DUCKDUCKGO + query;
            case 5: return SEARCH_ENGINE_GOOGLE + query;
            case 6: return SEARCH_ENGINE_SEARX + query;
            case 7: return SEARCH_ENGINE_QWANT + query;
            case 8: return SEARCH_ENGINE_ECOSIA + query;
            case 9: return SEARCH_ENGINE_METAGER + query;
            case 10: return SEARCH_ENGINE_BRAVE + query;
            default: return SEARCH_ENGINE_STARTPAGE + query;
        }
    }
}
