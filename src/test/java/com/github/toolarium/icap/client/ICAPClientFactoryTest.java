/*
 * ICAPClientFactoryTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.MalformedURLException;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPClientFactory} URL parsing and error handling.
 *
 * @author patrick
 */
public class ICAPClientFactoryTest {

    /**
     * Test valid icap URL with host, port and service connects (expects IOException since myhost is unreachable)
     */
    @Test
    public void parseValidIcapUrlUnreachableHost() {
        assertThrows(IOException.class, () -> {
            ICAPClientFactory.getInstance().getICAPClient("icap://myhost:1344/srv_clamav");
        });
    }


    /**
     * Test valid icaps URL with unreachable host
     */
    @Test
    public void parseValidIcapsUrlUnreachableHost() {
        assertThrows(IOException.class, () -> {
            ICAPClientFactory.getInstance().getICAPClient("icaps://myhost:1344/srv_clamav");
        });
    }


    /**
     * Test icap URL without port with unreachable host
     */
    @Test
    public void parseIcapUrlWithoutPortUnreachableHost() {
        assertThrows(IOException.class, () -> {
            ICAPClientFactory.getInstance().getICAPClient("icap://myhost/srv_clamav");
        });
    }


    /**
     * Test icap URL without service name with unreachable host
     */
    @Test
    public void parseIcapUrlWithoutServiceUnreachableHost() {
        assertThrows(IOException.class, () -> {
            ICAPClientFactory.getInstance().getICAPClient("icap://myhost:1344");
        });
    }


    /**
     * Test valid icap URL with localhost (requires running ICAP server)
     *
     * @throws Exception In case of an error
     */
    @Test
    public void parseValidIcapUrlLocalhost() throws Exception {
        ICAPClient client = ICAPClientFactory.getInstance().getICAPClient("icap://localhost:1344/srv_clamav");
        assertNotNull(client);
    }


    /**
     * Test null URL
     */
    @Test
    public void parseNullUrl() {
        assertThrows(MalformedURLException.class, () -> {
            ICAPClientFactory.getInstance().getICAPClient((String) null);
        });
    }


    /**
     * Test empty URL
     */
    @Test
    public void parseEmptyUrl() {
        assertThrows(MalformedURLException.class, () -> {
            ICAPClientFactory.getInstance().getICAPClient("");
        });
    }


    /**
     * Test invalid protocol
     */
    @Test
    public void parseInvalidProtocol() {
        assertThrows(MalformedURLException.class, () -> {
            ICAPClientFactory.getInstance().getICAPClient("http://localhost:1344/srv_clamav");
        });
    }


    /**
     * Test blank URL
     */
    @Test
    public void parseBlankUrl() {
        assertThrows(MalformedURLException.class, () -> {
            ICAPClientFactory.getInstance().getICAPClient("   ");
        });
    }


    /**
     * Test connection manager is not null
     */
    @Test
    public void getConnectionManager() {
        assertNotNull(ICAPClientFactory.getInstance().getICAPConnectionManager());
    }


    /**
     * Test setting null connection manager throws exception
     */
    @Test
    public void setNullConnectionManager() {
        assertThrows(IllegalArgumentException.class, () -> {
            ICAPClientFactory.getInstance().setICAPConnectionManager(null);
        });
    }
}
