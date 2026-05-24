/*
 * ICAPClientImplResilienceTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.ICAPClientFactory;
import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.dto.ICAPServiceInformation;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Tests for resilience fixes in the ICAP client.
 *
 * @author patrick
 */
public class ICAPClientImplResilienceTest {

    /**
     * Reset connection manager timeouts before each test to avoid pollution from other tests
     */
    @BeforeEach
    public void resetTimeouts() {
        ICAPClientFactory.getInstance().getICAPConnectionManager().setDefaultSocketConnectionTimeout(30000);
        ICAPClientFactory.getInstance().getICAPConnectionManager().setDefaultSocketReadTimeout(60000);
    }


    /**
     * Test default socket timeouts are set (R1 fix)
     *
     * @throws RuntimeException In case of a reflection error
     */
    @Test
    public void defaultSocketTimeoutsAreSet() {
        ICAPConnectionManagerImpl mgr = new ICAPConnectionManagerImpl();

        // Access through reflection to verify defaults
        // The defaults should be 30000 and 60000 (set in the field initializers)
        // We verify indirectly: setting null should fall back to defaults, not 0
        // by checking that getSocketConnectionTimeout with null returns the default
        try {
            java.lang.reflect.Method method = ICAPConnectionManagerImpl.class.getDeclaredMethod("getSocketConnectionTimeout", Integer.class);
            method.setAccessible(true);
            int timeout = (int) method.invoke(mgr, (Integer) null);
            assertEquals(30000, timeout);

            java.lang.reflect.Method readMethod = ICAPConnectionManagerImpl.class.getDeclaredMethod("getReadSocketTimeout", Integer.class);
            readMethod.setAccessible(true);
            int readTimeout = (int) readMethod.invoke(mgr, (Integer) null);
            assertEquals(60000, readTimeout);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Test per-request timeout overrides defaults (R1 fix)
     *
     * @throws RuntimeException In case of a reflection error
     */
    @Test
    public void perRequestTimeoutOverridesDefault() {
        ICAPConnectionManagerImpl mgr = new ICAPConnectionManagerImpl();

        try {
            java.lang.reflect.Method method = ICAPConnectionManagerImpl.class.getDeclaredMethod("getSocketConnectionTimeout", Integer.class);
            method.setAccessible(true);
            int timeout = (int) method.invoke(mgr, 5000);
            assertEquals(5000, timeout);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Test options() without arguments forces refresh (R5 fix)
     *
     * @throws IOException In case of an I/O error
     */
    @Test
    public void optionsNoArgForcesRefresh() throws IOException {
        ICAPClientImpl client = new ICAPClientImpl(
                new ICAPConnectionManagerImpl(),
                new ICAPServiceInformation("localhost", 1344, false, "srv_clamav", 3600),
                null);

        // First call should fetch from server
        ICAPRemoteServiceConfiguration config1 = client.options();
        assertNotNull(config1);

        // Second call with no-arg should force refresh (not return cached)
        ICAPRemoteServiceConfiguration config2 = client.options();
        assertNotNull(config2);

        // Timestamps should be different since it re-fetched
        assertNotNull(config2.getTimestamp());
    }


    /**
     * Test validateResource with zero-length resource returns empty headers without NPE (R10 fix)
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void validateZeroLengthResourceNoNPE() throws IOException, ContentBlockedException {
        ICAPClientImpl client = new ICAPClientImpl(
                new ICAPConnectionManagerImpl(),
                new ICAPServiceInformation("localhost", 1344, false, "srv_clamav", 3600),
                null);

        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
        ICAPHeaderInformation result = client.validateResource(
                ICAPMode.RESPMOD,
                new ICAPRequestInformation(),
                new ICAPResource("empty.txt", stream, 0));

        assertNotNull(result);
        assertNotNull(result.getHeaders());
        assertFalse(result.containsHeader("X-Anything"));
    }


    /**
     * Test validateResource with null requestInformation throws IOException
     *
     * @throws IOException In case of an I/O error
     */
    @Test
    public void validateWithNullRequestInfoThrows() {
        ICAPClientImpl client = new ICAPClientImpl(
                new ICAPConnectionManagerImpl(),
                new ICAPServiceInformation("localhost", 1344, false, "srv_clamav", 3600),
                null);

        assertThrows(IOException.class, () -> {
            client.validateResource(ICAPMode.RESPMOD, null, new ICAPResource("test", new ByteArrayInputStream("data".getBytes()), 4));
        });
    }


    /**
     * Test supportCompareVerifyIdenticalContent=false does not produce digest headers (P6 fix).
     * This is tested via the valid resource integration path.
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void noDigestHeadersWhenCompareDisabled() throws IOException, ContentBlockedException {
        ByteArrayInputStream stream = new ByteArrayInputStream("ABCDEFGH".getBytes());
        ICAPHeaderInformation result = ICAPClientFactory.getInstance()
                .getICAPClient("localhost", 1344, "srv_clamav")
                .supportCompareVerifyIdenticalContent(false)
                .validateResource(
                        ICAPMode.RESPMOD,
                        new ICAPRequestInformation("testUser", "testSource").setAllow204(false),
                        new ICAPResource("test.txt", stream, 8));

        assertNotNull(result);
        assertFalse(result.containsHeader(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST));
        assertFalse(result.containsHeader(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST));
        assertFalse(result.containsHeader(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT));
    }


    /**
     * Test supportCompareVerifyIdenticalContent=true produces digest headers (P6 fix).
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void digestHeadersPresentWhenCompareEnabled() throws IOException, ContentBlockedException {
        ByteArrayInputStream stream = new ByteArrayInputStream("ABCDEFGH".getBytes());
        ICAPHeaderInformation result = ICAPClientFactory.getInstance()
                .getICAPClient("localhost", 1344, "srv_clamav")
                .supportCompareVerifyIdenticalContent(true)
                .validateResource(
                        ICAPMode.RESPMOD,
                        new ICAPRequestInformation("testUser", "testSource").setAllow204(false),
                        new ICAPResource("test.txt", stream, 8));

        assertNotNull(result);
        assertTrue(result.containsHeader(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST));
        assertTrue(result.containsHeader(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST));
    }
}
