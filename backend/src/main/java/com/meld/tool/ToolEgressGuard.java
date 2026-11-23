package com.meld.tool;

import com.meld.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Default-deny egress policy for network tools (ARCHITECTURE.md §11,
 * RULES.md Security, TEST.md SEC-015/SEC-016).
 *
 * <p>Resolution returns one vetted address; the transport connects to exactly
 * that address, so a DNS answer cannot change between validation and connect.
 * Redirects are validated again before they are followed.
 */
@Component
public class ToolEgressGuard {

    private static final Set<String> METADATA_HOSTS = Set.of(
            "metadata.google.internal",
            "metadata.goog",
            "instance-data",
            "169.254.169.254",
            "100.100.100.200",
            "fd00:ec2::254");

    private final ToolProperties properties;

    public ToolEgressGuard(ToolProperties properties) {
        this.properties = properties;
    }

    public record PinnedTarget(URI uri, String host, int port, InetAddress address) {
    }

    /**
     * Syntax-only validation used when a workflow version is saved. DNS is not
     * consulted here so saving does not depend on the destination being up.
     */
    public URI validateForSave(String url) {
        URI uri = parse(url);
        String host = unbracket(uri.getHost());
        if (METADATA_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOOL_EGRESS_BLOCKED",
                    "The tool destination is not an approved public address");
        }
        if (isIpLiteral(host)) {
            // Literal addresses can be checked without DNS.
            InetAddress literal;
            try {
                literal = InetAddress.getByName(host);
            } catch (UnknownHostException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TOOL_URL_INVALID",
                        "The tool destination is not a valid address");
            }
            if (isForbidden(literal)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TOOL_EGRESS_BLOCKED",
                        "The tool destination is not an approved public address");
            }
        }
        return uri;
    }

    /**
     * Resolves and accepts a destination for an actual connection.
     */
    public PinnedTarget resolve(String url) {
        URI uri = parse(url);

        String host = unbracket(uri.getHost());
        if (METADATA_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw blocked();
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new ToolExecutionException("TOOL_HOST_UNRESOLVED",
                    "The tool destination could not be resolved");
        }

        InetAddress chosen = null;
        for (InetAddress address : addresses) {
            if (!isForbidden(address)) {
                chosen = address;
                break;
            }
        }
        if (chosen == null) {
            throw blocked();
        }

        int port = uri.getPort() != -1 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
        return new PinnedTarget(uri, host, port, chosen);
    }

    private static String unbracket(String host) {
        if (host != null && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isIpLiteral(String host) {
        return host != null && (host.indexOf(':') >= 0 || host.matches("\\d{1,3}(\\.\\d{1,3}){3}"));
    }

    /**
     * Checks one address. Package-private so security tests can assert the
     * policy directly without performing network calls.
     */
    boolean isForbidden(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isForbiddenIpv4(bytes);
        }
        if (bytes.length == 16) {
            if (isIpv4Mapped(bytes)) {
                return isForbiddenIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
            }
            return isForbiddenIpv6(bytes);
        }
        return true;
    }

    private static boolean isForbiddenIpv4(byte[] b) {
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;

        if (first == 0) return true;                                  // 0.0.0.0/8
        if (first == 10) return true;                                 // private
        if (first == 127) return true;                                // loopback
        if (first == 169 && second == 254) return true;               // link-local + metadata
        if (first == 172 && second >= 16 && second <= 31) return true; // private
        if (first == 192 && second == 168) return true;               // private
        if (first == 100 && second >= 64 && second <= 127) return true; // carrier-grade NAT
        if (first == 192 && second == 0 && (b[2] & 0xff) == 0) return true; // 192.0.0.0/24
        if (first == 192 && second == 0 && (b[2] & 0xff) == 2) return true; // 192.0.2.0/24 documentation
        if (first == 198 && (second == 18 || second == 19)) return true; // benchmarking
        if (first == 198 && second == 51 && (b[2] & 0xff) == 100) return true; // documentation
        if (first == 203 && second == 0 && (b[2] & 0xff) == 113) return true;  // documentation
        if (first >= 224) return true;                                // multicast + reserved
        return false;
    }

    private static boolean isForbiddenIpv6(byte[] b) {
        if ((b[0] & 0xfe) == 0xfc) return true;                       // unique local fc00::/7
        if ((b[0] & 0xff) == 0xfe && (b[1] & 0xc0) == 0x80) return true; // link-local fe80::/10
        if ((b[0] & 0xff) == 0xff) return true;                       // multicast
        if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x01
                && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) {
            return true;                                              // documentation 2001:db8::/32
        }
        return false;
    }

    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
    }

    private URI parse(String url) {
        if (url == null || url.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOOL_URL_REQUIRED",
                    "An HTTP tool needs a destination URL");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOOL_URL_INVALID",
                    "The tool destination is not a valid URL");
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOOL_URL_INVALID",
                    "The tool destination must be an absolute URL with a host");
        }
        if (uri.getUserInfo() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOOL_URL_INVALID",
                    "The tool destination must not contain user information");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            if (!"http".equals(scheme) || !properties.isAllowInsecureHttp()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TOOL_URL_INSECURE",
                        "Tools may only call https destinations");
            }
        }
        return uri;
    }

    private static ToolExecutionException blocked() {
        return new ToolExecutionException("TOOL_EGRESS_BLOCKED",
                "The tool destination is not an approved public address");
    }

    /** Visible for tests that need to document the accepted set. */
    List<String> metadataHosts() {
        return METADATA_HOSTS.stream().sorted().toList();
    }
}
