package com.meld.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider transport settings.
 *
 * <p>Base URLs are server configuration, never user input, so a workspace
 * cannot point the backend at an arbitrary host. They can be overridden with
 * environment variables for a gateway or a local test stub.
 */
@Component
@ConfigurationProperties(prefix = "meld.providers")
public class ProviderProperties {

    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(60);
    private Map<String, String> baseUrls = new LinkedHashMap<>();

    public String baseUrl(ProviderKind kind) {
        return baseUrls.get(kind.key());
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Map<String, String> getBaseUrls() {
        return baseUrls;
    }

    public void setBaseUrls(Map<String, String> baseUrls) {
        this.baseUrls = baseUrls;
    }
}
