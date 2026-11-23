package com.meld.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meld.common.exception.ApiException;
import com.meld.engine.domain.ExecutionStatus;
import com.meld.engine.domain.NodeStatus;
import com.meld.provider.DecryptedCredential;
import com.meld.provider.ProviderCredentialService;
import com.meld.provider.adapter.ChatProvider;
import com.meld.provider.adapter.ChatProviderRouter;
import com.meld.tool.ToolExecutionException;
import com.meld.tool.ToolRuntime;
import com.meld.usage.UsageRecord;
import com.meld.usage.UsageRecordRepository;
import com.meld.workflow.WorkflowRunDefinition;
import com.meld.workflow.WorkflowService;
import com.meld.workspace.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable PostgreSQL-backed execution worker per ARCHITECTURE.md §10.
 *
 * <p>Each execution row is the queue item. A claim takes a row lock with
 * {@code FOR UPDATE SKIP LOCKED}, records an expiring lease, and then runs the
 * workflow outside the claim transaction. Node state, logs and usage are
 * committed in short transactions, so a crash never loses completed history.
 * Recovery never replays a node that was running when its worker disappeared:
 * its outcome is unknown and is surfaced for an operator.
 */
@Component
public class ExecutionWorker {

    private static final Logger log = LoggerFactory.getLogger(ExecutionWorker.class);

    private final ExecutionRepository executions;
    private final NodeExecutionRepository nodeExecutions;
    private final ExecutionLogRepository executionLogs;
    private final UsageRecordRepository usageRecords;
    private final WorkspaceRepository workspaces;
    private final WorkflowService workflows;
    private final ProviderCredentialService credentials;
    private final ChatProviderRouter chatProviders;
    private final ToolRuntime tools;
    private final ObjectMapper mapper;
    private final ExecutionProperties properties;
    private final TransactionTemplate tx;
    private final String workerId = "worker-" + UUID.randomUUID();

    public ExecutionWorker(ExecutionRepository executions,
                           NodeExecutionRepository nodeExecutions,
                           ExecutionLogRepository executionLogs,
                           UsageRecordRepository usageRecords,
                           WorkspaceRepository workspaces,
                           WorkflowService workflows,
                           ProviderCredentialService credentials,
                           ChatProviderRouter chatProviders,
                           ToolRuntime tools,
                           ObjectMapper mapper,
                           ExecutionProperties properties,
                           PlatformTransactionManager transactionManager) {
        this.executions = executions;
        this.nodeExecutions = nodeExecutions;
        this.executionLogs = executionLogs;
        this.usageRecords = usageRecords;
        this.workspaces = workspaces;
        this.workflows = workflows;
        this.credentials = credentials;
        this.chatProviders = chatProviders;
        this.tools = tools;
        this.mapper = mapper;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${meld.execution.poll-interval-ms:500}")
    public void poll() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        try {
            recoverExpired();
        } catch (RuntimeException ex) {
            log.warn("Execution recovery pass failed ({})", ex.getClass().getSimpleName());
        }
        for (int i = 0; i < properties.getMaxPerPoll(); i++) {
            try {
                if (!claimAndRunOne()) {
                    return;
                }
            } catch (RuntimeException ex) {
                log.warn("Execution worker pass failed ({})", ex.getClass().getSimpleName());
                return;
            }
        }
    }

