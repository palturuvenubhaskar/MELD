package com.meld.provider;

import com.meld.common.exception.ApiException;
import com.meld.provider.crypto.SecretCipher;
import com.meld.workspace.WorkspaceRepository;
import com.meld.workspace.WorkspaceAccessService;
import com.meld.workspace.WorkspaceRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Provider credential management per ROADMAP.md M5.
 *
 * <p>Only {@code OWNER} can create or delete credentials; any member can list
 * them. Plaintext never leaves this class except through
 * {@link #resolve(UUID, UUID)}, which is used by the provider adapter at call
 * time.
 */
@Service
public class ProviderCredentialService {

    private final ProviderCredentialRepository credentials;
    private final WorkspaceRepository workspaces;
    private final WorkspaceAccessService access;
    private final SecretCipher cipher;

    public ProviderCredentialService(ProviderCredentialRepository credentials,
                                     WorkspaceRepository workspaces,
                                     WorkspaceAccessService access,
                                     SecretCipher cipher) {
        this.credentials = credentials;
        this.workspaces = workspaces;
        this.access = access;
        this.cipher = cipher;
    }

    @Transactional
    public ProviderDtos.CredentialResponse create(UUID actorId, UUID workspaceId,
                                                  ProviderDtos.CreateCredentialRequest request) {
        access.requireRole(workspaceId, actorId, WorkspaceRole.OWNER);

        ProviderKind kind = ProviderKind.fromString(request.provider())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROVIDER",
                        "Unsupported provider; supported: " + ProviderKind.supportedNames()));

        ProviderCredential credential = new ProviderCredential(
                UUID.randomUUID(),
                workspaces.getReferenceById(workspaceId),
                kind.name(),
                request.displayName().trim(),
                cipher.encrypt(request.apiKey()),
                SecretCipher.KEY_VERSION,
                actorId);
        credentials.saveAndFlush(credential);
        return toResponse(credential);
    }

    @Transactional(readOnly = true)
    public List<ProviderDtos.CredentialResponse> list(UUID userId, UUID workspaceId) {
        access.requireMembership(workspaceId, userId);
        return credentials.findByWorkspace_IdOrderByCreatedAtDesc(workspaceId).stream()
                .map(ProviderCredentialService::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID actorId, UUID workspaceId, UUID credentialId) {
        access.requireRole(workspaceId, actorId, WorkspaceRole.OWNER);
        ProviderCredential credential = credentials.findByIdAndWorkspace_Id(credentialId, workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "CREDENTIAL_NOT_FOUND", "Provider credential not found"));
        try {
            credentials.delete(credential);
            credentials.flush();
        } catch (DataIntegrityViolationException ex) {
            // Agents reference the credential; the FK is intentionally restrictive.
            throw new ApiException(HttpStatus.CONFLICT, "CREDENTIAL_IN_USE",
                    "This credential is used by an agent and cannot be deleted");
        }
    }

    /**
     * Internal only. The caller must already have verified workspace access.
     */
    @Transactional(readOnly = true)
    public DecryptedCredential resolve(UUID workspaceId, UUID credentialId) {
        ProviderCredential credential = credentials.findByIdAndWorkspace_Id(credentialId, workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CREDENTIAL_MISSING",
                        "The agent's provider credential is no longer available"));
        ProviderKind kind = ProviderKind.fromString(credential.getProvider())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CREDENTIAL_MISSING",
                        "The agent's provider credential is not usable"));
        return new DecryptedCredential(kind, cipher.decrypt(credential.getEncryptedSecret()),
                credential.getDisplayName());
    }

    private static ProviderDtos.CredentialResponse toResponse(ProviderCredential credential) {
        return new ProviderDtos.CredentialResponse(
                credential.getId(),
                credential.getProvider(),
                credential.getDisplayName(),
                credential.getCreatedAt());
    }
}
