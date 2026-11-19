package com.meld.workspace;

import com.meld.testsupport.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Workspace authorization tests per ROADMAP.md M4 and TEST.md §4:
 * SEC-001 (other user's workspace denied), SEC-002 (swapped id leaks nothing),
 * SEC-003 (safe not-found), SEC-008 (no session rejected) and SEC-009
 * (insufficient role cannot mutate membership).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkspaceApiTest extends ApiTestSupport {

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isUnauthorized());

        // A valid CSRF token but no session must still be rejected by
        // authentication, not merely by CSRF.
        Cookie csrf = csrfCookie();
        mockMvc.perform(post("/api/v1/workspaces")
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"name\":\"No Session\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturnsOwnerRoleAndAppearsInList() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession session = signIn("owner-create@example.test", csrf);

        UUID workspaceId = createWorkspace(session, csrf, "Acme Rocketry");

        mockMvc.perform(get("/api/v1/workspaces/{id}", workspaceId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.slug").value("acme-rocketry"));

        mockMvc.perform(get("/api/v1/workspaces").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Acme Rocketry"));
    }

    @Test
    void listOnlyContainsTheCallersWorkspaces() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession alice = signIn("list-alice@example.test", csrf);
        MockHttpSession bob = signIn("list-bob@example.test", csrf);

        createWorkspace(alice, csrf, "Alice Space");
        createWorkspace(bob, csrf, "Bob Space");

        mockMvc.perform(get("/api/v1/workspaces").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alice Space"));

        mockMvc.perform(get("/api/v1/workspaces").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bob Space"));
    }

    @Test
    void otherUsersWorkspaceIsReportedAsNotFound() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession alice = signIn("tenant-alice@example.test", csrf);
        MockHttpSession bob = signIn("tenant-bob@example.test", csrf);

        UUID aliceWorkspace = createWorkspace(alice, csrf, "Alice Private");

        // SEC-001/SEC-002: swapping the id in the path must not leak anything,
        // and must not distinguish "exists but forbidden" from "missing".
        mockMvc.perform(get("/api/v1/workspaces/{id}", aliceWorkspace).session(bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/workspaces/{id}/members", aliceWorkspace).session(bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/workspaces/{id}", UUID.randomUUID()).session(bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    void duplicateSlugConflicts() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession session = signIn("slug@example.test", csrf);

        createWorkspace(session, csrf, "Duplicate Slug");
        createWorkspace(session, csrf, "Duplicate Slug", 409);
    }

    @Test
    void ownerCanAddAndRemoveAMember() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("member-owner@example.test", csrf);
        MockHttpSession member = signIn("member-user@example.test", csrf);

        UUID workspaceId = createWorkspace(owner, csrf, "Shared Space");
        addMember(owner, csrf, workspaceId, "member-user@example.test", "MEMBER", 201);

        mockMvc.perform(get("/api/v1/workspaces/{id}/members", workspaceId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // The new member can read the workspace and sees the MEMBER role.
        mockMvc.perform(get("/api/v1/workspaces/{id}", workspaceId).session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        UUID memberId = users.findByEmailIgnoreCase("member-user@example.test").orElseThrow().getId();
        mockMvc.perform(delete("/api/v1/workspaces/{id}/members/{userId}", workspaceId, memberId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workspaces/{id}", workspaceId).session(member))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberCannotMutateMembership() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("role-owner@example.test", csrf);
        MockHttpSession member = signIn("role-member@example.test", csrf);
        register("role-outsider@example.test", PASSWORD, csrf, 201);

        UUID workspaceId = createWorkspace(owner, csrf, "Role Space");
        addMember(owner, csrf, workspaceId, "role-member@example.test", "MEMBER", 201);

        // SEC-009: a MEMBER may read, but not add or remove members.
        addMember(member, csrf, workspaceId, "role-outsider@example.test", "MEMBER", 403);

        UUID ownerId = users.findByEmailIgnoreCase("role-owner@example.test").orElseThrow().getId();
        mockMvc.perform(delete("/api/v1/workspaces/{id}/members/{userId}", workspaceId, ownerId)
                        .session(member)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotBeRemoved() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("last-owner@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Owner Protected");

        UUID ownerId = users.findByEmailIgnoreCase("last-owner@example.test").orElseThrow().getId();
        mockMvc.perform(delete("/api/v1/workspaces/{id}/members/{userId}", workspaceId, ownerId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWNER"));
    }

    @Test
    void addingAnUnknownEmailFails() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("unknown-member@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Unknown Member");

        addMember(owner, csrf, workspaceId, "ghost@example.test", "MEMBER", 404);
    }

    @Test
    void duplicateMemberConflicts() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("dupe-owner@example.test", csrf);
        MockHttpSession member = signIn("dupe-member@example.test", csrf);

        UUID workspaceId = createWorkspace(owner, csrf, "Dupe Member");
        addMember(owner, csrf, workspaceId, "dupe-member@example.test", "MEMBER", 201);
        addMember(owner, csrf, workspaceId, "dupe-member@example.test", "MEMBER", 409);
    }

    @Test
    void ownershipCannotBeGrantedThroughTheMemberEndpoint() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("escalate-owner@example.test", csrf);
        register("escalate-target@example.test", PASSWORD, csrf, 201);

        UUID workspaceId = createWorkspace(owner, csrf, "Escalation");
        addMember(owner, csrf, workspaceId, "escalate-target@example.test", "OWNER", 400);
    }

    @Test
    void mutatingWorkspaceRequestsRequireCsrf() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession session = signIn("csrf-workspace@example.test", csrf);

        mockMvc.perform(post("/api/v1/workspaces")
                        .session(session)
                        .contentType("application/json")
                        .content("{\"name\":\"No Token\"}"))
                .andExpect(status().isForbidden());
    }
}
