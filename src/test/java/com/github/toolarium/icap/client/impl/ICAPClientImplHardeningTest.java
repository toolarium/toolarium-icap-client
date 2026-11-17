/*
 * ICAPClientImplHardeningTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.dto.ICAPServiceInformation;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import com.github.toolarium.icap.client.impl.dto.ICAPRemoteServiceConfigurationImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;


/**
 * Tests the hardening of the ICAP client against protocol edge cases. All tests use an in-process mock ICAP server.
 *
 * @author patrick
 */
public class ICAPClientImplHardeningTest {
    private static final String ICAP_204_RESPONSE = "ICAP/1.0 204 Unmodified\r\nServer: mock\r\nEncapsulated: null-body=0\r\n\r\n";
    private static final String IEOF_SENTINEL = "0; ieof\r\n\r\n";
    private static final ICAPMode[] METHODS = new ICAPMode[] {ICAPMode.REQMOD, ICAPMode.RESPMOD};
    private static final String LOCALHOST = "localhost";
    private static final String SERVICE_NAME = "test";
    private static final String RESOURCE_NAME = "test.txt";
    private com.github.toolarium.icap.client.dto.ICAPHeaderInformation lastResult;


    /**
     * Server threat headers in lower case must be detected (header names are case-insensitive).
     */
    @Test
    public void lowerCaseThreatHeaderIsBlocked() {
        String response = "ICAP/1.0 200 OK\r\nserver: mock\r\nx-virus-id: EICAR-Test-File\r\nencapsulated: null-body=0\r\n\r\n";
        assertThrows(ContentBlockedException.class, () ->
            runRequest(ICAPMode.REQMOD, new ICAPRequestInformation(), content("clean"), 4096, IEOF_SENTINEL, response));
    }


    /**
     * A REQMOD response which contains an HTTP response (e.g. block page) is blocked even without threat headers.
     */
    @Test
    public void reqmodResponseWithResponseHeaderIsBlocked() {
        String httpResponse = "HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\n\r\n";
        String response = "ICAP/1.0 200 OK\r\nServer: mock\r\nEncapsulated: res-hdr=0, res-body=" + httpResponse.length() + "\r\n\r\n"
                          + httpResponse + "7\r\nblocked\r\n0\r\n\r\n";
        assertThrows(ContentBlockedException.class, () ->
            runRequest(ICAPMode.REQMOD, new ICAPRequestInformation(), content("clean"), 4096, IEOF_SENTINEL, response));
    }


