package com.meld.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meld.agent.AgentService;
import com.meld.agent.AgentSnapshot;
import com.meld.common.exception.ApiException;
import com.meld.tool.ToolEgressGuard;
import com.meld.workspace.WorkspaceAccessService;
import com.meld.workspace.WorkspaceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Workflow definitions and immutable versions per ROADMAP.md M6.
 *
 * <p>Creating a version snapshots every referenced agent's non-secret settings
 * into its node configuration. Later edits to the reusable agent create a new
 * version instead of changing this one (ARCHITECTURE.md §8, DATABASE.md §5).
 */
@Service
public class WorkflowService {

    private static final Set<String> NODE_TYPES = Set.of("agent", "tool", "output");
    private static final Set<String> TOOL_TYPES = Set.of("http_get", "json_transform");

    private final WorkflowRepository workflows;
    private final WorkflowVersionRepository versions;
    private final WorkflowNodeRepository nodes;
    private final WorkflowEdgeRepository edges;
    private final WorkspaceRepository workspaces;
    private final WorkspaceAccessService access;
    private final AgentService agents;
    private final ToolEgressGuard egressGuard;
    private final ObjectMapper mapper;

    public WorkflowService(WorkflowRepository workflows,
                           WorkflowVersionRepository versions,
                           WorkflowNodeRepository nodes,
                           WorkflowEdgeRepository edges,
                           WorkspaceRepository workspaces,
                           WorkspaceAccessService access,
                           AgentService agents,
                           ToolEgressGuard egressGuard,
                           ObjectMapper mapper) {
        this.workflows = workflows;
        this.versions = versions;
        this.nodes = nodes;
        this.edges = edges;
        this.workspaces = workspaces;
        this.access = access;
        this.agents = agents;
        this.egressGuard = egressGuard;
        this.mapper = mapper;
    }

