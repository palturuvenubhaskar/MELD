package com.meld.provider;

import com.meld.workspace.Workspace;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Provider credential entity per DATABASE.md §5.
 * Stores encrypted secrets; never plaintext.
 * Composite unique constraint on (workspace_id, id) for FK references.
 */
@Entity
@Table(name = "provider_credentials", uniqueConstraints = {
        @UniqueConstraint(name = "uk_provider_credentials_workspace_id", columnNames = {"workspace_id", "id"})
})
public class ProviderCredential {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "provider", nullable = false, length = 60)
    private String provider;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "BYTEA")
    private byte[] encryptedSecret;

    @Column(name = "key_version", nullable = false, length = 40)
    private String keyVersion;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProviderCredential() {}

    public ProviderCredential(UUID id, Workspace workspace, String provider, String displayName,
                               byte[] encryptedSecret, String keyVersion, UUID createdBy) {
        this.id = id;
        this.workspace = workspace;
        this.provider = provider;
        this.displayName = displayName;
        this.encryptedSecret = encryptedSecret;
        this.keyVersion = keyVersion;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Workspace getWorkspace() { return workspace; }
    public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public byte[] getEncryptedSecret() { return encryptedSecret; }
    public void setEncryptedSecret(byte[] encryptedSecret) { this.encryptedSecret = encryptedSecret; }
    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String keyVersion) { this.keyVersion = keyVersion; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}