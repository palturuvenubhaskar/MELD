package com.meld.provider;

/**
 * A decrypted credential, passed only between provider and agent services.
 *
 * <p>Deliberately not a record: a record's generated {@code toString()} would
 * print the API key, and that string could reach a log through an exception or
 * debug statement. This type prints only the provider and display name.
 */
public final class DecryptedCredential {

    private final ProviderKind kind;
    private final String apiKey;
    private final String displayName;

    public DecryptedCredential(ProviderKind kind, String apiKey, String displayName) {
        this.kind = kind;
        this.apiKey = apiKey;
        this.displayName = displayName;
    }

    public ProviderKind kind() {
        return kind;
    }

    public String apiKey() {
        return apiKey;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return "DecryptedCredential[" + kind + ", " + displayName + ", apiKey=REDACTED]";
    }
}
