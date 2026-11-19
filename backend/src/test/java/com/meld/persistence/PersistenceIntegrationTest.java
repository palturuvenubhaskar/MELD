package com.meld.persistence;

import com.meld.agent.Agent;
import com.meld.agent.AgentRepository;
import com.meld.agent.AgentTool;
import com.meld.agent.AgentToolRepository;
import com.meld.approval.Approval;
import com.meld.approval.ApprovalRepository;
import com.meld.audit.AuditLog;
import com.meld.audit.AuditLogRepository;
import com.meld.execution.Execution;
import com.meld.execution.ExecutionLog;
import com.meld.execution.ExecutionLogRepository;
import com.meld.execution.ExecutionRepository;
import com.meld.execution.NodeExecution;
import com.meld.execution.NodeExecutionRepository;
import com.meld.provider.ProviderCredential;
import com.meld.provider.ProviderCredentialRepository;
import com.meld.tool.Tool;
import com.meld.tool.ToolRepository;
import com.meld.usage.UsageRecord;
import com.meld.usage.UsageRecordRepository;
import com.meld.user.User;
import com.meld.user.UserRepository;
import com.meld.workflow.Workflow;
import com.meld.workflow.WorkflowEdge;
import com.meld.workflow.WorkflowEdgeRepository;
import com.meld.workflow.WorkflowNode;
import com.meld.workflow.WorkflowNodeRepository;
import com.meld.workflow.WorkflowRepository;
import com.meld.workflow.WorkflowVersion;
import com.meld.workflow.WorkflowVersionRepository;
import com.meld.workspace.Workspace;
import com.meld.workspace.WorkspaceMember;
import com.meld.workspace.WorkspaceMemberRepository;
import com.meld.workspace.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3 persistence tests against the real PostgreSQL {@code meld_test} database.
 *
 * <p>Every test runs in a rolled-back transaction, so the schema is shared but
 * data never accumulates between runs. The tests assert both persistence and
 * workspace scoping, which is the tenant boundary from DATABASE.md §7.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private WorkspaceRepository workspaces;
    @Autowired private WorkspaceMemberRepository members;
    @Autowired private ProviderCredentialRepository credentials;
    @Autowired private AgentRepository agents;
    @Autowired private ToolRepository tools;
    @Autowired private AgentToolRepository agentTools;
    @Autowired private WorkflowRepository workflows;
    @Autowired private WorkflowVersionRepository versions;
    @Autowired private WorkflowNodeRepository nodes;
    @Autowired private WorkflowEdgeRepository edges;
    @Autowired private ExecutionRepository executions;
    @Autowired private NodeExecutionRepository nodeExecutions;
    @Autowired private ExecutionLogRepository logs;
    @Autowired private ApprovalRepository approvals;
    @Autowired private UsageRecordRepository usage;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    // --- fixtures ---------------------------------------------------------

    private User user(String email) {
        return users.saveAndFlush(new User(UUID.randomUUID(), email, "{noop}hash-" + email));
    }

    private Workspace workspace(User owner, String slug) {
        return workspaces.saveAndFlush(
                new Workspace(UUID.randomUUID(), "Workspace " + slug, slug, owner.getId()));
    }

    // --- tenancy ----------------------------------------------------------

    @Test
    void workspaceAndMembershipPersist() {
        User owner = user("owner@example.test");
        Workspace ws = workspace(owner, "acme");
        members.saveAndFlush(new WorkspaceMember(ws.getId(), owner.getId(), "OWNER"));

        assertThat(workspaces.findBySlug("acme")).isPresent();
        assertThat(workspaces.existsBySlug("acme")).isTrue();
        assertThat(members.existsByWorkspaceIdAndUserId(ws.getId(), owner.getId())).isTrue();
        assertThat(members.findOwnerByWorkspaceId(ws.getId()))
                .get()
                .extracting(WorkspaceMember::getUserId)
                .isEqualTo(owner.getId());
        assertThat(workspaces.findByMemberUserId(owner.getId()))
                .extracting(Workspace::getId)
                .containsExactly(ws.getId());
    }

    @Test
    void agentIsIsolatedPerWorkspace() {
        User owner = user("owner-agent@example.test");
        Workspace a = workspace(owner, "alpha");
        Workspace b = workspace(owner, "beta");

        Agent agent = new Agent(UUID.randomUUID(), a, "Summarizer", "meta-llama/llama-3.1-8b-instruct:free", "Summarize.");
        agent.setConfiguration("{\"temperature\":0.2}");
        agents.saveAndFlush(agent);

        assertThat(agents.findByWorkspace(a)).extracting(Agent::getId).containsExactly(agent.getId());
        assertThat(agents.findByWorkspace(b)).isEmpty();
        assertThat(agents.findByIdAndWorkspace(agent.getId(), a)).isPresent();
        assertThat(agents.findByIdAndWorkspace(agent.getId(), b)).isEmpty();
        assertThat(agents.existsByWorkspaceAndName(a, "Summarizer")).isTrue();
        assertThat(agents.existsByWorkspaceAndName(b, "Summarizer")).isFalse();

        // JSONB round-trips through the entity without an extra converter.
        assertThat(agents.findById(agent.getId()).orElseThrow().getConfiguration())
                .isEqualTo("{\"temperature\":0.2}");
    }

    @Test
    void providerCredentialBytesPersistAndStayWorkspaceScoped() {
        User owner = user("owner-cred@example.test");
        Workspace a = workspace(owner, "cred-a");
        Workspace b = workspace(owner, "cred-b");
        byte[] secret = "ciphertext-not-plaintext".getBytes(StandardCharsets.UTF_8);

        ProviderCredential credential = new ProviderCredential(
                UUID.randomUUID(), a, "openrouter", "OpenRouter", secret, "v1", owner.getId());
        credentials.saveAndFlush(credential);

        ProviderCredential reloaded = credentials.findById(credential.getId()).orElseThrow();
        assertThat(reloaded.getEncryptedSecret()).isEqualTo(secret);
        assertThat(credentials.findByIdAndWorkspace(credential.getId(), a)).isPresent();
        assertThat(credentials.findByIdAndWorkspace(credential.getId(), b)).isEmpty();
    }

    // --- workflow graph ---------------------------------------------------

    @Test
    void workflowGraphPersistsNodesAndEdges() {
        User owner = user("owner-flow@example.test");
        Workspace ws = workspace(owner, "flow");
        Workflow workflow = workflows.saveAndFlush(new Workflow(UUID.randomUUID(), ws, "Pipeline"));
        WorkflowVersion version = versions.saveAndFlush(
                new WorkflowVersion(UUID.randomUUID(), workflow, 1));

        WorkflowNode start = new WorkflowNode(UUID.randomUUID(), version, "start", "AGENT");
        start.setLabel("Start");
        start.setPositionX(10.0);
        start.setPositionY(20.0);
        start.setConfiguration("{\"temperature\":0}");
        WorkflowNode end = new WorkflowNode(UUID.randomUUID(), version, "end", "OUTPUT");
        start = nodes.saveAndFlush(start);
        end = nodes.saveAndFlush(end);

        WorkflowEdge edge = edges.saveAndFlush(new WorkflowEdge(
                UUID.randomUUID(), version, start, end));

        assertThat(versions.findByWorkflowOrderByVersionNumberDesc(workflow))
                .extracting(WorkflowVersion::getVersionNumber)
                .containsExactly(1);
        assertThat(versions.findMaxVersionNumberByWorkflow(workflow)).isEqualTo(1);
        assertThat(nodes.findByWorkflowVersion(version)).hasSize(2);
        assertThat(nodes.findByWorkflowVersionAndNodeKey(version, "start").orElseThrow().getLabel())
                .isEqualTo("Start");

        WorkflowEdge reloaded = edges.findById(edge.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowVersionId()).isEqualTo(version.getId());
        assertThat(reloaded.getSourceNodeKey()).isEqualTo("start");
        assertThat(reloaded.getTargetNodeKey()).isEqualTo("end");
        assertThat(edges.findByWorkflowVersionId(version.getId())).hasSize(1);
    }

    @Test
    void workflowIsScopedToWorkspace() {
        User owner = user("owner-flow-scope@example.test");
        Workspace a = workspace(owner, "flow-a");
        Workspace b = workspace(owner, "flow-b");
        Workflow workflow = workflows.saveAndFlush(new Workflow(UUID.randomUUID(), a, "OnlyA"));

        assertThat(workflows.findByIdAndWorkspace(workflow.getId(), a)).isPresent();
        assertThat(workflows.findByIdAndWorkspace(workflow.getId(), b)).isEmpty();
        assertThat(workflows.findByWorkspace(a, PageRequest.of(0, 10)).getContent())
                .extracting(Workflow::getId).containsExactly(workflow.getId());
        assertThat(workflows.findByWorkspace(b, PageRequest.of(0, 10))).isEmpty();
    }

    // --- agents and tools -------------------------------------------------

    @Test
    void agentToolAssociationLinksScopedRows() {
        User owner = user("owner-tool@example.test");
        Workspace ws = workspace(owner, "tools");
        Agent agent = agents.saveAndFlush(
                new Agent(UUID.randomUUID(), ws, "Researcher", "model", "Research."));
        Tool tool = tools.saveAndFlush(new Tool(UUID.randomUUID(), ws, "http-get", "HTTP"));
        agentTools.saveAndFlush(new AgentTool(ws.getId(), agent.getId(), tool.getId()));

        assertThat(agentTools.findByAgentId(agent.getId())).hasSize(1);
        assertThat(agentTools.findByToolId(tool.getId())).hasSize(1);
        assertThat(agentTools.findByAgentIdAndToolId(agent.getId(), tool.getId())).isPresent();
        assertThat(tools.findEnabledByWorkspace(ws)).extracting(Tool::getId).containsExactly(tool.getId());
    }

    // --- execution state --------------------------------------------------

    private Execution pendingExecution(Workspace ws) {
        Workflow workflow = workflows.saveAndFlush(new Workflow(UUID.randomUUID(), ws, "Run " + UUID.randomUUID()));
        WorkflowVersion version = versions.saveAndFlush(
                new WorkflowVersion(UUID.randomUUID(), workflow, 1));
        return executions.saveAndFlush(new Execution(
                UUID.randomUUID(), ws.getId(), workflow.getId(), version.getId(), "PENDING"));
    }

    @Test
    void executionQueueQueriesAreWorkspaceScoped() {
        User owner = user("owner-exec@example.test");
        Workspace a = workspace(owner, "exec-a");
        Workspace b = workspace(owner, "exec-b");
        Execution execution = pendingExecution(a);

        assertThat(executions.findByIdAndWorkspaceId(execution.getId(), a.getId())).isPresent();
        assertThat(executions.findByIdAndWorkspaceId(execution.getId(), b.getId())).isEmpty();
        assertThat(executions.findByWorkspaceId(a.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(Execution::getId).containsExactly(execution.getId());
        assertThat(executions.findByWorkspaceId(b.getId(), PageRequest.of(0, 10))).isEmpty();

        assertThat(executions.findClaimableExecutions(Instant.now()))
                .extracting(Execution::getId).containsExactly(execution.getId());
        assertThat(executions.findExpiredLeasesByOwner("nobody", Instant.now())).isEmpty();
    }

    @Test
    void idempotencyLookupIsWorkspaceScoped() {
        User owner = user("owner-idem@example.test");
        Workspace a = workspace(owner, "idem-a");
        Workspace b = workspace(owner, "idem-b");
        Execution execution = pendingExecution(a);
        execution.setIdempotencyKey("key-1");
        execution.setRequestHash("a".repeat(64));
        executions.saveAndFlush(execution);

        assertThat(executions.findByWorkspaceIdAndIdempotencyKey(a.getId(), "key-1"))
                .get().extracting(Execution::getId).isEqualTo(execution.getId());
        assertThat(executions.findByWorkspaceIdAndIdempotencyKey(b.getId(), "key-1")).isEmpty();
    }

    @Test
    void nodeExecutionLogAndApprovalPersist() {
        User owner = user("owner-node@example.test");
        Workspace ws = workspace(owner, "node");
        Execution execution = pendingExecution(ws);

        NodeExecution node = new NodeExecution(
                UUID.randomUUID(), execution, "start", "SUCCEEDED", 1);
        node.setInput("{\"in\":1}");
        node.setOutput("{\"out\":2}");
        node.setStartedAt(Instant.now());
        node.setCompletedAt(Instant.now());
        node = nodeExecutions.saveAndFlush(node);

        ExecutionLog log = new ExecutionLog(
                UUID.randomUUID(), execution, "INFO", "NODE_SUCCEEDED", "node finished");
        log.setNodeExecutionId(node.getId());
        log.setMetadata("{\"node\":\"start\"}");
        log = logs.saveAndFlush(log);

        Approval approval = approvals.saveAndFlush(
                new Approval(UUID.randomUUID(), execution, "start"));
        approval.setDecidedBy(owner);
        approval.setDecidedAt(Instant.now());
        approvals.saveAndFlush(approval);

        assertThat(nodeExecutions.findByExecution(execution)).hasSize(1);
        assertThat(nodeExecutions.findByExecutionAndNodeKeyAndAttemptNumber(execution, "start", 1))
                .isPresent();
        assertThat(logs.findByExecution(execution, PageRequest.of(0, 10)).getContent())
                .extracting(ExecutionLog::getId).containsExactly(log.getId());
        assertThat(logs.findById(log.getId()).orElseThrow().getMetadata())
                .isEqualTo("{\"node\":\"start\"}");
        assertThat(logs.findByNodeExecutionIdOrderByCreatedAtAsc(node.getId())).hasSize(1);
        assertThat(approvals.findByExecution(execution)).hasSize(1);
        assertThat(approvals.findByDecidedBy(owner.getId())).hasSize(1);
    }

    @Test
    void usageRecordsAggregatePerWorkspace() {
        User owner = user("owner-usage@example.test");
        Workspace ws = workspace(owner, "usage");
        Execution execution = pendingExecution(ws);

        UsageRecord first = new UsageRecord(UUID.randomUUID(), ws, "openrouter", "m");
        first.setExecutionId(execution.getId());
        first.setInputTokens(100L);
        first.setOutputTokens(40L);
        first.setEstimatedCost(new BigDecimal("0.000123"));
        first.setDurationMs(850L);
        usage.saveAndFlush(first);

        UsageRecord second = new UsageRecord(UUID.randomUUID(), ws, "openrouter", "m");
        second.setInputTokens(10L);
        second.setOutputTokens(2L);
        usage.saveAndFlush(second);

        Instant start = Instant.now().minusSeconds(60);
        Instant end = Instant.now().plusSeconds(60);
        assertThat(usage.findByExecutionId(execution.getId())).hasSize(1);
        assertThat(usage.sumInputTokensByWorkspaceAndDateRange(ws, start, end)).isEqualTo(110L);
        assertThat(usage.sumOutputTokensByWorkspaceAndDateRange(ws, start, end)).isEqualTo(42L);
        assertThat(usage.findByWorkspace(ws, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    void auditLogPersistsWithWorkspaceAndUser() {
        User owner = user("owner-audit@example.test");
        Workspace ws = workspace(owner, "audit");

        AuditLog entry = new AuditLog(UUID.randomUUID(), ws, "WORKSPACE", "CREATE");
        entry.setUser(owner);
        entry.setResourceType("workspace");
        entry.setResourceId(ws.getId());
        entry.setMetadata("{\"source\":\"test\"}");
        entry.setIpAddress("127.0.0.1");
        auditLogs.saveAndFlush(entry);

        assertThat(auditLogs.findByWorkspace(ws, PageRequest.of(0, 10)).getContent())
                .extracting(AuditLog::getId).containsExactly(entry.getId());
        assertThat(auditLogs.findByUser(owner, PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(auditLogs.findByResource("workspace", ws.getId())).hasSize(1);
    }

    @Test
    void migrationsProducedEveryCoreTable() {
        // Guards against a migration being dropped: the context only loads when
        // Hibernate validation matches these tables and columns.
        List<String> expected = List.of(
                "users", "workspaces", "workspace_members", "provider_credentials",
                "agents", "tools", "agent_tools", "workflows", "workflow_versions",
                "workflow_nodes", "workflow_edges", "executions", "node_executions",
                "execution_logs", "approvals", "usage_records", "audit_logs");
        List<String> actual = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        assertThat(actual).containsAll(expected);
    }
}
