package com.meld.auth;

import com.meld.common.exception.ApiException;
import com.meld.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated principal for MELD.
 *
 * <p>Global authorities are intentionally coarse ({@code ROLE_USER}): workspace
 * roles are per-workspace and are enforced by {@code WorkspaceAccessService},
 * never by stuffing a workspace role into the session.
 */
public class MeldUserPrincipal implements UserDetails {

    private static final String GLOBAL_AUTHORITY = "ROLE_USER";

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String displayName;
    private final boolean active;

    public MeldUserPrincipal(UUID id, String email, String passwordHash, String displayName, boolean active) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.active = active;
    }

    public static MeldUserPrincipal from(User user) {
        return new MeldUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getDisplayName(),
                "ACTIVE".equals(user.getStatus()));
    }

    public static MeldUserPrincipal current(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MeldUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
        }
        return principal;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(GLOBAL_AUTHORITY));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
