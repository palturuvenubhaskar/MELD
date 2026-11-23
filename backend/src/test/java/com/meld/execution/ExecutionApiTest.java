package com.meld.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.meld.engine.domain.ExecutionStatus;
import com.meld.testsupport.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Execution lifecycle, idempotency and log retrieval
 * (docs/TEST.md TC-018, TC-019, TC-022, TC-027, TC-028).
 *
 * <p>The worker is disabled in the test profile, so these tests only prove the
 * durable queue contract; {@link ExecutionWorkerTest} runs the worker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExecutionApiTest extends ApiTestSupport {

    @Autowired private ExecutionRepository executions;
    @Autowired private ExecutionLogRepository executionLogs;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * These tests commit rows, so queued work must not leak into other suites
     * (the worker suite would otherwise execute it).
     */
    @org.junit.jupiter.api.AfterEach
    void cancelLeakedQueueRows() {
        jdbc.update("UPDATE executions SET status = 'CANCELLED', completed_at = NOW() "
                + "WHERE status = 'PENDING'");
    }

    @Test
    void startingAnExecutionQueuesAPendingRow() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("ex-start"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Execution Start"));
        Fixture fixture = fixture(owner, csrf, workspaceId);

        JsonNode execution = contentAsTree(postJson(owner, csrf, startUrl(workspaceId, fixture.workflowId()),
                "{\"input\":{\"topic\":\"durable work\"}}", 201));

        assertThat(execution.get("status").asText()).isEqualTo("PENDING");
        assertThat(execution.get("workflowVersionId").asText()).isEqualTo(fixture.versionId().toString());
        assertThat(execution.get("versionNumber").asInt()).isEqualTo(1);
        assertThat(execution.get("startedAt").isNull()).isTrue();

        UUID executionId = UUID.fromString(execution.get("id").asText());
        JsonNode fetched = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/executions/" + executionId, 200));
        assertThat(fetched.get("status").asText()).isEqualTo("PENDING");
        assertThat(fetched.get("input").get("topic").asText()).isEqualTo("durable work");
        assertThat(fetched.get("nodes")).isEmpty();

        JsonNode listed = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/executions", 200));
        assertThat(listed.get("totalElements").asInt()).isEqualTo(1);
        assertThat(listed.get("items").get(0).get("id").asText())
                .isEqualTo(executionId.toString());
    }

    @Test
    void startingWithoutAPublishedVersionConflicts() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("ex-unpublished"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Execution Unpublished"));
        UUID workflowId = createWorkflow(owner, csrf, workspaceId, "No published version");

        JsonNode error = contentAsTree(postJson(owner, csrf, startUrl(workspaceId, workflowId),
                "{}", 409));
        assertThat(error.get("code").asText()).isEqualTo("WORKFLOW_NOT_PUBLISHED");

        postJson(owner, csrf, startUrl(workspaceId, workflowId),
                "{\"versionId\":\"" + UUID.randomUUID() + "\"}", 404);
    }

    @Test
    void idempotentReplayReturnsTheOriginalExecution() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("ex-idem"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Execution Idempotency"));
        Fixture fixture = fixture(owner, csrf, workspaceId);

        String body = "{\"input\":{\"b\":2,\"a\":1},\"idempotencyKey\":\"client-1\"}";
        JsonNode first = contentAsTree(postJson(owner, csrf,
                startUrl(workspaceId, fixture.workflowId()), body, 201));
        JsonNode replay = contentAsTree(postJson(owner, csrf,
                startUrl(workspaceId, fixture.workflowId()),
                "{\"input\":{\"a\":1,\"b\":2},\"idempotencyKey\":\"client-1\"}", 200));

        assertThat(replay.get("id").asText()).isEqualTo(first.get("id").asText());

        JsonNode listed = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/executions", 200));
        assertThat(listed.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentInputConflicts() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("ex-idem-conflict"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Execution Conflict"));
        Fixture fixture = fixture(owner, csrf, workspaceId);

        postJson(owner, csrf, startUrl(workspaceId, fixture.workflowId()),
                "{\"input\":{\"a\":1},\"idempotencyKey\":\"client-2\"}", 201);

        JsonNode error = contentAsTree(postJson(owner, csrf,
                startUrl(workspaceId, fixture.workflowId()),
                "{\"input\":{\"a\":2},\"idempotencyKey\":\"client-2\"}", 409));
        assertThat(error.get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");

        JsonNode listed = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/executions", 200));
        assertThat(listed.get("totalElements").asInt()).isEqualTo(1);
        JsonNode original = listed.get("items").get(0);
        assertThat(original.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void cancellingAPendingExecutionMarksItCancelledImmediately() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("ex-cancel"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Execution Cancel"));
        Fixture fixture = fixture(owner, csrf, workspaceId);

        JsonNode execution = contentAsTree(postJson(owner, csrf,
                startUrl(workspaceId, fixture.workflowId()), "{}", 201));
        UUID executionId = UUID.fromString(execution.get("id").asText());

        JsonNode cancelled = contentAsTree(postJson(owner, csrf,
                "/api/v1/workspaces/" + workspaceId + "/executions/" + executionId + "/cancel",
                "{}", 200));
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("cancelRequestedAt").isNull()).isFalse();
        assertThat(cancelled.get("completedAt").isNull()).isFalse();

        JsonNode logs = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/executions/" + executionId + "/logs", 200));
        assertThat(logs.get("items").findValuesAsText("eventType"))
                .contains("EXECUTION_CREATED", "EXECUTION_CANCELLED");
    }

    @Test
    void cancellingAFinishedExecutionIsRejected() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("ex-cancel-done"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Execution Finished"));
        Fixture fixture = fixture(owner, csrf, workspaceId);

        JsonNode execution = contentAsTree(postJson(owner, csrf,
                startUrl(workspaceId, fixture.workflowId()), "{}", 201));
        UUID executionId = UUID.fromString(execution.get("id").asText());

        Execution row = executions.findById(executionId).orElseThrow();
        row.setStatus(ExecutionStatus.COMPLETED.name());
        executions.saveAndFlush(row);

        JsonNode error = contentAsTree(postJson(owner, csrf,
                "/api/v1/workspaces/" + workspaceId + "/executions/" + executionId + "/cancel",
                "{}", 409));
        assertThat(error.get("code").asText()).isEqualTo("EXECUTION_NOT_CANCELLABLE");
    }

    @Test
    void executionsAndLogsAreWorkspaceScoped() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("ex-scope-owner"), csrf);
        MockHttpSession outsider = signIn(uniqueEmail("ex-scope-outsider"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Execution Scope"));
        Fixture fixture = fixture(owner, csrf, workspaceId);

        JsonNode execution = contentAsTree(postJson(owner, csrf,
                startUrl(workspaceId, fixture.workflowId()), "{}", 201));
        UUID executionId = UUID.fromString(execution.get("id").asText());

        getJson(outsider, "/api/v1/workspaces/" + workspaceId + "/executions/" + executionId, 404);
        getJson(outsider,
                "/api/v1/workspaces/" + workspaceId + "/executions/" + executionId + "/logs", 404);
        postJson(outsider, csrf,
                "/api/v1/workspaces/" + workspaceId + "/executions/" + executionId + "/cancel",
                "{}", 404);
        getJson(outsider, "/api/v1/workspaces/" + workspaceId + "/executions", 404);
    }

    @Test
    void logsAreOrderedAndScopedToTheExecution() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("ex-logs"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Execution Logs"));
        Fixture fixture = fixture(owner, csrf, workspaceId);

        UUID first = UUID.fromString(contentAsTree(postJson(owner, csrf,
                startUrl(workspaceId, fixture.workflowId()), "{}", 201)).get("id").asText());
        UUID second = UUID.fromString(contentAsTree(postJson(owner, csrf,
                startUrl(workspaceId, fixture.workflowId()), "{}", 201)).get("id").asText());

        JsonNode logs = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/executions/" + first + "/logs", 200));
        assertThat(logs.get("totalElements").asInt()).isEqualTo(1);
        assertThat(logs.get("items").get(0).get("eventType").asText()).isEqualTo("EXECUTION_CREATED");
        assertThat(executionLogs.findByExecution(executions.findById(second).orElseThrow(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    private Fixture fixture(MockHttpSession session, Cookie csrf, UUID workspaceId) throws Exception {
        UUID credentialId = createCredential(session, csrf, workspaceId);
        UUID agentId = createAgent(session, csrf, workspaceId, "Worker Agent", credentialId);
        UUID workflowId = createWorkflow(session, csrf, workspaceId, "Execution flow");
        JsonNode version = contentAsTree(postJson(session, csrf,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId + "/versions",
                """
                        {"publish":true,
                         "nodes":[
                           {"key":"agent","type":"agent","config":{"agentId":"%s","inputTemplate":"{{input.topic}}"}},
                           {"key":"out","type":"output","config":{"outputTemplate":{"answer":"{{agent.output}}"}}}
                         ],
                         "edges":[{"source":"agent","target":"out"}]}
                        """.formatted(agentId), 201));
        return new Fixture(workflowId, UUID.fromString(version.get("id").asText()));
    }

    private UUID createWorkflow(MockHttpSession session, Cookie csrf, UUID workspaceId, String name)
            throws Exception {
        return UUID.fromString(contentAsTree(postJson(session, csrf,
                "/api/v1/workspaces/" + workspaceId + "/workflows",
                objectMapper.writeValueAsString(Map.of("name", name)), 201)).get("id").asText());
    }

    private static String startUrl(UUID workspaceId, UUID workflowId) {
        return "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId + "/executions";
    }

    private record Fixture(UUID workflowId, UUID versionId) {
    }
}
