package com.meld.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 configuration per ARCHITECTURE.md §7.
 * API versioned under /api/v1.
 * Uses cookie-based auth (session) for MVP.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MELD API")
                        .version("v1")
                        .description("""
                                MELD AI Workflow Orchestration Platform API.
                                
                                ## Authentication
                                Session cookie based authentication. After login, the `SESSION` cookie
                                is set and must be included in subsequent requests.
                                
                                ## Workspace Isolation
                                All workspace-scoped endpoints require the `X-Workspace-ID` header
                                or the workspace is resolved from the authenticated session.
                                
                                ## Idempotency
                                For mutating operations, provide `Idempotency-Key` header to ensure
                                exactly-once execution semantics.
                                """)
                        .contact(new Contact()
                                .name("MELD Team")
                                .email("support@meld.example"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://meld.example/license")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local development"),
                        new Server().url("https://api.meld.example").description("Production")
                ))
                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
                .components(new Components()
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")
                                .description("Session cookie set after login")));
    }
}