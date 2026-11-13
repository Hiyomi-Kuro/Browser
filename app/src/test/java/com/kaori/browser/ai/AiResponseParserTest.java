package com.kaori.browser.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.kaori.browser.ai.api.AiException;
import com.kaori.browser.ai.api.AiResponseParser;
import com.kaori.browser.ai.model.ChatResponse;

import org.junit.Test;

import java.util.List;

/** Every response shape the client claims to understand. */
public class AiResponseParserTest {

    @Test
    public void parsesOpenAiStringContent() throws Exception {
        String body = "{\"model\":\"test-model\",\"choices\":[{\"message\":"
                + "{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":7}}";
        ChatResponse response = AiResponseParser.parseChat(body);
        assertEquals("Hello", response.getContent());
        assertEquals("stop", response.getFinishReason());
        assertEquals("test-model", response.getModelName());
        assertEquals(11, response.getPromptTokens());
        assertEquals(7, response.getCompletionTokens());
    }

    @Test
    public void parsesContentPartsArray() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\","
                + "\"text\":\"part one \"},{\"type\":\"text\",\"text\":\"part two\"}]}}]}";
        assertEquals("part one part two", AiResponseParser.parseChat(body).getContent());
    }

    @Test
    public void parsesAnthropicBlocks() throws Exception {
        String body = "{\"model\":\"claude-x\",\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}],"
                + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":9,"
                + "\"output_tokens\":3}}";
        ChatResponse response = AiResponseParser.parseChat(body);
        assertEquals("Hi", response.getContent());
        assertEquals("end_turn", response.getFinishReason());
        assertEquals(9, response.getPromptTokens());
        assertEquals(3, response.getCompletionTokens());
    }

    @Test
    public void parsesGeminiCandidates() throws Exception {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Gemini says hi\"}]},"
                + "\"finishReason\":\"STOP\"}]}";
        ChatResponse response = AiResponseParser.parseChat(body);
        assertEquals("Gemini says hi", response.getContent());
        assertEquals("STOP", response.getFinishReason());
    }

    @Test
    public void parsesResponsesApiOutputText() throws Exception {
        String body = "{\"output_text\":\"responses api\"}";
        assertEquals("responses api", AiResponseParser.parseChat(body).getContent());
    }

    @Test
    public void parsesNativeToolCalls() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":"
                + "\"call_9\",\"type\":\"function\",\"function\":{\"name\":\"browser.click\","
                + "\"arguments\":\"{\\\"element_id\\\":\\\"e4\\\"}\"}}]}}]}";
        ChatResponse response = AiResponseParser.parseChat(body);
        assertTrue(response.hasToolCalls());
        assertEquals("browser.click", response.getToolCalls().get(0).getName());
        assertEquals("{\"element_id\":\"e4\"}", response.getToolCalls().get(0).getArguments());
    }

    @Test
    public void surfacesErrorEnvelopeMessage() {
        String body = "{\"error\":{\"message\":\"Invalid API key provided\","
                + "\"type\":\"invalid_request_error\"}}";
        try {
            AiResponseParser.parseChat(body);
            fail("expected an AiException");
        } catch (AiException e) {
            assertTrue(e.getDetail().contains("Invalid API key"));
            assertEquals(AiException.Kind.AUTHENTICATION, e.getKind());
        }
    }

    @Test
    public void reportsReasoningOnlyLengthReply() {
        String body = "{\"choices\":[{\"message\":{\"content\":\"\","
                + "\"reasoning_content\":\"thinking for a very long time\"},"
                + "\"finish_reason\":\"length\"}]}";
        try {
            AiResponseParser.parseChat(body);
            fail("expected an AiException");
        } catch (AiException e) {
            assertEquals(AiException.Kind.EMPTY_RESPONSE, e.getKind());
            assertEquals("length", e.getFinishReason());
            assertTrue(e.isLengthTruncated());
            assertTrue(e.getDetail().contains("output budget"));
            assertTrue(e.getDetail().contains("finish_reason=length"));
        }
    }

    @Test
    public void classifiesProviderWording() {
        assertEquals(AiException.Kind.RATE_LIMIT,
                AiResponseParser.classify("You exceeded your rate limit"));
        assertEquals(AiException.Kind.AUTHENTICATION,
                AiResponseParser.classify("invalid api key"));
        assertEquals(AiException.Kind.FORBIDDEN,
                AiResponseParser.classify("model not available in your region"));
        assertEquals(AiException.Kind.TIMEOUT,
                AiResponseParser.classify("request timed out"));
    }

    @Test
    public void parsesModelListShapes() throws Exception {
        List<String> ids = AiResponseParser.parseModels(
                "{\"data\":[{\"id\":\"model-b\"},{\"id\":\"model-a\"}]}");
        assertEquals(2, ids.size());
        assertEquals("model-a", ids.get(0));
        assertEquals("model-b", ids.get(1));

        List<String> named = AiResponseParser.parseModels(
                "{\"models\":[{\"name\":\"plain-a\"},{\"name\":\"plain-b\"}]}");
        assertEquals(2, named.size());

        List<String> plain = AiResponseParser.parseModels("[\"only-one\"]");
        assertEquals(1, plain.size());
        assertEquals("only-one", plain.get(0));
    }

    @Test
    public void rejectsUnparseableBodies() {
        try {
            AiResponseParser.parseChat("<html>gateway error</html>");
            fail("expected an AiException");
        } catch (AiException e) {
            assertEquals(AiException.Kind.PARSE, e.getKind());
        }
        try {
            AiResponseParser.parseChat("");
            fail("expected an AiException");
        } catch (AiException e) {
            assertNotNull(e.getKind());
        }
    }
}