    /**
     * The Encapsulated offsets are byte offsets, also with non ASCII characters in the encapsulated HTTP header.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void encapsulatedOffsetIsByteLength() throws Exception {
        ICAPRequestInformation requestInformation = new ICAPRequestInformation("user", "Zürich-Server-ä");
        byte[] request = runRequest(ICAPMode.RESPMOD, requestInformation, content("Hello"), 4096, IEOF_SENTINEL, ICAP_204_RESPONSE);

        String text = new String(request, StandardCharsets.ISO_8859_1);
        int icapHeaderEnd = text.indexOf("\r\n\r\n") + 4;
        String encapsulated = extractHeaderValue(text, "Encapsulated");
        int resHdr = parseOffset(encapsulated, "res-hdr");
        int resBody = parseOffset(encapsulated, "res-body");

        String atResHdr = new String(request, icapHeaderEnd + resHdr, 15, StandardCharsets.ISO_8859_1);
        assertTrue(atResHdr.startsWith("HTTP/1.1 200 OK"), "res-hdr offset must point to the HTTP response line but was: " + atResHdr);

        String atResBody = new String(request, icapHeaderEnd + resBody, 3, StandardCharsets.ISO_8859_1);
        assertEquals("5\r\n", atResBody, "res-body offset must point to the first chunk");
    }


    /**
     * A server preview size of 0 must flush the request so the server can answer.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void previewZeroIsFlushed() throws Exception {
        byte[] request = runRequest(ICAPMode.REQMOD, new ICAPRequestInformation().maxReadTimeout(3000), content("0123456789"), 0,
                                    "\r\n\r\n0\r\n\r\n", ICAP_204_RESPONSE);
        String text = new String(request, StandardCharsets.UTF_8);
        assertTrue(text.contains("Preview: 0\r\n"));
    }


    /**
     * A stream which is shorter than the declared length sends the available content as complete preview.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void shortStreamIsSentAsCompletePreview() throws Exception {
        byte[] data = "0123456789".getBytes(StandardCharsets.UTF_8);
        ICAPResource resource = new ICAPResource(RESOURCE_NAME, new ByteArrayInputStream(data), 100);
        byte[] request = runRequest(ICAPMode.REQMOD, new ICAPRequestInformation(), resource, 4096, IEOF_SENTINEL, ICAP_204_RESPONSE);

        String text = new String(request, StandardCharsets.UTF_8);
        assertTrue(text.contains("Preview: 10\r\n"));
        assertTrue(text.endsWith("a\r\n0123456789\r\n0; ieof\r\n\r\n"));
    }


    /**
     * A stream which returns less bytes per read than requested must still send the full preview.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void slowStreamSendsFullPreview() throws Exception {
        byte[] data = "0123456789".getBytes(StandardCharsets.UTF_8);
        InputStream slowStream = new ByteArrayInputStream(data) {
            /**
             * @see java.io.ByteArrayInputStream#read(byte[], int, int)
             */
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, 3));
            }
        };

        byte[] request = runRequest(ICAPMode.REQMOD, new ICAPRequestInformation(), new ICAPResource(RESOURCE_NAME, slowStream, data.length), 4096, IEOF_SENTINEL, ICAP_204_RESPONSE);
        String text = new String(request, StandardCharsets.UTF_8);
        assertTrue(text.endsWith("a\r\n0123456789\r\n0; ieof\r\n\r\n"));
    }


    /**
     * A resource with length 0 but content is scanned as content with unknown length.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void zeroLengthResourceWithContentIsScanned() throws Exception {
        ICAPResource resource = new ICAPResource(RESOURCE_NAME, new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)), 0);
        byte[] request = runRequest(ICAPMode.REQMOD, new ICAPRequestInformation(), resource, 4096, IEOF_SENTINEL, ICAP_204_RESPONSE);
        assertTrue(new String(request, StandardCharsets.UTF_8).endsWith("4\r\ndata\r\n0; ieof\r\n\r\n"));
    }


    /**
     * A resource with length 0 and an empty stream returns an empty result without any request.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void zeroLengthEmptyResourceIsNotSent() throws Exception {
        ICAPClientImpl client = new ICAPClientImpl(new ICAPConnectionManagerImpl(), new ICAPServiceInformation(LOCALHOST, 1, false, SERVICE_NAME, 3600), createConfig(4096));
        assertEquals(0, client.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation(), new ICAPResource(RESOURCE_NAME, new ByteArrayInputStream(new byte[0]), 0)).getStatus());
    }


    /**
     * A resource can only be claimed once at a time; an unread resource can be claimed again.
     */
    @Test
    public void resourceAcquireIsExclusive() {
        ICAPResource resource = content("data");
        assertTrue(resource.acquire());
        assertFalse(resource.acquire());

        resource.release();
        assertTrue(resource.acquire());

        resource.markConsumed();
        resource.release();
        assertTrue(resource.isConsumed());
        assertFalse(resource.acquire());
    }


    /**
     * The server Max-Connections limits only the pool of the server.
     */
    @Test
    public void serverMaxConnectionsLimitsOnlyItsHost() {
        ICAPConnectionPool pool = new ICAPConnectionPool(5, 60000);
        pool.setServerMaxConnections("host-a", 1344, false, 2);

        assertEquals(2, pool.getEffectiveMaxConnections("host-a", 1344, false));
        assertEquals(5, pool.getEffectiveMaxConnections("host-b", 1344, false));

        pool.setServerMaxConnections("host-a", 1344, false, 0);
        assertEquals(5, pool.getEffectiveMaxConnections("host-a", 1344, false));
    }


    /**
     * The cached remote configuration can not be modified.
     */
    @Test
    public void remoteConfigurationHeadersAreUnmodifiable() {
        ICAPRemoteServiceConfiguration config = new ICAPRemoteServiceConfigurationImpl(Instant.now(), METHODS, 4096, false,
                                                                                        new java.util.HashMap<>(java.util.Map.of("Preview", Arrays.asList("1024"))));
        assertEquals("1024", config.getHeaders().get("preview").get(0));
        assertThrows(UnsupportedOperationException.class, () -> config.getHeaders().put("X-Test", Arrays.asList("x")));
        assertThrows(UnsupportedOperationException.class, () -> config.getHeaders().get("Preview").add("x"));
    }


    /**
     * A modified response content is marked as not identical if the content comparison is enabled.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void modifiedContentIsMarkedNotIdenticalWithCompare() throws Exception {
        String httpResponse = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\n";
        String response = "ICAP/1.0 200 OK\r\nServer: mock\r\nEncapsulated: res-hdr=0, res-body=" + httpResponse.length() + "\r\n\r\n"
                          + httpResponse + "5\r\nHallo\r\n0\r\n\r\n";
        runRequest(ICAPMode.RESPMOD, new ICAPRequestInformation(), content("Hello"), 4096, IEOF_SENTINEL, response, true);
        assertEquals("[false]", "" + lastResult.getHeaderValues(com.github.toolarium.icap.client.dto.ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT));
    }


    /**
     * A RESPMOD response which replaces the HTTP response by an error (e.g. block page) is blocked even without threat headers.
     */
    @Test
    public void respmodBlockPageIsBlocked() {
        String httpResponse = "HTTP/1.1 403 Forbidden\r\nContent-Length: 7\r\n\r\n";
        String response = "ICAP/1.0 200 OK\r\nServer: mock\r\nEncapsulated: res-hdr=0, res-body=" + httpResponse.length() + "\r\n\r\n"
                          + httpResponse + "7\r\nblocked\r\n0\r\n\r\n";
        assertThrows(ContentBlockedException.class, () ->
            runRequest(ICAPMode.RESPMOD, new ICAPRequestInformation(), content("Hello"), 4096, IEOF_SENTINEL, response));
    }


    /**
     * A stream which is longer than the declared length is sent and scanned completely.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void streamLongerThanDeclaredLengthIsScanned() throws Exception {
        byte[] data = "0123456789".getBytes(StandardCharsets.UTF_8);
        ICAPResource resource = new ICAPResource(RESOURCE_NAME, new ByteArrayInputStream(data), 5);
        BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        byte[] terminator = "\r\n0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5000);
            Thread serverThread = new Thread(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(5000);
                    queue.put(readUntil(conn.getInputStream(), terminator));
                    conn.getOutputStream().write("ICAP/1.0 100 Continue\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    conn.getOutputStream().flush();
                    queue.put(readUntil(conn.getInputStream(), terminator));
                    conn.getOutputStream().write(ICAP_204_RESPONSE.getBytes(StandardCharsets.UTF_8));
                    conn.getOutputStream().flush();
                } catch (Exception e) {
                    // NOP
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ICAPServiceInformation serviceInfo = new ICAPServiceInformation(LOCALHOST, serverSocket.getLocalPort(), false, SERVICE_NAME, 3600);
            ICAPClientImpl client = new ICAPClientImpl(new ICAPConnectionManagerImpl(), serviceInfo, createConfig(4096));
            assertEquals(204, client.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation().maxReadTimeout(5000), resource).getStatus());
            serverThread.join(5000);

            String preview = new String(queue.poll(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            String remaining = new String(queue.poll(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            assertTrue(preview.endsWith("5\r\n01234\r\n0\r\n\r\n"), "Preview: " + preview);
            assertEquals("5\r\n56789\r\n0\r\n\r\n", remaining);
        }
    }


    /**
     * An invalid api version or service name must not reach the ICAP request line.
     */
    @Test
    public void invalidRequestLineValuesAreRejected() {
        ICAPClientImpl client = new ICAPClientImpl(new ICAPConnectionManagerImpl(), new ICAPServiceInformation(LOCALHOST, 1, false, SERVICE_NAME, 3600), createConfig(4096));
        assertThrows(IOException.class, () ->
            client.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation().setApiVersion("1.0\r\nX-Injected: true"), content("data")));

        ICAPClientImpl invalidServiceClient = new ICAPClientImpl(new ICAPConnectionManagerImpl(), new ICAPServiceInformation(LOCALHOST, 1, false, "srv clamav", 3600), createConfig(4096));
        assertThrows(IOException.class, () ->
            invalidServiceClient.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation(), content("data")));
    }


    /**
     * A write to a server which does not read is aborted when the max request timeout is exceeded.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void stalledWriteTimesOut() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReceiveBufferSize(4096);
            serverSocket.bind(new java.net.InetSocketAddress(LOCALHOST, 0));
            Thread serverThread = new Thread(() -> {
                try (Socket conn = serverSocket.accept()) {
                    // accept but never read
                    Thread.sleep(20000);
                } catch (Exception e) {
                    // NOP
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            byte[] data = new byte[1024 * 1024];
            ICAPServiceInformation serviceInfo = new ICAPServiceInformation(LOCALHOST, serverSocket.getLocalPort(), false, SERVICE_NAME, 3600);
            ICAPClientImpl client = new ICAPClientImpl(new ICAPConnectionManagerImpl(), serviceInfo, createConfig(1024 * 1024));

            long start = System.currentTimeMillis();
            assertThrows(java.net.SocketTimeoutException.class, () ->
                client.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation().maxRequestTimeout(2000), new ICAPResource("test.bin", new ByteArrayInputStream(data), data.length)));
            assertTrue(System.currentTimeMillis() - start < 15000, "Stalled write must be aborted");
        }
    }


    /**
     * A pooled connection which was closed by the server is replaced by a new connection.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void closedPooledConnectionIsRetried() throws Exception {
        String keepAliveResponse = "ICAP/1.0 204 Unmodified\r\nServer: mock\r\nConnection: keep-alive\r\nEncapsulated: null-body=0\r\n\r\n";
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(10000);
            Thread serverThread = new Thread(() -> {
                for (int i = 0; i < 2; i++) {
                    try (Socket conn = serverSocket.accept()) {
                        conn.setSoTimeout(5000);
                        readUntil(conn.getInputStream(), IEOF_SENTINEL.getBytes(StandardCharsets.UTF_8));
                        conn.getOutputStream().write(keepAliveResponse.getBytes(StandardCharsets.UTF_8));
                        conn.getOutputStream().flush();
                        // the server closes the connection although it announced keep-alive
                    } catch (Exception e) {
                        // NOP
                    }
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ICAPConnectionManagerImpl connectionManager = new ICAPConnectionManagerImpl();
            connectionManager.setMaxPoolConnectionsPerHost(2);
            ICAPServiceInformation serviceInfo = new ICAPServiceInformation(LOCALHOST, serverSocket.getLocalPort(), false, SERVICE_NAME, 3600);
            ICAPClientImpl client = new ICAPClientImpl(connectionManager, serviceInfo, createConfig(4096));

            client.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation().maxReadTimeout(5000), content("first"));
            Thread.sleep(200);
            assertEquals(204, client.validateResource(ICAPMode.REQMOD, new ICAPRequestInformation().maxReadTimeout(5000), content("second")).getStatus());
            serverThread.join(5000);
            connectionManager.closePool();
        }
    }


    /**
     * A status line without reason phrase is parsed.
     */
    @Test
    public void statusLineWithoutReasonPhrase() {
        assertEquals(204, com.github.toolarium.icap.client.impl.parser.ICAPParser.getInstance().parseICAPHeaderInformation("ICAP/1.0 204").getStatus());
    }


    /**
     * The remote configuration is read-only after it was frozen.
     */
    @Test
    public void frozenConfigurationIsReadOnly() {
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl().freeze();
        assertThrows(IllegalStateException.class, () -> config.setOptionsTTL(10));
    }


    /**
     * The header map methods with a mapping function are case-insensitive.
     */
    @Test
    public void headerMapComputeIsCaseInsensitive() {
        com.github.toolarium.icap.client.dto.ICAPHeaderMap map = new com.github.toolarium.icap.client.dto.ICAPHeaderMap();
        map.put("Server", Arrays.asList("a"));
        map.computeIfAbsent("server", k -> Arrays.asList("b"));
        map.putIfAbsent("SERVER", Arrays.asList("c"));
        map.merge("server", Arrays.asList("d"), (a, b) -> b);

        assertEquals(1, map.size());
        assertEquals("d", map.get("Server").get(0));
    }


    /**
     * A resource can be serialized, the stream is not part of it.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void resourceIsSerializable() throws Exception {
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(content("data"));
        }
    }


    /**
     * Create a test resource
     *
     * @param text the content
     * @return the resource
     */
    private ICAPResource content(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        return new ICAPResource(RESOURCE_NAME, new ByteArrayInputStream(data), data.length);
    }


    /**
     * Create a remote service configuration
     *
     * @param previewSize the preview size
     * @return the configuration
     */
    private ICAPRemoteServiceConfiguration createConfig(int previewSize) {
        return new ICAPRemoteServiceConfigurationImpl(Instant.now(), METHODS, previewSize, false, null);
    }


    /**
     * Run a request against a single-connection mock server and return the raw request.
     *
     * @param mode the mode
     * @param requestInformation the request information
     * @param resource the resource
     * @param previewSize the server preview size
     * @param sentinel the end of the request
     * @param response the response of the mock server
     * @return the raw request
     * @throws Exception In case of an error
     */
    private byte[] runRequest(ICAPMode mode, ICAPRequestInformation requestInformation, ICAPResource resource, int previewSize, String sentinel, String response) throws Exception {
        return runRequest(mode, requestInformation, resource, previewSize, sentinel, response, false);
    }


    /**
     * Run a request against a single-connection mock server and return the raw request.
     *
     * @param mode the mode
     * @param requestInformation the request information
     * @param resource the resource
     * @param previewSize the server preview size
     * @param sentinel the end of the request
     * @param response the response of the mock server
     * @param compareContent true to compare the request and response content
     * @return the raw request
     * @throws Exception In case of an error
     */
    private byte[] runRequest(ICAPMode mode, ICAPRequestInformation requestInformation, ICAPResource resource, int previewSize, String sentinel, String response, boolean compareContent) throws Exception {
        BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setSoTimeout(5000);
            Thread serverThread = new Thread(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(5000);
                    queue.put(readUntil(conn.getInputStream(), sentinel.getBytes(StandardCharsets.UTF_8)));
                    conn.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                    conn.getOutputStream().flush();
                } catch (Exception e) {
                    // NOP
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ICAPServiceInformation serviceInfo = new ICAPServiceInformation(LOCALHOST, serverSocket.getLocalPort(), false, SERVICE_NAME, 3600);
            ICAPClientImpl client = new ICAPClientImpl(new ICAPConnectionManagerImpl(), serviceInfo, createConfig(previewSize));
            client.supportCompareVerifyIdenticalContent(compareContent);
            lastResult = client.validateResource(mode, requestInformation, resource);
            serverThread.join(5000);

            byte[] request = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull(request, "Mock server did not receive a request");
            return request;
        }
    }


    /**
     * Read until a sentinel was received
     *
     * @param in the input stream
     * @param sentinel the sentinel
     * @return the read bytes
     * @throws IOException In case of an I/O error
     */
    private byte[] readUntil(InputStream in, byte[] sentinel) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            byte[] data = buf.toByteArray();
            if (data.length >= sentinel.length
                    && Arrays.equals(Arrays.copyOfRange(data, data.length - sentinel.length, data.length), sentinel)) {
                break;
            }
        }
        return buf.toByteArray();
    }


    /**
     * Extracts the value of a header
     *
     * @param request the raw request
     * @param headerName the header name
     * @return the header value
     */
    private String extractHeaderValue(String request, String headerName) {
        int idx = request.indexOf(headerName + ":");
        int start = idx + headerName.length() + 1;
        return request.substring(start, request.indexOf("\r\n", start)).trim();
    }


    /**
     * Parse an offset of the encapsulated header
     *
     * @param encHeader the encapsulated header value
     * @param key the key
     * @return the offset
     */
    private int parseOffset(String encHeader, String key) {
        for (String entry : encHeader.split(",")) {
            String[] split = entry.trim().split("=");
            if (split[0].equals(key)) {
                return Integer.parseInt(split[1]);
            }
        }
        return fail("Missing encapsulated entry [" + key + "] in [" + encHeader + "]");
    }
}
