package com.meld.tool;

import com.meld.workspace.Workspace;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Tool entity per DATABASE.md §5.
 * Approved tool definitions with input schema and configuration.
 * MVP tools are read-only or low-risk (RULES.md Security).
 */
@Entity
@Table(name = "tools", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tools_workspace_name", columnNames = {"workspace_id", "name"}),
        @UniqueConstraint(name = "uk_tools_workspace_id", columnNames = {"workspace_id", "id"})
})
public class Tool {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tool_type", nullable = false, length = 40)
    private String toolType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", nullable = false, columnDefinition = "JSONB DEFAULT '{}'")
    private String inputSchema = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false, columnDefinition = "JSONB DEFAULT '{}'")
    private String configuration = "{}";

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tool() {}

    public Tool(UUID id, Workspace workspace, String name, String toolType) {
        this.id = id;
        this.workspace = workspace;
        this.name = name;
        this.toolType = toolType;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Workspace getWorkspace() { return workspace; }
    public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }
    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }
    public String getConfiguration() { return configuration; }
    public void setConfiguration(String configuration) { this.configuration = configuration; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
}