    @Transactional
    public WorkflowDtos.WorkflowResponse create(UUID actorId, UUID workspaceId,
                                                WorkflowDtos.CreateWorkflowRequest request) {
        access.requireMembership(workspaceId, actorId);
        Workflow workflow = new Workflow(UUID.randomUUID(), workspaces.getReferenceById(workspaceId),
                request.name().trim());
        workflow.setDescription(trimToNull(request.description()));
        workflow.setCreatedBy(actorId);
        workflow = workflows.saveAndFlush(workflow);
        return toResponse(workflow);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDtos.WorkflowResponse> list(UUID userId, UUID workspaceId) {
        access.requireMembership(workspaceId, userId);
        return workflows.findByWorkspaceOrderByCreatedAtDesc(workspaces.getReferenceById(workspaceId)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDtos.WorkflowResponse get(UUID userId, UUID workspaceId, UUID workflowId) {
        access.requireMembership(workspaceId, userId);
        return toResponse(requireWorkflow(workspaceId, workflowId));
    }

    @Transactional
    public WorkflowDtos.WorkflowResponse update(UUID actorId, UUID workspaceId, UUID workflowId,
                                                WorkflowDtos.UpdateWorkflowRequest request) {
        access.requireMembership(workspaceId, actorId);
        Workflow workflow = requireWorkflow(workspaceId, workflowId);
        if (request.name() != null) {
            workflow.setName(request.name().trim());
        }
        if (request.description() != null) {
            workflow.setDescription(trimToNull(request.description()));
        }
        workflows.saveAndFlush(workflow);
        return toResponse(workflow);
    }

    @Transactional
    public void delete(UUID actorId, UUID workspaceId, UUID workflowId) {
        access.requireMembership(workspaceId, actorId);
        Workflow workflow = requireWorkflow(workspaceId, workflowId);
        try {
            workflows.delete(workflow);
            workflows.flush();
        } catch (DataIntegrityViolationException ex) {
            // Executions keep an immutable reference to the version they ran.
            throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_IN_USE",
                    "This workflow has executions and cannot be deleted");
        }
    }

    @Transactional
    public WorkflowDtos.VersionResponse createVersion(UUID actorId, UUID workspaceId, UUID workflowId,
                                                      WorkflowDtos.CreateVersionRequest request) {
        access.requireMembership(workspaceId, actorId);
        Workflow workflow = workflows.findByIdAndWorkspace_IdForUpdate(workflowId, workspaceId)
                .orElseThrow(this::workflowNotFound);

        List<WorkflowDtos.NodeInput> requestedNodes = request.nodes();
        List<WorkflowDtos.EdgeInput> requestedEdges = request.edges() == null ? List.of() : request.edges();
        WorkflowGraphValidator.validate(requestedNodes, requestedEdges);

        Integer max = versions.findMaxVersionNumberByWorkflow(workflow);
        int nextNumber = max == null ? 1 : max + 1;

        WorkflowVersion version = new WorkflowVersion(UUID.randomUUID(), workflow, nextNumber);
        if (request.publish()) {
            version.setPublishedAt(Instant.now());
        }
        version = versions.saveAndFlush(version);

        List<WorkflowNode> savedNodes = new ArrayList<>();
        for (WorkflowDtos.NodeInput input : requestedNodes) {
            String type = input.type().trim().toLowerCase(Locale.ROOT);
            if (!NODE_TYPES.contains(type)) {
                throw WorkflowGraphValidator.invalid("Unsupported node type: " + input.type());
            }
            WorkflowNode node = new WorkflowNode(UUID.randomUUID(), version, input.key().trim(), type);
            node.setLabel(trimToNull(input.label()));
            node.setPositionX(input.positionX() == null ? 0.0 : input.positionX());
            node.setPositionY(input.positionY() == null ? 0.0 : input.positionY());
            node.setConfiguration(configurationFor(workspaceId, type, input.config()).toString());
            savedNodes.add(nodes.save(node));
        }
        nodes.flush();

        java.util.Map<String, WorkflowNode> byKey = new java.util.HashMap<>();
        savedNodes.forEach(node -> byKey.put(node.getNodeKey(), node));
        for (WorkflowDtos.EdgeInput input : requestedEdges) {
            WorkflowEdge edge = new WorkflowEdge(UUID.randomUUID(), version,
                    byKey.get(input.source().trim()), byKey.get(input.target().trim()));
            if (input.condition() != null) {
                edge.setCondition(input.condition().toString());
            }
            edges.save(edge);
        }
        edges.flush();

        if (request.publish()) {
            workflow.setStatus("PUBLISHED");
            workflows.saveAndFlush(workflow);
        }

        return toResponse(version);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDtos.VersionSummary> listVersions(UUID userId, UUID workspaceId, UUID workflowId) {
        access.requireMembership(workspaceId, userId);
        Workflow workflow = requireWorkflow(workspaceId, workflowId);
        return versions.findByWorkflowOrderByVersionNumberDesc(workflow).stream()
                .map(v -> new WorkflowDtos.VersionSummary(
                        v.getId(), v.getVersionNumber(), v.getPublishedAt(), v.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDtos.VersionResponse getVersion(UUID userId, UUID workspaceId, UUID workflowId,
                                                   int versionNumber) {
        access.requireMembership(workspaceId, userId);
        Workflow workflow = requireWorkflow(workspaceId, workflowId);
        WorkflowVersion version = versions.findByWorkflowAndVersionNumber(workflow, versionNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "WORKFLOW_VERSION_NOT_FOUND", "Workflow version not found"));
        return toResponse(version);
    }

    /**
     * Internal read used by the execution module after it has verified access.
     */
    @Transactional(readOnly = true)
    public WorkflowRunDefinition loadRunDefinition(UUID versionId) {
        WorkflowVersion version = versions.findById(versionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "WORKFLOW_VERSION_NOT_FOUND", "Workflow version not found"));
        List<WorkflowRunDefinition.RunNode> runNodes = nodes.findByWorkflowVersion(version).stream()
                .map(node -> new WorkflowRunDefinition.RunNode(
                        node.getNodeKey(), node.getNodeType(), readJson(node.getConfiguration())))
                .toList();
        List<WorkflowRunDefinition.RunEdge> runEdges = edges.findByWorkflowVersionId(version.getId()).stream()
                .map(edge -> new WorkflowRunDefinition.RunEdge(
                        edge.getSourceNodeKey(), edge.getTargetNodeKey()))
                .toList();
        return new WorkflowRunDefinition(version.getWorkflow().getId(), version.getId(),
                version.getVersionNumber(), runNodes, runEdges);
    }

    @Transactional(readOnly = true)
    public WorkflowDtos.VersionResponse requirePublishedVersion(UUID workspaceId, UUID workflowId,
                                                                UUID versionId) {
        Workflow workflow = requireWorkflow(workspaceId, workflowId);
        WorkflowVersion version = versions.findByIdAndWorkflow(versionId, workflow)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "WORKFLOW_VERSION_NOT_FOUND", "Workflow version not found"));
        if (version.getPublishedAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_NOT_PUBLISHED",
                    "Executions require a published workflow version");
        }
        return toResponse(version);
    }

    @Transactional(readOnly = true)
    public WorkflowDtos.VersionResponse latestPublishedVersion(UUID workspaceId, UUID workflowId) {
        Workflow workflow = requireWorkflow(workspaceId, workflowId);
        List<WorkflowVersion> published = versions.findPublishedByWorkflowOrderByVersionNumberDesc(workflow);
        if (published.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "WORKFLOW_NOT_PUBLISHED",
                    "This workflow has no published version");
        }
        return toResponse(published.getFirst());
    }

