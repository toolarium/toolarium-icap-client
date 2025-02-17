/*
 * ICAPSocketTimeoutTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Test socket timeouts
 *
 * @author patrick
 */
public class ICAPSocketTimeoutTest {
    private static final String RESOURCE_COULD_NOT_BE_ACCESSED = "Resource could not be accessed: ";
    private static final String LOCALHOST = "localhost";
    private static final String SERVICENAME = "srv_clamav";
    private static final Logger LOG = LoggerFactory.getLogger(ICAPSocketTimeoutTest.class);

    
    /**
     * Socket timeout
     */
    @Test
    public void testSocketTimeout() {
        LOG.debug("START");
        ICAPClientFactory.getInstance().getICAPConnectionManager().setDefaultSocketConnectionTimeout(10);
        final int port = 1344;

        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(new byte[] {});
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, port, SERVICENAME)
                 .validateResource(ICAPMode.RESPMOD,
                                   new ICAPRequestInformation("userb", "emptyfile").addCustomHeader("Test", "Header").maxConnectionTimeout(1),
                                   new ICAPResource("build/test-emptyfile.com", resourceInputStream, 0));
        } catch (Exception ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED + ioe.getMessage(), ioe);
            fail();
        }

        LOG.debug("END");
    }


    /**
     * Socket read timeout
     *
     * @throws IOException if the server socket cannot be opened
     */
    @Test
    public void testSocketReadTimeout() throws IOException {
        int port = 12345;
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // start dummy server that accepts connections, but does not respond
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                try (Socket s = serverSocket.accept()) {
                    // keep connection open, but do not send any response (simulates a hanging system)
                    InputStream is = s.getInputStream();
                    // read all data from socket
                    byte[] buffer = new byte[128];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        String output = new String(buffer, 0, read);
                        LOG.trace(output);
                    }
                } catch (Exception e) {
                    // ignore any server side exceptions
                }
            });

            // Client: Open icap connection to dummy server
            ICAPClientFactory icapClientFactory = ICAPClientFactory.getInstance();
            icapClientFactory.getICAPConnectionManager().setDefaultSocketReadTimeout(1); // <== this is the timeout we test

            Exception e = assertThrows(SocketTimeoutException.class,
                () -> icapClientFactory.getICAPClient(
                    "icap://localhost:" + port + "/reqmod", 3600
                    ).validateResource(
                        ICAPMode.RESPMOD,
                        new ICAPRequestInformation(),
                        new ICAPResource("dummy resource",
                            new ByteArrayInputStream("dummy data".getBytes()), 10)
                    )
            );
            assertEquals("Read timed out", e.getMessage());

            executor.shutdownNow();
        }
    }


    /**
     * Test connection
     */
    @Test
    public void testConnection() {
        int port = 1345; // invalid port

        try {
            String hostName = "localhost1";
            ICAPClientFactory.getInstance().getICAPClient(hostName, port, SERVICENAME, false);
            fail("Expecting exception");
        } catch (Exception e) {
            LOG.debug("Connection error:", e);
        }

        try {
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, port, SERVICENAME, false);
            fail("Expecting exception");
        } catch (Exception e) {
            LOG.debug("Connection error:", e);
        }
    }
}
