/*
 * ICAPSocket.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

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
    
    private String requestIdentifier;
    private String connection;
    private Socket socket;
    private ChunkedInputStream is;
    private OutputStream os;


    /**
     * Constructor for ICAPSocket
     *
     * @param requestIdentifier the request identifier
     * @param host the host
     * @param port the port
     * @param service the service
     * @throws IOException In case of an I/O error
     */
    public ICAPSocket(String requestIdentifier, String host, int port, String service) throws IOException {
        this.requestIdentifier = requestIdentifier;
        this.connection = "" + host + ":" + port + "/" + service;
        LOG.debug(requestIdentifier + "Send create socket to [" + connection + "]");

        try {
            socket = new Socket(host, port);
            is = new ChunkedInputStream(requestIdentifier, socket.getInputStream());
            os = socket.getOutputStream();
        } catch (IOException e) {
            LOG.warn(requestIdentifier + "Could not connect to [" + connection + "]: " + e.getMessage());
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
     * Flush the output stream
     *
     * @throws IOException In case of an I/O error
     */
    public void flush() throws IOException {
        os.flush();

        LOG.debug(requestIdentifier + "Flushed request of [" + connection + "]");
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
        long size = ICAPClientUtil.getInstance().copy(is, outputStream);
        LOG.debug(requestIdentifier + "Process content [" + connection + "] copied bytes " + size);        
        return size;
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
        ICAPHeaderInformation icapHeaderInformation = null;
        Map<String, List<String>> header = readHTTPHeader(separator, bufferSize);
        if (header.containsKey(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE) && !header.get(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE).isEmpty()) {            
            String protocolHeaderLine = header.get(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE).get(0); // parse protocol line
            if (protocolHeaderLine != null && !protocolHeaderLine.isBlank()) {
                icapHeaderInformation = ICAPParser.getInstance().parseICAPHeaderInformation(protocolHeaderLine);
                LOG.debug(requestIdentifier + "Received ICAP response status: " + protocolHeaderLine);
            }
        }

        if (icapHeaderInformation == null) {
            icapHeaderInformation = new ICAPHeaderInformation();
        }

        // parse header values
        icapHeaderInformation.setHeaders(header);
        return icapHeaderInformation;
    }

    
    /**
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() throws IOException {
        LOG.debug(requestIdentifier + "Close socket of [" + connection + "]");

        close(is);
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
