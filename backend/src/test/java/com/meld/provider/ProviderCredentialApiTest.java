package com.meld.provider;

import com.meld.provider.crypto.SecretCipher;
import com.meld.testsupport.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider credential API tests per ROADMAP.md M5 and TEST.md:
 * SEC-005 (credentials never returned), SEC-009 (role checks), plus
 * encryption at rest and tenant isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProviderCredentialApiTest extends ApiTestSupport {

    @Autowired private ProviderCredentialRepository credentials;
    @Autowired private SecretCipher cipher;

    @Test
    void createdCredentialIsEncryptedAtRestAndNeverReturned() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("cred-owner@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Credential Space");

        String secret = "sk-or-v1-" + UUID.randomUUID();
        String body = createCredential(owner, csrf, workspaceId, secret, 201)
                .getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain(secret)
                .doesNotContain("sk-or-v1")
                .doesNotContain("apiKey")
                .doesNotContain("encryptedSecret");
        assertThat(body).contains("\"provider\":\"OPENROUTER\"");

        ProviderCredential stored = credentials.findByWorkspace_IdOrderByCreatedAtDesc(workspaceId).get(0);
        String ciphertextAsText = Base64.getEncoder().encodeToString(stored.getEncryptedSecret());
        assertThat(ciphertextAsText).doesNotContain(secret);
        assertThat(new String(stored.getEncryptedSecret(), StandardCharsets.UTF_8)).doesNotContain(secret);
        assertThat(stored.getKeyVersion()).isEqualTo("v1");
        assertThat(cipher.decrypt(stored.getEncryptedSecret())).isEqualTo(secret);
    }

    @Test
    void listNeverIncludesSecrets() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("cred-list@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Credential List");
        createCredential(owner, csrf, workspaceId);

        String body = mockMvc.perform(get("/api/v1/workspaces/{id}/providers", workspaceId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(API_KEY).doesNotContain("apiKey").doesNotContain("encrypted");
    }

    @Test
    void memberCannotCreateOrDeleteCredentials() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("cred-role-owner@example.test", csrf);
        MockHttpSession member = signIn("cred-role-member@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Credential Roles");
        addMember(owner, csrf, workspaceId, "cred-role-member@example.test", "MEMBER", 201);

        createCredential(member, csrf, workspaceId, API_KEY, 403);

        UUID credentialId = createCredential(owner, csrf, workspaceId);
        mockMvc.perform(delete("/api/v1/workspaces/{id}/providers/{credentialId}", workspaceId, credentialId)
                        .session(member)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCanListCredentials() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("cred-list-owner@example.test", csrf);
        MockHttpSession member = signIn("cred-list-member@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Credential Listing");
        addMember(owner, csrf, workspaceId, "cred-list-member@example.test", "MEMBER", 201);
        createCredential(owner, csrf, workspaceId);

        mockMvc.perform(get("/api/v1/workspaces/{id}/providers", workspaceId).session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void ownerCanDeleteAnUnusedCredential() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("cred-delete@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Credential Delete");
        UUID credentialId = createCredential(owner, csrf, workspaceId);

        mockMvc.perform(delete("/api/v1/workspaces/{id}/providers/{credentialId}", workspaceId, credentialId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workspaces/{id}/providers", workspaceId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void credentialInUseCannotBeDeleted() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("cred-in-use@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Credential In Use");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        createAgent(owner, csrf, workspaceId, "Credential Consumer", credentialId);

        mockMvc.perform(delete("/api/v1/workspaces/{id}/providers/{credentialId}", workspaceId, credentialId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_IN_USE"));
    }

    @Test
    void invalidProviderIsRejected() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("cred-provider@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Credential Provider");

        mockMvc.perform(post("/api/v1/workspaces/{id}/providers", workspaceId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"provider\":\"NOT_A_PROVIDER\",\"displayName\":\"Bad\",\"apiKey\":\"12345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROVIDER"));
    }

    @Test
    void shortApiKeyIsRejected() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("cred-short@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Credential Short");

        createCredential(owner, csrf, workspaceId, "short", 400);
    }

    @Test
    void otherWorkspaceCredentialsAreInvisible() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession alice = signIn("cred-tenant-alice@example.test", csrf);
        MockHttpSession bob = signIn("cred-tenant-bob@example.test", csrf);
        UUID aliceWorkspace = createWorkspace(alice, csrf, "Alice Credentials");
        UUID bobWorkspace = createWorkspace(bob, csrf, "Bob Credentials");
        UUID aliceCredential = createCredential(alice, csrf, aliceWorkspace);

        mockMvc.perform(get("/api/v1/workspaces/{id}/providers", aliceWorkspace).session(bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/workspaces/{id}/providers/{credentialId}", aliceWorkspace, aliceCredential)
                        .session(bob)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isNotFound());

        // Even naming Bob's own workspace cannot reach Alice's credential.
        mockMvc.perform(delete("/api/v1/workspaces/{id}/providers/{credentialId}", bobWorkspace, aliceCredential)
                        .session(bob)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isNotFound());
    }
}
