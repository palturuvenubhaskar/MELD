package com.meld.workspace;

import java.util.Locale;

/**
 * Workspace roles per ROADMAP.md M4.
 *
 * <p>M4 implements the smallest model that satisfies the authorization tests:
 * {@code OWNER} (full control, including membership) and {@code MEMBER}
 * (read/participate). The database stores the role as a string, so additional
 * roles can be introduced without a migration.
 */
public enum WorkspaceRole {

    OWNER(2),
    MEMBER(1);

    private final int rank;

    WorkspaceRole(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(WorkspaceRole required) {
        return rank >= required.rank;
    }

    public static WorkspaceRole parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown workspace role: " + value);
        }
    }
}
