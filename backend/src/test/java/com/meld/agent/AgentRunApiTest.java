package com.meld.agent;

import com.meld.testsupport.ApiTestSupport;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs agent test prompts against a local stub provider, so the adapter,
 * timeout handling and error mapping are exercised without a real API key or
 * network access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentRunApiTest extends ApiTestSupport {

    private static final HttpServer STUB;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicReference<String> MODE = new AtomicReference<>("success");
    private static final AtomicReference<String> LAST_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB.createContext("/v1/chat/completions", exchange -> {
                LAST_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
                LAST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

                int status = 200;
                String body;
                switch (MODE.get()) {
                    case "unauthorized" -> {
                        status = 401;
                        body = "{\"error\":{\"message\":\"invalid api key\"}}";
                    }
                    case "server_error" -> {
                        status = 500;
                        body = "{\"error\":\"upstream failure\"}";
                    }
                    case "malformed" -> body = "<html>not json</html>";
                    case "slow" -> {
                        sleep(3000);
                        body = "{}";
                    }
                    default -> body = "{\"model\":\"" + TEST_MODEL + "\","
                            + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"stub answer\"}}],"
                            + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5}}";
                }

                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            STUB.setExecutor(EXECUTOR);
            STUB.start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void providerBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("meld.providers.base-urls.openrouter",
                () -> "http://127.0.0.1:" + STUB.getAddress().getPort() + "/v1");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
        EXECUTOR.shutdownNow();
    }

    @BeforeEach
    void resetStub() {
        MODE.set("success");
        LAST_AUTH.set(null);
        LAST_BODY.set(null);
    }

    @Test
    void testPromptReturnsOutputAndUsage() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("run-owner@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Run Space");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Runner", credentialId);

        mockMvc.perform(post("/api/v1/workspaces/{id}/agents/{agentId}/test", workspaceId, agentId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"input\":\"Summarize this sentence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("stub answer"))
                .andExpect(jsonPath("$.model").value(TEST_MODEL))
                .andExpect(jsonPath("$.inputTokens").value(12))
                .andExpect(jsonPath("$.outputTokens").value(5));

        assertThat(LAST_AUTH.get()).isEqualTo("Bearer " + API_KEY);
        assertThat(LAST_BODY.get())
                .contains(TEST_MODEL)
                .contains("You are a test agent.")
                .contains("Summarize this sentence")
                .doesNotContain(API_KEY);
    }

    @Test
    void rejectedCredentialIsReportedAsProviderAuthFailure() throws Exception {
        MODE.set("unauthorized");
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("run-auth@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Run Auth");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Rejected", credentialId);

        String body = mockMvc.perform(post("/api/v1/workspaces/{id}/agents/{agentId}/test", workspaceId, agentId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"input\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_AUTH_FAILED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(API_KEY).doesNotContain("invalid api key");
    }

    @Test
    void providerServerErrorIsReportedSafely() throws Exception {
        MODE.set("server_error");
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("run-500@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Run 500");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Failing", credentialId);

        String body = mockMvc.perform(post("/api/v1/workspaces/{id}/agents/{agentId}/test", workspaceId, agentId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"input\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("upstream failure").doesNotContain(API_KEY);
    }

    @Test
    void malformedProviderResponseIsReported() throws Exception {
        MODE.set("malformed");
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("run-malformed@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Run Malformed");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Malformed", credentialId);

        mockMvc.perform(post("/api/v1/workspaces/{id}/agents/{agentId}/test", workspaceId, agentId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"input\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_BAD_RESPONSE"));
    }

    @Test
    void providerTimeoutIsReportedAsGatewayTimeout() throws Exception {
        MODE.set("slow");
        Cookie csrf = csrfCookie();
        MockHttpSession owner = signIn("run-slow@example.test", csrf);
        UUID workspaceId = createWorkspace(owner, csrf, "Run Slow");
        UUID credentialId = createCredential(owner, csrf, workspaceId);
        UUID agentId = createAgent(owner, csrf, workspaceId, "Slow", credentialId);

        mockMvc.perform(post("/api/v1/workspaces/{id}/agents/{agentId}/test", workspaceId, agentId)
                        .session(owner)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"input\":\"hello\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PROVIDER_TIMEOUT"));
    }

    @Test
    void testPromptNeedsAuthentication() throws Exception {
        Cookie csrf = csrfCookie();
        mockMvc.perform(post("/api/v1/workspaces/{id}/agents/{agentId}/test", UUID.randomUUID(), UUID.randomUUID())
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue())
                        .contentType("application/json")
                        .content("{\"input\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
