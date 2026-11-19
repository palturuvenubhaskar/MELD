package com.meld.provider.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for credential encryption (RULES.md: secrets encrypted at rest,
 * key outside PostgreSQL).
 */
class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(
            "unit-test-key-0123456789abcdefgh".getBytes(StandardCharsets.UTF_8));

    @Test
    void roundTripsASecret() {
        SecretCipher cipher = new SecretCipher(KEY);

        byte[] payload = cipher.encrypt("sk-or-v1-secret-value");
        String decrypted = cipher.decrypt(payload);

        assertThat(decrypted).isEqualTo("sk-or-v1-secret-value");
        assertThat(new String(payload, StandardCharsets.UTF_8)).doesNotContain("sk-or-v1");
    }

    @Test
    void usesAFreshIvPerEncryption() {
        SecretCipher cipher = new SecretCipher(KEY);

        byte[] first = cipher.encrypt("same-secret");
        byte[] second = cipher.encrypt("same-secret");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second));
    }

    @Test
    void rejectsATamperedPayload() {
        SecretCipher cipher = new SecretCipher(KEY);
        byte[] payload = cipher.encrypt("tamper-me");
        payload[payload.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }

    @Test
    void rejectsAPayloadEncryptedWithAnotherKey() {
        SecretCipher writer = new SecretCipher(KEY);
        String otherKey = Base64.getEncoder().encodeToString(
                "another-test-key-0123456789abcde".getBytes(StandardCharsets.UTF_8));
        SecretCipher reader = new SecretCipher(otherKey);

        byte[] payload = writer.encrypt("cross-key");

        assertThatThrownBy(() -> reader.decrypt(payload))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsFastWhenTheKeyIsMissing() {
        assertThatThrownBy(() -> new SecretCipher(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MELD_CREDENTIAL_ENCRYPTION_KEY");
    }

    @Test
    void failsFastWhenTheKeyIsNot32Bytes() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new SecretCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void rejectsMalformedStoredPayload() {
        SecretCipher cipher = new SecretCipher(KEY);

        assertThatThrownBy(() -> cipher.decrypt(Arrays.copyOf(new byte[4], 4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("malformed");
    }
}
