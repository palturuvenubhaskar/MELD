package com.meld.auth;

import com.meld.auth.dto.AuthDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication endpoints per ARCHITECTURE.md §7.
 * POST /api/v1/auth/register
 * POST /api/v1/auth/login
 * POST /api/v1/auth/logout
 * GET  /api/v1/auth/me
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        // TODO: Implement user registration with password hashing
        // For now, return a placeholder response
        return ResponseEntity.status(501).body(new AuthDtos.AuthResponse(
                "not-implemented", request.email(), request.displayName(), null, null));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                request.email(), request.password());
        Authentication auth = authenticationManager.authenticate(token);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // TODO: Resolve workspace from user membership
        return ResponseEntity.ok(new AuthDtos.AuthResponse(
                "user-id", request.email(), "Display Name", "workspace-id", "Workspace Name"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDtos.AuthResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        // TODO: Return actual user info from principal
        return ResponseEntity.ok(new AuthDtos.AuthResponse(
                "user-id", auth.getName(), "Display Name", "workspace-id", "Workspace Name"));
    }
}