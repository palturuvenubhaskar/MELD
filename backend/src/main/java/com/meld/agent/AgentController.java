package com.meld.agent;

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
 * Agent endpoints per ARCHITECTURE.md §7.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public List<AgentDtos.AgentResponse> list(@PathVariable UUID workspaceId,
                                              Authentication authentication) {
        return agentService.list(currentUserId(authentication), workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentDtos.AgentResponse create(@PathVariable UUID workspaceId,
                                          @Valid @RequestBody AgentDtos.CreateAgentRequest request,
                                          Authentication authentication) {
        return agentService.create(currentUserId(authentication), workspaceId, request);
    }

    @GetMapping("/{agentId}")
    public AgentDtos.AgentResponse get(@PathVariable UUID workspaceId,
                                       @PathVariable UUID agentId,
                                       Authentication authentication) {
        return agentService.get(currentUserId(authentication), workspaceId, agentId);
    }

    @PatchMapping("/{agentId}")
    public AgentDtos.AgentResponse update(@PathVariable UUID workspaceId,
                                          @PathVariable UUID agentId,
                                          @Valid @RequestBody AgentDtos.UpdateAgentRequest request,
                                          Authentication authentication) {
        return agentService.update(currentUserId(authentication), workspaceId, agentId, request);
    }

    @DeleteMapping("/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId,
                       @PathVariable UUID agentId,
                       Authentication authentication) {
        agentService.delete(currentUserId(authentication), workspaceId, agentId);
    }

    @PostMapping("/{agentId}/test")
    public AgentDtos.AgentTestResponse test(@PathVariable UUID workspaceId,
                                            @PathVariable UUID agentId,
                                            @Valid @RequestBody AgentDtos.AgentTestRequest request,
                                            Authentication authentication) {
        return agentService.test(currentUserId(authentication), workspaceId, agentId, request);
    }

    private static UUID currentUserId(Authentication authentication) {
        return MeldUserPrincipal.current(authentication).getId();
    }
}
