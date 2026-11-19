package com.meld.provider.adapter;

import com.meld.common.exception.ApiException;
import com.meld.provider.ProviderKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects the adapter for a provider kind. With one adapter family the routing
 * is trivial, but the seam keeps provider-specific code out of the agent and
 * execution modules.
 */
@Component
public class ChatProviderRouter {

    private final List<ChatProvider> providers;

    public ChatProviderRouter(List<ChatProvider> providers) {
        this.providers = providers;
    }

    public ChatProvider.ChatResponse complete(ChatProvider.ChatInvocation invocation) {
        return providers.stream()
                .filter(provider -> provider.supports(invocation.kind()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_NOT_SUPPORTED",
                        "No adapter is configured for " + invocation.kind()))
                .complete(invocation);
    }
}
