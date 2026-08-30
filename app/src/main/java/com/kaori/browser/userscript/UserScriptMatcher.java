package com.kaori.browser.userscript;

import com.kaori.browser.database.UserScript;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** URL matching for @match, @include and @exclude metadata. */
public final class UserScriptMatcher {
    private UserScriptMatcher() {
    }

    public static boolean matches(UserScript script, String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        for (String pattern : script.getExcludePatterns()) {
            if (matchesIncludePattern(url, pattern)) {
                return false;
            }
        }

        return matchesAnyMatch(url, script.getMatchPatterns())
                || matchesAnyInclude(url, script.getIncludePatterns());
    }

    private static boolean matchesAnyMatch(String url, List<String> patterns) {
        for (String pattern : patterns) {
            if (matchesMatchPattern(url, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyInclude(String url, List<String> patterns) {
        for (String pattern : patterns) {
            if (matchesIncludePattern(url, pattern)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesMatchPattern(String url, String rawPattern) {
        String pattern = rawPattern.trim();
        if (pattern.isEmpty()) {
            return false;
        }
        if ("<all_urls>".equals(pattern)) {
            return url.matches("^(?:http|https|file|ftp)://.*$");
        }
        if (isRegexLiteral(pattern)) {
            return matchesRegex(url, pattern.substring(1, pattern.length() - 1), true);
        }

        int schemeEnd = pattern.indexOf("://");
        if (schemeEnd <= 0) {
            return false;
        }
        String scheme = pattern.substring(0, schemeEnd);
        String remainder = pattern.substring(schemeEnd + 3);
        int pathStart = remainder.indexOf('/');
        String host = pathStart >= 0 ? remainder.substring(0, pathStart) : remainder;
        String path = pathStart >= 0 ? remainder.substring(pathStart) : "/*";
        if (host.isEmpty() && !"file".equals(scheme)) {
            return false;
        }

        StringBuilder regex = new StringBuilder("^");
        if ("*".equals(scheme)) {
            regex.append("(?:http|https)");
        } else {
            regex.append(Pattern.quote(scheme));
        }
        regex.append("://");

        if ("*".equals(host)) {
            regex.append("[^/]+");
        } else if (host.startsWith("*.")) {
            String baseHost = host.substring(2);
            if (baseHost.isEmpty()) {
                return false;
            }
            regex.append("(?:[^/]+\\.)?").append(Pattern.quote(baseHost));
        } else if (host.contains("*")) {
            regex.append(globToRegexBody(host, false));
        } else {
            regex.append(Pattern.quote(host));
        }

        regex.append(globToRegexBody(path, true)).append('$');
        return matchesRegex(url, regex.toString(), true);
    }

    static boolean matchesIncludePattern(String url, String rawPattern) {
        String pattern = rawPattern.trim();
        if (pattern.isEmpty()) {
            return false;
        }
        if ("*".equals(pattern)) {
            return true;
        }
        if (isRegexLiteral(pattern)) {
            return matchesRegex(url, pattern.substring(1, pattern.length() - 1), false);
        }
        return matchesRegex(url, "^" + globToRegexBody(pattern, true) + "$", true);
    }

    private static boolean isRegexLiteral(String pattern) {
        return pattern.length() >= 2 && pattern.startsWith("/") && pattern.endsWith("/");
    }

    private static boolean matchesRegex(String url, String regex, boolean wholeString) {
        try {
            Pattern compiled = Pattern.compile(regex);
            return wholeString ? compiled.matcher(url).matches() : compiled.matcher(url).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static String globToRegexBody(String glob, boolean allowSlash) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(allowSlash ? ".*" : "[^/]*");
            } else {
                if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
            }
        }
        return regex.toString();
    }
}
