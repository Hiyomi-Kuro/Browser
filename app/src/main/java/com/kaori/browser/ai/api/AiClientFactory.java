package com.kaori.browser.ai.api;

import android.content.Context;

import com.kaori.browser.ai.config.AiSettings;
import com.kaori.browser.ai.security.ApiKeyStore;

/** Builds a provider client from persisted configuration. */
public final class AiClientFactory {

    private AiClientFactory() {
    }

    /** Client for normal AI use; requires AI to be enabled and a key to be stored. */
    public static AiClient create(Context context) throws AiException {
        AiSettings.Snapshot snapshot = AiSettings.load(context);
        if (!snapshot.enabled) {
            throw new AiException(AiException.Kind.NOT_CONFIGURED, 0,
                    "AI features are disabled. Enable them in AI settings.");
        }
        return create(snapshot, context);
    }

    /** Client for configuration/connectivity tests; works even while AI is disabled. */
    public static AiClient createForTest(Context context) throws AiException {
        return create(AiSettings.load(context), context);
    }

    private static AiClient create(AiSettings.Snapshot snapshot, Context context) throws AiException {
        String key = ApiKeyStore.load(context);
        if (key == null || key.trim().isEmpty()) {
            throw new AiException(AiException.Kind.NO_API_KEY, 0,
                    "No API key configured. Add one in AI settings.");
        }
        return new OpenAiCompatibleClient(snapshot, key);
    }
}
