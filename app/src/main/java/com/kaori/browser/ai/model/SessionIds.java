package com.kaori.browser.ai.model;

import java.security.SecureRandom;
import java.util.Locale;

/** Generates stable, opaque identifiers for AI chat and agent sessions. */
public final class SessionIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionIds() {
    }

    public static String newSessionId() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder("kaori-");
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value));
        }
        return builder.toString();
    }
}
