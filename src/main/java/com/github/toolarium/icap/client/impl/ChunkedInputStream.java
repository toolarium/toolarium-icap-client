/*
 * ChunkedInputStream.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.impl.parser.ICAPParser;
import com.github.toolarium.icap.client.util.HexDump;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implements a chunk input stram
 *  
 * @author patrick
 */
public class ChunkedInputStream extends BufferedInputStream {
    private static final Logger LOG = LoggerFactory.getLogger(ChunkedInputStream.class);
    private static final Charset StandardCharsetsUTF8 = Charset.forName("UTF-8");
    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final String NEWLINE = "" + (char)CR + (char)LF;

    private String requestIdentifier;
    private int currentChunkPos;
    private int currentChunkSize;
    private boolean ended;
    private Map<String, List<String>> headers;
    private long chunkSize;
    private long maxChunkSize;

    
    /**
     * Constructor for ChunkedInputStream
     *
     * @param requestIdentifier the request identifier
     * @param is the input stream
     * @throws IOException In case of a stream error.
     */
    public ChunkedInputStream(final String requestIdentifier, final InputStream is) throws IOException {
        super(is);
        if (is == null) {
            throw new IOException("Invalid stream!");
        }
        
        this.requestIdentifier = requestIdentifier;
        currentChunkPos = 0;
        currentChunkSize = 0;
        chunkSize = 0;
        maxChunkSize = -1;
        ended = false;
    }
    

