package com.kaori.browser.ai.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the AI API key encrypted with an Android Keystore backed AES/GCM key.
 *
 * <p>Two details matter for correctness on Android:</p>
 * <ol>
 *   <li>The cipher is taken from the {@code AndroidKeyStore} provider. The default
 *       provider cannot operate on a Keystore key, so asking for it by name avoids a
 *       failed provider negotiation.</li>
 *   <li>Keystore keys require a KeyStore-generated IV by default
 *       ({@code setRandomizedEncryptionRequired(true)}). Supplying a caller IV while
 *       encrypting is rejected with "Caller-provided IV not permitted"; the IV is
 *       therefore read back from {@link Cipher#getIV()} and stored with the blob.</li>
 * </ol>
 *
 * <p>The plaintext key is never logged, never written to plain preferences and never
 * handed to page JavaScript.</p>
 */
public final class ApiKeyStore {

    private static final String TAG = "ApiKeyStore";

    private static final String PREFS_NAME = "ai_secrets";
    private static final String PREF_CIPHERTEXT = "api_key_ciphertext";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "kaori_browser_ai_api_key_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int[] KEY_SIZES_BITS = {256, 128};

    private static volatile String lastError = "";

    private ApiKeyStore() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** @return true when an encrypted blob is present (it may still fail to decrypt). */
    public static boolean hasStoredKey(Context context) {
        String stored = preferences(context).getString(PREF_CIPHERTEXT, null);
        return stored != null && !stored.isEmpty();
    }

    /** @return true only when the stored key can actually be decrypted right now. */
    public static boolean hasUsableKey(Context context) {
        return load(context) != null;
    }

    /** Reason of the last failed {@link #save(Context, String)}; empty when it worked. */
    public static String getLastError() {
        return lastError;
    }

    /** Encrypts and persists the key. An empty value removes the stored key. */
    public static boolean save(Context context, String apiKey) {
        lastError = "";
        if (apiKey == null || apiKey.trim().isEmpty()) {
            clear(context);
            return true;
        }
        String value = apiKey.trim();

        try {
            store(context, encryptWithKey(value));
            return true;
        } catch (Exception first) {
            // The alias may hold an unusable key (incompatible parameters or a corrupted
            // entry). A fresh key cannot open the old blob, so drop both and retry once.
            Log.w(TAG, "Keystore encryption failed, recreating the key: " + describe(first));
            preferences(context).edit().remove(PREF_CIPHERTEXT).apply();
            deleteKeyEntry();
            try {
                store(context, encryptWithKey(value));
                return true;
            } catch (Exception second) {
                second.addSuppressed(first);
                lastError = describe(second);
                Log.w(TAG, "Keystore encryption still failing: " + lastError);
                return false;
            }
        }
    }

    /** @return the decrypted key, or {@code null} when absent or undecryptable. */
    public static String load(Context context) {
        String stored = preferences(context).getString(PREF_CIPHERTEXT, null);
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return decryptWithKey(stored);
        } catch (Exception e) {
            Log.w(TAG, "Unable to decrypt the stored API key: " + describe(e));
            // Nothing can be recovered with this device key; drop the blob so the UI
            // stops claiming that a usable key exists.
            preferences(context).edit().remove(PREF_CIPHERTEXT).apply();
            return null;
        }
    }

    public static void clear(Context context) {
        preferences(context).edit().remove(PREF_CIPHERTEXT).apply();
        deleteKeyEntry();
        lastError = "";
    }

    /**
     * Verifies the Keystore path without touching the stored key.
     *
     * @return {@code null} when an encrypt/decrypt round-trip succeeded, otherwise a
     *         human-readable reason (never containing secret material).
     */
    public static String selfTest() {
        String probe = "kaori-keystore-probe-" + System.nanoTime();
        try {
            String blob = encryptWithKey(probe);
            String restored = decryptWithKey(blob);
            if (!probe.equals(restored)) {
                return "Round-trip mismatch: the decrypted text differs from the input";
            }
            return null;
        } catch (Exception e) {
            return describe(e);
        }
    }

    /** Masked representation for UI; never returns the real secret. */
    public static String maskedKey(Context context) {
        String key = load(context);
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() <= 8) {
            return "********";
        }
        return key.substring(0, 4) + "********" + key.substring(key.length() - 4);
    }

    /** Exception chain summary safe for logs and dialogs. */
    public static String describe(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 3) {
            if (depth > 0) {
                builder.append(" <- ");
            }
            builder.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                builder.append(": ").append(message.trim());
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    private static void store(Context context, String blob) {
        preferences(context).edit().putString(PREF_CIPHERTEXT, blob).apply();
    }

    private static String encryptWithKey(String plainText) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = newCipher();
        // No caller IV: the KeyStore generates it, and getIV() returns it for storage.
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length < SecretBlob.MIN_IV_BYTES) {
            throw new IllegalStateException("The KeyStore did not return a GCM IV");
        }
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return SecretBlob.pack(iv, cipherText);
    }

    private static String decryptWithKey(String stored) throws Exception {
        byte[] combined = SecretBlob.unpack(stored);
        byte[] iv = SecretBlob.iv(combined);
        byte[] payload = SecretBlob.payload(combined);

        SecretKey key = getOrCreateKey();
        Cipher cipher = newCipher();
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = cipher.doFinal(payload);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static Cipher newCipher() throws Exception {
        try {
            return Cipher.getInstance(TRANSFORMATION, KEYSTORE);
        } catch (NoSuchProviderException e) {
            return Cipher.getInstance(TRANSFORMATION);
        } catch (NoSuchAlgorithmException e) {
            return Cipher.getInstance(TRANSFORMATION);
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
            keyStore.deleteEntry(KEY_ALIAS);
        }

        Exception failure = null;
        for (int keySize : KEY_SIZES_BITS) {
            try {
                return createKey(keySize);
            } catch (Exception e) {
                failure = e;
                deleteKeyEntry();
            }
        }
        throw failure == null
                ? new IllegalStateException("Unable to create a Keystore encryption key")
                : failure;
    }

    private static SecretKey createKey(int keySizeBits) throws Exception {
        KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(keySizeBits)
                // The KeyStore owns the IV; a caller-supplied IV would be rejected.
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKey();
    }

    private static void deleteKeyEntry() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        } catch (Exception ignored) {
            // Cleanup is best effort; the ciphertext is removed separately.
        }
    }
}
