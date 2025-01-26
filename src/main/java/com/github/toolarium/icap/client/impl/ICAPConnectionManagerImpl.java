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
    private Integer defaultSocketTimeout;

    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#createSocket(java.lang.String, int, boolean, java.lang.Integer)
     */
    @Override
    public Socket createSocket(String hostname, int port, boolean secureConnection, Integer maxRequestTimeout) throws UnknownHostException, IOException {
        
        if (!secureConnection) {
            return createUnsecureSocket(hostname, port, maxRequestTimeout);
        }

        return createSecureSocket(hostname, port, maxRequestTimeout);
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#setDefaultSocketTimeout(java.lang.Integer)
     */
    @Override
    public void setDefaultSocketTimeout(Integer defaultSocketTimeout) {
        this.defaultSocketTimeout = defaultSocketTimeout;
    }

    
    /**
     * Create a simple socket
     * 
     * @param hostname the name of host
     * @param port the port
     * @param maxRequestTimeout the max request timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block. 
     * @return the socket
     * @throws UnknownHostException In case of unknown host
     * @throws IOException In case of an I/O error
     */
    protected Socket createUnsecureSocket(String hostname, int port, Integer maxRequestTimeout) throws UnknownHostException, IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(hostname,port), getRequestSocketTimeout(maxRequestTimeout));
        return socket;
    }


    /**
     * Create a secure socket
     * 
     * @param hostname the name of host
     * @param port the port
     * @param maxRequestTimeout the max request timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block. 
     * @return the socket
     * @throws UnknownHostException In case of unknown host
     * @throws IOException In case of an I/O error
     */
    protected Socket createSecureSocket(String hostname, int port, Integer maxRequestTimeout) throws UnknownHostException, IOException {
        SSLSocketFactory factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
        Socket sslSocket = (SSLSocket)factory.createSocket();
        sslSocket.connect(new InetSocketAddress(hostname,port), getRequestSocketTimeout(maxRequestTimeout));
        return sslSocket;
    }


    /**
     * Get the request socket timeout
     *
     * @param maxRequestTimeout the max request timeout or null
     * @return the socket timeout to use
     */
    private int getRequestSocketTimeout(Integer maxRequestTimeout) {
        int socketTimeout = 0;
        if (defaultSocketTimeout != null && defaultSocketTimeout.intValue() >= 0) {
            socketTimeout = defaultSocketTimeout.intValue();
        }

        if (maxRequestTimeout != null && maxRequestTimeout.intValue() >= 0) {
            socketTimeout = maxRequestTimeout.intValue();
        }
        return socketTimeout;
    }
}
