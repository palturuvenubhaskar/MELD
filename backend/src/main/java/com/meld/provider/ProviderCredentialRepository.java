package com.meld.provider;

import com.meld.workspace.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ProviderCredential repository per DATABASE.md §5.
 * Never returns decrypted secrets.
 */
@Repository
public interface ProviderCredentialRepository extends JpaRepository<ProviderCredential, UUID> {

    @Query("SELECT c FROM ProviderCredential c WHERE c.workspace = :workspace")
    List<ProviderCredential> findByWorkspace(@Param("workspace") Workspace workspace);

    @Query("SELECT c FROM ProviderCredential c WHERE c.workspace = :workspace AND c.provider = :provider")
    List<ProviderCredential> findByWorkspaceAndProvider(@Param("workspace") Workspace workspace, @Param("provider") String provider);

    Optional<ProviderCredential> findByIdAndWorkspace(UUID id, Workspace workspace);

    @Query("SELECT c FROM ProviderCredential c WHERE c.id = :id AND c.workspace = :workspace")
    Optional<ProviderCredential> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspace") Workspace workspace);

    List<ProviderCredential> findByWorkspace_IdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<ProviderCredential> findByIdAndWorkspace_Id(UUID id, UUID workspaceId);
}