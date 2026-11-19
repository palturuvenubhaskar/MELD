package com.meld.workspace;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Workspace entity per DATABASE.md §5.
 * Tenant boundary - all workspace-owned records reference this.
 */
@Entity
@Table(name = "workspaces", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workspaces_slug", columnNames = "slug"),
        @UniqueConstraint(name = "uk_workspaces_id", columnNames = "id")
})
public class Workspace {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Workspace() {}

    public Workspace(UUID id, String name, String slug, UUID createdBy) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}