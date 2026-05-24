/*
 * ICAPConnectionManagerPoolingTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.ICAPClientFactory;
import com.github.toolarium.icap.client.dto.ICAPServiceInformation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


/**
 * Test connection pooling integration with {@link ICAPConnectionManagerImpl} and {@link ICAPClientFactory}.
 *
 * @author patrick
 */
public class ICAPConnectionManagerPoolingTest {

    /**
     * Reset pool settings after each test.
     */
    @AfterEach
    public void resetPoolSettings() {
        ICAPClientFactory.getInstance().getICAPConnectionManager().setMaxPoolConnectionsPerHost(0);
        ICAPClientFactory.getInstance().getICAPConnectionManager().setPoolIdleTimeout(60000);
    }


    /**
     * Test pooling is disabled by default.
     */
    @Test
    public void poolingDisabledByDefault() {
        assertFalse(ICAPClientFactory.getInstance().getICAPConnectionManager().isPoolingEnabled());
    }


    /**
     * Test enabling pooling.
     */
    @Test
    public void enablePooling() {
        ICAPClientFactory.getInstance().getICAPConnectionManager().setMaxPoolConnectionsPerHost(5);
        assertTrue(ICAPClientFactory.getInstance().getICAPConnectionManager().isPoolingEnabled());
    }


    /**
     * Test disabling pooling by setting to zero.
     */
    @Test
    public void disablePoolingWithZero() {
        ICAPClientFactory.getInstance().getICAPConnectionManager().setMaxPoolConnectionsPerHost(5);
        assertTrue(ICAPClientFactory.getInstance().getICAPConnectionManager().isPoolingEnabled());

        ICAPClientFactory.getInstance().getICAPConnectionManager().setMaxPoolConnectionsPerHost(0);
        assertFalse(ICAPClientFactory.getInstance().getICAPConnectionManager().isPoolingEnabled());
    }


    /**
     * Test setting idle timeout.
     */
    @Test
    public void setIdleTimeout() {
        ICAPClientFactory.getInstance().getICAPConnectionManager().setPoolIdleTimeout(30000);
        ICAPConnectionManagerImpl mgr = (ICAPConnectionManagerImpl) ICAPClientFactory.getInstance().getICAPConnectionManager();
        assertEquals(30000, mgr.getConnectionPool().getIdleTimeoutMs());
    }


    /**
     * Test closePool does not throw when pool is empty.
     */
    @Test
    public void closePoolWhenEmpty() {
        ICAPClientFactory.getInstance().closePool();
    }


    /**
     * Test getConnectionPool returns non-null.
     */
    @Test
    public void getConnectionPoolNotNull() {
        ICAPConnectionManagerImpl mgr = (ICAPConnectionManagerImpl) ICAPClientFactory.getInstance().getICAPConnectionManager();
        assertNotNull(mgr.getConnectionPool());
    }


    /**
     * Test getConnectionMode returns "close" when pooling disabled.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void connectionModeCloseWhenPoolingDisabled() throws Exception {
        ICAPClientFactory.getInstance().getICAPConnectionManager().setMaxPoolConnectionsPerHost(0);
        ICAPClientImpl client = createClient();

        Method method = ICAPClientImpl.class.getDeclaredMethod("getConnectionMode");
        method.setAccessible(true);
        assertEquals("close", method.invoke(client));
    }


    /**
     * Test getConnectionMode returns "keep-alive" when pooling enabled.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void connectionModeKeepAliveWhenPoolingEnabled() throws Exception {
        ICAPClientFactory.getInstance().getICAPConnectionManager().setMaxPoolConnectionsPerHost(5);
        ICAPClientImpl client = createClient();

        Method method = ICAPClientImpl.class.getDeclaredMethod("getConnectionMode");
        method.setAccessible(true);
        assertEquals("keep-alive", method.invoke(client));
    }


    /**
     * Create a client for testing.
     *
     * @return the client
     */
    private ICAPClientImpl createClient() {
        ICAPServiceInformation serviceInfo = new ICAPServiceInformation("localhost", 1344, false, "srv_clamav", 3600);
        return new ICAPClientImpl(ICAPClientFactory.getInstance().getICAPConnectionManager(), serviceInfo, null);
    }
}
