package com.kaori.browser.ai;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import com.kaori.browser.ai.security.SecretBlob;

import org.junit.Test;

import java.util.Base64;

/** Blob encoding used for the encrypted API key. */
public class SecretBlobTest {

    private static byte[] bytes(int length, int seed) {
        byte[] value = new byte[length];
        for (int i = 0; i < length; i++) {
            value[i] = (byte) (seed + i);
        }
        return value;
    }

    @Test
    public void roundTripsIvAndPayload() {
        byte[] iv = bytes(12, 1);
        byte[] payload = bytes(20, 2);
        String packed = SecretBlob.pack(iv, payload);
        byte[] combined = SecretBlob.unpack(packed);
        assertArrayEquals(iv, SecretBlob.iv(combined));
        assertArrayEquals(payload, SecretBlob.payload(combined));
    }

    @Test
    public void supportsDifferentIvLengths() {
        byte[] iv = bytes(16, 5);
        byte[] payload = bytes(8, 6);
        byte[] combined = SecretBlob.unpack(SecretBlob.pack(iv, payload));
        assertArrayEquals(iv, SecretBlob.iv(combined));
        assertArrayEquals(payload, SecretBlob.payload(combined));
    }

    @Test
    public void rejectsShortIv() {
        assertThrows(IllegalArgumentException.class,
                () -> SecretBlob.pack(bytes(4, 1), bytes(8, 2)));
    }

    @Test
    public void rejectsOversizedIv() {
        assertThrows(IllegalArgumentException.class,
                () -> SecretBlob.pack(bytes(64, 1), bytes(8, 2)));
    }

    @Test
    public void rejectsMissingIvAndEmptyPayload() {
        assertThrows(IllegalArgumentException.class, () -> SecretBlob.pack(null, bytes(8, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> SecretBlob.pack(bytes(12, 1), new byte[0]));
    }

    @Test
    public void rejectsEmptyOrInvalidBase64() {
        assertThrows(IllegalArgumentException.class, () -> SecretBlob.unpack(null));
        assertThrows(IllegalArgumentException.class, () -> SecretBlob.unpack("   "));
        assertThrows(IllegalArgumentException.class, () -> SecretBlob.unpack("not*base64*"));
    }

    @Test
    public void rejectsTruncatedBlob() {
        byte[] combined = new byte[13];
        combined[0] = 12;
        String packed = Base64.getEncoder().encodeToString(combined);
        assertThrows(IllegalArgumentException.class, () -> SecretBlob.unpack(packed));
    }

    @Test
    public void rejectsInvalidIvLengthHeader() {
        byte[] combined = new byte[40];
        combined[0] = 0;
        String packed = Base64.getEncoder().encodeToString(combined);
        assertThrows(IllegalArgumentException.class, () -> SecretBlob.unpack(packed));
    }
}
