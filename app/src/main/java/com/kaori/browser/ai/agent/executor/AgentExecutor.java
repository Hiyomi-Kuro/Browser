package com.kaori.browser.ai.agent.executor;

import com.kaori.browser.ai.agent.ActionValidator;
import com.kaori.browser.ai.agent.AgentSafetyManager;
import com.kaori.browser.ai.agent.tools.BrowserTool;
import com.kaori.browser.ai.agent.tools.ToolContext;
import com.kaori.browser.ai.agent.tools.ToolRegistry;
import com.kaori.browser.ai.agent.tools.ToolResult;

import org.json.JSONObject;

/** Validates, authorises and runs one tool call. */
public final class AgentExecutor {

    public enum Outcome {
        READY,
        INVALID,
        DENIED,
        REQUIRES_CONFIRMATION
    }

    public static final class Execution {
        public final Outcome outcome;
        public final String message;
        public final BrowserTool tool;

        Execution(Outcome outcome, String message, BrowserTool tool) {
            this.outcome = outcome;
            this.message = message == null ? "" : message;
            this.tool = tool;
        }

        public boolean canRun() {
            return outcome == Outcome.READY;
        }
    }

    private final ToolRegistry registry;
    private final ActionValidator validator = new ActionValidator();
    private final AgentSafetyManager safety = new AgentSafetyManager();

    public AgentExecutor(ToolRegistry registry) {
        this.registry = registry == null ? new ToolRegistry() : registry;
    }

    public ToolRegistry getRegistry() {
        return registry;
    }

    /** Static checks plus the safety decision for the proposed action. */
    public Execution prepare(String toolName, JSONObject arguments, String permissionMode,
                             boolean agentOwnedTab, boolean sensitiveTarget) {
        BrowserTool tool = registry.get(toolName);
        if (tool == null) {
            return new Execution(Outcome.INVALID,
                    "Unknown tool \"" + toolName + "\". Use only the documented tools.", null);
        }

        ActionValidator.Result validation = validator.validate(tool, arguments);
        if (!validation.ok) {
            return new Execution(Outcome.INVALID, validation.message, tool);
        }

        AgentSafetyManager.Assessment assessment =
                safety.evaluate(tool, arguments, permissionMode, agentOwnedTab, sensitiveTarget);
        switch (assessment.decision) {
            case DENY:
                return new Execution(Outcome.DENIED, assessment.reason, tool);
            case CONFIRM:
                return new Execution(Outcome.REQUIRES_CONFIRMATION, assessment.reason, tool);
            default:
                return new Execution(Outcome.READY, assessment.reason, tool);
        }
    }

    /** Runs the tool. Must be called from the agent thread. */
    public ToolResult run(ToolContext context, BrowserTool tool, JSONObject arguments) {
        if (tool == null) {
            return ToolResult.fail("The tool is not available.");
        }
        try {
            ToolResult result = tool.execute(context, arguments == null
                    ? new JSONObject() : arguments);
            if (result == null) {
                return ToolResult.fail("The tool returned no result.");
            }
            return result;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ToolResult.fail("The tool failed: " + message);
        }
    }
}
