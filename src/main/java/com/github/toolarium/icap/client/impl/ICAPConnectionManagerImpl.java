/*
 * ICAPConnectionManagerImpl.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import com.github.toolarium.icap.client.ICAPConnectionManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;


/**
 * Implements the {@link ICAPConnectionManager}.
 *
 * @author patrick
 */
public class ICAPConnectionManagerImpl implements ICAPConnectionManager {
    private static final int DEFAULT_CONNECTION_TIMEOUT = 30000;
    private static final int DEFAULT_READ_TIMEOUT = 60000;
    private volatile Integer defaultSocketConnectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private volatile Integer defaultSocketReadTimeout = DEFAULT_READ_TIMEOUT;
    private final ICAPConnectionPool connectionPool;


    /**
     * Constructor for ICAPConnectionManagerImpl
     */
    public ICAPConnectionManagerImpl() {
        this.connectionPool = new ICAPConnectionPool();
    }


    /**
     * Get the connection pool
     *
     * @return the connection pool
     */
    public ICAPConnectionPool getConnectionPool() {
        return connectionPool;
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#createSocket(java.lang.String, int, boolean, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public Socket createSocket(String hostname, int port, boolean secureConnection, Integer maxConnectionTimeout, Integer maxReadTimeout) throws UnknownHostException, IOException {
        Socket pooled = connectionPool.acquire(hostname, port, secureConnection);
        if (pooled != null) {
            pooled.setSoTimeout(getReadSocketTimeout(maxReadTimeout));
            return pooled;
        }

        if (!secureConnection) {
            return createUnsecureSocket(hostname, port, maxConnectionTimeout, maxReadTimeout);
        }

        return createSecureSocket(hostname, port, maxConnectionTimeout, maxReadTimeout);
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#getMaxPoolConnectionsPerHost()
     */
    @Override
    public int getMaxPoolConnectionsPerHost() {
        return connectionPool.getMaxConnectionsPerHost();
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#setMaxPoolConnectionsPerHost(int)
     */
    @Override
    public void setMaxPoolConnectionsPerHost(int maxConnectionsPerHost) {
        connectionPool.setMaxConnectionsPerHost(maxConnectionsPerHost);
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#setPoolIdleTimeout(long)
     */
    @Override
    public void setPoolIdleTimeout(long idleTimeoutMs) {
        connectionPool.setIdleTimeoutMs(idleTimeoutMs);
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#isPoolingEnabled()
     */
    @Override
    public boolean isPoolingEnabled() {
        return connectionPool.getMaxConnectionsPerHost() > 0;
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#releaseSocket(java.lang.String, int, boolean, java.net.Socket)
     */
    @Override
    public void releaseSocket(String hostname, int port, boolean secureConnection, Socket socket) {
        connectionPool.release(hostname, port, secureConnection, socket);
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#closePool()
     */
    @Override
    public void closePool() {
        connectionPool.close();
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#setDefaultSocketConnectionTimeout(java.lang.Integer)
     */
    @Override
    public void setDefaultSocketConnectionTimeout(Integer defaultSocketConnectionTimeout) {
        this.defaultSocketConnectionTimeout = defaultSocketConnectionTimeout;
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#setDefaultSocketReadTimeout(java.lang.Integer)
     */
    @Override
    public void setDefaultSocketReadTimeout(Integer defaultSocketReadTimeout) {
        this.defaultSocketReadTimeout = defaultSocketReadTimeout;
    }


    /**
     * Create a simple socket
     *
     * @param hostname the name of host
     * @param port the port
     * @param maxConnectionTimeout the max connection timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     * @param maxReadTimeout the max read timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     * @return the socket
     * @throws UnknownHostException In case of unknown host
     * @throws IOException In case of an I/O error
     */
    protected Socket createUnsecureSocket(String hostname, int port, Integer maxConnectionTimeout, Integer maxReadTimeout) throws UnknownHostException, IOException {
        Socket socket = new Socket();
        socket.setSoTimeout(getReadSocketTimeout(maxReadTimeout));
        socket.connect(new InetSocketAddress(hostname,port), getSocketConnectionTimeout(maxConnectionTimeout));
        return socket;
    }


    /**
     * Create a secure socket
     *
     * @param hostname the name of host
     * @param port the port
     * @param maxConnectionTimeout the max connection timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     * @param maxReadTimeout the max read timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     * @return the socket
     * @throws UnknownHostException In case of unknown host
     * @throws IOException In case of an I/O error
     */
    protected Socket createSecureSocket(String hostname, int port, Integer maxConnectionTimeout, Integer maxReadTimeout) throws UnknownHostException, IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket sslSocket = (SSLSocket)factory.createSocket();

        // enable hostname verification (same as HTTPS)
        SSLParameters sslParams = sslSocket.getSSLParameters();
        sslParams.setEndpointIdentificationAlgorithm("HTTPS");
        sslSocket.setSSLParameters(sslParams);

        sslSocket.setSoTimeout(getReadSocketTimeout(maxReadTimeout));
        sslSocket.connect(new InetSocketAddress(hostname, port), getSocketConnectionTimeout(maxConnectionTimeout));
        sslSocket.startHandshake();
        return sslSocket;
    }


    /**
     * Get the socket connection timeout
     *
     * @param maxConnectionTimeout the max connection timeout or null
     * @return the socket timeout to use
     */
    private int getSocketConnectionTimeout(Integer maxConnectionTimeout) {
        int socketTimeout = 0;
        // Capture volatile field once to avoid a concurrent setDefaultSocketConnectionTimeout(null) causing a NullPointerException between the null-check and the intValue() call.
        Integer defaultTimeout = defaultSocketConnectionTimeout;
        if (defaultTimeout != null && defaultTimeout.intValue() >= 0) {
            socketTimeout = defaultTimeout.intValue();
        }

        if (maxConnectionTimeout != null && maxConnectionTimeout.intValue() >= 0) {
            socketTimeout = maxConnectionTimeout.intValue();
        }
        return socketTimeout;
    }


    /**
     * Get the read socket timeout
     *
     * @param maxReadTimeout the max read timeout or null
     * @return the socket timeout to use
     */
    private int getReadSocketTimeout(Integer maxReadTimeout) {
        int socketReadTimeout = 0;
        // Capture volatile field once to avoid a concurrent setDefaultSocketReadTimeout(null) causing a NullPointerException between the null-check and the intValue() call.
        Integer defaultTimeout = defaultSocketReadTimeout;
        if (defaultTimeout != null && defaultTimeout.intValue() >= 0) {
            socketReadTimeout = defaultTimeout.intValue();
        }

        if (maxReadTimeout != null && maxReadTimeout.intValue() >= 0) {
            socketReadTimeout = maxReadTimeout.intValue();
        }
        return socketReadTimeout;
    }
}
