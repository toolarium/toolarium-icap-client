/*
 * ICAPConnectionPool.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import java.io.IOException;
import java.net.Socket;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A simple connection pool for ICAP sockets, keyed by host:port:secure.
 * Supports bounded per-host connections and idle timeout eviction.
 *
 * @author Patrick Meier
 */
public class ICAPConnectionPool {
    private static final Logger LOG = LoggerFactory.getLogger(ICAPConnectionPool.class);
    private static final int DEFAULT_MAX_PER_HOST = 0;
    private static final long DEFAULT_IDLE_TIMEOUT_MS = 60000;

    private final Map<String, Deque<PooledSocket>> pool;
    private volatile int maxConnectionsPerHost;
    private volatile long idleTimeoutMs;


    /**
     * Constructor for ICAPConnectionPool
     */
    public ICAPConnectionPool() {
        this(DEFAULT_MAX_PER_HOST, DEFAULT_IDLE_TIMEOUT_MS);
    }


    /**
     * Constructor for ICAPConnectionPool
     *
     * @param maxConnectionsPerHost the maximum number of idle connections per host
     * @param idleTimeoutMs the idle timeout in milliseconds after which connections are evicted
     */
    public ICAPConnectionPool(int maxConnectionsPerHost, long idleTimeoutMs) {
        this.pool = new ConcurrentHashMap<>();
        this.maxConnectionsPerHost = maxConnectionsPerHost;
        this.idleTimeoutMs = idleTimeoutMs;
    }


    /**
     * Get the maximum connections per host
     *
     * @return the maximum connections per host
     */
    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }


    /**
     * Set the maximum connections per host
     *
     * @param maxConnectionsPerHost the maximum connections per host
     */
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }


    /**
     * Get the idle timeout in milliseconds
     *
     * @return the idle timeout
     */
    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }


    /**
     * Set the idle timeout in milliseconds
     *
     * @param idleTimeoutMs the idle timeout
     */
    public void setIdleTimeoutMs(long idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
    }


    /**
     * Acquire a socket from the pool, or return null if none available.
     *
     * @param hostname the hostname
     * @param port the port
     * @param secureConnection true for SSL connections
     * @return a pooled socket, or null if none available
     */
    public Socket acquire(String hostname, int port, boolean secureConnection) {
        String key = createKey(hostname, port, secureConnection);
        Deque<PooledSocket> hostPool = pool.get(key);
        if (hostPool == null) {
            return null;
        }

        evictExpired(hostPool);

        PooledSocket pooled;
        while ((pooled = hostPool.pollFirst()) != null) {
            if (isSocketValid(pooled)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Reusing pooled connection to [" + key + "]");
                }
                return pooled.getSocket();
            }
            closeQuietly(pooled.getSocket());
        }

        return null;
    }


    /**
     * Release a socket back to the pool.
     *
     * @param hostname the hostname
     * @param port the port
     * @param secureConnection true for SSL connections
     * @param socket the socket to release
     */
    public void release(String hostname, int port, boolean secureConnection, Socket socket) {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            closeQuietly(socket);
            return;
        }

        String key = createKey(hostname, port, secureConnection);
        Deque<PooledSocket> hostPool = pool.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        evictExpired(hostPool);

        if (hostPool.size() >= maxConnectionsPerHost) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Pool full for [" + key + "], closing socket");
            }
            closeQuietly(socket);
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Returning connection to pool [" + key + "], pool size: " + (hostPool.size() + 1));
        }
        hostPool.addLast(new PooledSocket(socket));
    }


    /**
     * Close all pooled connections.
     */
    public void close() {
        for (Map.Entry<String, Deque<PooledSocket>> entry : pool.entrySet()) {
            PooledSocket pooled;
            while ((pooled = entry.getValue().pollFirst()) != null) {
                closeQuietly(pooled.getSocket());
            }
        }
        pool.clear();
        LOG.debug("Connection pool closed");
    }


    /**
     * Get the total number of pooled connections across all hosts.
     *
     * @return the total pool size
     */
    public int size() {
        int total = 0;
        for (Deque<PooledSocket> hostPool : pool.values()) {
            total += hostPool.size();
        }
        return total;
    }


    /**
     * Create pool key from connection parameters.
     *
     * @param hostname the hostname
     * @param port the port
     * @param secureConnection true for SSL
     * @return the pool key
     */
    private String createKey(String hostname, int port, boolean secureConnection) {
        String scheme = "plain";
        if (secureConnection) {
            scheme = "ssl";
        }
        return hostname + ":" + port + ":" + scheme;
    }


    /**
     * Check if a pooled socket is still valid.
     *
     * @param pooled the pooled socket
     * @return true if valid
     */
    private boolean isSocketValid(PooledSocket pooled) {
        Socket s = pooled.getSocket();
        if (s.isClosed() || !s.isConnected() || s.isInputShutdown() || s.isOutputShutdown()) {
            return false;
        }

        long idleTime = System.currentTimeMillis() - pooled.getReturnedAt();
        if (idleTime > idleTimeoutMs) {
            return false;
        }

        return true;
    }


    /**
     * Evict expired connections from a host pool.
     *
     * @param hostPool the host pool
     */
    private void evictExpired(Deque<PooledSocket> hostPool) {
        long now = System.currentTimeMillis();
        Iterator<PooledSocket> it = hostPool.iterator();
        while (it.hasNext()) {
            PooledSocket pooled = it.next();
            long idleTime = now - pooled.getReturnedAt();
            if (idleTime > idleTimeoutMs || pooled.getSocket().isClosed()) {
                it.remove();
                closeQuietly(pooled.getSocket());
            }
        }
    }


    /**
     * Close a socket without throwing exceptions.
     *
     * @param socket the socket
     */
    private void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // NOP
            }
        }
    }


    /**
     * Wrapper for a pooled socket with timestamp tracking.
     */
    private static final class PooledSocket {
        private final Socket socket;
        private final long returnedAt;


        /**
         * Constructor for PooledSocket
         *
         * @param socket the socket
         */
        PooledSocket(Socket socket) {
            this.socket = socket;
            this.returnedAt = System.currentTimeMillis();
        }


        /**
         * Get the socket
         *
         * @return the socket
         */
        Socket getSocket() {
            return socket;
        }


        /**
         * Get the timestamp when the socket was returned to the pool
         *
         * @return the timestamp in milliseconds
         */
        long getReturnedAt() {
            return returnedAt;
        }
    }
}
