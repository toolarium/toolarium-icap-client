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
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;


/**
 * Implements the {@link ICAPConnectionManager}.
 *
 * @author patrick
 */
public class ICAPConnectionManagerImpl implements ICAPConnectionManager {
    private Integer defaultSocketConnectionTimeout;
    private Integer defaultSocketReadTimeout;


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#createSocket(java.lang.String, int, boolean, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public Socket createSocket(String hostname, int port, boolean secureConnection, Integer maxConnectionTimeout, Integer maxReadTimeout) throws UnknownHostException, IOException {
        if (!secureConnection) {
            return createUnsecureSocket(hostname, port, maxConnectionTimeout, maxReadTimeout);
        }

        return createSecureSocket(hostname, port, maxConnectionTimeout, maxReadTimeout);
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
        SSLSocketFactory factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
        Socket sslSocket = (SSLSocket)factory.createSocket();
        sslSocket.setSoTimeout(getReadSocketTimeout(maxReadTimeout));
        sslSocket.connect(new InetSocketAddress(hostname,port), getSocketConnectionTimeout(maxConnectionTimeout));
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
        if (defaultSocketConnectionTimeout != null && defaultSocketConnectionTimeout.intValue() >= 0) {
            socketTimeout = defaultSocketConnectionTimeout.intValue();
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
        if (defaultSocketReadTimeout != null && defaultSocketReadTimeout.intValue() >= 0) {
            socketReadTimeout = defaultSocketReadTimeout.intValue();
        }

        if (maxReadTimeout != null && maxReadTimeout.intValue() >= 0) {
            socketReadTimeout = maxReadTimeout.intValue();
        }
        return socketReadTimeout;
    }
}