    private JsonNode configurationFor(UUID workspaceId, String type, JsonNode requested) {
        JsonNode config = requested == null ? mapper.createObjectNode() : requested;
        if (!config.isObject()) {
            throw WorkflowGraphValidator.invalid("Node configuration must be a JSON object");
        }
        return switch (type) {
            case "agent" -> agentConfiguration(workspaceId, config);
            case "tool" -> toolConfiguration(config);
            case "output" -> outputConfiguration(config);
            default -> throw WorkflowGraphValidator.invalid("Unsupported node type: " + type);
        };
    }

    private JsonNode agentConfiguration(UUID workspaceId, JsonNode config) {
        String agentId = config.path("agentId").asText("");
        if (agentId.isBlank()) {
            throw WorkflowGraphValidator.invalid("An agent node requires agentId");
        }
        UUID id;
        try {
            id = UUID.fromString(agentId);
        } catch (IllegalArgumentException ex) {
            throw WorkflowGraphValidator.invalid("agentId must be a UUID");
        }

        AgentSnapshot snapshot = agents.snapshot(workspaceId, id);
        ObjectNode node = mapper.createObjectNode();
        node.put("agentId", snapshot.id().toString());
        node.put("agentName", snapshot.name());
        node.put("providerCredentialId", snapshot.providerCredentialId().toString());
        node.put("model", snapshot.model());
        node.put("systemPrompt", snapshot.systemPrompt());
        if (snapshot.temperature() != null) {
            node.put("temperature", snapshot.temperature());
        }
        if (snapshot.maxTokens() != null) {
            node.put("maxTokens", snapshot.maxTokens());
        }
        if (config.hasNonNull("inputTemplate")) {
            node.put("inputTemplate", config.get("inputTemplate").asText());
        }
        return node;
    }

    private JsonNode toolConfiguration(JsonNode config) {
        String toolType = config.path("toolType").asText("").trim().toLowerCase(Locale.ROOT);
        if (!TOOL_TYPES.contains(toolType)) {
            throw WorkflowGraphValidator.invalid("Unsupported tool type: " + toolType);
        }
        if ("http_get".equals(toolType)) {
            String url = config.path("url").asText("");
            egressGuard.validateForSave(url);
        }
        ObjectNode node = (ObjectNode) config.deepCopy();
        node.put("toolType", toolType);
        return node;
    }

    private JsonNode outputConfiguration(JsonNode config) {
        if (!config.hasNonNull("outputTemplate")) {
            throw WorkflowGraphValidator.invalid("An output node requires outputTemplate");
        }
        return config.deepCopy();
    }

    private Workflow requireWorkflow(UUID workspaceId, UUID workflowId) {
        return workflows.findByIdAndWorkspace_Id(workflowId, workspaceId)
                .orElseThrow(this::workflowNotFound);
    }

    private ApiException workflowNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", "Workflow not found");
    }

    private WorkflowDtos.WorkflowResponse toResponse(Workflow workflow) {
        List<WorkflowVersion> all = versions.findByWorkflowOrderByVersionNumberDesc(workflow);
        Integer latest = all.isEmpty() ? null : all.getFirst().getVersionNumber();
        Integer latestPublished = all.stream()
                .filter(v -> v.getPublishedAt() != null)
                .map(WorkflowVersion::getVersionNumber)
                .findFirst()
                .orElse(null);
        return new WorkflowDtos.WorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getStatus(),
                latest,
                latestPublished,
                workflow.getCreatedAt(),
                workflow.getUpdatedAt());
    }

    private WorkflowDtos.VersionResponse toResponse(WorkflowVersion version) {
        List<WorkflowDtos.NodeResponse> nodeResponses = nodes.findByWorkflowVersion(version).stream()
                .sorted(Comparator.comparing(WorkflowNode::getNodeKey))
                .map(node -> new WorkflowDtos.NodeResponse(
                        node.getNodeKey(),
                        node.getNodeType(),
                        node.getLabel(),
                        node.getPositionX(),
                        node.getPositionY(),
                        readJson(node.getConfiguration())))
                .toList();
        List<WorkflowDtos.EdgeResponse> edgeResponses = edges.findByWorkflowVersionId(version.getId()).stream()
                .sorted(Comparator.comparing(WorkflowEdge::getSourceNodeKey))
                .map(edge -> new WorkflowDtos.EdgeResponse(
                        edge.getId(),
                        edge.getSourceNodeKey(),
                        edge.getTargetNodeKey(),
                        readJson(edge.getCondition())))
                .toList();
        return new WorkflowDtos.VersionResponse(
                version.getId(),
                version.getWorkflow().getId(),
                version.getVersionNumber(),
                version.getPublishedAt(),
                version.getCreatedAt(),
                nodeResponses,
                edgeResponses);
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored workflow configuration is not valid JSON", ex);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
