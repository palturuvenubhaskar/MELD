package com.meld.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meld.common.exception.ApiException;
import com.meld.provider.DecryptedCredential;
import com.meld.provider.ProviderCredentialRepository;
import com.meld.provider.ProviderCredentialService;
import com.meld.provider.adapter.ChatProvider;
import com.meld.provider.adapter.ChatProviderRouter;
import com.meld.workspace.WorkspaceAccessService;
import com.meld.workspace.WorkspaceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Agent management and test prompts per ROADMAP.md M5.
 *
 * <p>Any workspace member can build agents; only {@code OWNER} manages
 * credentials (see {@link com.meld.provider.ProviderCredentialService}). Test
 * prompts are stateless: they do not create executions or persist usage.
 */
@Service
public class AgentService {

    private final AgentRepository agents;
    private final ProviderCredentialRepository credentials;
    private final WorkspaceRepository workspaces;
    private final WorkspaceAccessService access;
    private final ProviderCredentialService providerCredentials;
    private final ChatProviderRouter chatProviders;
    private final ObjectMapper objectMapper;

    public AgentService(AgentRepository agents,
                        ProviderCredentialRepository credentials,
                        WorkspaceRepository workspaces,
                        WorkspaceAccessService access,
                        ProviderCredentialService providerCredentials,
                        ChatProviderRouter chatProviders,
                        ObjectMapper objectMapper) {
        this.agents = agents;
        this.credentials = credentials;
        this.workspaces = workspaces;
        this.access = access;
        this.providerCredentials = providerCredentials;
        this.chatProviders = chatProviders;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentDtos.AgentResponse create(UUID actorId, UUID workspaceId, AgentDtos.CreateAgentRequest request) {
        access.requireMembership(workspaceId, actorId);

        String name = request.name().trim();
        if (agents.existsByWorkspace_IdAndName(workspaceId, name)) {
            throw nameTaken();
        }
        requireCredential(workspaceId, request.providerCredentialId());

        Agent agent = new Agent(UUID.randomUUID(), workspaces.getReferenceById(workspaceId),
                name, request.model().trim(), request.systemPrompt());
        agent.setDescription(trimToNull(request.description()));
        agent.setProviderCredentialId(request.providerCredentialId());
        agent.setCreatedBy(actorId);
        agent.setConfiguration(new AgentRuntimeConfig(request.temperature(), request.maxTokens())
                .toJson(objectMapper));

        try {
            agents.saveAndFlush(agent);
        } catch (DataIntegrityViolationException ex) {
            throw nameTaken();
        }
        return toResponse(agent);
    }

    @Transactional(readOnly = true)
    public List<AgentDtos.AgentResponse> list(UUID userId, UUID workspaceId) {
        access.requireMembership(workspaceId, userId);
        return agents.findByWorkspace_IdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentDtos.AgentResponse get(UUID userId, UUID workspaceId, UUID agentId) {
        access.requireMembership(workspaceId, userId);
        return toResponse(requireAgent(workspaceId, agentId));
    }

    @Transactional
    public AgentDtos.AgentResponse update(UUID actorId, UUID workspaceId, UUID agentId,
                                          AgentDtos.UpdateAgentRequest request) {
        access.requireMembership(workspaceId, actorId);
        Agent agent = requireAgent(workspaceId, agentId);

        if (request.name() != null) {
            String name = request.name().trim();
            if (agents.existsByWorkspace_IdAndNameAndIdNot(workspaceId, name, agentId)) {
                throw nameTaken();
            }
            agent.setName(name);
        }
        if (request.description() != null) {
            agent.setDescription(trimToNull(request.description()));
        }
        if (request.model() != null) {
            agent.setModel(request.model().trim());
        }
        if (request.systemPrompt() != null) {
            agent.setSystemPrompt(request.systemPrompt());
        }
        if (request.providerCredentialId() != null) {
            requireCredential(workspaceId, request.providerCredentialId());
            agent.setProviderCredentialId(request.providerCredentialId());
        }
        if (request.temperature() != null || request.maxTokens() != null) {
            AgentRuntimeConfig current = AgentRuntimeConfig.parse(agent.getConfiguration(), objectMapper);
            AgentRuntimeConfig updated = new AgentRuntimeConfig(
                    request.temperature() != null ? request.temperature() : current.temperature(),
                    request.maxTokens() != null ? request.maxTokens() : current.maxTokens());
            agent.setConfiguration(updated.toJson(objectMapper));
        }

        try {
            agents.saveAndFlush(agent);
        } catch (DataIntegrityViolationException ex) {
            throw nameTaken();
        }
        return toResponse(agent);
    }

    @Transactional
    public void delete(UUID actorId, UUID workspaceId, UUID agentId) {
        access.requireMembership(workspaceId, actorId);
        agents.delete(requireAgent(workspaceId, agentId));
    }

    /**
     * Runs one prompt against the provider using the agent's stored settings.
     * Returns model output and token usage; the API key is never echoed.
     */
    @Transactional(readOnly = true)
    public AgentDtos.AgentTestResponse test(UUID userId, UUID workspaceId, UUID agentId,
                                            AgentDtos.AgentTestRequest request) {
        access.requireMembership(workspaceId, userId);
        Agent agent = requireAgent(workspaceId, agentId);
        if (agent.getProviderCredentialId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AGENT_NOT_READY",
                    "The agent has no provider credential");
        }

        DecryptedCredential credential = providerCredentials.resolve(workspaceId, agent.getProviderCredentialId());
        AgentRuntimeConfig config = AgentRuntimeConfig.parse(agent.getConfiguration(), objectMapper);

        ChatProvider.ChatResponse response;
        try {
            response = chatProviders.complete(new ChatProvider.ChatInvocation(
                    credential.kind(),
                    credential.apiKey(),
                    agent.getModel(),
                    List.of(
                            ChatProvider.ChatMessage.system(agent.getSystemPrompt()),
                            ChatProvider.ChatMessage.user(request.input())),
                    config.temperature(),
                    config.maxTokens()));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Never propagate a message that could contain request details.
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR",
                    "The provider request failed");
        }

        return new AgentDtos.AgentTestResponse(
                response.content(),
                response.model(),
                response.inputTokens(),
                response.outputTokens(),
                response.latencyMs());
    }

