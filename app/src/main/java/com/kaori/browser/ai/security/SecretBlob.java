package com.kaori.browser.ai.security;

import java.util.Base64;

/**
 * Encoding of one encrypted secret: {@code [ivLength][iv][ciphertext]} in Base64.
 *
 * <p>Storing the IV length makes the blob self-describing, so a device that returns a
 * different GCM IV length still round-trips instead of silently corrupting the payload.
 * Pure Java (no Android types) so the codec is unit tested.</p>
 */
public final class SecretBlob {

    public static final int MIN_IV_BYTES = 8;
    public static final int MAX_IV_BYTES = 32;
    public static final int HEADER_BYTES = 1;

    private SecretBlob() {
    }

    public static String pack(byte[] iv, byte[] payload) {
        if (iv == null) {
            throw new IllegalArgumentException("The GCM IV is missing");
        }
        if (iv.length < MIN_IV_BYTES || iv.length > MAX_IV_BYTES) {
            throw new IllegalArgumentException("Unsupported GCM IV length: " + iv.length);
        }
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("The ciphertext is empty");
        }
        byte[] combined = new byte[HEADER_BYTES + iv.length + payload.length];
        combined[0] = (byte) iv.length;
        System.arraycopy(iv, 0, combined, HEADER_BYTES, iv.length);
        System.arraycopy(payload, 0, combined, HEADER_BYTES + iv.length, payload.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    public static byte[] unpack(String packed) {
        if (packed == null || packed.trim().isEmpty()) {
            throw new IllegalArgumentException("The stored key blob is empty");
        }
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(packed.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The stored key blob is not valid Base64", e);
        }
        int ivLength = combined.length == 0 ? 0 : (combined[0] & 0xFF);
        if (ivLength < MIN_IV_BYTES || ivLength > MAX_IV_BYTES) {
            throw new IllegalArgumentException("The stored key blob has an invalid IV length");
        }
        if (combined.length <= HEADER_BYTES + ivLength) {
            throw new IllegalArgumentException("The stored key blob is too short");
        }
        return combined;
    }

    public static byte[] iv(byte[] combined) {
        int ivLength = validate(combined);
        byte[] iv = new byte[ivLength];
        System.arraycopy(combined, HEADER_BYTES, iv, 0, ivLength);
        return iv;
    }

    public static byte[] payload(byte[] combined) {
        int ivLength = validate(combined);
        int offset = HEADER_BYTES + ivLength;
        byte[] payload = new byte[combined.length - offset];
        System.arraycopy(combined, offset, payload, 0, payload.length);
        return payload;
    }

    private static int validate(byte[] combined) {
        if (combined == null || combined.length <= HEADER_BYTES) {
            throw new IllegalArgumentException("The stored key blob is empty");
        }
        int ivLength = combined[0] & 0xFF;
        if (ivLength < MIN_IV_BYTES || ivLength > MAX_IV_BYTES) {
            throw new IllegalArgumentException("The stored key blob has an invalid IV length");
        }
        if (combined.length <= HEADER_BYTES + ivLength) {
            throw new IllegalArgumentException("The stored key blob is too short");
        }
        return ivLength;
    }
}
