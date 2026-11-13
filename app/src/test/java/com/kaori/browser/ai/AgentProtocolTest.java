package com.kaori.browser.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.kaori.browser.ai.agent.planner.AgentDecision;
import com.kaori.browser.ai.agent.planner.AgentPlanner;

import org.junit.Test;

/** The strict JSON action protocol and its tolerant parser. */
public class AgentProtocolTest {

    @Test
    public void parsesActionWithThought() {
        AgentDecision decision = AgentPlanner.parse("{\"thought_summary\":\"open the search engine\","
                + "\"action\":{\"tool\":\"browser.open_url\",\"arguments\":"
                + "{\"url\":\"https://example.com\"}}}");
        assertFalse(decision.isFinal);
        assertFalse(decision.protocolError);
        assertEquals("browser.open_url", decision.tool);
        assertEquals("https://example.com", decision.arguments.optString("url"));
        assertEquals("open the search engine", decision.thought);
    }

    @Test
    public void parsesFinalAnswer() {
        AgentDecision decision = AgentPlanner.parse("{\"final_answer\":\"The task is complete.\"}");
        assertTrue(decision.isFinal);
        assertEquals("The task is complete.", decision.finalAnswer);
    }

    @Test
    public void parsesDoneSummary() {
        AgentDecision decision = AgentPlanner.parse("{\"done\":true,\"summary\":\"done it\"}");
        assertTrue(decision.isFinal);
        assertEquals("done it", decision.finalAnswer);
    }

    @Test
    public void parsesStringActionForm() {
        AgentDecision decision = AgentPlanner.parse(
                "{\"action\":\"browser.back\",\"arguments\":{}}");
        assertEquals("browser.back", decision.tool);
    }

    @Test
    public void stripsMarkdownFences() {
        String content = "```json\n{\"action\":{\"tool\":\"browser.reload\"}}\n```";
        AgentDecision decision = AgentPlanner.parse(content);
        assertEquals("browser.reload", decision.tool);
    }

    @Test
    public void findsJsonInsideProse() {
        String content = "Sure. Here is my next step:\n{\"action\":{\"tool\":\"browser.scroll\","
                + "\"arguments\":{\"direction\":\"down\"}}}\nThat should reveal more results.";
        AgentDecision decision = AgentPlanner.parse(content);
        assertEquals("browser.scroll", decision.tool);
        assertEquals("down", decision.arguments.optString("direction"));
    }

    @Test
    public void rejectsPlainTextReplies() {
        AgentDecision decision = AgentPlanner.parse("I will now click the button.");
        assertTrue(decision.protocolError);
        assertTrue(decision.protocolMessage.contains("no JSON"));
    }

    @Test
    public void rejectsEmptyReplies() {
        assertTrue(AgentPlanner.parse("").protocolError);
        assertTrue(AgentPlanner.parse("   ").protocolError);
    }

    @Test
    public void firstJsonObjectHandlesBracesInsideStrings() {
        String text = "prefix {\"note\":\"a } brace\",\"action\":{\"tool\":\"browser.back\"}} suffix";
        assertNotNull(AgentPlanner.firstJsonObject(text));
        assertEquals("browser.back",
                AgentPlanner.firstJsonObject(text).optJSONObject("action").optString("tool"));
    }

    @Test
    public void firstJsonObjectReturnsNullWithoutJson() {
        assertNull(AgentPlanner.firstJsonObject("no braces here"));
        assertNull(AgentPlanner.firstJsonObject(null));
    }
}
