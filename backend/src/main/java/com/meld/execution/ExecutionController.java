package com.meld.execution;

import com.meld.auth.MeldUserPrincipal;
import com.meld.common.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Execution endpoints per ARCHITECTURE.md §7 / ROADMAP.md M6.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/workflows/{workflowId}/executions")
    public ResponseEntity<ExecutionDtos.ExecutionSummary> start(
            @PathVariable UUID workspaceId,
            @PathVariable UUID workflowId,
            @Valid @RequestBody(required = false) ExecutionDtos.StartExecutionRequest request,
            Authentication authentication) {
        ExecutionDtos.StartExecutionRequest safeRequest = request == null
                ? new ExecutionDtos.StartExecutionRequest(null, null, null)
                : request;
        ExecutionService.StartResult result = executionService.start(
                currentUserId(authentication), workspaceId, workflowId, safeRequest);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.summary());
    }

    @GetMapping("/executions")
    public PageResponse<ExecutionDtos.ExecutionSummary> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID workflowId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return executionService.list(currentUserId(authentication), workspaceId, workflowId, page, size);
    }

    @GetMapping("/executions/{executionId}")
    public ExecutionDtos.ExecutionResponse get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID executionId,
            Authentication authentication) {
        return executionService.get(currentUserId(authentication), workspaceId, executionId);
    }

    @GetMapping("/executions/{executionId}/logs")
    public PageResponse<ExecutionDtos.ExecutionLogResponse> logs(
            @PathVariable UUID workspaceId,
            @PathVariable UUID executionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            Authentication authentication) {
        return executionService.logs(currentUserId(authentication), workspaceId, executionId, page, size);
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ExecutionDtos.ExecutionResponse cancel(
            @PathVariable UUID workspaceId,
            @PathVariable UUID executionId,
            Authentication authentication) {
        return executionService.cancel(currentUserId(authentication), workspaceId, executionId);
    }

    private static UUID currentUserId(Authentication authentication) {
        return MeldUserPrincipal.current(authentication).getId();
    }
}
