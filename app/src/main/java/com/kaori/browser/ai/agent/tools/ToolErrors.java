package com.kaori.browser.ai.agent.tools;

import java.util.Locale;

/** Maps script-level failure reasons to actionable model-facing text. */
public final class ToolErrors {

    private ToolErrors() {
    }

    public static String reasonText(String reason) {
        String code = reason == null ? "" : reason.trim().toLowerCase(Locale.US);
        if (code.startsWith("click_failed")) {
            return "The page rejected the click (" + reason + ").";
        }
        switch (code) {
            case "element_not_found":
                return "That element id is not valid for the current page. "
                        + "Take a fresh snapshot with browser.get_elements.";
            case "element_detached":
                return "The page re-rendered and the element is gone. "
                        + "Take a fresh snapshot with browser.get_elements.";
            case "not_visible":
                return "The element exists but is not visible. Scroll it into view or "
                        + "choose another element.";
            case "disabled":
                return "The element is disabled and cannot be used right now.";
            case "not_an_input":
                return "That element is not an editable input, textarea or contenteditable.";
            case "target_is_a_select_use_browser_select":
                return "That element is a dropdown; use browser.select instead.";
            case "option_not_found":
                return "The requested option does not exist in this dropdown.";
            case "no_form":
                return "The element is not inside a form.";
            case "no_such_table":
                return "There is no table with that index on the page.";
            case "not_select":
                return "That element is not a <select> element.";
            default:
                return code.isEmpty() ? "The action failed for an unknown reason."
                        : "The action failed: " + reason + ".";
        }
    }
}
