package com.example.myapplication;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    private static final String TAG = "CryptoUtils";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PAYLOAD_PREFIX = "v2";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String DEFAULT_SHARED_SECRET = "change-this-mesh-secret";

    private static String resolveSharedSecret() {
        try {
            Class<?> buildConfigClass = Class.forName("com.example.myapplication.BuildConfig");
            Object value = buildConfigClass.getField("MESH_SHARED_SECRET").get(null);
            if (value instanceof String) {
                String secret = (String) value;
                if (!secret.trim().isEmpty()) {
                    return secret;
                }
            }
        } catch (Exception ignored) {
            // Fall back to default when BuildConfig is unavailable in IDE/source analysis.
        }
        return DEFAULT_SHARED_SECRET;
    }

    private static SecretKey getSharedSecretKey() throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(resolveSharedSecret().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static String encrypt(String value) {
        if (value == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSharedSecretKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();

            String encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP);
            String encodedCiphertext = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            return PAYLOAD_PREFIX + ":" + encodedIv + ":" + encodedCiphertext;
        } catch (Exception ex) {
            Log.e(TAG, "Encryption failed", ex);
        }
        return null;
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return null;
        try {
            String[] parts = encrypted.split(":", 3);
            if (parts.length != 3 || !PAYLOAD_PREFIX.equals(parts[0])) {
                return null;
            }

            byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(parts[2], Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSharedSecretKey(), gcmSpec);

            byte[] original = cipher.doFinal(ciphertext);
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            Log.e(TAG, "Decryption failed", ex);
        }
        return null;
    }
}
