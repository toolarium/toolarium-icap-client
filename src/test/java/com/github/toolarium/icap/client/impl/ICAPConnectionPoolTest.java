/*
 * ICAPConnectionPoolTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPConnectionPool}.
 *
 * @author patrick
 */
public class ICAPConnectionPoolTest {
    private static final String HOST = "localhost";
    private static final boolean PLAIN = false;
    private ICAPConnectionPool pool;
    private ServerSocket serverSocket;
    private int port;


    /**
     * Set up a local server socket and pool for each test.
     *
     * @throws Exception In case of an error
     */
    @BeforeEach
    public void setUp() throws Exception {
        serverSocket = new ServerSocket(0);
        port = serverSocket.getLocalPort();
        pool = new ICAPConnectionPool(3, 60000);
    }


    /**
     * Tear down server socket and pool after each test.
     *
     * @throws Exception In case of an error
     */
    @AfterEach
    public void tearDown() throws Exception {
        pool.close();
        serverSocket.close();
    }


    /**
     * Test default constructor has pooling disabled.
     */
    @Test
    public void defaultConstructorPoolingDisabled() {
        ICAPConnectionPool defaultPool = new ICAPConnectionPool();
        assertEquals(0, defaultPool.getMaxConnectionsPerHost());
        assertEquals(60000, defaultPool.getIdleTimeoutMs());
        assertEquals(0, defaultPool.size());
    }


    /**
     * Test acquire returns null when pool is empty.
     */
    @Test
    public void acquireFromEmptyPoolReturnsNull() {
        assertNull(pool.acquire(HOST, port, PLAIN));
    }


    /**
     * Test release and acquire round-trip.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void releaseAndAcquireRoundTrip() throws Exception {
        Socket socket = createConnectedSocket();
        pool.release(HOST, port, PLAIN, socket);
        assertEquals(1, pool.size());

        Socket acquired = pool.acquire(HOST, port, PLAIN);
        assertNotNull(acquired);
        assertEquals(0, pool.size());
        acquired.close();
    }


    /**
     * Test pool respects max connections per host.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void maxConnectionsPerHostRespected() throws Exception {
        pool.setMaxConnectionsPerHost(2);

        Socket s1 = createConnectedSocket();
        Socket s2 = createConnectedSocket();
        Socket s3 = createConnectedSocket();

        pool.release(HOST, port, PLAIN, s1);
        pool.release(HOST, port, PLAIN, s2);
        pool.release(HOST, port, PLAIN, s3); // should be closed, pool full

        assertEquals(2, pool.size());
        pool.close();
    }


    /**
     * Test pool size 0 means all released sockets are closed.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void poolSizeZeroClosesAll() throws Exception {
        pool.setMaxConnectionsPerHost(0);

        Socket socket = createConnectedSocket();
        pool.release(HOST, port, PLAIN, socket);

        assertEquals(0, pool.size());
    }


    /**
     * Test release with closed socket does not add to pool.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void releaseClosedSocketIgnored() throws Exception {
        Socket socket = createConnectedSocket();
        socket.close();
        pool.release(HOST, port, PLAIN, socket);

        assertEquals(0, pool.size());
    }


    /**
     * Test release with null socket does not throw.
     */
    @Test
    public void releaseNullSocketIgnored() {
        pool.release(HOST, port, PLAIN, null);
        assertEquals(0, pool.size());
    }


    /**
     * Test close empties the pool.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void closeEmptiesPool() throws Exception {
        Socket s1 = createConnectedSocket();
        Socket s2 = createConnectedSocket();
        pool.release(HOST, port, PLAIN, s1);
        pool.release(HOST, port, PLAIN, s2);
        assertEquals(2, pool.size());

        pool.close();
        assertEquals(0, pool.size());
    }


    /**
     * Test idle timeout eviction.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void idleTimeoutEviction() throws Exception {
        pool.setIdleTimeoutMs(1); // 1ms timeout

        Socket socket = createConnectedSocket();
        pool.release(HOST, port, PLAIN, socket);

        Thread.sleep(50); // wait for idle timeout

        Socket acquired = pool.acquire(HOST, port, PLAIN);
        assertNull(acquired, "Expired socket should be evicted");
        assertEquals(0, pool.size());
    }


    /**
     * Test different hosts use separate pools.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void separatePoolsPerHost() throws Exception {
        Socket s1 = createConnectedSocket();
        Socket s2 = createConnectedSocket();

        pool.release(HOST, port, PLAIN, s1);
        pool.release(HOST, port + 1, PLAIN, s2); // different port = different key

        assertEquals(2, pool.size());

        // acquire from first key
        Socket acquired = pool.acquire(HOST, port, PLAIN);
        assertNotNull(acquired);
        assertEquals(1, pool.size());

        // acquire from second key returns nothing for wrong port
        assertNull(pool.acquire(HOST, port + 2, PLAIN));

        acquired.close();
    }


    /**
     * Test secure and plain connections use different pool keys.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void secureAndPlainSeparateKeys() throws Exception {
        Socket s1 = createConnectedSocket();
        pool.release(HOST, port, PLAIN, s1);
        assertEquals(1, pool.size());

        // acquire with secure=true should not find the plain socket
        Socket acquired = pool.acquire(HOST, port, true);
        assertNull(acquired);

        // acquire with plain should find it
        acquired = pool.acquire(HOST, port, PLAIN);
        assertNotNull(acquired);
        acquired.close();
    }


    /**
     * Test setters for pool configuration.
     */
    @Test
    public void settersWork() {
        pool.setMaxConnectionsPerHost(10);
        assertEquals(10, pool.getMaxConnectionsPerHost());

        pool.setIdleTimeoutMs(30000);
        assertEquals(30000, pool.getIdleTimeoutMs());
    }


    /**
     * Create a socket connected to the local server socket.
     *
     * @return the connected socket
     * @throws Exception In case of an error
     */
    private Socket createConnectedSocket() throws Exception {
        Socket socket = new Socket(HOST, port);
        serverSocket.accept().close(); // accept and close server side
        return socket;
    }
}
