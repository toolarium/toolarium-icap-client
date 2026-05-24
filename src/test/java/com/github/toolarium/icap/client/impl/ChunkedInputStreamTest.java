/*
 * ChunkedInputStreamTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ChunkedInputStream}.
 *
 * <p>Addresses issue #12: duplicate readHeader() and chunk extension handling.
 *
 * @author patrick
 */
public class ChunkedInputStreamTest {
    private static final String CRLF = "\r\n";
    private static final String REQUEST_ID = "test - ";


    /**
     * Test reading headers from a simple ICAP response.
     *
     * @throws IOException In case of an I/O error
     */
    @Test
    public void readSimpleHeaders() throws IOException {
        String data = "ICAP/1.0 200 OK" + CRLF
                + "Server: C-ICAP/0.4.4" + CRLF
                + "Connection: keep-alive" + CRLF
                + CRLF;

        try (ChunkedInputStream cis = createStream(data)) {
            Map<String, List<String>> headers = cis.readHeader();
            assertNotNull(headers);
            assertTrue(headers.containsKey("Server"));
            assertEquals("C-ICAP/0.4.4", headers.get("Server").get(0));
        }
    }


    /**
     * Issue #12 - Bug 1: nextChunk must call readHeader() only once for GET/POST lines.
     *
     * <p>Per RFC 3507 Section 4.8.3, a REQMOD response may encapsulate a request line,
     * request headers and request body without response headers. With the duplicate
     * readHeader() bug, the second call consumed body data as headers.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void nextChunkReqmodSingleReadHeader() throws Exception {
        // After ICAP headers are read, the stream contains: GET request + headers + chunk size
        String remaining = "GET /resource HTTP/1.1" + CRLF
                + "Host: server.com" + CRLF
                + CRLF
                + "11" + CRLF;

        try (ChunkedInputStream cis = createStream(remaining)) {
            int chunkSize = invokeNextChunk(cis);
            // With the fix: readHeader reads request headers, then "11" (hex 17) is parsed as chunk size
            // With the bug: readHeader called twice, "11" consumed as header, wrong result
            assertEquals(17, chunkSize, "Chunk size 0x11 = 17 should be parsed correctly after single readHeader");
        }
    }


    /**
     * Issue #12 - Bug 2: Chunk extension with semicolon and ieof marker.
     *
     * <p>The ICAP protocol uses "0; ieof" to indicate end of preview. The parser
     * must strip everything from the first semicolon before parsing as hex.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void nextChunkWithIeofExtension() throws Exception {
        String remaining = "0; ieof" + CRLF;

        try (ChunkedInputStream cis = createStream(remaining)) {
            int chunkSize = invokeNextChunk(cis);
            assertEquals(0, chunkSize, "Chunk size should be 0 after stripping '; ieof' extension");
        }
    }


    /**
     * Issue #12 - Bug 2: Chunk extension with key=value format.
     *
     * <p>RFC 2616 Section 3.6.1 allows: chunk-size ";" chunk-ext-name "=" chunk-ext-val.
     * Example: "1a;ext=value" means chunk size 0x1a (26).
     *
     * @throws Exception In case of an error
     */
    @Test
    public void nextChunkWithKeyValueExtension() throws Exception {
        String remaining = "1a;ext=value" + CRLF;

        try (ChunkedInputStream cis = createStream(remaining)) {
            int chunkSize = invokeNextChunk(cis);
            assertEquals(26, chunkSize, "Chunk size 0x1a = 26 after stripping ';ext=value'");
        }
    }


