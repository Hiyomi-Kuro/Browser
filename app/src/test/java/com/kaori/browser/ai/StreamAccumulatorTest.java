package com.kaori.browser.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kaori.browser.ai.api.AiClient;
import com.kaori.browser.ai.api.StreamAccumulator;
import com.kaori.browser.ai.model.ChatResponse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** SSE delta assembly, including streamed tool-call fragments. */
public class StreamAccumulatorTest {

    @Test
    public void assemblesTextDeltas() throws Exception {
        ChatResponse response = new ChatResponse();
        StreamAccumulator accumulator = new StreamAccumulator();
        List<String> deltas = new ArrayList<>();
        AiClient.StreamListener listener = new AiClient.StreamListener() {
            @Override
            public void onTextDelta(String text) {
                deltas.add(text);
            }

            @Override
            public void onReasoningDelta(String text) {
            }
        };

        accumulator.accept("{\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}", response, listener);
        accumulator.accept("{\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}", response, listener);
        accumulator.accept("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                response, listener);

        assertEquals("Hello", response.getContent());
        assertEquals("stop", response.getFinishReason());
        assertEquals(2, deltas.size());
    }

    @Test
    public void assemblesToolCallFragments() throws Exception {
        ChatResponse response = new ChatResponse();
        StreamAccumulator accumulator = new StreamAccumulator();

        accumulator.accept("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"id\":\"call_1\",\"function\":{\"name\":\"browser.click\","
                + "\"arguments\":\"{\\\"element_id\\\":\\\"\"}}]}}]}", response, null);
        accumulator.accept("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                + "\"function\":{\"arguments\":\"e3\\\"}\"}}]}}]}", response, null);
        accumulator.finish(response);

        assertTrue(response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("browser.click", response.getToolCalls().get(0).getName());
        assertEquals("{\"element_id\":\"e3\"}",
                response.getToolCalls().get(0).getArguments());
    }

    @Test
    public void ignoresKeepAlivePayloads() throws Exception {
        ChatResponse response = new ChatResponse();
        StreamAccumulator accumulator = new StreamAccumulator();
        accumulator.accept("", response, null);
        accumulator.accept("[DONE]", response, null);
        accumulator.accept("not json", response, null);
        assertEquals("", response.getContent());
    }

    @Test
    public void handlesAnthropicContentBlockDelta() throws Exception {
        ChatResponse response = new ChatResponse();
        StreamAccumulator accumulator = new StreamAccumulator();
        accumulator.accept("{\"type\":\"content_block_delta\",\"delta\":{\"text\":\"hi\"}}",
                response, null);
        assertEquals("hi", response.getContent());
    }
}
