package com.kaori.browser.ai.agent.tools;

import org.json.JSONObject;

/**
 * One validated browser capability.
 *
 * <p>Tools are the only way the model affects the device. Implementations must never
 * evaluate model-provided JavaScript: they build their own script and interpolate only
 * validated numbers or JSON-quoted strings.</p>
 */
public interface BrowserTool {

    /** Risk used by the safety manager when deciding whether to ask the user. */
    enum Risk {
        LOW,
        MEDIUM,
        HIGH
    }

    String name();

    String description();

    /** JSON Schema object: {"type":"object","properties":{...},"required":[...]}. */
    JSONObject parameters();

    ToolResult execute(ToolContext context, JSONObject arguments) throws Exception;

    /** Tool-specific argument rules; {@code null} means the arguments are acceptable. */
    String validateArguments(JSONObject arguments);

    Risk risk();

    /** Read-only tools never need confirmation and never change the page. */
    boolean readOnly();
}
