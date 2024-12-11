/*
 * ICAPConnectionHandler.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;


import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;


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
     * @return the socket / SSL socket
     * @throws UnknownHostException In case of unknown host
     * @throws IOException In case of an I/O error
     */
    Socket createSocket(String hostname, int port, boolean secureConnection) throws UnknownHostException, IOException;

    
    /**
     * Define the socket timeout
     *
     * @param timeout the socket timeout
     */
    void setSocketTimeout(int timeout);
}
