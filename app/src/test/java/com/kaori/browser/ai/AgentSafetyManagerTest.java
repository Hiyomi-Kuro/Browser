package com.kaori.browser.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kaori.browser.ai.agent.AgentSafetyManager;
import com.kaori.browser.ai.agent.tools.BrowserTool;
import com.kaori.browser.ai.agent.tools.ToolContext;
import com.kaori.browser.ai.agent.tools.ToolResult;
import com.kaori.browser.ai.agent.tools.ToolSchemas;

import org.json.JSONObject;
import org.junit.Test;

/** The permission policy: what runs, what asks, what is refused. */
public class AgentSafetyManagerTest {

    private static final String SAFE = "safe";
    private static final String ASK_ALL = "ask_all";
    private static final String AUTO = "auto";

    private final AgentSafetyManager safety = new AgentSafetyManager();

    private static BrowserTool tool(final String name, final boolean readOnly) {
        return new BrowserTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "test";
            }

            @Override
            public JSONObject parameters() {
                return ToolSchemas.schema(ToolSchemas.properties());
            }

            @Override
            public ToolResult execute(ToolContext context, JSONObject arguments) {
                return ToolResult.ok("ran");
            }

            @Override
            public String validateArguments(JSONObject arguments) {
                return null;
            }

            @Override
            public Risk risk() {
                return readOnly ? Risk.LOW : Risk.MEDIUM;
            }

            @Override
            public boolean readOnly() {
                return readOnly;
            }
        };
    }

    @Test
    public void refusesJavascriptAndFileSchemes() throws Exception {
        JSONObject args = new JSONObject();
        args.put("url", "javascript:alert(1)");
        assertEquals(AgentSafetyManager.Decision.DENY,
                safety.evaluate(tool("browser.open_url", false), args, SAFE, false, false).decision);

        args.put("url", "file:///data/local/tmp/x");
        assertEquals(AgentSafetyManager.Decision.DENY,
                safety.evaluate(tool("browser.open_url", false), args, SAFE, false, false).decision);
    }

    @Test
    public void refusesContentScheme() throws Exception {
        JSONObject args = new JSONObject();
        args.put("url", "content://com.android.providers/x");
        assertTrue(safety.evaluate(tool("browser.download", false), args, AUTO, false, false)
                .denied());
    }

    @Test
    public void readOnlyToolsNeverAsk() throws Exception {
        AgentSafetyManager.Assessment assessment = safety.evaluate(
                tool("browser.get_text", true), new JSONObject(), ASK_ALL, false, false);
        assertEquals(AgentSafetyManager.Decision.ALLOW, assessment.decision);
    }

    @Test
    public void askAllModeConfirmsEveryWrite() throws Exception {
        AgentSafetyManager.Assessment assessment = safety.evaluate(
                tool("browser.scroll", false), new JSONObject(), ASK_ALL, false, false);
        assertEquals(AgentSafetyManager.Decision.CONFIRM, assessment.decision);
    }

    @Test
    public void safeModeAllowsPlainScrolling() throws Exception {
        assertEquals(AgentSafetyManager.Decision.ALLOW, safety.evaluate(
                tool("browser.scroll", false), new JSONObject(), SAFE, false, false).decision);
    }

    @Test
    public void submitAndDownloadAlwaysAsk() throws Exception {
        assertEquals(AgentSafetyManager.Decision.CONFIRM, safety.evaluate(
                tool("browser.submit", false), new JSONObject(), AUTO, false, false).decision);
        JSONObject args = new JSONObject();
        args.put("url", "https://example.com/file.pdf");
        assertEquals(AgentSafetyManager.Decision.CONFIRM, safety.evaluate(
                tool("browser.download", false), args, AUTO, false, false).decision);
        assertEquals(AgentSafetyManager.Decision.CONFIRM, safety.evaluate(
                tool("browser.save_pdf", false), new JSONObject(), AUTO, false, false).decision);
    }

    @Test
    public void closingAnAgentTabNeedsNoConfirmation() throws Exception {
        assertEquals(AgentSafetyManager.Decision.ALLOW, safety.evaluate(
                tool("browser.close_tab", false), new JSONObject(), SAFE, true, false).decision);
        assertEquals(AgentSafetyManager.Decision.CONFIRM, safety.evaluate(
                tool("browser.close_tab", false), new JSONObject(), SAFE, false, false).decision);
    }

    @Test
    public void typingIntoPasswordFieldAsks() throws Exception {
        assertEquals(AgentSafetyManager.Decision.CONFIRM, safety.evaluate(
                tool("browser.type", false), new JSONObject(), SAFE, false, true).decision);
        assertEquals(AgentSafetyManager.Decision.ALLOW, safety.evaluate(
                tool("browser.type", false), new JSONObject(), SAFE, false, false).decision);
    }

    @Test
    public void describesSensitiveTools() throws Exception {
        assertTrue(AgentSafetyManager.isAlwaysSensitive("browser.submit"));
        assertFalse(AgentSafetyManager.isAlwaysSensitive("browser.get_text"));
    }
}
