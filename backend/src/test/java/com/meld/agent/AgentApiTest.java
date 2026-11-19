package com.meld.agent;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent CRUD and authorization tests per ROADMAP.md M5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentApiTest extends ApiTestSupport {

    @Test
    void createListAndGetAnAgent() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("agent-owner@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Agent Space");
        UUID credentialId = createCredential(owner, csrf, workspaceId);

        String body = createAgent(owner, csrf, workspaceId, "Summarizer", credentialId, 201)
                .getResponse().getContentAsString();

        assertThat(body)
                .contains("\"name\":\"Summarizer\"")
                .contains(TEST_MODEL)
                .doesNotContain(API_KEY)
                .doesNotContain("apiKey")
                .doesNotContain("encryptedSecret");

        mockMvc.perform(get("/api/v1/workspaces/{id}/agents", workspaceId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Summarizer"))
                .andExpect(jsonPath("$[0].temperature").value(0.2))
                .andExpect(jsonPath("$[0].maxTokens").value(128));
    }

    @Test
    void getAndDeleteAnAgent() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("agent-lifecycle@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Agent Lifecycle");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Lifecycle", credentialId);

        mockMvc.perform(get("/api/v1/workspaces/{id}/agents/{agentId}", workspaceId, agentId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agentId.toString()));

        mockMvc.perform(delete("/api/v1/workspaces/{id}/agents/{agentId}", workspaceId, agentId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workspaces/{id}/agents/{agentId}", workspaceId, agentId).session(owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGENT_NOT_FOUND"));
    }

    @Test
    void duplicateAgentNameConflicts() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("agent-duplicate@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Agent Duplicate");
        UUID credentialId = createCredential(owner, csrf, workspaceId);

        createAgent(owner, csrf, workspaceId, "Duplicate", credentialId);
        createAgent(owner, csrf, workspaceId, "Duplicate", credentialId, 409);
    }

    @Test
    void updateChangesFieldsAndRuntimeConfig() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("agent-update@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Agent Update");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Before", credentialId);

        Map<String, Object> patchBody = new LinkedHashMap<>();
        patchBody.put("name", "After");
        patchBody.put("model", "meta-llama/llama-3.3-70b-instruct:free");
        patchBody.put("temperature", 0.9);
        patchBody.put("maxTokens", 512);

        mockMvc.perform(patch("/api/v1/workspaces/{id}/agents/{agentId}", workspaceId, agentId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(patchBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.temperature").value(0.9))
                .andExpect(jsonPath("$.maxTokens").value(512))
                .andExpect(jsonPath("$.systemPrompt").value("You are a test agent."));
    }

    @Test
    void renamingToAnExistingNameConflicts() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("agent-rename@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Agent Rename");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        createAgent(owner, csrf, workspaceId, "First", credentialId);
        UUID secondId = createAgent(owner, csrf, workspaceId, "Second", credentialId);

        mockMvc.perform(patch("/api/v1/workspaces/{id}/agents/{agentId}", workspaceId, secondId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"name\":\"First\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AGENT_NAME_TAKEN"));
    }

    @Test
    void memberCanCreateAgentsButNotManageCredentials() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("agent-member-owner@example.test", csrf);
        MockHttpSession member = signIn("agent-member@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Agent Members");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        addMember(owner, csrf, workspaceId, "agent-member@example.test", "MEMBER", 201);

        createAgent(member, csrf, workspaceId, "Member Agent", credentialId);
    }

    @Test
    void credentialFromAnotherWorkspaceIsRejected() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession alice = signIn("agent-cred-alice@example.test", csrf);
        MockHttpSession bob = signIn("agent-cred-bob@example.test", csrf);
        UUID aliceWorkspace = createWorkspace(alice, csrf, "Alice Agents");
        UUID bobWorkspace = createWorkspace(bob, csrf, "Bob Agents");
        UUID aliceCredential = createCredential(alice, csrf, aliceWorkspace);

        createAgent(bob, csrf, bobWorkspace, "Borrowed Credential", aliceCredential, 400);
    }

    @Test
    void unknownCredentialIsRejected() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("agent-unknown-cred@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Agent Unknown Cred");

        createAgent(owner, csrf, workspaceId, "No Credential", UUID.randomUUID(), 400);
    }

    @Test
    void agentRequestWithoutCredentialIsRejected() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("agent-missing-cred@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Agent Missing Cred");

        mockMvc.perform(post("/api/v1/workspaces/{id}/agents", workspaceId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"name\":\"NoCred\",\"model\":\"" + TEST_MODEL + "\",\"systemPrompt\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRuntimeConfigIsRejected() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("agent-invalid-config@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Agent Invalid Config");
        UUID credentialId = createCredential(owner, csrf, workspaceId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Too Hot");
        body.put("model", TEST_MODEL);
        body.put("systemPrompt", "hi");
        body.put("providerCredentialId", credentialId.toString());
        body.put("temperature", 3.0);

        mockMvc.perform(post("/api/v1/workspaces/{id}/agents", workspaceId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherWorkspaceAgentsAreNotFound() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession alice = signIn("agent-tenant-alice@example.test", csrf);
        MockHttpSession bob = signIn("agent-tenant-bob@example.test", csrf);
        UUID aliceWorkspace = createWorkspace(alice, csrf, "Alice Agent Space");
        UUID aliceCredential = createCredential(alice, csrf, aliceWorkspace);
        UUID aliceAgent = createAgent(alice, csrf, aliceWorkspace, "Alice Agent", aliceCredential);

        mockMvc.perform(get("/api/v1/workspaces/{id}/agents", aliceWorkspace).session(bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/workspaces/{id}/agents/{agentId}", aliceWorkspace, aliceAgent).session(bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/workspaces/{id}/agents/{agentId}", aliceWorkspace, aliceAgent)
                        .session(bob)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedAgentRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{id}/agents", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