    /**
     * Keeps this worker's leases alive while a long node call is in flight, so
     * another instance does not treat live work as abandoned. Tests disable the
     * scheduler and drive {@link #claimAndRunOne()} directly.
     */
    @Scheduled(fixedDelayString = "${meld.execution.heartbeat-interval-ms:10000}")
    public void heartbeat() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        Instant now = Instant.now();
        try {
            tx.executeWithoutResult(status -> executions.extendLeases(
                    workerId, now, now.plus(properties.getLeaseDuration())));
        } catch (RuntimeException ex) {
            log.warn("Execution heartbeat failed ({})", ex.getClass().getSimpleName());
        }
    }

    /**
     * Claims and runs at most one queued execution. Package-visible so tests
     * can drive the worker deterministically with the scheduler disabled.
     */
    public boolean claimAndRunOne() {
        UUID claimed = tx.execute(status -> claim());
        if (claimed == null) {
            return false;
        }
        run(claimed);
        return true;
    }

    private UUID claim() {
        Instant now = Instant.now();
        Optional<UUID> candidate = executions.findNextClaimableId(now);
        if (candidate.isEmpty()) {
            return null;
        }
        Execution execution = executions.findById(candidate.get()).orElse(null);
        if (execution == null || !ExecutionStatus.PENDING.name().equals(execution.getStatus())) {
            return null;
        }
        execution.setStatus(ExecutionStatus.RUNNING.name());
        execution.setLeaseOwner(workerId);
        execution.setLeaseExpiresAt(now.plus(properties.getLeaseDuration()));
        execution.setHeartbeatAt(now);
        if (execution.getStartedAt() == null) {
            execution.setStartedAt(now);
        }
        executions.saveAndFlush(execution);
        writeLog(execution, null, "INFO", "EXECUTION_STARTED", "Worker claimed the execution");
        return execution.getId();
    }

    private void run(UUID executionId) {
        ExecutionData data = tx.execute(status -> loadExecution(executionId));
        if (data == null || !ExecutionStatus.RUNNING.name().equals(data.status())) {
            return;
        }

        WorkflowRunDefinition definition = workflows.loadRunDefinition(data.versionId());
        Map<String, JsonNode> outputs = loadCompletedOutputs(executionId);
        Map<String, String> next = nextEdges(definition);
        String start = startNode(definition);

        String current = start;
        JsonNode previous = null;
        while (current != null && outputs.containsKey(current)) {
            previous = outputs.get(current);
            current = next.get(current);
        }

        int executed = 0;
        while (current != null) {
            if (executed++ >= properties.getMaxNodesPerExecution()) {
                finalizeFailed(executionId, "EXECUTION_NODE_LIMIT",
                        "The execution exceeded the configured node limit");
                return;
            }
            if (cancelRequested(executionId)) {
                finalizeCancelled(executionId);
                return;
            }

            WorkflowRunDefinition.RunNode node = findNode(definition, current);
            if (node == null) {
                finalizeFailed(executionId, "WORKFLOW_INVALID", "A referenced node is missing from the version");
                return;
            }

            JsonNode nodeInput = buildNodeInput(data.input(), outputs, previous);
            UUID nodeExecutionId = startNode(executionId, current, nodeInput);
            NodeOutcome outcome = executeNode(data.workspaceId(), node, nodeInput, data.input(), previous, outputs);
            persistNodeResult(executionId, data.workspaceId(), nodeExecutionId, current, outcome);

            if (outcome.status() != NodeStatus.COMPLETED) {
                finalizeFailed(executionId, outcome.errorCode(), outcome.errorMessage());
                return;
            }

            outputs.put(current, outcome.output());
            previous = outcome.output();
            if (cancelRequested(executionId)) {
                finalizeCancelled(executionId);
                return;
            }
            current = next.get(current);
        }

        finalizeCompleted(executionId, previous == null ? mapper.createObjectNode() : previous);
    }

    private NodeOutcome executeNode(UUID workspaceId, WorkflowRunDefinition.RunNode node,
                                    JsonNode nodeInput, JsonNode workflowInput,
                                    JsonNode previous, Map<String, JsonNode> outputs) {
        try {
            return switch (node.type()) {
                case "agent" -> runAgent(workspaceId, node.config(), nodeInput, workflowInput, previous, outputs);
                case "tool" -> NodeOutcome.completed(tools.execute(workspaceId, node.config(),
                        previous != null ? previous : workflowInput));
                case "output" -> NodeOutcome.completed(TemplateRenderer.render(
                        node.config().path("outputTemplate"), outputs, workflowInput, previous, mapper));
                default -> NodeOutcome.failed("NODE_TYPE_UNSUPPORTED",
                        "Unsupported node type: " + node.type());
            };
        } catch (ApiException ex) {
            return NodeOutcome.failed(ex.getCode(), ex.getMessage());
        } catch (ToolExecutionException ex) {
            return NodeOutcome.failed(ex.getCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            // Never persist exception text: it can contain request details.
            log.warn("Node {} failed ({})", node.key(), ex.getClass().getSimpleName());
            return NodeOutcome.failed("NODE_EXECUTION_ERROR", "The node failed unexpectedly");
        }
    }

    private NodeOutcome runAgent(UUID workspaceId, JsonNode config, JsonNode nodeInput,
                                 JsonNode workflowInput, JsonNode previous, Map<String, JsonNode> outputs) {
        String agentId = config.path("agentId").asText("");
        String credentialIdText = config.path("providerCredentialId").asText("");
        String model = config.path("model").asText("");
        String systemPrompt = config.path("systemPrompt").asText("");
        if (agentId.isBlank() || credentialIdText.isBlank() || model.isBlank() || systemPrompt.isBlank()) {
            return NodeOutcome.failed("AGENT_SNAPSHOT_INVALID",
                    "The workflow version does not contain a complete agent snapshot");
        }

        UUID credentialId;
        try {
            credentialId = UUID.fromString(credentialIdText);
        } catch (IllegalArgumentException ex) {
            return NodeOutcome.failed("AGENT_SNAPSHOT_INVALID",
                    "The workflow version contains an invalid credential reference");
        }

        String userContent;
        if (config.hasNonNull("inputTemplate")) {
            userContent = TemplateRenderer.text(config.get("inputTemplate").asText(),
                    outputs, workflowInput, previous, mapper);
        } else if (previous != null) {
            userContent = previous.isObject() && previous.path("output").isTextual()
                    ? previous.path("output").asText()
                    : previous.toString();
        } else {
            userContent = workflowInput == null ? "{}" : workflowInput.toString();
        }

        Double temperature = config.hasNonNull("temperature") ? config.get("temperature").asDouble() : null;
        Integer maxTokens = config.hasNonNull("maxTokens") ? config.get("maxTokens").asInt() : null;

        DecryptedCredential credential = credentials.resolve(workspaceId, credentialId);
        ChatProvider.ChatResponse response = chatProviders.complete(new ChatProvider.ChatInvocation(
                credential.kind(),
                credential.apiKey(),
                model,
                List.of(
                        ChatProvider.ChatMessage.system(systemPrompt),
                        ChatProvider.ChatMessage.user(userContent)),
                temperature,
                maxTokens));

        ObjectNode output = mapper.createObjectNode();
        output.put("output", response.content());
        output.put("model", response.model());
        if (response.inputTokens() != null) {
            output.put("inputTokens", response.inputTokens());
        }
        if (response.outputTokens() != null) {
            output.put("outputTokens", response.outputTokens());
        }
        output.put("latencyMs", response.latencyMs());

        return new NodeOutcome(NodeStatus.COMPLETED, output, null, null,
                credential.kind().name(), response.model(),
                response.inputTokens(), response.outputTokens(), response.latencyMs());
    }

    private JsonNode buildNodeInput(JsonNode workflowInput, Map<String, JsonNode> outputs, JsonNode previous) {
        ObjectNode input = mapper.createObjectNode();
        input.set("workflowInput", workflowInput == null ? mapper.createObjectNode() : workflowInput);
        if (previous != null) {
            input.set("previous", previous);
        }
        ObjectNode nodeOutputs = mapper.createObjectNode();
        outputs.forEach(nodeOutputs::set);
        input.set("nodes", nodeOutputs);
        return input;
    }

    private UUID startNode(UUID executionId, String nodeKey, JsonNode nodeInput) {
        return tx.execute(status -> {
            Instant now = Instant.now();
            Execution execution = executions.findById(executionId).orElseThrow();
            int attempt = nodeExecutions.findByExecutionAndNodeKey(execution, nodeKey).size() + 1;
            NodeExecution nodeExecution = new NodeExecution(UUID.randomUUID(), execution,
                    nodeKey, NodeStatus.RUNNING.name(), attempt);
            nodeExecution.setInput(json(nodeInput));
            nodeExecution.setStartedAt(now);
            nodeExecutions.saveAndFlush(nodeExecution);

            execution.setHeartbeatAt(now);
            execution.setLeaseExpiresAt(now.plus(properties.getLeaseDuration()));
            executions.saveAndFlush(execution);
            writeLog(execution, nodeExecution.getId(), "INFO", "NODE_STARTED",
                    "Node '" + nodeKey + "' started");
            return nodeExecution.getId();
        });
    }

    private void persistNodeResult(UUID executionId, UUID workspaceId, UUID nodeExecutionId,
                                   String nodeKey, NodeOutcome outcome) {
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            Execution execution = executions.findById(executionId).orElseThrow();
            NodeExecution nodeExecution = nodeExecutions.findById(nodeExecutionId).orElseThrow();

            nodeExecution.setStatus(outcome.status().name());
            nodeExecution.setOutput(outcome.output() == null ? null : json(outcome.output()));
            nodeExecution.setErrorCode(outcome.errorCode());
            nodeExecution.setErrorMessage(outcome.errorMessage());
            nodeExecution.setCompletedAt(now);
            nodeExecutions.saveAndFlush(nodeExecution);

            if (outcome.provider() != null) {
                UsageRecord usage = new UsageRecord(UUID.randomUUID(),
                        workspaces.getReferenceById(workspaceId), outcome.provider(), outcome.model());
                usage.setExecutionId(executionId);
                usage.setInputTokens(outcome.inputTokens() == null ? 0L : outcome.inputTokens().longValue());
                usage.setOutputTokens(outcome.outputTokens() == null ? 0L : outcome.outputTokens().longValue());
                usage.setDurationMs(outcome.durationMs());
                usageRecords.save(usage);
            }

            execution.setHeartbeatAt(now);
            execution.setLeaseExpiresAt(now.plus(properties.getLeaseDuration()));
            executions.saveAndFlush(execution);

            boolean ok = outcome.status() == NodeStatus.COMPLETED;
            writeLog(execution, nodeExecutionId, ok ? "INFO" : "ERROR",
                    ok ? "NODE_COMPLETED" : "NODE_FAILED",
                    ok ? "Node '" + nodeKey + "' completed"
                            : "Node '" + nodeKey + "' failed: " + outcome.errorCode());
        });
    }

    private void finalizeCompleted(UUID executionId, JsonNode output) {
        tx.executeWithoutResult(status -> {
            Execution execution = executions.findById(executionId).orElseThrow();
            if (!ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
                return;
            }
            Instant now = Instant.now();
            if (execution.getCancelRequestedAt() != null) {
                finish(execution, ExecutionStatus.CANCELLED, null, null, null, now, "EXECUTION_CANCELLED",
                        "Cancellation took effect at the last node boundary");
                return;
            }
            finish(execution, ExecutionStatus.COMPLETED, output, null, null, now,
                    "EXECUTION_COMPLETED", "Execution completed");
        });
    }

    private void finalizeFailed(UUID executionId, String errorCode, String errorMessage) {
        tx.executeWithoutResult(status -> {
            Execution execution = executions.findById(executionId).orElseThrow();
            if (!ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
                return;
            }
            finish(execution, ExecutionStatus.FAILED, null,
                    errorCode == null ? "EXECUTION_FAILED" : errorCode,
                    errorMessage == null ? "The execution failed" : errorMessage,
                    Instant.now(), "EXECUTION_FAILED", "Execution failed");
        });
    }

    private void finalizeCancelled(UUID executionId) {
        tx.executeWithoutResult(status -> {
            Execution execution = executions.findById(executionId).orElseThrow();
            if (!ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
                return;
            }
            finish(execution, ExecutionStatus.CANCELLED, null, null, null,
                    Instant.now(), "EXECUTION_CANCELLED",
                    "Cancellation took effect at a node boundary");
        });
    }

    private void finish(Execution execution, ExecutionStatus status, JsonNode output,
                        String errorCode, String errorMessage, Instant now,
                        String event, String message) {
        execution.setStatus(status.name());
        if (output != null) {
            execution.setOutput(json(output));
        }
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorMessage);
        execution.setCompletedAt(now);
        execution.setLeaseOwner(null);
        execution.setLeaseExpiresAt(null);
        execution.setHeartbeatAt(null);
        executions.saveAndFlush(execution);
        writeLog(execution, null, status == ExecutionStatus.COMPLETED ? "INFO" : "WARN", event, message);
    }

    void recoverExpired() {
        Instant now = Instant.now();
        tx.executeWithoutResult(status -> {
            for (Execution execution : executions.findExpiredRunning(now, workerId)) {
                List<NodeExecution> running = nodeExecutions.findRunningByExecution(execution);
                if (!running.isEmpty()) {
                    for (NodeExecution node : running) {
                        node.setStatus(NodeStatus.OUTCOME_UNKNOWN.name());
                        node.setErrorCode("OUTCOME_UNKNOWN");
                        node.setErrorMessage("The worker stopped while this node was running; "
                                + "verify the external call before retrying");
                        node.setCompletedAt(now);
                        nodeExecutions.save(node);
                    }
                    execution.setStatus(ExecutionStatus.FAILED.name());
                    execution.setErrorCode("OUTCOME_UNKNOWN");
                    execution.setErrorMessage("A node's outcome is unknown because its worker stopped; "
                            + "it was not replayed automatically");
                    execution.setCompletedAt(now);
                    execution.setLeaseOwner(null);
                    execution.setLeaseExpiresAt(null);
                    execution.setHeartbeatAt(null);
                    executions.saveAndFlush(execution);
                    writeLog(execution, null, "WARN", "EXECUTION_OUTCOME_UNKNOWN",
                            "An in-flight node outcome is unknown; automatic replay is blocked");
                } else {
                    execution.setStatus(ExecutionStatus.PENDING.name());
                    execution.setLeaseOwner(null);
                    execution.setLeaseExpiresAt(null);
                    execution.setHeartbeatAt(null);
                    executions.saveAndFlush(execution);
                    writeLog(execution, null, "INFO", "EXECUTION_RECOVERED",
                            "Expired claim returned to the queue; completed nodes are not replayed");
                }
            }
        });
    }

    private ExecutionData loadExecution(UUID executionId) {
        return executions.findById(executionId)
                .map(execution -> new ExecutionData(
                        execution.getId(),
                        execution.getWorkspaceId(),
                        execution.getWorkflowVersionId(),
                        readJson(execution.getInput()),
                        execution.getStatus()))
                .orElse(null);
    }

    private Map<String, JsonNode> loadCompletedOutputs(UUID executionId) {
        return tx.execute(status -> {
            Execution execution = executions.findById(executionId).orElseThrow();
            Map<String, JsonNode> outputs = new LinkedHashMap<>();
            for (NodeExecution node : nodeExecutions.findByExecutionOrdered(execution)) {
                if (NodeStatus.COMPLETED.name().equals(node.getStatus()) && node.getOutput() != null) {
                    outputs.put(node.getNodeKey(), readJson(node.getOutput()));
                }
            }
            return outputs;
        });
    }

    private boolean cancelRequested(UUID executionId) {
        Boolean requested = tx.execute(status -> executions.findById(executionId)
                .map(execution -> execution.getCancelRequestedAt() != null)
                .orElse(false));
        return Boolean.TRUE.equals(requested);
    }

    private static Map<String, String> nextEdges(WorkflowRunDefinition definition) {
        Map<String, String> next = new HashMap<>();
        definition.edges().forEach(edge -> next.put(edge.source(), edge.target()));
        return next;
    }

    private static String startNode(WorkflowRunDefinition definition) {
        List<String> targets = new ArrayList<>();
        definition.edges().forEach(edge -> targets.add(edge.target()));
        return definition.nodes().stream()
                .map(WorkflowRunDefinition.RunNode::key)
                .filter(key -> !targets.contains(key))
                .findFirst()
                .orElse(null);
    }

    private static WorkflowRunDefinition.RunNode findNode(WorkflowRunDefinition definition, String key) {
        return definition.nodes().stream()
                .filter(node -> node.key().equals(key))
                .findFirst()
                .orElse(null);
    }

    private void writeLog(Execution execution, UUID nodeExecutionId, String level,
                          String eventType, String message) {
        ExecutionLog entry = new ExecutionLog(UUID.randomUUID(), execution, level, eventType, message);
        entry.setNodeExecutionId(nodeExecutionId);
        executionLogs.save(entry);
    }

    private String json(JsonNode value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize execution data", ex);
        }
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored execution data is not valid JSON", ex);
        }
    }

    private record ExecutionData(UUID id, UUID workspaceId, UUID versionId, JsonNode input, String status) {
    }

    private record NodeOutcome(
            NodeStatus status,
            JsonNode output,
            String errorCode,
            String errorMessage,
            String provider,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            Long durationMs
    ) {
        static NodeOutcome completed(JsonNode output) {
            return new NodeOutcome(NodeStatus.COMPLETED, output, null, null,
                    null, null, null, null, null);
        }

        static NodeOutcome failed(String errorCode, String errorMessage) {
            return new NodeOutcome(NodeStatus.FAILED, null, errorCode, errorMessage,
                    null, null, null, null, null);
        }
    }
}
