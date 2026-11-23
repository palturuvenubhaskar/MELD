package com.meld.tool;

import org.springframework.stereotype.Component;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal HTTP/1.1 GET client that connects to the exact address approved by
 * {@link ToolEgressGuard}. No proxy, no automatic redirects, no connection
 * reuse; every redirect target is resolved and validated again.
 */
@Component
public class PinnedHttpGet {

    private static final int MAX_LINE_BYTES = 8192;

    private final ToolEgressGuard guard;
    private final ToolProperties properties;

    public PinnedHttpGet(ToolEgressGuard guard, ToolProperties properties) {
        this.guard = guard;
        this.properties = properties;
    }

    public record HttpResult(int status, String contentType, String body, String location) {}

    public HttpResult get(String url) {
        String currentUrl = url == null ? "" : url.trim();
        int redirects = 0;
        while (true) {
            ToolEgressGuard.PinnedTarget target = guard.resolve(currentUrl);
            HttpResult result = execute(target);
            if (isRedirect(result.status()) && redirects < properties.getMaxRedirects()) {
                URI next = redirectTarget(target.uri(), result);
                if (next == null) {
                    throw new ToolExecutionException("TOOL_BAD_RESPONSE",
                            "The tool destination returned a redirect without a location");
                }
                currentUrl = next.toString();
                redirects++;
                continue;
            }
            if (isRedirect(result.status())) {
                throw new ToolExecutionException("TOOL_REDIRECT_LIMIT",
                        "The tool destination redirected too many times");
            }
            if (result.status() < 200 || result.status() >= 300) {
                throw new ToolExecutionException("TOOL_HTTP_ERROR",
                        "The tool destination returned HTTP " + result.status());
            }
            return result;
        }
    }

    private HttpResult execute(ToolEgressGuard.PinnedTarget target) {
        Socket raw = new Socket();
        try {
            raw.connect(new InetSocketAddress(target.address(), target.port()),
                    (int) properties.getConnectTimeout().toMillis());
            raw.setSoTimeout((int) properties.getReadTimeout().toMillis());

            Socket socket = raw;
            if ("https".equalsIgnoreCase(target.uri().getScheme())) {
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(raw, target.host(), target.port(), true);
                SSLParameters params = ssl.getSSLParameters();
                if (!isIpLiteral(target.host())) {
                    params.setServerNames(List.of(new SNIHostName(target.host())));
                }
                params.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(params);
                ssl.startHandshake();
                socket = ssl;
            }

            writeRequest(socket.getOutputStream(), target);
            return readResponse(new BufferedInputStream(socket.getInputStream()));
        } catch (SocketTimeoutException ex) {
            throw new ToolExecutionException("TOOL_TIMEOUT", "The tool destination did not respond in time");
        } catch (ToolExecutionException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ToolExecutionException("TOOL_CONNECTION_FAILED", "The tool destination could not be reached");
        } finally {
            try {
                raw.close();
            } catch (IOException ignored) {
                // Best effort.
            }
        }
    }

    private void writeRequest(OutputStream out, ToolEgressGuard.PinnedTarget target) throws IOException {
        URI uri = target.uri();
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path = path + "?" + uri.getRawQuery();
        }
        boolean defaultPort = ("https".equalsIgnoreCase(uri.getScheme()) && target.port() == 443)
                || ("http".equalsIgnoreCase(uri.getScheme()) && target.port() == 80);
        String hostHeader = defaultPort ? target.host() : target.host() + ":" + target.port();

        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "Accept: application/json, text/plain;q=0.9, */*;q=0.1\r\n"
                + "User-Agent: MELD-Tool/1.0\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private HttpResult readResponse(InputStream in) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null) {
            throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool destination returned no response");
        }
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
            throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool destination returned an invalid response");
        }
        int status;
        try {
            status = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool destination returned an invalid status");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        int headerBytes = 0;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            headerBytes += line.length();
            if (headerBytes > MAX_LINE_BYTES) {
                throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool destination sent oversized headers");
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.merge(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim(), (a, b) -> a + ", " + b);
            }
        }

        byte[] body;
        if (status == 204 || status == 304) {
            body = new byte[0];
        } else if (headers.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT).contains("chunked")) {
            body = readChunked(in);
        } else if (headers.containsKey("content-length")) {
            body = readFixed(in, parseLength(headers.get("content-length")));
        } else {
            body = readToEnd(in);
        }

        return new HttpResult(status, headers.get("content-type"),
                new String(body, StandardCharsets.UTF_8), headers.get("location"));
    }

    private byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) {
                throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool response ended early");
            }
            String sizeText = sizeLine.split(";", 2)[0].trim();
            int size;
            try {
                size = Integer.parseInt(sizeText, 16);
            } catch (NumberFormatException ex) {
                throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool response used an invalid chunk size");
            }
            if (size == 0) {
                // Consume trailers.
                String trailer;
                while ((trailer = readLine(in)) != null && !trailer.isEmpty()) {
                    // Ignore trailer fields.
                }
                return out.toByteArray();
            }
            if (out.size() + size > properties.getMaxResponseBytes()) {
                throw tooLarge();
            }
            copy(in, out, size);
            String crlf = readLine(in);
            if (crlf == null || !crlf.isEmpty()) {
                throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool response used invalid chunk framing");
            }
        }
    }

    private byte[] readFixed(InputStream in, int length) throws IOException {
        if (length > properties.getMaxResponseBytes()) {
            throw tooLarge();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        copy(in, out, length);
        return out.toByteArray();
    }

    private byte[] readToEnd(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > properties.getMaxResponseBytes()) {
                throw tooLarge();
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void copy(InputStream in, ByteArrayOutputStream out, int length) throws IOException {
        byte[] buffer = new byte[8192];
        int remaining = length;
        while (remaining > 0) {
            int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) {
                throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool response ended early");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static int parseLength(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool response used an invalid content length");
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = in.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                byte[] bytes = out.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1);
            }
            out.write(current);
            previous = current;
            if (out.size() > MAX_LINE_BYTES) {
                throw new ToolExecutionException("TOOL_BAD_RESPONSE", "The tool response sent an oversized header line");
            }
        }
        return out.size() == 0 ? null : out.toString(StandardCharsets.ISO_8859_1);
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private URI redirectTarget(URI current, HttpResult result) {
        if (result.location() == null || result.location().isBlank()) {
            return null;
        }
        try {
            return current.resolve(result.location().trim());
        } catch (IllegalArgumentException ex) {
            throw new ToolExecutionException("TOOL_BAD_RESPONSE",
                    "The tool destination returned an invalid redirect location");
        }
    }

    private static ToolExecutionException tooLarge() {
        return new ToolExecutionException("TOOL_RESPONSE_TOO_LARGE",
                "The tool response exceeded the configured size limit");
    }
}
