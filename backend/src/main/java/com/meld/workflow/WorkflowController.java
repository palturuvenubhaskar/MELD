package com.meld.workflow;

import com.meld.auth.MeldUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Workflow and version endpoints per ARCHITECTURE.md §7 / ROADMAP.md M6.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public List<WorkflowDtos.WorkflowResponse> list(@PathVariable UUID workspaceId,
                                                    Authentication authentication) {
        return workflowService.list(currentUserId(authentication), workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDtos.WorkflowResponse create(@PathVariable UUID workspaceId,
                                                @Valid @RequestBody WorkflowDtos.CreateWorkflowRequest request,
                                                Authentication authentication) {
        return workflowService.create(currentUserId(authentication), workspaceId, request);
    }

    @GetMapping("/{workflowId}")
    public WorkflowDtos.WorkflowResponse get(@PathVariable UUID workspaceId,
                                             @PathVariable UUID workflowId,
                                             Authentication authentication) {
        return workflowService.get(currentUserId(authentication), workspaceId, workflowId);
    }

    @PatchMapping("/{workflowId}")
    public WorkflowDtos.WorkflowResponse update(@PathVariable UUID workspaceId,
                                                @PathVariable UUID workflowId,
                                                @Valid @RequestBody WorkflowDtos.UpdateWorkflowRequest request,
                                                Authentication authentication) {
        return workflowService.update(currentUserId(authentication), workspaceId, workflowId, request);
    }

    @DeleteMapping("/{workflowId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId,
                       @PathVariable UUID workflowId,
                       Authentication authentication) {
        workflowService.delete(currentUserId(authentication), workspaceId, workflowId);
    }

    @GetMapping("/{workflowId}/versions")
    public List<WorkflowDtos.VersionSummary> listVersions(@PathVariable UUID workspaceId,
                                                          @PathVariable UUID workflowId,
                                                          Authentication authentication) {
        return workflowService.listVersions(currentUserId(authentication), workspaceId, workflowId);
    }

    @PostMapping("/{workflowId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDtos.VersionResponse createVersion(@PathVariable UUID workspaceId,
                                                      @PathVariable UUID workflowId,
                                                      @Valid @RequestBody WorkflowDtos.CreateVersionRequest request,
                                                      Authentication authentication) {
        return workflowService.createVersion(currentUserId(authentication), workspaceId, workflowId, request);
    }

    @GetMapping("/{workflowId}/versions/{versionNumber}")
    public WorkflowDtos.VersionResponse getVersion(@PathVariable UUID workspaceId,
                                                   @PathVariable UUID workflowId,
                                                   @PathVariable int versionNumber,
                                                   Authentication authentication) {
        return workflowService.getVersion(currentUserId(authentication), workspaceId, workflowId, versionNumber);
    }

    private static UUID currentUserId(Authentication authentication) {
        return MeldUserPrincipal.current(authentication).getId();
    }
}
