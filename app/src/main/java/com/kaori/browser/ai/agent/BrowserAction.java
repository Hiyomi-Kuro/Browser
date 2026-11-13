package com.kaori.browser.ai.agent;

import org.json.JSONObject;

/** A single tool invocation requested by the model. */
public final class BrowserAction {

    private final String tool;
    private final JSONObject arguments;
    private final String thought;

    public BrowserAction(String tool, JSONObject arguments, String thought) {
        this.tool = tool == null ? "" : tool.trim();
        this.arguments = arguments == null ? new JSONObject() : arguments;
        this.thought = thought == null ? "" : thought.trim();
    }

    public String getTool() {
        return tool;
    }

    public JSONObject getArguments() {
        return arguments;
    }

    public String getThought() {
        return thought;
    }

    public String summary() {
        String args = arguments.toString();
        if (args.length() > 220) {
            args = args.substring(0, 220) + "…";
        }
        return tool + " " + args;
    }
}
