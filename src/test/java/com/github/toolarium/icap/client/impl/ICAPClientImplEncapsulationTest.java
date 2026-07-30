/*
 * ICAPClientImplEncapsulationTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.dto.ICAPServiceInformation;
import com.github.toolarium.icap.client.impl.dto.ICAPRemoteServiceConfigurationImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;


/**
 * Verifies that REQMOD and RESPMOD build the correct Encapsulated body layout,
 * and that ICAPSocket buffers writes to avoid TLS record fragmentation.
 *
 * Both tests use a self-contained in-process mock ICAP server (ServerSocket),
 * so they run without any external docker container.
 *
 * @author patrick
 */
public class ICAPClientImplEncapsulationTest {

    /** Minimal ICAP 204 response that satisfies ChunkedInputStream.readHeader() */
    private static final String ICAP_204_RESPONSE =
            "ICAP/1.0 204 Unmodified\r\n"
            + "Server: mock\r\n"
            + "Encapsulated: null-body=0\r\n\r\n";

    /**
     * Pre-built remote service configuration with default preview size (4096 bytes)
     * and allow-204 disabled. Injecting this avoids an OPTIONS round-trip so the
     * mock server only needs to handle a single REQMOD/RESPMOD connection.
     */
    private static final ICAPRemoteServiceConfiguration DEFAULT_CONFIG =
            new ICAPRemoteServiceConfigurationImpl();


