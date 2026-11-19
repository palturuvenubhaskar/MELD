package com.meld.provider;

import com.meld.auth.MeldUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Provider credential endpoints. Secrets are write-only: they are accepted on
 * create and never returned.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/providers")
public class ProviderCredentialController {

    private final ProviderCredentialService credentialService;

    public ProviderCredentialController(ProviderCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping
    public List<ProviderDtos.CredentialResponse> list(@PathVariable UUID workspaceId,
                                                      Authentication authentication) {
        return credentialService.list(currentUserId(authentication), workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderDtos.CredentialResponse create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody ProviderDtos.CreateCredentialRequest request,
            Authentication authentication) {
        return credentialService.create(currentUserId(authentication), workspaceId, request);
    }

    @DeleteMapping("/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId,
                       @PathVariable UUID credentialId,
                       Authentication authentication) {
        credentialService.delete(currentUserId(authentication), workspaceId, credentialId);
    }

    private static UUID currentUserId(Authentication authentication) {
        return MeldUserPrincipal.current(authentication).getId();
    }
}
