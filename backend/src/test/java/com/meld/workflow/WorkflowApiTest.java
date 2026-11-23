package com.meld.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.meld.testsupport.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workflow CRUD, immutable versioning and graph validation
 * (docs/TEST.md TC-011 - TC-013, TC-023, TC-032; ROADMAP.md M6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkflowApiTest extends ApiTestSupport {

    @Test
    void createUpdateListAndDeleteWorkflow() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("wf-crud@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Workflow CRUD");

        Map<String, Object> create = new LinkedHashMap<>();
        create.put("name", "Pipeline");
        create.put("description", "First draft");
        JsonNode created = contentAsTree(postJson(owner, csrf,
                "/api/v1/workspaces/" + workspaceId + "/workflows",
                objectMapper.writeValueAsString(create), 201));
        UUID workflowId = UUID.fromString(created.get("id").asText());

        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("latestVersion").isNull()).isTrue();

        JsonNode listed = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/workflows", 200));
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("name").asText()).isEqualTo("Pipeline");

        JsonNode patched = contentAsTree(patchJson(owner, csrf,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId,
                "{\"name\":\"Pipeline v2\"}", 200));
        assertThat(patched.get("name").asText()).isEqualTo("Pipeline v2");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isNoContent());

        getJson(owner, "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId, 404);
    }

    @Test
    void publishedVersionSnapshotsAgentSettings() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("wf-snapshot@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Workflow Snapshot");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Researcher", credentialId);

        UUID workflowId = createWorkflow(owner, csrf, workspaceId, "Snapshot flow");
        JsonNode version = contentAsTree(postJson(owner, csrf,
                versionUrl(workspaceId, workflowId),
                versionBody(true, agentId, "Summarize {{input.topic}}"), 201));

        assertThat(version.get("versionNumber").asInt()).isEqualTo(1);
        assertThat(version.get("publishedAt").isNull()).isFalse();
        assertThat(version.get("createdAt").isNull()).isFalse();

        JsonNode agentConfig = nodeConfig(version, "agent");
        assertThat(agentConfig.get("agentId").asText()).isEqualTo(agentId.toString());
        assertThat(agentConfig.get("agentName").asText()).isEqualTo("Researcher");
        assertThat(agentConfig.get("model").asText()).isEqualTo(TEST_MODEL);
        assertThat(agentConfig.get("systemPrompt").asText()).isEqualTo("You are a test agent.");
        assertThat(agentConfig.get("providerCredentialId").asText()).isEqualTo(credentialId.toString());
        assertThat(agentConfig.get("inputTemplate").asText()).isEqualTo("Summarize {{input.topic}}");
        assertThat(version.toString()).doesNotContain(API_KEY);

        JsonNode workflow = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId, 200));
        assertThat(workflow.get("latestVersion").asInt()).isEqualTo(1);
        assertThat(workflow.get("latestPublishedVersion").asInt()).isEqualTo(1);
        assertThat(workflow.get("status").asText()).isEqualTo("PUBLISHED");

        JsonNode fetched = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId + "/versions/1", 200));
        assertThat(fetched.get("id").asText()).isEqualTo(version.get("id").asText());
        assertThat(fetched.get("edges")).hasSize(1);
    }

    @Test
    void agentEditDoesNotChangeAnExistingVersion() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("wf-immutable@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Workflow Immutable");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Evolving", credentialId);
        UUID workflowId = createWorkflow(owner, csrf, workspaceId, "Immutable flow");

        postJson(owner, csrf, versionUrl(workspaceId, workflowId),
                versionBody(true, agentId, "first prompt"), 201);

        patchJson(owner, csrf, "/api/v1/workspaces/" + workspaceId + "/agents/" + agentId,
                "{\"systemPrompt\":\"second prompt\",\"model\":\"meta-llama/llama-3.3-70b-instruct:free\"}",
                200);

        postJson(owner, csrf, versionUrl(workspaceId, workflowId),
                versionBody(true, agentId, "second template"), 201);

        JsonNode first = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId + "/versions/1", 200));
        JsonNode second = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId + "/versions/2", 200));

        assertThat(nodeConfig(first, "agent").get("systemPrompt").asText())
                .isEqualTo("You are a test agent.");
        assertThat(nodeConfig(second, "agent").get("systemPrompt").asText())
                .isEqualTo("second prompt");
        assertThat(nodeConfig(first, "agent").get("model").asText()).isEqualTo(TEST_MODEL);
        assertThat(nodeConfig(second, "agent").get("model").asText())
                .isEqualTo("meta-llama/llama-3.3-70b-instruct:free");
    }

    @Test
    void unpublishedVersionCannotBeExecutedYet() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("wf-unpublished@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Workflow Unpublished");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Draft", credentialId);
        UUID workflowId = createWorkflow(owner, csrf, workspaceId, "Draft flow");

        JsonNode version = contentAsTree(postJson(owner, csrf, versionUrl(workspaceId, workflowId),
                versionBody(false, agentId, "draft"), 201));

        assertThat(version.get("publishedAt").isNull()).isTrue();
        JsonNode workflow = contentAsTree(getJson(owner,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId, 200));
        assertThat(workflow.get("latestVersion").asInt()).isEqualTo(1);
        assertThat(workflow.get("latestPublishedVersion").isNull()).isTrue();
    }

    @Test
    void rejectsEdgeThatReferencesAMissingNode() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("wf-missing-edge@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Workflow Missing Edge");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Edge Agent", credentialId);
        UUID workflowId = createWorkflow(owner, csrf, workspaceId, "Bad edge");

        String body = """
                {"publish":true,
                 "nodes":[
                   {"key":"agent","type":"agent","config":{"agentId":"%s"}},
                   {"key":"out","type":"output","config":{"outputTemplate":{"answer":"{{agent.output}}"}}}
                 ],
                 "edges":[{"source":"agent","target":"ghost"}]}
                """.formatted(agentId);

        JsonNode error = contentAsTree(postJson(owner, csrf, versionUrl(workspaceId, workflowId), body, 400));
        assertThat(error.get("code").asText()).isEqualTo("WORKFLOW_INVALID");
        assertThat(error.get("message").asText()).contains("missing node");
    }

    @Test
    void rejectsCycles() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("wf-cycle@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Workflow Cycle");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Cycle Agent", credentialId);
        UUID workflowId = createWorkflow(owner, csrf, workspaceId, "Bad cycle");

        String body = """
                {"publish":true,
                 "nodes":[
                   {"key":"a","type":"agent","config":{"agentId":"%s"}},
                   {"key":"b","type":"output","config":{"outputTemplate":{"answer":"{{a.output}}"}}}
                 ],
                 "edges":[{"source":"a","target":"b"},{"source":"b","target":"a"}]}
                """.formatted(agentId);

        JsonNode error = contentAsTree(postJson(owner, csrf, versionUrl(workspaceId, workflowId), body, 400));
        assertThat(error.get("code").asText()).isEqualTo("WORKFLOW_INVALID");
        assertThat(error.get("message").asText().toLowerCase()).contains("cycle");
    }

    @Test
    void rejectsBranchingAndUnreachableNodes() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("wf-shape@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Workflow Shape");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Shape Agent", credentialId);
        UUID workflowId = createWorkflow(owner, csrf, workspaceId, "Bad shape");

        String branching = """
                {"nodes":[
                   {"key":"a","type":"agent","config":{"agentId":"%s"}},
                   {"key":"b","type":"output","config":{"outputTemplate":{"x":"{{a.output}}"}}},
                   {"key":"c","type":"output","config":{"outputTemplate":{"x":"{{a.output}}"}}}
                 ],
                 "edges":[{"source":"a","target":"b"},{"source":"a","target":"c"}]}
                """.formatted(agentId);
        postJson(owner, csrf, versionUrl(workspaceId, workflowId), branching, 400);

        String unreachable = """
                {"nodes":[
                   {"key":"a","type":"agent","config":{"agentId":"%s"}},
                   {"key":"b","type":"output","config":{"outputTemplate":{"x":"{{a.output}}"}}},
                   {"key":"isolated","type":"output","config":{"outputTemplate":{"x":"{{a.output}}"}}}
                 ],
                 "edges":[{"source":"a","target":"b"}]}
                """.formatted(agentId);
        postJson(owner, csrf, versionUrl(workspaceId, workflowId), unreachable, 400);
    }

    @Test
    void rejectsUnsupportedNodeTypeAndBadToolDestination() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("wf-node-type@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Workflow Node Type");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Type Agent", credentialId);
        UUID workflowId = createWorkflow(owner, csrf, workspaceId, "Bad node");

        String unknownType = """
                {"nodes":[{"key":"a","type":"script","config":{}}],"edges":[]}
                """;
        postJson(owner, csrf, versionUrl(workspaceId, workflowId), unknownType, 400);

        String loopbackTool = """
                {"nodes":[
                   {"key":"a","type":"agent","config":{"agentId":"%s"}},
                   {"key":"t","type":"tool","config":{"toolType":"http_get","url":"https://127.0.0.1/admin"}}
                 ],
                 "edges":[{"source":"a","target":"t"}]}
                """.formatted(agentId);
        JsonNode blocked = contentAsTree(postJson(owner, csrf,
                versionUrl(workspaceId, workflowId), loopbackTool, 400));
        assertThat(blocked.get("code").asText()).isEqualTo("TOOL_EGRESS_BLOCKED");

        String insecure = """
                {"nodes":[
                   {"key":"a","type":"agent","config":{"agentId":"%s"}},
                   {"key":"t","type":"tool","config":{"toolType":"http_get","url":"http://example.com/data"}}
                 ],
                 "edges":[{"source":"a","target":"t"}]}
                """.formatted(agentId);
        JsonNode rejected = contentAsTree(postJson(owner, csrf,
                versionUrl(workspaceId, workflowId), insecure, 400));
        assertThat(rejected.get("code").asText()).isEqualTo("TOOL_URL_INSECURE");
    }

    @Test
    void memberCanReadAndCreateButOutsiderSeesNotFound() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("wf-member-owner@example.test", csrf);
        MockHttpSession member = signIn("wf-member@example.test", csrf);
        MockHttpSession outsider = signIn("wf-outsider@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Workflow Members");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Member Agent", credentialId);
        UUID workflowId = createWorkflow(owner, csrf, workspaceId, "Member flow");
        addMember(owner, csrf, workspaceId, "wf-member@example.test", "MEMBER", 201);

        JsonNode read = contentAsTree(getJson(member,
                "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId, 200));
        assertThat(read.get("id").asText()).isEqualTo(workflowId.toString());
        postJson(member, csrf, versionUrl(workspaceId, workflowId),
                versionBody(true, agentId, "member template"), 201);

        getJson(outsider, "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId, 404);
        postJson(outsider, csrf, versionUrl(workspaceId, workflowId),
                versionBody(true, agentId, "outsider template"), 404);
    }

    private UUID createWorkflow(MockHttpSession session, Cookie csrf, UUID workspaceId, String name)
            throws Exception {
        JsonNode created = contentAsTree(postJson(session, csrf,
                "/api/v1/workspaces/" + workspaceId + "/workflows",
                objectMapper.writeValueAsString(Map.of("name", name)), 201));
        return UUID.fromString(created.get("id").asText());
    }

    private static String versionUrl(UUID workspaceId, UUID workflowId) {
        return "/api/v1/workspaces/" + workspaceId + "/workflows/" + workflowId + "/versions";
    }

    private static String versionBody(boolean publish, UUID agentId, String inputTemplate) {
        return """
                {"publish":%s,
                 "nodes":[
                   {"key":"agent","type":"agent","positionX":40,"positionY":80,
                    "config":{"agentId":"%s","inputTemplate":"%s"}},
                   {"key":"out","type":"output","positionX":320,"positionY":80,
                    "config":{"outputTemplate":{"answer":"{{agent.output}}"}}}
                 ],
                 "edges":[{"source":"agent","target":"out"}]}
                """.formatted(publish, agentId, inputTemplate);
    }

    private static JsonNode nodeConfig(JsonNode version, String key) {
        for (JsonNode node : version.get("nodes")) {
            if (key.equals(node.get("key").asText())) {
                return node.get("config");
            }
        }
        throw new AssertionError("Node not found: " + key);
    }
}
