package com.meld.auth;

import com.meld.testsupport.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-level authentication tests per ROADMAP.md M4 and TEST.md §4:
 * SEC-007 (hash only), SEC-008 (unauthenticated rejected), plus CSRF and
 * session lifecycle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest extends ApiTestSupport {

    @Test
    void csrfBootstrapReturnsTokenAndCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value(CSRF_HEADER))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(result -> assertThat(result.getResponse().getCookie(CSRF_COOKIE)).isNotNull());
    }

    @Test
    void registrationStoresOnlyAHashAndNeverReturnsIt() throws Exception {
        Cookie csrf = csrfCookie();
        String email = "hash-check@example.test";

        String body = register(email, PASSWORD, csrf, 201)
                .getResponse().getContentAsString();

        assertThat(body).doesNotContain(PASSWORD).doesNotContain("$2");
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    void emailIsNormalizedAndCaseVariantsConflict() throws Exception {
        Cookie csrf = csrfCookie();
        register("Mixed.Case@Example.TEST", PASSWORD, csrf, 201);

        assertThat(users.findByEmailIgnoreCase("mixed.case@example.test")).isPresent();

        register("mixed.case@example.test", PASSWORD, csrf, 409);
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        Cookie csrf = csrfCookie();
        register("short@example.test", "short", csrf, 400);
    }

    @Test
    void loginSucceedsAndMeReturnsTheProfile() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession session = signIn("login@example.test", csrf);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.test"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("password"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        Cookie csrf = csrfCookie();
        register("wrong-pass@example.test", PASSWORD, csrf, 201);

        login("wrong-pass@example.test", "not-the-password", csrf, 401);
    }

    @Test
    void unknownEmailIsUnauthorized() throws Exception {
        Cookie csrf = csrfCookie();
        login("nobody@example.test", PASSWORD, csrf, 401);
    }

    @Test
    void unauthenticatedMeIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mutatingAuthRequestsRequireCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"no-csrf@example.test\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutClearsTheSessionCookie() throws Exception {
        Cookie csrf = csrfCookie();
        MockHttpSession session = signIn("logout@example.test", csrf);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                        .contains("MELD_SESSION="));
    }
}