    /**
     * Issue #12 - Bug 2: Chunk extension with trailing semicolon only.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void nextChunkWithTrailingSemicolon() throws Exception {
        String remaining = "5;" + CRLF;

        try (ChunkedInputStream cis = createStream(remaining)) {
            int chunkSize = invokeNextChunk(cis);
            assertEquals(5, chunkSize, "Chunk size should be 5 after stripping trailing semicolon");
        }
    }


    /**
     * Plain chunk size without extension still works.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void nextChunkPlainHexSize() throws Exception {
        String remaining = "ff" + CRLF;

        try (ChunkedInputStream cis = createStream(remaining)) {
            int chunkSize = invokeNextChunk(cis);
            assertEquals(255, chunkSize, "Chunk size 0xff = 255");
        }
    }


    /**
     * RESPMOD: GET + request headers + HTTP response headers should read both header sections.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void nextChunkRespmodDoubleHeaders() throws Exception {
        String remaining = "GET /resource HTTP/1.1" + CRLF
                + "Host: server.com" + CRLF
                + CRLF
                + "HTTP/1.1 200 OK" + CRLF
                + "Transfer-Encoding: chunked" + CRLF
                + CRLF
                + "a" + CRLF;

        try (ChunkedInputStream cis = createStream(remaining)) {
            int chunkSize = invokeNextChunk(cis);
            assertEquals(10, chunkSize, "Chunk size 0xa = 10 after reading both req and res headers");
        }
    }


    /**
     * Test trailer headers are read after zero-length terminating chunk (RFC 3507 §4.3.1).
     *
     * @throws Exception In case of an error
     */
    @Test
    public void readTrailersAfterZeroChunk() throws Exception {
        String remaining = "0" + CRLF
                + "X-Checksum: abc123" + CRLF
                + "X-Scan-Time: 42ms" + CRLF
                + CRLF;

        try (ChunkedInputStream cis = createStream(remaining)) {
            int chunkSize = invokeNextChunk(cis);
            assertEquals(0, chunkSize);
            assertNotNull(cis.getTrailers());
            assertTrue(cis.getTrailers().containsKey("X-Checksum"));
            assertEquals("abc123", cis.getTrailers().get("X-Checksum").get(0));
            assertTrue(cis.getTrailers().containsKey("X-Scan-Time"));
            assertEquals("42ms", cis.getTrailers().get("X-Scan-Time").get(0));
        }
    }


    /**
     * Test no trailers after zero-length chunk.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void noTrailersAfterZeroChunk() throws Exception {
        String remaining = "0" + CRLF + CRLF;

        try (ChunkedInputStream cis = createStream(remaining)) {
            int chunkSize = invokeNextChunk(cis);
            assertEquals(0, chunkSize);
            // No trailers should be null
            assertNull(cis.getTrailers());
        }
    }


    /**
     * Test trailers with ieof extension.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void readTrailersAfterIeofChunk() throws Exception {
        String remaining = "0; ieof" + CRLF
                + "X-Trail: value" + CRLF
                + CRLF;

        try (ChunkedInputStream cis = createStream(remaining)) {
            int chunkSize = invokeNextChunk(cis);
            assertEquals(0, chunkSize);
            assertNotNull(cis.getTrailers());
            assertTrue(cis.getTrailers().containsKey("X-Trail"));
        }
    }


    /**
     * Test that null input stream throws IOException.
     */
    @Test
    public void nullInputStreamThrows() {
        assertThrows(IOException.class, () -> {
            new ChunkedInputStream(REQUEST_ID, null);
        });
    }


    /**
     * Create a ChunkedInputStream from a string.
     *
     * @param data the string data
     * @return the stream
     * @throws IOException In case of an I/O error
     */
    private ChunkedInputStream createStream(String data) throws IOException {
        return new ChunkedInputStream(REQUEST_ID, new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
    }


    /**
     * Invoke the protected nextChunk() method via reflection.
     *
     * @param cis the chunked input stream
     * @return the chunk size
     * @throws Exception In case of an error
     */
    private int invokeNextChunk(ChunkedInputStream cis) throws Exception {
        Method method = ChunkedInputStream.class.getDeclaredMethod("nextChunk");
        method.setAccessible(true);
        return (int) method.invoke(cis);
    }
}
