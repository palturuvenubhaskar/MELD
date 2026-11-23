package com.meld.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.meld.engine.domain.ExecutionStatus;
import com.meld.engine.domain.NodeStatus;
import com.meld.testsupport.ApiTestSupport;
import com.meld.usage.UsageRecord;
import com.meld.usage.UsageRecordRepository;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Runs the durable worker against a real PostgreSQL queue and a local stub
 * provider (docs/TEST.md TC-014, TC-016, TC-017, TC-022, TC-023, TC-027,
 * TC-029, TC-030).
 *
 * <p>The worker deliberately commits in short {@code REQUIRES_NEW}
 * transactions, so this suite runs without a test transaction and uses unique
 * accounts per run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExecutionWorkerTest extends ApiTestSupport {

    private static final HttpServer STUB;
    private static final ExecutorService STUB_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicReference<String> MODE = new AtomicReference<>("success");
    private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB.createContext("/v1/chat/completions", exchange -> {
                LAST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                int status = 200;
                String body;
                switch (MODE.get()) {
                    case "slow" -> {
                        sleep(3000);
                        body = "{}";
                    }
                    default -> body = "{\"model\":\"" + TEST_MODEL + "\","
                            + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"stub answer\"}}],"
                            + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5}}";
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            STUB.setExecutor(STUB_EXECUTOR);
            STUB.start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void providerBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("meld.providers.base-urls.openrouter",
                () -> "http://127.0.0.1:" + STUB.getAddress().getPort() + "/v1");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
        STUB_EXECUTOR.shutdownNow();
    }

    @BeforeEach
    void resetStub() {
        MODE.set("success");
        LAST_BODY.set(null);
    }

    @Autowired private ExecutionWorker worker;
    @Autowired private ExecutionService executionService;
    @Autowired private ExecutionRepository executions;
    @Autowired private NodeExecutionRepository nodeExecutions;
    @Autowired private ExecutionLogRepository executionLogs;
    @Autowired private UsageRecordRepository usageRecords;

    @Test
    void runsSequentialNodesAndRecordsUsage() throws Exception {
        drainQueue();
        LAST_BODY.set(null);

        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("worker-seq"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Worker Sequential"));
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Sequencer", credentialId);

        UUID workflowId = publish(owner, csrf, workspaceId, """
                [
                  {"key":"agent","type":"agent","config":{"agentId":"%s","inputTemplate":"Research {{input.topic}}"}},
                  {"key":"transform","type":"tool","config":{"toolType":"json_transform","mapping":{"summary":"output"}}},
                  {"key":"out","type":"output","config":{"outputTemplate":{"answer":"{{agent.output}}","summary":"{{transform.summary}}"}}}
                ]
                """.formatted(agentId), """
                [{"source":"agent","target":"transform"},{"source":"transform","target":"out"}]
                """);

        UUID executionId = start(owner, csrf, workspaceId, workflowId,
                "{\"input\":{\"topic\":\"meld\"}}");
        drainQueue();

        Execution execution = reload(executionId);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED.name());
        assertThat(execution.getCompletedAt()).isNotNull();

        JsonNode output = objectMapper.readTree(execution.getOutput());
        assertThat(output.get("answer").asText()).isEqualTo("stub answer");
        assertThat(output.get("summary").asText()).isEqualTo("stub answer");

        List<NodeExecution> nodes = nodeExecutions.findByExecutionOrdered(execution);
        assertThat(nodes).extracting(NodeExecution::getNodeKey)
                .containsExactly("agent", "transform", "out");
        assertThat(nodes).allSatisfy(node -> {
            assertThat(node.getStatus()).isEqualTo(NodeStatus.COMPLETED.name());
            assertThat(node.getCompletedAt()).isNotNull();
        });

        List<ExecutionLog> logs = executionLogs.findByExecution(execution, PageRequest.of(0, 100))
                .getContent();
        assertThat(logs).extracting(ExecutionLog::getEventType)
                .contains("EXECUTION_STARTED", "EXECUTION_COMPLETED")
                .containsOnlyOnce("EXECUTION_STARTED");
        assertThat(logs.stream().filter(entry -> entry.getEventType().equals("NODE_COMPLETED")).count())
                .isEqualTo(3);

        List<UsageRecord> usage = usageRecords.findByExecutionId(executionId);
        assertThat(usage).hasSize(1);
        assertThat(usage.get(0).getProvider()).isEqualTo("OPENROUTER");
        assertThat(usage.get(0).getModel()).isEqualTo(TEST_MODEL);
        assertThat(usage.get(0).getInputTokens()).isEqualTo(12L);
        assertThat(usage.get(0).getOutputTokens()).isEqualTo(5L);
        assertThat(usage.get(0).getDurationMs()).isNotNull();

        assertThat(LAST_BODY.get())
                .contains("Research meld")
                .contains("You are a test agent.")
                .doesNotContain(API_KEY);
    }

    @Test
    void runtimeUsesTheVersionSnapshotNotTheEditedAgent() throws Exception {
        drainQueue();
        LAST_BODY.set(null);

        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("worker-snapshot"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Worker Snapshot"));
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Snapshot Agent", credentialId);

        UUID workflowId = publish(owner, csrf, workspaceId, simpleNodes(agentId, "{{input.topic}}"), simpleEdges());

        patchJson(owner, csrf, "/api/v1/workspaces/" + workspaceId + "/agents/" + agentId,
                "{\"systemPrompt\":\"NEW PROMPT AFTER PUBLISH\"}", 200);

        UUID executionId = start(owner, csrf, workspaceId, workflowId, "{\"input\":{\"topic\":\"frozen\"}}");
        drainQueue();

        assertThat(reload(executionId).getStatus()).isEqualTo(ExecutionStatus.COMPLETED.name());
        assertThat(LAST_BODY.get())
                .contains("You are a test agent.")
                .contains("frozen")
                .doesNotContain("NEW PROMPT AFTER PUBLISH");
    }

    @Test
    void providerTimeoutIsPersistedAsAFailure() throws Exception {
        drainQueue();
        MODE.set("slow");

        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("worker-timeout"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Worker Timeout"));
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Slow Agent", credentialId);
        UUID workflowId = publish(owner, csrf, workspaceId, simpleNodes(agentId, "{{input.topic}}"), simpleEdges());

        UUID executionId = start(owner, csrf, workspaceId, workflowId, "{\"input\":{\"topic\":\"slow\"}}");
        drainQueue();

        Execution execution = reload(executionId);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED.name());
        assertThat(execution.getErrorCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(execution.getErrorMessage()).doesNotContain(API_KEY);

        NodeExecution node = nodeExecutions.findByExecutionOrdered(execution).get(0);
        assertThat(node.getStatus()).isEqualTo(NodeStatus.FAILED.name());
        assertThat(node.getErrorCode()).isEqualTo("PROVIDER_TIMEOUT");

        assertThat(executionLogs.findByExecution(execution, PageRequest.of(0, 100)).getContent())
                .extracting(ExecutionLog::getEventType)
                .contains("NODE_FAILED", "EXECUTION_FAILED");
    }

    @Test
    void blockedToolDestinationFailsTheNodeAndExecution() throws Exception {
        drainQueue();

        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("worker-egress"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Worker Egress"));
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Egress Agent", credentialId);

        UUID workflowId = publish(owner, csrf, workspaceId, """
                [
                  {"key":"agent","type":"agent","config":{"agentId":"%s","inputTemplate":"{{input.topic}}"}},
                  {"key":"probe","type":"tool","config":{"toolType":"http_get","url":"https://localhost:9/probe"}},
                  {"key":"out","type":"output","config":{"outputTemplate":{"x":"{{agent.output}}"}}}
                ]
                """.formatted(agentId), """
                [{"source":"agent","target":"probe"},{"source":"probe","target":"out"}]
                """);

        UUID executionId = start(owner, csrf, workspaceId, workflowId, "{\"input\":{\"topic\":\"egress\"}}");
        drainQueue();

        Execution execution = reload(executionId);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED.name());
        assertThat(execution.getErrorCode()).isEqualTo("TOOL_EGRESS_BLOCKED");

        List<NodeExecution> nodes = nodeExecutions.findByExecutionOrdered(execution);
        assertThat(nodes).extracting(NodeExecution::getNodeKey).containsExactly("agent", "probe");
        assertThat(nodes.get(1).getStatus()).isEqualTo(NodeStatus.FAILED.name());
        assertThat(nodes.get(1).getErrorCode()).isEqualTo("TOOL_EGRESS_BLOCKED");
    }

    @Test
    void cancellationTakesEffectAtTheNextNodeBoundary() throws Exception {
        drainQueue();

        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("worker-cancel"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Worker Cancel"));
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Cancel Agent", credentialId);
        UUID workflowId = publish(owner, csrf, workspaceId, simpleNodes(agentId, "{{input.topic}}"), simpleEdges());

        UUID executionId = start(owner, csrf, workspaceId, workflowId, "{\"input\":{\"topic\":\"cancel\"}}");

        // Simulate a cancellation requested after the row was queued but before
        // a worker picked it up. No external action has happened yet.
        Execution row = executions.findById(executionId).orElseThrow();
        row.setCancelRequestedAt(Instant.now());
        executions.saveAndFlush(row);

        drainQueue();

        Execution execution = reload(executionId);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED.name());
        assertThat(execution.getCancelRequestedAt()).isNotNull();
        assertThat(nodeExecutions.findByExecution(execution)).isEmpty();
        assertThat(executionLogs.findByExecution(execution, PageRequest.of(0, 100)).getContent())
                .extracting(ExecutionLog::getEventType)
                .contains("EXECUTION_CANCELLED");
    }

    @Test
    void expiredLeaseWithAnInFlightNodeBecomesOutcomeUnknown() throws Exception {
        drainQueue();

        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("worker-unknown"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Worker Unknown"));
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Crashed Agent", credentialId);
        UUID workflowId = publish(owner, csrf, workspaceId, simpleNodes(agentId, "{{input.topic}}"), simpleEdges());

        UUID executionId = start(owner, csrf, workspaceId, workflowId, "{\"input\":{\"topic\":\"crash\"}}");
        Execution execution = executions.findById(executionId).orElseThrow();
        execution.setStatus(ExecutionStatus.RUNNING.name());
        execution.setLeaseOwner("crashed-worker");
        execution.setLeaseExpiresAt(Instant.now().minusSeconds(60));
        execution.setStartedAt(Instant.now().minusSeconds(120));
        executions.saveAndFlush(execution);

        NodeExecution inFlight = new NodeExecution(UUID.randomUUID(), execution,
                "agent", NodeStatus.RUNNING.name(), 1);
        inFlight.setInput("{}");
        inFlight.setStartedAt(Instant.now().minusSeconds(120));
        nodeExecutions.saveAndFlush(inFlight);

        worker.recoverExpired();

        Execution recovered = reload(executionId);
        assertThat(recovered.getStatus()).isEqualTo(ExecutionStatus.FAILED.name());
        assertThat(recovered.getErrorCode()).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(nodeExecutions.findByExecution(recovered)).singleElement()
                .satisfies(node -> assertThat(node.getStatus()).isEqualTo(NodeStatus.OUTCOME_UNKNOWN.name()));

        int attemptsBefore = nodeExecutions.findByExecution(recovered).size();
        drainQueue();

        Execution untouched = reload(executionId);
        assertThat(untouched.getStatus()).isEqualTo(ExecutionStatus.FAILED.name());
        assertThat(nodeExecutions.findByExecution(untouched)).hasSize(attemptsBefore);
    }

    @Test
    void expiredLeaseWithoutAnInFlightNodeReturnsToTheQueue() throws Exception {
        drainQueue();

        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn(uniqueEmail("worker-recover"), csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Worker Recover"));
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Recovered Agent", credentialId);
        UUID workflowId = publish(owner, csrf, workspaceId, simpleNodes(agentId, "{{input.topic}}"), simpleEdges());

        UUID executionId = start(owner, csrf, workspaceId, workflowId, "{\"input\":{\"topic\":\"recover\"}}");
        Execution execution = executions.findById(executionId).orElseThrow();
        execution.setStatus(ExecutionStatus.RUNNING.name());
        execution.setLeaseOwner("crashed-worker");
        execution.setLeaseExpiresAt(Instant.now().minusSeconds(60));
        executions.saveAndFlush(execution);

        worker.recoverExpired();

        Execution queued = reload(executionId);
        assertThat(queued.getStatus()).isEqualTo(ExecutionStatus.PENDING.name());
        assertThat(queued.getLeaseOwner()).isNull();
        assertThat(queued.getLeaseExpiresAt()).isNull();

        drainQueue();
        assertThat(reload(executionId).getStatus()).isEqualTo(ExecutionStatus.COMPLETED.name());
    }

    @Test
    void simultaneousStartsWithTheSameKeyCreateOneExecution() throws Exception {
        drainQueue();

        Cookie csrf = csrfCookie();
        String email = uniqueEmail("worker-idem");
        MockHttpSession owner = signIn(email, csrf);
        UUID workspaceId = createWorkspace(owner, csrf, uniqueName("Worker Idempotency"));
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Idempotent Agent", credentialId);
        UUID workflowId = publish(owner, csrf, workspaceId, simpleNodes(agentId, "{{input.topic}}"), simpleEdges());
        UUID actorId = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        UUID versionId = UUID.fromString(contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId + "/versions", 200))
                .get(0).get("id").asText());

        ExecutionDtos.StartExecutionRequest request = new ExecutionDtos.StartExecutionRequest(
                versionId,
                objectMapper.readTree("{\"topic\":\"race\"}"),
                "race-key");

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<ExecutionService.StartResult> call = () -> {
                ready.await(5, TimeUnit.SECONDS);
                return executionService.start(actorId, workspaceId, workflowId, request);
            };
            Future<ExecutionService.StartResult> first = pool.submit(call);
            Future<ExecutionService.StartResult> second = pool.submit(call);
            ready.countDown();

            ExecutionService.StartResult a = first.get(10, TimeUnit.SECONDS);
            ExecutionService.StartResult b = second.get(10, TimeUnit.SECONDS);

            assertThat(a.summary().id()).isEqualTo(b.summary().id());
            assertThat(a.created() ^ b.created()).isTrue();
            assertThat(executions.findByWorkspaceIdAndIdempotencyKey(workspaceId, "race-key")).isPresent();
        } finally {
            pool.shutdownNow();
        }

        drainQueue();
    }

    private UUID publish(MockHttpSession session, Cookie csrf, UUID workspaceId,
                         String nodesJson, String edgesJson) throws Exception {
        UUID workflowId = UUID.fromString(contentAsTree(postJson(session, csrf,
                "/api/v1/workspaces/" + workspaceId + "/workflows",
                objectMapper.writeValueAsString(Map.of("name", "Worker flow " + UUID.randomUUID())), 201))
                .get("id").asText());

        postJson(session, csrf, "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId + "/versions",
                "{\"publish\":true,\"nodes\":" + nodesJson + ",\"edges\":" + edgesJson + "}", 201);
        return workflowId;
    }

    private UUID start(MockHttpSession session, Cookie csrf, UUID workspaceId, UUID workflowId,
                       String body) throws Exception {
        return UUID.fromString(contentAsTree(postJson(session, csrf,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId + "/executions",
                body, 201)).get("id").asText());
    }

    private Execution reload(UUID executionId) {
        return executions.findById(executionId).orElseThrow();
    }

    private void drainQueue() {
        for (int i = 0; i < 50; i++) {
            if (!worker.claimAndRunOne()) {
                return;
            }
        }
        fail("The execution queue did not drain");
    }

    private static String simpleNodes(UUID agentId, String inputTemplate) {
        return """
                [
                  {"key":"agent","type":"agent","config":{"agentId":"%s","inputTemplate":"%s"}},
                  {"key":"out","type":"output","config":{"outputTemplate":{"answer":"{{agent.output}}"}}}
                ]
                """.formatted(agentId, inputTemplate);
    }

    private static String simpleEdges() {
        return "[{\"source\":\"agent\",\"target\":\"out\"}]";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
