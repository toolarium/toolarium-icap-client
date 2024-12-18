/*
 * ICAPConnectionManagerImpl.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import com.github.toolarium.icap.client.ICAPConnectionManager;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Implements the {@link ICAPConnectionManager}.
 * 
 * @author patrick
 */
public class ICAPConnectionManagerImpl implements ICAPConnectionManager {
    private int socketTimeout;

    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#createSocket(java.lang.String, int, boolean)
     */
    @Override
    public Socket createSocket(String hostname, int port, boolean secureConnection) throws UnknownHostException, IOException {
        
        if (!secureConnection) {
            return createUnsecureSocket(hostname, port);
        }

        return createSecureSocket(hostname, port);
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPConnectionManager#setSocketTimeout(int)
     */
    @Override
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    
    /**
     * Create a simple socket
     * 
     * @param hostname the name of host
     * @param port the port
     * @return the socket
     * @throws UnknownHostException In case of unknown host
     * @throws IOException In case of an I/O error
     */
    protected Socket createUnsecureSocket(String hostname, int port) throws UnknownHostException, IOException {
        return applySocketSettings(new Socket(hostname, port)); 
    }


    /**
     * Create a secure socket
     * 
     * @param hostname the name of host
     * @param port the port
     * @return the socket
     * @throws UnknownHostException In case of unknown host
     * @throws IOException In case of an I/O error
     */
    protected Socket createSecureSocket(String hostname, int port) throws UnknownHostException, IOException {
        SSLSocketFactory factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
        Socket sslSocket = applySocketSettings((SSLSocket)factory.createSocket(hostname, port));
        return sslSocket;
    }
    
    
    /**
     * Apply the socket settings
     *
     * @param socket the socket
     * @return the socket
     * @throws SocketException Inb case of a socket issue
     */
    protected Socket applySocketSettings(Socket socket) throws SocketException {
        socket.setSoTimeout(socketTimeout);
        return socket;
    }
}
