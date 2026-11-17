/*
 * ICAPConnectionHandler.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;


import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.net.ssl.SSLSocketFactory;


/**
 * Defines the connection manager
 *
 * @author patrick
 */
public interface ICAPConnectionManager {

    /**
     * Create a socket connection to the ICAP.
     *
     * @param hostname the name of the host to connect
     * @param port the port
     * @param secureConnection true to use secured SSL connection
     * @param maxConnectionTimeout the max connection timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     * @param maxReadTimeout the max read timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     * @return the socket / SSL socket
     * @throws UnknownHostException In case of unknown host
     * @throws IOException In case of an I/O error
     */
    Socket createSocket(String hostname, int port, boolean secureConnection, Integer maxConnectionTimeout, Integer maxReadTimeout) throws UnknownHostException, IOException;


    /**
     * Define the default socket connection timeout in milliseconds or null. A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     *
     * @param defaultSocketConnectionTimeout the default socket connection timeout in milliseconds or null.
     */
    void setDefaultSocketConnectionTimeout(Integer defaultSocketConnectionTimeout);

    
    /**
     * Define the default socket read timeout in milliseconds or null. A timeout of null or zero are interpreted as an infinite timeout.
     *
     * @param defaultSocketReadTimeout the default socket read timeout in milliseconds or null.
     */
    void setDefaultSocketReadTimeout(Integer defaultSocketReadTimeout);


    /**
     * Get the default socket connection timeout in milliseconds or null.
     *
     * @return the default socket connection timeout in milliseconds or null
     */
    Integer getDefaultSocketConnectionTimeout();


    /**
     * Get the default socket read timeout in milliseconds or null.
     *
     * @return the default socket read timeout in milliseconds or null
     */
    Integer getDefaultSocketReadTimeout();


    /**
     * Define the SSL socket factory for secure connections (icaps), e.g. from an SSLContext with a custom trust store
     * or a client certificate. The hostname verification stays enabled. A value of null uses the default of the JVM.
     *
     * @param sslSocketFactory the SSL socket factory or null
     */
    void setSSLSocketFactory(SSLSocketFactory sslSocketFactory);


    /**
     * Get the maximum number of idle connections per host in the pool.
     *
     * @return the maximum number of idle connections per host
     */
    int getMaxPoolConnectionsPerHost();


    /**
     * Set the maximum number of idle connections per host in the pool. A value of 0 disables pooling (default).
     *
     * @param maxConnectionsPerHost the maximum number of idle connections per host
     */
    void setMaxPoolConnectionsPerHost(int maxConnectionsPerHost);


    /**
     * Set the server-advertised maximum connections of a specific host (RFC 3507 §4.10). It limits only the pool
     * of this host and only if it is lower than the maximum pool connections per host.
     *
     * @param hostname the hostname
     * @param port the port
     * @param secureConnection true for SSL connections
     * @param maxConnections the server maximum connections; a value &lt;= 0 removes the limit
     */
    void setServerMaxConnections(String hostname, int port, boolean secureConnection, int maxConnections);


    /**
     * Set the idle timeout in milliseconds for pooled connections. Connections idle longer than this are evicted.
     *
     * @param idleTimeoutMs the idle timeout in milliseconds
     */
    void setPoolIdleTimeout(long idleTimeoutMs);


    /**
     * Check if connection pooling is enabled.
     *
     * @return true if pooling is enabled (maxConnectionsPerHost &gt; 0)
     */
    boolean isPoolingEnabled();


    /**
     * Release a socket back to the connection pool for reuse.
     * If pooling is not supported or the socket is invalid, it will be closed.
     *
     * @param hostname the hostname
     * @param port the port
     * @param secureConnection true for SSL connections
     * @param socket the socket to release
     */
    void releaseSocket(String hostname, int port, boolean secureConnection, Socket socket);


    /**
     * Close all pooled connections of a specific host, e.g. after a pooled connection turned out to be closed by the server.
     *
     * @param hostname the hostname
     * @param port the port
     * @param secureConnection true for SSL connections
     */
    void evictPool(String hostname, int port, boolean secureConnection);


    /**
     * Close all pooled connections and release resources.
     */
    void closePool();
}
