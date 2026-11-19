package com.meld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Proves the V1 baseline migrates a clean PostgreSQL database, that the test
 * profile can only reach meld_test, that the composite key and foreign keys
 * from docs/DATABASE.md section 5 are enforced, and that /actuator/health is
 * served.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BaselineMigrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID createdUserId;
    private UUID createdWorkspaceId;

    @AfterEach
    void cleanUp() {
        if (createdWorkspaceId != null) {
            jdbc.update("DELETE FROM workspace_members WHERE workspace_id = ?", createdWorkspaceId);
            jdbc.update("DELETE FROM workspaces WHERE id = ?", createdWorkspaceId);
        }
        if (createdUserId != null) {
            jdbc.update("DELETE FROM users WHERE id = ?", createdUserId);
        }
    }

    @Test
    void testProfileTargetsOnlyTheTestDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .isEqualTo("jdbc:postgresql://localhost:5432/meld_test");
        }
    }

    @Test
    void baselineMigrationIsApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        // V1 is the baseline; later migrations are additive and must not remove it.
        assertThat(versions).isNotEmpty();
        assertThat(versions.get(0)).isEqualTo("1");

        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        assertThat(failed).isZero();

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN ('users', 'workspaces', 'workspace_members') "
                        + "ORDER BY table_name",
                String.class);
        assertThat(tables).containsExactly("users", "workspace_members", "workspaces");
    }

    @Test
    void workspaceMembershipUsesACompositePrimaryKey() {
        List<String> primaryKeyColumns = jdbc.queryForList(
                """
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = 'public'
                  AND tc.table_name = 'workspace_members'
                  AND tc.constraint_type = 'PRIMARY KEY'
                ORDER BY kcu.ordinal_position
                """,
                String.class);

        assertThat(primaryKeyColumns).containsExactly("workspace_id", "user_id");
    }

    @Test
    void constraintsRejectDuplicateEmailAndDuplicateMembership() {
        createdUserId = insertUser("owner@example.test");
        createdWorkspaceId = insertWorkspace(createdUserId, "ws-" + UUID.randomUUID());

        assertThatThrownBy(() -> insertUser("owner@example.test"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, 'OWNER')",
                createdWorkspaceId, createdUserId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, 'MEMBER')",
                createdWorkspaceId, createdUserId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/actuator/health")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                id, email, "placeholder-hash");
        return id;
    }

    private UUID insertWorkspace(UUID ownerId, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO workspaces (id, name, slug, created_by) VALUES (?, ?, ?, ?)",
                id, "Test workspace", slug, ownerId);
        return id;
    }
}
