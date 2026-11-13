package com.kaori.browser.ai.model;

/** A native tool call requested by a model. */
public final class ToolCall {

    private final String id;
    private final String name;
    private final String arguments;

    public ToolCall(String id, String name, String arguments) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.arguments = arguments == null ? "" : arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }

    public String summary() {
        return name + "(" + (arguments.length() > 160 ? arguments.substring(0, 160) + "..." : arguments) + ")";
    }
}
