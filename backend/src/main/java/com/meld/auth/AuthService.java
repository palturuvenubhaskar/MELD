package com.meld.auth;

import com.meld.auth.dto.AuthDtos;
import com.meld.common.exception.ApiException;
import com.meld.common.util.Normalizers;
import com.meld.user.User;
import com.meld.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registration and profile rules per ROADMAP.md M4.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates an account. Emails are normalized before storage; the password is
     * only ever persisted as a BCrypt hash. Registration does not create a
     * session (sign-in is a separate step).
     */
    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        String email = Normalizers.email(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }

        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode(request.password()));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }

        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Covers the race between the existence check and the insert.
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }

        return new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
