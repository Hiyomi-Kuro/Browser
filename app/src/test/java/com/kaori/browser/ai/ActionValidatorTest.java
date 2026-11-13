package com.kaori.browser.ai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kaori.browser.ai.agent.ActionValidator;
import com.kaori.browser.ai.agent.tools.BrowserTool;
import com.kaori.browser.ai.agent.tools.ToolContext;
import com.kaori.browser.ai.agent.tools.ToolResult;
import com.kaori.browser.ai.agent.tools.ToolSchemas;

import org.json.JSONObject;
import org.junit.Test;

/** Schema validation must reject anything the tools did not declare. */
public class ActionValidatorTest {

    private final ActionValidator validator = new ActionValidator();

    private static BrowserTool tool(final String name, JSONObject parameters,
                                    final String extraRule) {
        return new BrowserTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "test tool";
            }

            @Override
            public JSONObject parameters() {
                return parameters;
            }

            @Override
            public ToolResult execute(ToolContext context, JSONObject arguments) {
                return ToolResult.ok("ran");
            }

            @Override
            public String validateArguments(JSONObject arguments) {
                return extraRule;
            }

            @Override
            public Risk risk() {
                return Risk.LOW;
            }

            @Override
            public boolean readOnly() {
                return true;
            }
        };
    }

    private static BrowserTool clickTool() {
        return tool("browser.click", ToolSchemas.schema(ToolSchemas.properties(
                "element_id", ToolSchemas.stringProperty("id")), "element_id"), null);
    }

    private static BrowserTool scrollTool() {
        return tool("browser.scroll", ToolSchemas.schema(ToolSchemas.properties(
                "direction", ToolSchemas.enumProperty("dir", "up", "down"),
                "amount", ToolSchemas.integerProperty("n", 1, 10)), "direction"), null);
    }

    @Test
    public void acceptsValidArguments() throws Exception {
        JSONObject args = new JSONObject();
        args.put("element_id", "e12");
        assertTrue(validator.validate(clickTool(), args).ok);
    }

    @Test
    public void rejectsUnknownTool() throws Exception {
        ActionValidator.Result result = validator.validate(null, new JSONObject());
        assertFalse(result.ok);
        assertTrue(result.message.contains("Unknown tool"));
    }

    @Test
    public void rejectsMissingRequiredArgument() throws Exception {
        ActionValidator.Result result = validator.validate(clickTool(), new JSONObject());
        assertFalse(result.ok);
        assertTrue(result.message.contains("element_id"));
    }

    @Test
    public void rejectsEmptyRequiredString() throws Exception {
        JSONObject args = new JSONObject();
        args.put("element_id", "   ");
        assertFalse(validator.validate(clickTool(), args).ok);
    }

    @Test
    public void rejectsWrongType() throws Exception {
        JSONObject args = new JSONObject();
        try {
            args.put("element_id", new JSONObject());
        } catch (Exception e) {
            return;
        }
        assertFalse(validator.validate(clickTool(), args).ok);
    }

    @Test
    public void acceptsNumericStringForInteger() throws Exception {
        JSONObject args = new JSONObject();
        args.put("direction", "down");
        args.put("amount", "3");
        assertTrue(validator.validate(scrollTool(), args).ok);
    }

    @Test
    public void rejectsEnumViolation() throws Exception {
        JSONObject args = new JSONObject();
        args.put("direction", "sideways");
        ActionValidator.Result result = validator.validate(scrollTool(), args);
        assertFalse(result.ok);
        assertTrue(result.message.contains("up"));
    }

    @Test
    public void surfacesToolSpecificRule() throws Exception {
        BrowserTool strict = tool("browser.open_url",
                ToolSchemas.schema(ToolSchemas.properties(
                        "url", ToolSchemas.stringProperty("u")), "url"),
                "Only http and https are allowed.");
        JSONObject args = new JSONObject();
        args.put("url", "file:///etc/passwd");
        ActionValidator.Result result = validator.validate(strict, args);
        assertFalse(result.ok);
        assertTrue(result.message.contains("http"));
    }

    @Test
    public void typeMatchingIsTolerant() throws Exception {
        assertTrue(ActionValidator.typeMatches("integer", 5));
        assertTrue(ActionValidator.typeMatches("integer", "5"));
        assertFalse(ActionValidator.typeMatches("integer", "five"));
        assertTrue(ActionValidator.typeMatches("boolean", "true"));
        assertFalse(ActionValidator.typeMatches("boolean", "yes"));
    }
}
