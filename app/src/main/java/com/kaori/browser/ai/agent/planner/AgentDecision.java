package com.kaori.browser.ai.agent.planner;

import org.json.JSONObject;

/** Either one tool action or a final answer. */
public final class AgentDecision {

    public final String thought;
    public final String tool;
    public final JSONObject arguments;
    public final String finalAnswer;
    public final boolean isFinal;
    public final boolean protocolError;
    public final String protocolMessage;

    private AgentDecision(String thought, String tool, JSONObject arguments, String finalAnswer,
                          boolean isFinal, boolean protocolError, String protocolMessage) {
        this.thought = thought == null ? "" : thought;
        this.tool = tool == null ? "" : tool;
        this.arguments = arguments == null ? new JSONObject() : arguments;
        this.finalAnswer = finalAnswer == null ? "" : finalAnswer;
        this.isFinal = isFinal;
        this.protocolError = protocolError;
        this.protocolMessage = protocolMessage == null ? "" : protocolMessage;
    }

    public static AgentDecision action(String thought, String tool, JSONObject arguments) {
        return new AgentDecision(thought, tool, arguments, "", false, false, "");
    }

    public static AgentDecision finalAnswer(String answer) {
        return new AgentDecision("", "", null, answer, true, false, "");
    }

    public static AgentDecision protocolError(String message) {
        return new AgentDecision("", "", null, "", false, true, message);
    }

    public String argumentsSummary() {
        String text = arguments.toString();
        return text.length() <= 180 ? text : text.substring(0, 180) + "…";
    }
}
