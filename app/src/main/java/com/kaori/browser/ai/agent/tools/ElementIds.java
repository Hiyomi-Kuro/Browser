package com.kaori.browser.ai.agent.tools;

import java.util.regex.Pattern;

/** Validates the only element reference the model may send. */
public final class ElementIds {

    private static final Pattern PATTERN = Pattern.compile("^e[0-9]{1,4}$");

    private ElementIds() {
    }

    public static String validate(String elementId) {
        if (elementId == null || elementId.trim().isEmpty()) {
            return "element_id is required (for example \"e3\").";
        }
        if (!PATTERN.matcher(elementId.trim()).matches()) {
            return "element_id must look like \"e12\" and come from the latest snapshot.";
        }
        return null;
    }

    public static boolean isValid(String elementId) {
        return validate(elementId) == null;
    }
}