    /**
     * Internal only: the caller must have verified workspace access. Used by
     * the workflow module to snapshot non-secret settings into a version.
     */
    @Transactional(readOnly = true)
    public AgentSnapshot snapshot(UUID workspaceId, UUID agentId) {
        Agent agent = requireAgent(workspaceId, agentId);
        AgentRuntimeConfig config = AgentRuntimeConfig.parse(agent.getConfiguration(), objectMapper);
        if (agent.getProviderCredentialId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "AGENT_NOT_READY",
                    "The agent has no provider credential");
        }
        return new AgentSnapshot(
                agent.getId(),
                agent.getName(),
                agent.getModel(),
                agent.getSystemPrompt(),
                agent.getProviderCredentialId(),
                config.temperature(),
                config.maxTokens());
    }

    private Agent requireAgent(UUID workspaceId, UUID agentId) {
        return agents.findByIdAndWorkspace_Id(agentId, workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent not found"));
    }

    private void requireCredential(UUID workspaceId, UUID credentialId) {
        credentials.findByIdAndWorkspace_Id(credentialId, workspaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIAL",
                        "Provider credential does not belong to this workspace"));
    }

    private AgentDtos.AgentResponse toResponse(Agent agent) {
        AgentRuntimeConfig config = AgentRuntimeConfig.parse(agent.getConfiguration(), objectMapper);
        return new AgentDtos.AgentResponse(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.getModel(),
                agent.getSystemPrompt(),
                agent.getProviderCredentialId(),
                config.temperature(),
                config.maxTokens(),
                agent.getCreatedAt(),
                agent.getUpdatedAt());
    }

    private static ApiException nameTaken() {
        return new ApiException(HttpStatus.CONFLICT, "AGENT_NAME_TAKEN",
                "An agent with that name already exists in this workspace");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
