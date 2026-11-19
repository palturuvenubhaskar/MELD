package com.meld.agent;

import com.meld.provider.ProviderCredential;
import com.meld.workspace.Workspace;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent entity per DATABASE.md §5.
 * Reusable agent configurations with provider credential reference.
 * Composite unique constraint on (workspace_id, id) for FK references.
 * Composite FK to provider_credentials(workspace_id, id).
 */
@Entity
@Table(name = "agents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agents_workspace_name", columnNames = {"workspace_id", "name"}),
        @UniqueConstraint(name = "uk_agents_workspace_id", columnNames = {"workspace_id", "id"})
})
public class Agent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "workspace_id", referencedColumnName = "workspace_id", insertable = false, updatable = false),
            @JoinColumn(name = "provider_credential_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private ProviderCredential providerCredential;

    @Column(name = "provider_credential_id")
    private UUID providerCredentialId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "model", nullable = false, length = 120)
    private String model;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false, columnDefinition = "JSONB DEFAULT '{}'")
    private String configuration = "{}";

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Agent() {}

    public Agent(UUID id, Workspace workspace, String name, String model, String systemPrompt) {
        this.id = id;
        this.workspace = workspace;
        this.name = name;
        this.model = model;
        this.systemPrompt = systemPrompt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Workspace getWorkspace() { return workspace; }
    public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
    public ProviderCredential getProviderCredential() { return providerCredential; }
    public void setProviderCredential(ProviderCredential providerCredential) { this.providerCredential = providerCredential; }
    public UUID getProviderCredentialId() { return providerCredentialId; }
    public void setProviderCredentialId(UUID providerCredentialId) { this.providerCredentialId = providerCredentialId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getConfiguration() { return configuration; }
    public void setConfiguration(String configuration) { this.configuration = configuration; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}