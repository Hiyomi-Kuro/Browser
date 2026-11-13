package com.kaori.browser.ai.agent;

import com.kaori.browser.ai.agent.tools.BrowserTool;
import com.kaori.browser.ai.config.AiSettings;

import org.json.JSONObject;

/**
 * Decides whether an action may run, needs an explicit confirmation, or is refused.
 *
 * <p>Sensitive operations (form submit, downloads, PDF/print, closing a user tab,
 * typing into a sensitive field) always require confirmation, even when the user chose
 * the most permissive automatic mode. Navigation to {@code javascript:}, {@code file:},
 * {@code content:} and other high-risk schemes is always refused.</p>
 */
public final class AgentSafetyManager {

    public enum Decision {
        ALLOW,
        CONFIRM,
        DENY
    }

    public static final class Assessment {
        public final Decision decision;
        public final String reason;

        Assessment(Decision decision, String reason) {
            this.decision = decision;
            this.reason = reason == null ? "" : reason;
        }

        public boolean requiresUser() {
            return decision == Decision.CONFIRM;
        }

        public boolean denied() {
            return decision == Decision.DENY;
        }
    }

    public Assessment evaluate(BrowserTool tool, JSONObject arguments, String permissionMode,
                               boolean agentOwnedTab, boolean sensitiveTarget) {
        if (tool == null) {
            return new Assessment(Decision.DENY, "Unknown tool");
        }

        String name = tool.name();
        String url = arguments == null ? "" : arguments.optString("url", "");
        if (!url.isEmpty()) {
            if (UrlTools.isDangerousScheme(url)) {
                return new Assessment(Decision.DENY,
                        "The agent may not open " + UrlTools.schemeOf(url) + ": URLs.");
            }
            if ("browser.open_url".equals(name) || "browser.new_tab".equals(name)
                    || "browser.download".equals(name)) {
                String normalised = UrlTools.ensureScheme(url);
                if (!UrlTools.isHttpUrl(normalised)) {
                    return new Assessment(Decision.DENY,
                            "Only http:// and https:// URLs are allowed.");
                }
            }
        }

        if (tool.readOnly()) {
            return new Assessment(Decision.ALLOW, "read-only tool");
        }

        if (AiSettings.PERMISSION_ASK_ALL.equals(permissionMode)) {
            return new Assessment(Decision.CONFIRM, "ask before every action");
        }

        switch (name) {
            case "browser.submit":
                return new Assessment(Decision.CONFIRM, "submitting a form sends data to the site");
            case "browser.download":
                return new Assessment(Decision.CONFIRM, "downloading a file writes to storage");
            case "browser.save_pdf":
                return new Assessment(Decision.CONFIRM, "PDF export opens the system print dialog");
            case "browser.close_tab":
                if (agentOwnedTab) {
                    return new Assessment(Decision.ALLOW, "agent-owned tab");
                }
                return new Assessment(Decision.CONFIRM, "closing one of your own tabs");
            case "browser.type":
                if (sensitiveTarget) {
                    return new Assessment(Decision.CONFIRM,
                            "typing into a password or sensitive field");
                }
                return new Assessment(Decision.ALLOW, "text entry");
            default:
                return new Assessment(Decision.ALLOW, "bounded browser action");
        }
    }

    /** Tools whose effect is externally visible and therefore always confirmed. */
    public static boolean isAlwaysSensitive(String toolName) {
        return "browser.submit".equals(toolName)
                || "browser.download".equals(toolName)
                || "browser.save_pdf".equals(toolName);
    }

    /** One-line description shown in the confirmation dialog. */
    public static String describeAction(String toolName, JSONObject arguments) {
        StringBuilder builder = new StringBuilder(toolName == null ? "action" : toolName);
        if (arguments != null && arguments.length() > 0) {
            String args = arguments.toString();
            if (args.length() > 200) {
                args = args.substring(0, 200) + "…";
            }
            builder.append(' ').append(args);
        }
        return builder.toString();
    }
}
