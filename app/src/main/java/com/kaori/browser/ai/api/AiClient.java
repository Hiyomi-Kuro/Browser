package com.kaori.browser.ai.api;

import com.kaori.browser.ai.model.ChatRequest;
import com.kaori.browser.ai.model.ChatResponse;

import java.util.List;

/**
 * Replaceable provider interface. Nothing above this layer may depend on a specific
 * vendor, so adding another endpoint only requires a new implementation.
 */
public interface AiClient {

    interface StreamListener {
        void onTextDelta(String text);

        void onReasoningDelta(String text);
    }

    ChatResponse chat(ChatRequest request, CancelToken token, StreamListener listener)
            throws AiException;

    List<String> listModels(CancelToken token) throws AiException;

    ChatResponse testConnection(CancelToken token) throws AiException;
}