    /**
     * REQMOD must NOT include an HTTP response header block (HTTP/1.1 200 OK)
     * anywhere in the request body. Before the fix, that block was always appended
     * to the req-hdr region, causing strict servers to see no body and return 204
     * without ever scanning the content.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void reqmodBodyDoesNotContainHttpResponseHeaderBlock() throws Exception {
        String captured = captureIcapRequest(ICAPMode.REQMOD);

        assertFalse(captured.contains("HTTP/1.1 200 OK"),
                "REQMOD request body must not contain an HTTP response header block (HTTP/1.1 200 OK)");
    }


    /**
     * REQMOD Encapsulated header must declare req-body and must not declare res-hdr.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void reqmodEncapsulatedHeaderHasNoResHdr() throws Exception {
        String captured = captureIcapRequest(ICAPMode.REQMOD);

        assertTrue(captured.contains("req-body="),
                "REQMOD Encapsulated header must declare req-body");
        assertFalse(captured.contains("res-hdr="),
                "REQMOD Encapsulated header must not declare res-hdr");
    }


    /**
     * The req-body offset declared in the Encapsulated header must equal the byte
     * length of the HTTP request header block — that is, the GET line + Host + Via
     * headers — and must not include any HTTP response headers.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void reqmodEncapsulatedOffsetMatchesRequestHeadersOnly() throws Exception {
        String captured = captureIcapRequest(ICAPMode.REQMOD);

        String encHeader = extractHeaderValue(captured, "Encapsulated");
        assertNotNull(encHeader, "Encapsulated header must be present in REQMOD request");

        int reqBodyOffset = parseOffset(encHeader, "req-body");
        assertTrue(reqBodyOffset > 0, "req-body offset must be positive");

        // Everything after the ICAP headers double-CRLF is the body section.
        int icapHeadersEnd = captured.indexOf("\r\n\r\n") + 4;
        String bodyContent = captured.substring(icapHeadersEnd);

        // The region from 0 to req-body offset is the req-hdr section.
        String reqHdrSection = bodyContent.substring(0, reqBodyOffset);

        assertTrue(reqHdrSection.contains("GET /"),
                "req-hdr section must contain the HTTP request line");
        assertFalse(reqHdrSection.contains("HTTP/1.1 200 OK"),
                "req-hdr section must not contain an HTTP response header block");
    }


    /**
     * RESPMOD must include the HTTP response header block (HTTP/1.1 200 OK) in its
     * body, declared via res-hdr in the Encapsulated header.  This is the unchanged
     * behaviour and the test guards against regression.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void respmodBodyContainsHttpResponseHeaderBlock() throws Exception {
        String captured = captureIcapRequest(ICAPMode.RESPMOD);

        assertTrue(captured.contains("HTTP/1.1 200 OK"),
                "RESPMOD request body must contain the HTTP response header block (HTTP/1.1 200 OK)");
        assertTrue(captured.contains("res-hdr="),
                "RESPMOD Encapsulated header must declare res-hdr");
        assertTrue(captured.contains("res-body="),
                "RESPMOD Encapsulated header must declare res-body");
    }


    /**
     * After a successful validateResource call the ICAPResource must be marked
     * consumed so that a retry loop attempting to reuse it gets an IOException
     * rather than silently sending truncated content.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void resourceIsMarkedConsumedAfterSuccessfulValidation() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5000);
            int port = serverSocket.getLocalPort();

            Thread serverThread = new Thread(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(5000);
                    readUntilPreviewEnd(conn.getInputStream());
                    conn.getOutputStream().write(ICAP_204_RESPONSE.getBytes(StandardCharsets.UTF_8));
                    conn.getOutputStream().flush();
                } catch (Exception e) { /* ignore */ }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ICAPServiceInformation serviceInfo =
                    new ICAPServiceInformation("localhost", port, false, "test", 3600);
            ICAPClientImpl client =
                    new ICAPClientImpl(new ICAPConnectionManagerImpl(), serviceInfo, DEFAULT_CONFIG);

            byte[] content = "Hello ICAP consumed-stream test!".getBytes(StandardCharsets.UTF_8);
            ICAPResource resource = new ICAPResource("test.txt",
                    new ByteArrayInputStream(content), content.length);

            assertFalse(resource.isConsumed(), "Resource must not be consumed before the first call");

            client.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation(), resource);

            assertTrue(resource.isConsumed(), "Resource must be marked consumed after validateResource");

            serverThread.join(5000);
        }
    }


    /**
     * A second validateResource call with the same (consumed) ICAPResource must
     * throw IOException before any network activity, not silently send a truncated body.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void secondValidateCallWithConsumedResourceThrowsIOException() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5000);
            int port = serverSocket.getLocalPort();

            Thread serverThread = new Thread(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(5000);
                    readUntilPreviewEnd(conn.getInputStream());
                    conn.getOutputStream().write(ICAP_204_RESPONSE.getBytes(StandardCharsets.UTF_8));
                    conn.getOutputStream().flush();
                } catch (Exception e) { /* ignore */ }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ICAPServiceInformation serviceInfo =
                    new ICAPServiceInformation("localhost", port, false, "test", 3600);
            ICAPClientImpl client =
                    new ICAPClientImpl(new ICAPConnectionManagerImpl(), serviceInfo, DEFAULT_CONFIG);

            byte[] content = "Hello ICAP retry test!".getBytes(StandardCharsets.UTF_8);
            ICAPResource resource = new ICAPResource("test.txt",
                    new ByteArrayInputStream(content), content.length);

            // First call succeeds
            client.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation(), resource);
            serverThread.join(5000);

            // Second call with the same resource must throw before touching the network
            assertThrows(java.io.IOException.class, () ->
                    client.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation(), resource),
                    "Second validateResource call with a consumed ICAPResource must throw IOException");
        }
    }


    /**
     * ICAPSocket must wrap the raw socket output stream with BufferedOutputStream
     * so that small consecutive writes (ICAP headers, hex chunk size, preview data,
     * terminator) are coalesced into a single TLS record before flush().
     *
     * @throws Exception In case of an error
     */
    @Test
    public void socketOutputStreamIsWrappedInBufferedOutputStream() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(3000);
            int port = serverSocket.getLocalPort();

            Thread acceptThread = new Thread(() -> {
                try (Socket ignored = serverSocket.accept()) {
                    // hold the connection open long enough for the reflection check
                    Thread.sleep(500);
                } catch (Exception e) {
                    // ignore — we only need the connection to exist briefly
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            try (ICAPSocket icapSocket = new ICAPSocket(
                    new ICAPConnectionManagerImpl(), "test- ", "localhost", port, "test", false, 1000, 1000)) {

                java.lang.reflect.Field osField = ICAPSocket.class.getDeclaredField("os");
                osField.setAccessible(true);
                Object os = osField.get(icapSocket);

                assertTrue(os instanceof java.io.BufferedOutputStream,
                        "ICAPSocket.os must be a BufferedOutputStream to coalesce writes; was: "
                        + os.getClass().getName());
            }
        }
    }


    /**
     * Starts a single-connection mock ICAP server, runs validateResource against it
     * with the given mode, and returns the raw bytes the client sent as a UTF-8 string.
     *
     * The mock server responds with ICAP/1.0 204 Unmodified, which causes
     * validateResource to return normally without throwing.
     *
     * A pre-built ICAPRemoteServiceConfiguration is injected into the client so
     * that no OPTIONS exchange is needed — the mock server only ever sees one
     * connection (the REQMOD or RESPMOD request).
     *
     * @param mode the ICAP mode
     * @return the captured raw request
     * @throws Exception In case of an error
     */
    private String captureIcapRequest(ICAPMode mode) throws Exception {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5000);
            int port = serverSocket.getLocalPort();

            Thread serverThread = new Thread(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(5000);
                    String request = readUntilPreviewEnd(conn.getInputStream());
                    queue.put(request);
                    conn.getOutputStream().write(ICAP_204_RESPONSE.getBytes(StandardCharsets.UTF_8));
                    conn.getOutputStream().flush();
                } catch (Exception e) {
                    try {
                        queue.put("ERROR: " + e.getMessage());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ICAPServiceInformation serviceInfo =
                    new ICAPServiceInformation("localhost", port, false, "test", 3600);
            ICAPClientImpl client =
                    new ICAPClientImpl(new ICAPConnectionManagerImpl(), serviceInfo, DEFAULT_CONFIG);

            byte[] content = "Hello ICAP encapsulation test!".getBytes(StandardCharsets.UTF_8);
            client.validateResource(
                    mode,
                    new ICAPRequestInformation(),
                    new ICAPResource("test.txt",
                            new ByteArrayInputStream(content), content.length));

            serverThread.join(5000);

            String captured = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull(captured, "Mock server did not receive a request within 5 seconds");
            assertFalse(captured.startsWith("ERROR:"), "Mock server error: " + captured);
            return captured;
        }
    }


    /**
     * Reads bytes from the stream until the end-of-preview sentinel is found.
     *
     * When the entire resource fits inside the preview window (which is always true
     * for our 30-byte test content against the default 4096-byte preview), the client
     * terminates the preview phase with {@code 0; ieof\r\n\r\n}.
     *
     * @param in the input stream
     * @return the captured bytes as a UTF-8 string
     * @throws IOException In case of an I/O error
     */
    private String readUntilPreviewEnd(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] sentinel = "0; ieof\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            if (endsWith(buf.toByteArray(), sentinel)) {
                break;
            }
        }
        return buf.toString(StandardCharsets.UTF_8.name());
    }


    private boolean endsWith(byte[] data, byte[] suffix) {
        if (data.length < suffix.length) {
            return false;
        }
        int offset = data.length - suffix.length;
        for (int i = 0; i < suffix.length; i++) {
            if (data[offset + i] != suffix[i]) {
                return false;
            }
        }
        return true;
    }


    /**
     * Extracts the value of a named header from a raw HTTP/ICAP request string.
     *
     * @param request the raw request string
     * @param headerName the header name
     * @return the header value, or null if not found
     */
    private String extractHeaderValue(String request, String headerName) {
        String lowerRequest = request.toLowerCase();
        String searchKey = headerName.toLowerCase() + ":";
        int idx = lowerRequest.indexOf(searchKey);
        if (idx < 0) {
            return null;
        }
        int start = idx + searchKey.length();
        int end = request.indexOf("\r\n", start);
        if (end <= start) {
            return null;
        }
        return request.substring(start, end).trim();
    }


    /**
     * Parses the integer offset for a named key from an Encapsulated header value.
     * For example, given {@code "req-hdr=0, req-body=47"} and key {@code "req-body"},
     * returns {@code 47}.
     *
     * @param encHeader the Encapsulated header value
     * @param key the key to look up
     * @return the offset, or -1 if not found
     */
    private int parseOffset(String encHeader, String key) {
        String lower = encHeader.toLowerCase();
        String search = key.toLowerCase() + "=";
        int idx = lower.indexOf(search);
        if (idx < 0) {
            return -1;
        }
        int start = idx + search.length();
        int end = start;
        while (end < encHeader.length() && Character.isDigit(encHeader.charAt(end))) {
            end++;
        }
        if (start == end) {
            return -1;
        }
        return Integer.parseInt(encHeader.substring(start, end));
    }
}
