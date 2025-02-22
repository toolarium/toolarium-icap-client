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
import java.nio.charset.StandardCharsets;
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
    private static final Charset StandardCharsetsUTF8 = StandardCharsets.UTF_8;

    private final String requestIdentifier;
    private final String connection;
    private final Socket socket;
    private final ChunkedInputStream is;
    private final OutputStream os;


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
        this.requestIdentifier = requestIdentifier;
        this.connection = String.format("%s:%d/%s", host, port, service);
        if (LOG.isDebugEnabled()) {
            LOG.debug("{}Send create socket to [{}]", requestIdentifier, connection);
        }

        try {
            socket = connectionManager.createSocket(host, port, secureConnection, maxConnectionTimeout, maxReadTimeout);
            is = new ChunkedInputStream(requestIdentifier, socket.getInputStream());
            os = socket.getOutputStream();
        } catch (IOException e) {
            LOG.warn("{}Could not connect to [{}]: {}", requestIdentifier, connection, e.getMessage());
            throw e;
        }
    }


    /**
     * Write content
     *
     * @param content the content to write
     * @throws IOException In case of an I/O error
     */
    public void write(String content) throws IOException {
        if (LOG.isDebugEnabled() && content.length() > 10) {
            LOG.debug("{}Send request:\n{}", requestIdentifier, content);
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
            LOG.debug("{}Flushed request of [{}]", requestIdentifier, connection);
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
                outputStream.write(buf, 0, length);
                totalSize += length;
            }
        } catch (RuntimeException ex) {
            LOG.debug("Could not transfer all bytes from input to output stream: {}", ex.getMessage());
            totalSize = -1;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("{}Process content [{}] copied bytes {}", requestIdentifier, connection, totalSize);
        }
        return totalSize;
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

        // read http headers
        Map<String, List<String>> headers = readHTTPHeader(separator, bufferSize);
        if (LOG.isDebugEnabled()) {
            LOG.debug("{}Response headers: {}", requestIdentifier, headers);
        }

        ICAPHeaderInformation icapHeaderInformation = null;
        if (headers.containsKey(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE) && !headers.get(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE).isEmpty()) {
            String protocolHeaderLine = headers.get(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE).get(0); // parse protocol line
            if (protocolHeaderLine != null && !protocolHeaderLine.isBlank()) {
                icapHeaderInformation = ICAPParser.getInstance().parseICAPHeaderInformation(protocolHeaderLine);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{}Received ICAP response status: {}", requestIdentifier, protocolHeaderLine);
                }
            }
        }

        if (icapHeaderInformation == null) {
            icapHeaderInformation = new ICAPHeaderInformation.Builder().withHeaders(headers).build();
        } else {
            // parse headers values
            icapHeaderInformation.setHeaders(headers);
        }

        return icapHeaderInformation;
    }


    /**
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{}Close socket of [{}]", requestIdentifier, connection);
        }

        close(is);
        os.flush();
        close(os);
        socket.close();
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
}
