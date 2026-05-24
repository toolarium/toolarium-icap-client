/*
 * ICAPSocket.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import com.github.toolarium.icap.client.ICAPConnectionManager;
import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.impl.parser.ICAPParser;
import com.github.toolarium.icap.client.util.ICAPClientUtil;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The ICAP socket
 *
 * @author Patrick Meier
 */
public class ICAPSocket implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ICAPSocket.class);
    private static final Charset StandardCharsetsUTF8 = Charset.forName("UTF-8");
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static volatile boolean poolingUnsupportedWarned;

    private ICAPConnectionManager connectionManager;
    private String requestIdentifier;
    private String connection;
    private String host;
    private int port;
    private boolean secureConnection;
    private Socket socket;
    private ChunkedInputStream is;
    private OutputStream os;
    private boolean healthy;
    private boolean serverKeepAlive;


    /**
     * Constructor for ICAPSocket
     *
     * @param connectionManager the connection manager
     * @param requestIdentifier the request identifier
     * @param host the host
     * @param port the port
     * @param service the service
     * @param secureConnection true to establish a secured connection
     * @param maxConnectionTimeout the max connection timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     * @param maxReadTimeout the max read timeout in milliseconds. By default there is no timeout set (null). A timeout of null or zero are interpreted as an infinite timeout. The connection will then block.
     * @throws IOException In case of an I/O error
     */
    public ICAPSocket(ICAPConnectionManager connectionManager, String requestIdentifier, String host, int port, String service, boolean secureConnection, Integer maxConnectionTimeout, Integer maxReadTimeout) throws IOException {
        this.connectionManager = connectionManager;
        this.requestIdentifier = requestIdentifier;
        this.host = host;
        this.port = port;
        this.secureConnection = secureConnection;
        this.connection = "" + host + ":" + port + "/" + service;
        this.healthy = true;
        this.serverKeepAlive = false;
        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Send create socket to [" + connection + "]");
        }

        IOException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                socket = connectionManager.createSocket(host, port, secureConnection, maxConnectionTimeout, maxReadTimeout);
                is = new ChunkedInputStream(requestIdentifier, socket.getInputStream());
                os = socket.getOutputStream();
                return;
            } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    LOG.warn(requestIdentifier + "Connection attempt " + (attempt + 1) + " to [" + connection + "] failed: " + e.getMessage() + ", retrying...");
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            } catch (IOException e) {
                LOG.warn(requestIdentifier + "Could not connect to [" + connection + "]: " + e.getMessage());
                throw e;
            }
        }

        LOG.warn(requestIdentifier + "Could not connect to [" + connection + "] after " + (MAX_RETRIES + 1) + " attempts: " + lastException.getMessage());
        throw lastException;
    }

    
    /**
     * Write content
     *
     * @param content the content to write
     * @throws IOException In case of an I/O error
     */
    public void write(String content) throws IOException {
        if (LOG.isDebugEnabled() && content.length() > 10) {
            LOG.debug(requestIdentifier + "Send request:\n" + content);
        }
        
        write(content.getBytes(StandardCharsetsUTF8));
    }

    
    /**
     * Write some bytes
     *
     * @param bytes the bytes to write
     * @throws IOException In case of an I/O error
     */
    public void write(byte[] bytes) throws IOException {
        os.write(bytes);
    }

    
    /**
     * Write some bytes
     *
     * @param bytes the bytes to write
     * @param offset the offset
     * @param length the length
     * @throws IOException In case of an I/O error
     */
    public void write(byte[] bytes, int offset, int length) throws IOException {
        os.write(bytes, offset, length);
    }


    /**
     * Flush the output stream
     *
     * @throws IOException In case of an I/O error
     */
    public void flush() throws IOException {
        os.flush();

        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Flushed request of [" + connection + "]");
        }
    }

    
    /**
     * Receive an expected ICAP header as response of a request.
     * 
     * @param separator the separator
     * @param bufferSize the buffer size
     * @return the response header status
     * @throws IOException In case of an I/O error
     */
    public Map<String, List<String>> readHTTPHeader(final String separator, final int bufferSize) throws IOException {
        return is.readHeader();
    }


    /**
     * Write the server response.
     * 
     * @param outputStream the output stream
     * @return the copied bytes
     * @throws IOException In case of an I/O error
     */
    public long processContent(OutputStream outputStream) throws IOException {
        if (is == null || outputStream == null) {
            return 0;
        }
        
        long totalSize = 0;
        
        try {
            byte[] buf = new byte[ICAPClientUtil.INTERNAL_BUFFER_SIZE];
            int length;
            while ((length = is.read(buf)) > 0) {
                if (length > 0) {
                    outputStream.write(buf, 0, length);
                }
                totalSize += length;
            }
        } catch (RuntimeException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Could not transfer all bytes from input to output stream: " + ex.getMessage(), ex);
            }
            LOG.warn(requestIdentifier + "Could not transfer all bytes from input to output stream: " + ex.getMessage());
            throw ex;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Process content [" + connection + "] copied bytes " + totalSize);
        }
        return totalSize;
    }


    /**
     * Get trailer headers parsed after the chunked body (RFC 3507 §4.3.1).
     *
     * @return the trailer headers, or null if none present
     */
    public Map<String, List<String>> getTrailers() {
        if (is != null) {
            return is.getTrailers();
        }
        return null;
    }


    /**
     * Read the ICAP response
     *
     * @param requestIdentifier the request identifier
     * @param separator the separator
     * @param bufferSize the buffer size
     * @return the ICAP response
     * @throws IOException In case of an I/O error
     */
    public ICAPHeaderInformation readICAPResponse(String requestIdentifier, final String separator, final int bufferSize) throws IOException {
        
        // read http header
        Map<String, List<String>> header = readHTTPHeader(separator, bufferSize);
        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Response header: " + header);
        }
        
        ICAPHeaderInformation icapHeaderInformation = null;
        if (header.containsKey(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE) && !header.get(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE).isEmpty()) {            
            String protocolHeaderLine = header.get(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE).get(0); // parse protocol line
            if (protocolHeaderLine != null && !protocolHeaderLine.isBlank()) {
                icapHeaderInformation = ICAPParser.getInstance().parseICAPHeaderInformation(protocolHeaderLine);
                if (LOG.isDebugEnabled()) {
                    LOG.debug(requestIdentifier + "Received ICAP response status: " + protocolHeaderLine);
                }
            }
        }

        if (icapHeaderInformation == null) {
            icapHeaderInformation = new ICAPHeaderInformation();
        }

        // parse header values
        icapHeaderInformation.setHeaders(header);

        // track server's connection preference (RFC 3507 §4.1)
        if (header.containsKey(ICAPConstants.HEADER_KEY_CONNECTION)) {
            List<String> connValues = header.get(ICAPConstants.HEADER_KEY_CONNECTION);
            if (connValues != null && !connValues.isEmpty()) {
                String connValue = connValues.get(0).trim();
                if (connValue.equalsIgnoreCase("keep-alive")) {
                    serverKeepAlive = true;
                } else if (connValue.equalsIgnoreCase("close")) {
                    serverKeepAlive = false;
                }
            }
        }

        return icapHeaderInformation;
    }

    
    /**
     * Mark the socket as unhealthy so it will be closed instead of returned to pool.
     */
    public void markUnhealthy() {
        this.healthy = false;
    }


    /**
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() throws IOException {
        if (healthy && serverKeepAlive && connectionManager != null && socket != null && !socket.isClosed()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Releasing socket of [" + connection + "] to pool");
            }
            connectionManager.releaseSocket(host, port, secureConnection, socket);
            return;
        }

        if (!healthy) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Closing unhealthy socket of [" + connection + "]");
            }
        } else if (connectionManager != null && connectionManager.isPoolingEnabled() && !serverKeepAlive) {
            if (!poolingUnsupportedWarned) {
                poolingUnsupportedWarned = true;
                LOG.warn(requestIdentifier + "Connection pooling is enabled but server [" + connection + "] does not support keep-alive, pooling will not be used");
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Closing socket of [" + connection + "], server does not support keep-alive");
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Closing socket of [" + connection + "]");
            }
        }

        destroySocket();
    }


    /**
     * Close
     *
     * @param c the closeable
     */
    private void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                // NOP
            }
        }
    }


    /**
     * Destroy the socket by closing all streams and the socket itself.
     */
    private void destroySocket() {
        try {
            close(is);
        } finally {
            try {
                if (os != null) {
                    os.flush();
                }
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(requestIdentifier + "Could not flush output stream of [" + connection + "]: " + e.getMessage());
                }
            } finally {
                try {
                    close(os);
                } finally {
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        // NOP
                    }
                }
            }
        }
    }
}
