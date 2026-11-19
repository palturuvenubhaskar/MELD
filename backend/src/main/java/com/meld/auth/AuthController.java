package com.meld.auth;

import com.meld.auth.dto.AuthDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints per ARCHITECTURE.md §7 and ROADMAP.md M4.
 *
 * <p>Login is handled here (not by Spring's formLogin filter) so the API can
 * accept JSON and return JSON. The authenticated context is explicitly saved to
 * the session, and the session id is rotated to prevent fixation.
 * {@code POST /auth/logout} is handled by Spring Security's logout filter.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          AuthService authService,
                          SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.UserResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.UserResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Establish the session, then rotate its id to defend against fixation.
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return AuthDtos.UserResponse.from(MeldUserPrincipal.current(authentication));
    }

    @GetMapping("/csrf")
    public AuthDtos.CsrfResponse csrf(CsrfToken csrfToken) {
        // Reading the token forces the CSRF cookie to be written for the client.
        return new AuthDtos.CsrfResponse(
                csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(Authentication authentication) {
        return AuthDtos.UserResponse.from(MeldUserPrincipal.current(authentication));
    }
}
