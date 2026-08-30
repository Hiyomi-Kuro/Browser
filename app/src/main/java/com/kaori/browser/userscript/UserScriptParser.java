package com.kaori.browser.userscript;

import com.kaori.browser.database.UserScript;

import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the ==UserScript== metadata block into runtime fields on UserScript. */
public final class UserScriptParser {
    private static final Pattern METADATA_LINE =
            Pattern.compile("^\\s*//\\s*@([A-Za-z0-9_.:-]+)\\s*(.*)$");

    private UserScriptParser() {
    }

    public static void parse(UserScript script) {
        script.clearParsedMetadata();
        boolean inMetadata = false;

        try (Scanner scanner = new Scanner(script.getScript())) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains(UserScript.META_BEGIN)) {
                    inMetadata = true;
                    continue;
                }
                if (line.contains(UserScript.META_END)) {
                    break;
                }
                if (!inMetadata) {
                    continue;
                }

                Matcher matcher = METADATA_LINE.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }

                String key = matcher.group(1).toLowerCase(Locale.ROOT);
                String value = matcher.group(2).trim();
                if (value.isEmpty()) {
                    continue;
                }

                switch (key) {
                    case "match":
                        script.getMatchPatterns().add(value);
                        break;
                    case "include":
                        script.getIncludePatterns().add(value);
                        break;
                    case "exclude":
                        script.getExcludePatterns().add(value);
                        break;
                    case "require":
                        script.getRequireUrls().add(value);
                        break;
                    case "grant":
                        script.getGrants().add(value);
                        break;
                    default:
                        break;
                }
            }
        }
    }
}
