package com.meld.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meld.engine.domain.ExecutionContext;
import com.meld.engine.domain.ExecutionStatus;
import com.meld.engine.domain.NodeResult;
import com.meld.engine.domain.NodeStatus;
import com.meld.engine.domain.WorkflowDefinition;
import com.meld.engine.domain.WorkflowNode;
import com.meld.engine.node.NodeRegistry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequential workflow execution engine per ARCHITECTURE.md §8, §10.
 * MVP: executes connected nodes in order following directed edges.
 * Uses PostgreSQL execution row as durable queue item in production (Phase 6).
 * This is the in-memory prototype for Phase 1.
 */
public class WorkflowEngine {

    private final NodeRegistry nodeRegistry;
    private final ObjectMapper mapper;

    public WorkflowEngine() {
        this.nodeRegistry = new NodeRegistry();
        this.mapper = new ObjectMapper();
    }

    public WorkflowEngine(NodeRegistry nodeRegistry, ObjectMapper mapper) {
        this.nodeRegistry = nodeRegistry;
        this.mapper = mapper;
    }

    /**
     * Execute a workflow sequentially and return the final execution result.
     */
    public ExecutionResult execute(WorkflowDefinition workflow, JsonNode input) {
        // Build execution context
        ExecutionContext context = ExecutionContext.create(
                UUID.randomUUID(),
                UUID.fromString(workflow.workflowId()),
                UUID.fromString(workflow.versionId()),
                UUID.randomUUID(), // workspaceId - would come from auth context
                input
        );

        // Build adjacency list from edges
        Map<String, List<WorkflowDefinition.WorkflowEdgeDef>> adjacency = buildAdjacency(workflow.edges());

        // Find start nodes (nodes with no incoming edges)
        Set<String> allTargets = new HashSet<>();
        workflow.edges().forEach(e -> allTargets.add(e.targetNodeKey()));
        List<WorkflowDefinition.WorkflowNodeDef> startNodes = workflow.nodes().stream()
                .filter(n -> !allTargets.contains(n.nodeKey()))
                .toList();

        if (startNodes.isEmpty()) {
            throw new IllegalStateException("Workflow has no start node (no node without incoming edges)");
        }

        // Execute sequentially (MVP: single start node, linear chain)
        String currentNodeKey = startNodes.getFirst().nodeKey();
        ExecutionStatus executionStatus = ExecutionStatus.RUNNING;
        Map<String, NodeResult> nodeResults = new LinkedHashMap<>();
        Instant executionStartedAt = Instant.now();

        while (currentNodeKey != null) {
            // Check for cancellation (cooperative cancellation at node boundary)
            // In production, this would check a persisted cancellation flag

            // Get node definition - capture currentNodeKey for lambda
            final String nodeKey = currentNodeKey;
            WorkflowDefinition.WorkflowNodeDef nodeDef = workflow.nodes().stream()
                    .filter(n -> n.nodeKey().equals(nodeKey))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Node not found: " + nodeKey));

            // Get node implementation
            WorkflowNode node = nodeRegistry.get(nodeDef.nodeType());

            // Validate configuration
            node.validateConfiguration(nodeDef.configuration());

            // Execute node
            NodeResult result = node.execute(context, nodeDef.configuration());
            nodeResults.put(currentNodeKey, result);

            // Store node output in context for downstream nodes
            context.putNodeOutput(currentNodeKey, result.getOutput().orElse(mapper.createObjectNode()));

            // Check if execution should stop
            if (result.status() == NodeStatus.FAILED || result.status() == NodeStatus.OUTCOME_UNKNOWN) {
                executionStatus = ExecutionStatus.FAILED;
                break;
            }
            if (result.status() == NodeStatus.CANCELLED) {
                executionStatus = ExecutionStatus.CANCELLED;
                break;
            }

            // Find next node via edges
            List<WorkflowDefinition.WorkflowEdgeDef> outgoing = adjacency.get(currentNodeKey);
            if (outgoing == null || outgoing.isEmpty()) {
                // No more nodes - workflow complete
                currentNodeKey = null;
            } else {
                // MVP: take first edge (no conditional branching yet)
                // P1: evaluate condition on each edge
                currentNodeKey = outgoing.getFirst().targetNodeKey();
            }
        }

        if (executionStatus == ExecutionStatus.RUNNING) {
            executionStatus = ExecutionStatus.COMPLETED;
        }

        // Build final output from context
        JsonNode finalOutput = context.getVariable("finalOutput");
        if (finalOutput == null) {
            // Fallback: collect all node outputs
            ObjectNode fallback = mapper.createObjectNode();
            nodeResults.forEach((key, result) -> result.getOutput().ifPresent(out -> fallback.set(key, out)));
            finalOutput = fallback;
        }

        return new ExecutionResult(
                context.executionId(),
                workflow.workflowId(),
                workflow.versionId(),
                executionStatus,
                input,
                finalOutput,
                nodeResults,
                executionStartedAt,
                Instant.now()
        );
    }

    private Map<String, List<WorkflowDefinition.WorkflowEdgeDef>> buildAdjacency(List<WorkflowDefinition.WorkflowEdgeDef> edges) {
        Map<String, List<WorkflowDefinition.WorkflowEdgeDef>> adjacency = new HashMap<>();
        for (WorkflowDefinition.WorkflowEdgeDef edge : edges) {
            adjacency.computeIfAbsent(edge.sourceNodeKey(), k -> new ArrayList<>()).add(edge);
        }
        return adjacency;
    }

    public record ExecutionResult(
            UUID executionId,
            String workflowId,
            String workflowVersionId,
            ExecutionStatus status,
            JsonNode input,
            JsonNode output,
            Map<String, NodeResult> nodeResults,
            Instant startedAt,
            Instant completedAt
    ) {
        public boolean isSuccessful() {
            return status == ExecutionStatus.COMPLETED;
        }
    }
}