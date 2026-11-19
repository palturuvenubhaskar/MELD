package com.meld.provider.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for provider credentials (RULES.md, ARCHITECTURE.md
 * §11).
 *
 * <p>The key is read from the environment and never stored in PostgreSQL or in
 * the repository. The stored payload is {@code IV || ciphertext || tag}; the
 * IV is random per encryption so encrypting the same key twice produces
 * different bytes. GCM authenticates the payload, so tampering fails closed.
 */
@Component
public class SecretCipher {

    /** Matches provider_credentials.key_version. */
    public static final String KEY_VERSION = "v1";

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SecretCipher(@Value("${meld.security.credential-encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "meld.security.credential-encryption-key is required (set MELD_CREDENTIAL_ENCRYPTION_KEY)");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("MELD_CREDENTIAL_ENCRYPTION_KEY must be valid base64", ex);
        }
        if (raw.length != KEY_BYTES) {
            Arrays.fill(raw, (byte) 0);
            throw new IllegalStateException("MELD_CREDENTIAL_ENCRYPTION_KEY must decode to 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(raw, "AES");
        Arrays.fill(raw, (byte) 0);
    }

    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Secret is required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt credential", ex);
        }
    }

    public String decrypt(byte[] payload) {
        if (payload == null || payload.length <= IV_BYTES) {
            throw new IllegalStateException("Stored credential is malformed");
        }
        try {
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            // Wrong key or tampered payload. Never include the payload.
            throw new IllegalStateException("Unable to decrypt credential", ex);
        }
    }
}
