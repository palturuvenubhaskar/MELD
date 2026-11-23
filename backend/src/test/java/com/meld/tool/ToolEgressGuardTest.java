package com.meld.tool;

import com.meld.common.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Default-deny egress policy (docs/TEST.md SEC-015, SEC-016; RULES.md Security).
 */
class ToolEgressGuardTest {

    private final ToolProperties properties = new ToolProperties();
    private final ToolEgressGuard guard = new ToolEgressGuard(properties);

    @Test
    void blocksLoopbackPrivateLinkLocalMulticastAndMetadataAddresses() throws Exception {
        for (String address : List.of(
                "0.0.0.0", "127.0.0.1", "127.10.0.5", "10.1.2.3", "172.16.0.1", "172.31.255.255",
                "192.168.1.1", "169.254.169.254", "100.64.0.1", "192.0.0.1", "192.0.2.10",
                "198.18.0.1", "198.51.100.7", "203.0.113.9", "224.0.0.1", "240.0.0.1",
                "255.255.255.255", "::", "::1", "fd00::1", "fe80::1", "ff02::1",
                "2001:db8::1", "::ffff:127.0.0.1")) {
            assertThat(guard.isForbidden(InetAddress.getByName(address)))
                    .as("address %s must be blocked", address)
                    .isTrue();
        }
    }

    @Test
    void allowsPublicUnicastAddresses() throws Exception {
        for (String address : List.of(
                "8.8.8.8", "1.1.1.1", "93.184.216.34", "172.32.0.1", "192.169.0.1",
                "2606:4700:4700::1111")) {
            assertThat(guard.isForbidden(InetAddress.getByName(address)))
                    .as("address %s must be allowed", address)
                    .isFalse();
        }
    }

    @Test
    void rejectsUnsafeOrMalformedToolUrls() {
        assertThatThrownBy(() -> guard.validateForSave("ftp://example.com/file"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("https");
        assertThatThrownBy(() -> guard.validateForSave("http://example.com/data"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("TOOL_URL_INSECURE"));
        assertThatThrownBy(() -> guard.validateForSave("https://user:pass@example.com/"))
                .hasMessageContaining("user information");
        assertThatThrownBy(() -> guard.validateForSave("https://127.0.0.1/admin"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo("TOOL_EGRESS_BLOCKED"));
        assertThatThrownBy(() -> guard.validateForSave("https://[::1]/admin"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> guard.validateForSave("/relative"))
                .hasMessageContaining("absolute");
        assertThatThrownBy(() -> guard.validateForSave("https://169.254.169.254/latest"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void acceptsOrdinaryHttpsDestinationsWithoutDnsLookup() {
        URI uri = guard.validateForSave("https://example.com/data?x=1");
        assertThat(uri.getHost()).isEqualTo("example.com");
        assertThat(guard.validateForSave("https://localhost:8443/hook").getPort()).isEqualTo(8443);
    }

    @Test
    void resolveBlocksMetadataHostsBeforeDns() {
        assertThatThrownBy(() -> guard.resolve("https://metadata.google.internal/computeMetadata/v1/"))
                .isInstanceOf(ToolExecutionException.class)
                .satisfies(ex -> assertThat(((ToolExecutionException) ex).getCode())
                        .isEqualTo("TOOL_EGRESS_BLOCKED"));
        assertThatThrownBy(() -> guard.resolve("https://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(ToolExecutionException.class);
        assertThatThrownBy(() -> guard.resolve("https://localhost:9/probe"))
                .isInstanceOf(ToolExecutionException.class)
                .satisfies(ex -> assertThat(((ToolExecutionException) ex).getCode())
                        .isEqualTo("TOOL_EGRESS_BLOCKED"));
    }

    @Test
    void redirectToForbiddenDestinationIsRevalidatedAndBlocked() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        properties.setAllowInsecureHttp(true);

        // Test seam: allow only the local stub, then hand every redirect back to
        // the real policy so revalidation is exercised end to end.
        ToolEgressGuard seam = new ToolEgressGuard(properties) {
            @Override
            public PinnedTarget resolve(String url) {
                URI uri = URI.create(url);
                if (uri.getPort() == port) {
                    try {
                        return new PinnedTarget(uri, uri.getHost(), port,
                                InetAddress.getByName("127.0.0.1"));
                    } catch (java.net.UnknownHostException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                return super.resolve(url);
            }
        };

        try {
            PinnedHttpGet client = new PinnedHttpGet(seam, properties);
            assertThatThrownBy(() -> client.get("http://127.0.0.1:" + port + "/start"))
                    .isInstanceOf(ToolExecutionException.class)
                    .satisfies(ex -> assertThat(((ToolExecutionException) ex).getCode())
                            .isEqualTo("TOOL_EGRESS_BLOCKED"));
        } finally {
            server.stop(0);
        }
    }
}
