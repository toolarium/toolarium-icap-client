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
     * @param maxRequestTimeout the max request timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     * @return the socket / SSL socket
     * @throws UnknownHostException In case of unknown host
     * @throws IOException In case of an I/O error
     */
    Socket createSocket(String hostname, int port, boolean secureConnection, Integer maxRequestTimeout) throws UnknownHostException, IOException;


    /**
     * Define the default socket timeout in milliseconds or null. A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     *
     * @param defaultSocketTimeout the default socket timeout in milliseconds or null.
     */
    void setDefaultSocketTimeout(Integer defaultSocketTimeout);

    /**
     * Define the default socket read timeout in milliseconds or null. A timeout of null or zero are interpreted as an infinite timeout.
     *
     * @param defaultReadTimeout the default socket timeout in milliseconds or null.
     */
    void setDefaultReadTimeout(int defaultReadTimeout);
}
