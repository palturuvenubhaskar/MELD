package com.meld.auth;

import com.meld.common.util.Normalizers;
import com.meld.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users by email for Spring Security's DaoAuthenticationProvider.
 * The message is deliberately generic so account existence is not disclosed.
 */
@Service
public class MeldUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public MeldUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByEmailIgnoreCase(Normalizers.email(username))
                .map(MeldUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