    /**
     * @see java.io.InputStream#read()
     */
    @Override
    public int read() throws IOException {
        if (ended) {
            return -1;
        }
  
        if (currentChunkPos >= currentChunkSize) {
            nextChunk();
        }

        try {
            return super.read();
        } finally {
            currentChunkPos++;
            chunkSize++;
        }
    }

    
    /**
     * @see java.io.InputStream#read(byte[])
     */
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }
    

    /**
     * @see java.io.InputStream#read(byte[], int, int)
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (ended) {
            return -1;
        }

        if (currentChunkPos >= currentChunkSize) {
            nextChunk();
        }

        int sizeToRead = Math.min(len, currentChunkSize - currentChunkPos);
        if (maxChunkSize > 0 && (sizeToRead + chunkSize) > maxChunkSize) {
            sizeToRead = Long.valueOf(maxChunkSize - chunkSize).intValue();
            ended = true;
        } else {
            
            if (headers != null 
                    && headers.containsKey(ICAPConstants.HEADER_KEY_TRANSFER_ENCODING)
                    && !headers.get(ICAPConstants.HEADER_KEY_TRANSFER_ENCODING).isEmpty()
                    && headers.get(ICAPConstants.HEADER_KEY_TRANSFER_ENCODING).get(0).equalsIgnoreCase("chunked")
                    && headers.containsKey(ICAPConstants.HEADER_KEY_CONTENT_LENGTH)
                    && !headers.get(ICAPConstants.HEADER_KEY_CONTENT_LENGTH).isEmpty()
                    && !headers.get(ICAPConstants.HEADER_KEY_CONTENT_LENGTH).get(0).isBlank()) {
                // NOP
            } else {                
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                readLine(buffer);
                sizeToRead = len;
                String size = buffer.toString(StandardCharsetsUTF8);
                if (!size.isBlank()) {
                    try {
                        sizeToRead = Integer.parseInt(size, 16);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid size [" + size + "]:" + e.getMessage());
                    }
                }
            }
        }
        
        int readBytes = 0;
        try {
            readBytes = super.read(b, off, sizeToRead);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Raw data\n" + HexDump.getInstance().hexDump(new String(b, off, off + readBytes)));
            }
            
            if (maxChunkSize <= 0) {
                super.read(new byte[3], 0, 3); // read 0\r\n
            }
            
            return readBytes;
        } finally {
            currentChunkPos += readBytes;
            chunkSize += readBytes;
        }
    }

    
    /**
     * @see java.io.InputStream#close()
     */
    @Override
    public void close() throws IOException {
        super.close();
    }
    
    
    /**
     * Get the headers
     *
     * @return the headers
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }
    
    
    /**
     * Read the header
     * 
     * @return the header
     * @throws IOException If an IO error occurs.
     */
    public Map<String, List<String>> readHeader() throws IOException {
        List<String> headerLines = new ArrayList<>();
        String orgHeader = "";
        String line = null;
        do {
            line = readLine(new ByteArrayOutputStream());
            if (line != null && line.length() > 0) {
                headerLines.add(line);
                orgHeader += line + NEWLINE;
            }
        } while (line != null && line.length() > 0);
            
        if (line == null) {
            ended = true;
        }

        headers = ICAPParser.getInstance().parseHeader(headerLines);       
        if (headers.containsKey(ICAPConstants.HEADER_KEY_CONTENT_LENGTH) && !headers.get(ICAPConstants.HEADER_KEY_CONTENT_LENGTH).isEmpty()) {
            try {
                maxChunkSize = Long.valueOf(headers.get(ICAPConstants.HEADER_KEY_CONTENT_LENGTH).get(0));
            } catch (NumberFormatException e) {
                // NOP
            }
        }
        
        LOG.debug(requestIdentifier + "HTTP headers:\n" + orgHeader);        
        return headers;
    }

    
    /**
     * Read the next chunk.
     * 
     * @return the chunk size
     * @throws IOException If an IO error occurs.
     */
    protected int nextChunk() throws IOException {
        if (headers != null && currentChunkSize == 1) {
            return currentChunkPos; // skip
        }

        currentChunkPos = 0;
        currentChunkSize = 0;

        // skip trailing newlines
        ByteArrayOutputStream  buffer = new ByteArrayOutputStream();

        int b = readNewline();
        if (b != LF && b != CR) {
            buffer.write(b);
        }
        
        // read first line
        String line = readLine(buffer);
        if (line == null || line.length() <= 0) {
            return -1;
        }

        if (line.toString().startsWith("HTTP")) {
            readHeader();
            if (headers == null || !headers.containsKey(ICAPConstants.HEADER_KEY_TRANSFER_ENCODING)
                    || headers.get(ICAPConstants.HEADER_KEY_TRANSFER_ENCODING).isEmpty()
                    || !headers.get(ICAPConstants.HEADER_KEY_TRANSFER_ENCODING).get(0).equalsIgnoreCase("chunked")) {
                currentChunkSize = 1; // ignore not a chunked content
                return currentChunkSize;
            }
            
            line = readLine(new ByteArrayOutputStream());
            if (line != null && line.length() == 0) {
                line = readLine(new ByteArrayOutputStream());
            }
        }

        if (line.endsWith(";")) {
            line = line.substring(0, line.length() - 1);
        }
        
        try {
            currentChunkSize = Integer.parseInt(line.trim(), 16);
            return currentChunkSize;
        } catch (NumberFormatException e) {
            throw new IOException("Bad chunk header [" + line + "]:" + e.getMessage());
        }
    }
    
    
    /**
     * Read next newline
     *
     * @return true if a newline was read
     * @throws IOException In case of an I/O error
     */
    private int readNewline() throws IOException {
        int b = super.read();
        if (b < 0) {
            return b;
        }

        if (b == LF) {
            return b;
        } 

        if (b == CR) {
            b = super.read();
            
            if (b != LF) {
                return b;
            } else {
                return b;
            }
        }
        
        return b;
    }
    
    
    /**
     * Read the next line
     *
     * @param buffer the buffer
     * @return null in case the stream has ended otherwise the read line. In case there was only \r\n it will return an empty string.
     * @throws IOException In case of an I/O error
     */
    private String readLine(ByteArrayOutputStream buffer) throws IOException {
        int b;
        while (((b = super.read()) != -1) &&  b != CR) {
            buffer.write(b);
        }

        if (b == CR) {
            b = super.read();
            if (b != LF) {
                ((PushbackInputStream)in).unread(1);
            }
        } else {
            return null;
        }
        
        if (buffer.size() == 0) {
            return "";
        }
        
        return new String(buffer.toByteArray(), 0, buffer.size(), StandardCharsetsUTF8);
    }
}
