package com.meld.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meld.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for API tests that exercise the real session + CSRF flow.
 *
 * <p>The CSRF token comes from the {@code GET /api/v1/auth/csrf} bootstrap and
 * is reused as the cookie/header pair (double-submit). Sessions are returned
 * explicitly so a test can act as two different users.
 */
public abstract class ApiTestSupport {

    protected static final String CSRF_HEADER = "X-XSRF-TOKEN";
    protected static final String CSRF_COOKIE = "XSRF-TOKEN";
    protected static final String PASSWORD = "correct-horse-battery";
    protected static final String API_KEY = "sk-test-0123456789abcdef";
    protected static final String TEST_MODEL = "meta-llama/llama-3.1-8b-instruct:free";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected Cookie csrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).as("CSRF cookie").isNotNull();
        return cookie;
    }

    protected MvcResult register(String email, String password, Cookie csrf, int expectedStatus) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("displayName", "Test User");
        return mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected MvcResult login(String email, String password, Cookie csrf, int expectedStatus) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected MockHttpSession signIn(String email, Cookie csrf) throws Exception {
        register(email, PASSWORD, csrf, 201);
        return signInExisting(email, PASSWORD, csrf);
    }

    protected MockHttpSession signInExisting(String email, String password, Cookie csrf) throws Exception {
        MvcResult result = login(email, password, csrf, 200);
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).as("authenticated session").isNotNull();
        return session;
    }

    protected MvcResult createWorkspace(MockHttpSession session, Cookie csrf, String name, int expectedStatus)
            throws Exception {
        return mockMvc.perform(post("/api/v1/workspaces")
                        .session(session)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected UUID createWorkspace(MockHttpSession session, Cookie csrf, String name) throws Exception {
        MvcResult result = createWorkspace(session, csrf, name, 201);
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText());
    }

    protected MvcResult addMember(MockHttpSession session, Cookie csrf, UUID workspaceId,
                                  String email, String role, int expectedStatus) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("role", role);
        return mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .session(session)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected MvcResult createCredential(MockHttpSession session, Cookie csrf, UUID workspaceId,
                                         String apiKey, int expectedStatus) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", "OPENROUTER");
        body.put("displayName", "Test credential");
        body.put("apiKey", apiKey);
        return mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/providers", workspaceId)
                        .session(session)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected UUID createCredential(MockHttpSession session, Cookie csrf, UUID workspaceId) throws Exception {
        return UUID.fromString(contentAsTree(createCredential(session, csrf, workspaceId, API_KEY, 201))
                .get("id").asText());
    }

    protected MvcResult createAgent(MockHttpSession session, Cookie csrf, UUID workspaceId,
                                    String name, UUID credentialId, int expectedStatus) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "Test agent");
        body.put("model", TEST_MODEL);
        body.put("systemPrompt", "You are a test agent.");
        body.put("providerCredentialId", credentialId.toString());
        body.put("temperature", 0.2);
        body.put("maxTokens", 128);
        return mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/agents", workspaceId)
                        .session(session)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected UUID createAgent(MockHttpSession session, Cookie csrf, UUID workspaceId,
                               String name, UUID credentialId) throws Exception {
        return UUID.fromString(contentAsTree(createAgent(session, csrf, workspaceId, name, credentialId, 201))
                .get("id").asText());
    }

    protected com.fasterxml.jackson.databind.JsonNode contentAsTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * Unique email for suites that commit rows (worker/execution tests). Reusing
     * a fixed address would clash with data from an earlier run.
     */
    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }

    /**
     * Workspace slugs are globally unique, so committed suites cannot reuse a
     * fixed display name across runs.
     */
    protected static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    /** JSON POST helper used by the workflow and execution suites. */
    protected MvcResult postJson(MockHttpSession session, Cookie csrf, String url,
                                 String json, int expectedStatus) throws Exception {
        return mockMvc.perform(post(url)
                        .session(session)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected MvcResult patchJson(MockHttpSession session, Cookie csrf, String url,
                                  String json, int expectedStatus) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch(url)
                        .session(session)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected MvcResult getJson(MockHttpSession session, String url, int expectedStatus) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(url)
                        .session(session))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }
}
