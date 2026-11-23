package com.meld.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meld.common.exception.ApiException;
import com.meld.common.response.PageResponse;
import com.meld.common.util.JsonCanonicalizer;
import com.meld.engine.domain.ExecutionStatus;
import com.meld.workflow.WorkflowDtos;
import com.meld.workflow.WorkflowService;
import com.meld.workflow.WorkflowVersionRepository;
import com.meld.workspace.WorkspaceAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Execution lifecycle per ARCHITECTURE.md §8/§10 and ROADMAP.md M6.
 *
 * <p>Starting an execution only creates the durable queue row. A worker claims
 * it later, so an HTTP request never performs a model call. Idempotent replay
 * returns the original execution when the request hash matches.
 */
@Service
public class ExecutionService {

    private final ExecutionRepository executions;
    private final NodeExecutionRepository nodeExecutions;
    private final ExecutionLogRepository logs;
    private final WorkflowService workflows;
    private final WorkflowVersionRepository versions;
    private final WorkspaceAccessService access;
    private final ObjectMapper mapper;
    private final TransactionTemplate insertTx;

    public ExecutionService(ExecutionRepository executions,
                            NodeExecutionRepository nodeExecutions,
                            ExecutionLogRepository logs,
                            WorkflowService workflows,
                            WorkflowVersionRepository versions,
                            WorkspaceAccessService access,
                            ObjectMapper mapper,
                            PlatformTransactionManager transactionManager) {
        this.executions = executions;
        this.nodeExecutions = nodeExecutions;
        this.logs = logs;
        this.workflows = workflows;
        this.versions = versions;
        this.access = access;
        this.mapper = mapper;
        this.insertTx = new TransactionTemplate(transactionManager);
        // The queue insert commits in its own transaction so a unique-key race
        // can be resolved by reading the winner without poisoning this one.
        this.insertTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record StartResult(ExecutionDtos.ExecutionSummary summary, boolean created) {}

    @Transactional
    public StartResult start(UUID actorId, UUID workspaceId, UUID workflowId,
                             ExecutionDtos.StartExecutionRequest request) {
        access.requireMembership(workspaceId, actorId);

        WorkflowDtos.VersionResponse version = resolveVersion(workspaceId, workflowId, request.versionId());
        JsonNode input = request.input() == null ? mapper.createObjectNode() : request.input();
        String canonicalInput = JsonCanonicalizer.canonical(input);
        String requestHash = JsonCanonicalizer.sha256Hex(
                workflowId + "\n" + version.id() + "\n" + canonicalInput);

        String idempotencyKey = trimToNull(request.idempotencyKey());
        if (idempotencyKey != null) {
            var existing = executions.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey);
            if (existing.isPresent()) {
                Execution execution = existing.get();
                if (!requestHash.equals(execution.getRequestHash())) {
                    throw idempotencyConflict();
                }
                return new StartResult(toSummary(execution), false);
            }
        }

        Execution execution = new Execution(UUID.randomUUID(), workspaceId, workflowId,
                version.id(), ExecutionStatus.PENDING.name());
        execution.setInput(canonicalInput);
        execution.setCreatedBy(actorId);
        if (idempotencyKey != null) {
            execution.setIdempotencyKey(idempotencyKey);
            execution.setRequestHash(requestHash);
        }

        try {
            Execution[] persisted = new Execution[1];
            insertTx.executeWithoutResult(status -> {
                persisted[0] = executions.saveAndFlush(execution);
                writeLog(persisted[0], null, "INFO", "EXECUTION_CREATED", "Execution queued");
            });
            return new StartResult(toSummary(persisted[0]), true);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request with the same key won the unique index.
            if (idempotencyKey == null) {
                throw ex;
            }
            Execution winner = executions.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey)
                    .orElseThrow(() -> idempotencyConflict());
            if (!requestHash.equals(winner.getRequestHash())) {
                throw idempotencyConflict();
            }
            return new StartResult(toSummary(winner), false);
        }
    }

    @Transactional(readOnly = true)
    public ExecutionDtos.ExecutionResponse get(UUID userId, UUID workspaceId, UUID executionId) {
        access.requireMembership(workspaceId, userId);
        Execution execution = requireExecution(workspaceId, executionId);
        return toResponse(execution);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionDtos.ExecutionSummary> list(UUID userId, UUID workspaceId,
                                                             UUID workflowId, int page, int size) {
        access.requireMembership(workspaceId, userId);
        Pageable pageable = pageRequest(page, size);
        Page<Execution> result = workflowId == null
                ? executions.findByWorkspaceId(workspaceId, pageable)
                : executions.findByWorkspaceIdAndWorkflowId(workspaceId, workflowId, pageable);
        return PageResponse.from(result, this::toSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionDtos.ExecutionLogResponse> logs(UUID userId, UUID workspaceId,
                                                                 UUID executionId, int page, int size) {
        access.requireMembership(workspaceId, userId);
        Execution execution = requireExecution(workspaceId, executionId);
        Page<ExecutionLog> result = logs.findByExecution(execution, pageRequest(page, size));
        return PageResponse.from(result, this::toLogResponse);
    }

    @Transactional
    public ExecutionDtos.ExecutionResponse cancel(UUID actorId, UUID workspaceId, UUID executionId) {
        access.requireMembership(workspaceId, actorId);
        Execution execution = requireExecution(workspaceId, executionId);
        if (isTerminal(execution.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "EXECUTION_NOT_CANCELLABLE",
                    "This execution has already finished");
        }

        Instant now = Instant.now();
        if (execution.getCancelRequestedAt() == null) {
            execution.setCancelRequestedAt(now);
        }
        if (ExecutionStatus.PENDING.name().equals(execution.getStatus())) {
            execution.setStatus(ExecutionStatus.CANCELLED.name());
            execution.setCompletedAt(now);
            writeLog(execution, null, "INFO", "EXECUTION_CANCELLED",
                    "Execution cancelled before a worker claimed it");
        } else {
            writeLog(execution, null, "INFO", "EXECUTION_CANCEL_REQUESTED",
                    "Cancellation requested; it takes effect at the next node boundary");
        }
        executions.saveAndFlush(execution);
        return toResponse(execution);
    }

    private WorkflowDtos.VersionResponse resolveVersion(UUID workspaceId, UUID workflowId, UUID versionId) {
        if (versionId == null) {
            return workflows.latestPublishedVersion(workspaceId, workflowId);
        }
        return workflows.requirePublishedVersion(workspaceId, workflowId, versionId);
    }

    private Execution requireExecution(UUID workspaceId, UUID executionId) {
        return executions.findByIdAndWorkspaceId(executionId, workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "EXECUTION_NOT_FOUND", "Execution not found"));
    }

    private ExecutionDtos.ExecutionSummary toSummary(Execution execution) {
        return new ExecutionDtos.ExecutionSummary(
                execution.getId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersionId(),
                versionNumber(execution),
                execution.getStatus(),
                execution.getErrorCode(),
                execution.getErrorMessage(),
                execution.getCreatedAt(),
                execution.getStartedAt(),
                execution.getCompletedAt(),
                execution.getCancelRequestedAt());
    }

    private ExecutionDtos.ExecutionResponse toResponse(Execution execution) {
        List<ExecutionDtos.NodeExecutionResponse> nodes = nodeExecutions.findByExecutionOrdered(execution).stream()
                .sorted(Comparator.comparing(NodeExecution::getStartedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(node -> new ExecutionDtos.NodeExecutionResponse(
                        node.getId(),
                        node.getNodeKey(),
                        node.getStatus(),
                        node.getAttemptNumber(),
                        readJson(node.getInput()),
                        readJson(node.getOutput()),
                        node.getErrorCode(),
                        node.getErrorMessage(),
                        node.getStartedAt(),
                        node.getCompletedAt()))
                .toList();
        return new ExecutionDtos.ExecutionResponse(
                execution.getId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersionId(),
                versionNumber(execution),
                execution.getStatus(),
                readJson(execution.getInput()),
                readJson(execution.getOutput()),
                execution.getErrorCode(),
                execution.getErrorMessage(),
                execution.getCreatedAt(),
                execution.getStartedAt(),
                execution.getCompletedAt(),
                execution.getCancelRequestedAt(),
                nodes);
    }

    private ExecutionDtos.ExecutionLogResponse toLogResponse(ExecutionLog log) {
        return new ExecutionDtos.ExecutionLogResponse(
                log.getId(),
                log.getNodeExecutionId(),
                log.getLevel(),
                log.getEventType(),
                log.getMessage(),
                readJson(log.getMetadata()),
                log.getCreatedAt());
    }

    private int versionNumber(Execution execution) {
        return versions.findVersionNumberById(execution.getWorkflowVersionId()).orElse(0);
    }

    private void writeLog(Execution execution, UUID nodeExecutionId, String level,
                          String eventType, String message) {
        ExecutionLog log = new ExecutionLog(UUID.randomUUID(), execution, level, eventType, message);
        log.setNodeExecutionId(nodeExecutionId);
        logs.save(log);
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(value);
        } catch (Exception ex) {
            return mapper.getNodeFactory().textNode(value);
        }
    }

    private static boolean isTerminal(String status) {
        return ExecutionStatus.COMPLETED.name().equals(status)
                || ExecutionStatus.FAILED.name().equals(status)
                || ExecutionStatus.CANCELLED.name().equals(status);
    }

    private static Pageable pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(safePage, safeSize);
    }

    private static ApiException idempotencyConflict() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                "This idempotency key was already used for a different request");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
