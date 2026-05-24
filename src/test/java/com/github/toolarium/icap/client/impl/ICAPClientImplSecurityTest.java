/*
 * ICAPClientImplSecurityTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPServiceInformation;
import com.github.toolarium.icap.client.exception.ICAPRequestException;
import com.github.toolarium.icap.client.exception.UnknownIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;


/**
 * Tests for security fixes in {@link ICAPClientImpl}.
 *
 * @author patrick
 */
public class ICAPClientImplSecurityTest {

    /**
     * Test sanitizeHeaderValue strips CR and LF (S1/S2 fix)
     *
     * @throws Exception In case of an error
     */
    @Test
    public void sanitizeHeaderValueStripsCRLF() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("sanitizeHeaderValue", String.class);
        method.setAccessible(true);

        assertEquals("cleanvalue", method.invoke(client, "clean\r\nvalue"));
        assertEquals("cleanvalue", method.invoke(client, "clean\rvalue"));
        assertEquals("cleanvalue", method.invoke(client, "clean\nvalue"));
        assertEquals("clean", method.invoke(client, "clean"));
        assertEquals("", method.invoke(client, ""));
        assertEquals("", method.invoke(client, (Object) null));
    }


    /**
     * Test sanitizeLogValue replaces CR and LF with escaped versions (S6 fix)
     *
     * @throws Exception In case of an error
     */
    @Test
    public void sanitizeLogValueEscapesCRLF() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("sanitizeLogValue", String.class);
        method.setAccessible(true);

        assertEquals("clean\\r\\nvalue", method.invoke(client, "clean\r\nvalue"));
        assertEquals("clean\\rvalue", method.invoke(client, "clean\rvalue"));
        assertEquals("clean\\nvalue", method.invoke(client, "clean\nvalue"));
        assertEquals("clean", method.invoke(client, "clean"));
        assertEquals("", method.invoke(client, ""));
        assertEquals("", method.invoke(client, (Object) null));
    }


    /**
     * Test createCustomHeaders rejects blocked headers (S9 fix)
     *
     * @throws Exception In case of an error
     */
    @Test
    public void createCustomHeadersRejectsBlockedHeaders() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createCustomHeaders", ICAPRequestInformation.class);
        method.setAccessible(true);

        // All these should be silently rejected
        String[] blockedHeaders = {"Host", "Connection", "User-Agent", "Preview", "Encapsulated", "Allow", "Transfer-Encoding", "Content-Length"};
        for (String header : blockedHeaders) {
            ICAPRequestInformation info = new ICAPRequestInformation();
            info.addCustomHeader(header, "injected");
            String result = (String) method.invoke(client, info);
            assertFalse(result.contains(header + ": injected"), "Header " + header + " should be blocked");
        }
    }


    /**
     * Test createCustomHeaders allows valid headers
     *
     * @throws Exception In case of an error
     */
    @Test
    public void createCustomHeadersAllowsValidHeaders() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createCustomHeaders", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation info = new ICAPRequestInformation();
        info.addCustomHeader("X-Custom", "myvalue");
        String result = (String) method.invoke(client, info);
        assertTrue(result.contains("X-Custom: myvalue"));
    }


    /**
     * Test createCustomHeaders strips CRLF from values (S1 fix)
     *
     * @throws Exception In case of an error
     */
    @Test
    public void createCustomHeadersSanitizesCRLF() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createCustomHeaders", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation info = new ICAPRequestInformation();
        info.addCustomHeader("X-Custom", "value\r\nEvil-Header: injected");
        String result = (String) method.invoke(client, info);
        assertTrue(result.contains("X-Custom: valueEvil-Header: injected"));
        assertFalse(result.contains("\r\nEvil-Header"));
    }


    /**
     * Test createCustomHeaders with empty map
     *
     * @throws Exception In case of an error
     */
    @Test
    public void createCustomHeadersEmpty() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createCustomHeaders", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation info = new ICAPRequestInformation();
        String result = (String) method.invoke(client, info);
        assertEquals("", result);
    }


    /**
     * Test request identifier uses UUID (S7 fix) - should be unique across calls
     *
     * @throws Exception In case of an error
     */
    @Test
    public void requestIdentifierUnique() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createRequestIdentifier", String.class, String.class);
        method.setAccessible(true);

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String id = (String) method.invoke(client, "REQMOD", "test");
            ids.add(id);
        }

        // All 100 IDs should be unique (UUID-based)
        assertEquals(100, ids.size());
    }


    /**
     * Test request identifier with same input still produces different IDs (S7 fix)
     *
     * @throws Exception In case of an error
     */
    @Test
    public void requestIdentifierNotDeterministic() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createRequestIdentifier", String.class, String.class);
        method.setAccessible(true);

        String id1 = (String) method.invoke(client, "REQMOD", "same-input");
        String id2 = (String) method.invoke(client, "REQMOD", "same-input");
        assertNotEquals(id1, id2);
    }


    /**
     * Test createAuthorizationHeader with authorization set
     *
     * @throws Exception In case of an error
     */
    @Test
    public void createAuthorizationHeaderWithValue() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createAuthorizationHeader", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation info = new ICAPRequestInformation();
        info.setAuthorization("Basic dXNlcjpwYXNz");
        String result = (String) method.invoke(client, info);
        assertEquals("Authorization: Basic dXNlcjpwYXNz\r\n", result);
    }


    /**
     * Test createAuthorizationHeader with null authorization
     *
     * @throws Exception In case of an error
     */
    @Test
    public void createAuthorizationHeaderNull() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createAuthorizationHeader", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation info = new ICAPRequestInformation();
        String result = (String) method.invoke(client, info);
        assertEquals("", result);
    }


    /**
     * Test createAuthorizationHeader sanitizes CRLF
     *
     * @throws Exception In case of an error
     */
    @Test
    public void createAuthorizationHeaderSanitizesCRLF() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createAuthorizationHeader", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation info = new ICAPRequestInformation();
        info.setAuthorization("Basic abc\r\nEvil: injected");
        String result = (String) method.invoke(client, info);
        assertFalse(result.contains("\r\nEvil"));
    }


    /**
     * Test Authorization is blocked in custom headers
     *
     * @throws Exception In case of an error
     */
    @Test
    public void authorizationBlockedInCustomHeaders() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("createCustomHeaders", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation info = new ICAPRequestInformation();
        info.addCustomHeader("Authorization", "Basic injected");
        String result = (String) method.invoke(client, info);
        assertFalse(result.contains("Authorization"));
    }


    /**
     * Test throwStatusCodeException throws ICAPRequestException for 4xx codes
     *
     * @throws Exception In case of an error
     */
    @Test
    public void throwStatusCode4xx() throws Exception {
        assertThrowsStatusCode(400, ICAPRequestException.class);
        assertThrowsStatusCode(404, ICAPRequestException.class);
        assertThrowsStatusCode(405, ICAPRequestException.class);
        assertThrowsStatusCode(408, ICAPRequestException.class);
        assertThrowsStatusCode(499, ICAPRequestException.class);
    }


    /**
     * Test throwStatusCodeException throws UnknownIOException for 5xx and unknown codes
     *
     * @throws Exception In case of an error
     */
    @Test
    public void throwStatusCode5xx() throws Exception {
        assertThrowsStatusCode(500, UnknownIOException.class);
        assertThrowsStatusCode(501, UnknownIOException.class);
        assertThrowsStatusCode(502, UnknownIOException.class);
        assertThrowsStatusCode(503, UnknownIOException.class);
        assertThrowsStatusCode(505, UnknownIOException.class);
        assertThrowsStatusCode(999, UnknownIOException.class);
    }


    /**
     * Test getEffectiveRequestInformation returns provided when not null
     *
     * @throws Exception In case of an error
     */
    @Test
    public void getEffectiveRequestInfoProvided() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("getEffectiveRequestInformation", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation provided = new ICAPRequestInformation();
        provided.setAuthorization("Basic test");
        ICAPRequestInformation result = (ICAPRequestInformation) method.invoke(client, provided);
        assertEquals(provided, result);
    }


    /**
     * Test getEffectiveRequestInformation returns default when provided is null
     *
     * @throws Exception In case of an error
     */
    @Test
    public void getEffectiveRequestInfoDefault() throws Exception {
        ICAPClientImpl client = createClient();
        ICAPRequestInformation defaultInfo = new ICAPRequestInformation();
        defaultInfo.setAuthorization("Basic default");
        client.setDefaultRequestInformation(defaultInfo);

        Method method = ICAPClientImpl.class.getDeclaredMethod("getEffectiveRequestInformation", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation result = (ICAPRequestInformation) method.invoke(client, (Object) null);
        assertEquals(defaultInfo, result);
    }


    /**
     * Test getEffectiveRequestInformation returns new instance when both null
     *
     * @throws Exception In case of an error
     */
    @Test
    public void getEffectiveRequestInfoFallback() throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("getEffectiveRequestInformation", ICAPRequestInformation.class);
        method.setAccessible(true);

        ICAPRequestInformation result = (ICAPRequestInformation) method.invoke(client, (Object) null);
        assertNotNull(result);
        assertEquals(ICAPRequestInformation.USER_AGENT, result.getUserAgent());
    }


    /**
     * Assert that throwStatusCodeException throws the expected exception type for the given status code.
     *
     * @param statusCode the status code
     * @param expectedType the expected exception type
     * @throws Exception In case of an error
     * @throws AssertionError if no exception is thrown
     */
    private void assertThrowsStatusCode(int statusCode, Class<?> expectedType) throws Exception {
        ICAPClientImpl client = createClient();
        Method method = ICAPClientImpl.class.getDeclaredMethod("throwStatusCodeException", ICAPHeaderInformation.class);
        method.setAccessible(true);

        ICAPHeaderInformation headers = new ICAPHeaderInformation().setStatus(statusCode);
        try {
            method.invoke(client, headers);
        } catch (InvocationTargetException e) {
            assertTrue(expectedType.isInstance(e.getCause()), "Expected " + expectedType.getSimpleName() + " for status " + statusCode);
            return;
        }
        throw new AssertionError("Expected exception for status " + statusCode);
    }


    /**
     * Create a client for testing (no actual connection needed for private method tests)
     *
     * @return the client
     */
    private ICAPClientImpl createClient() {
        ICAPServiceInformation serviceInfo = new ICAPServiceInformation("localhost", 1344, false, "srv_clamav", 3600);
        return new ICAPClientImpl(new ICAPConnectionManagerImpl(), serviceInfo, null);
    }
}
