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
    private static final int MAX_HEADER_COUNT = 128;
    private static final int MAX_HEADER_BYTES = 64 * 1024;

    private String requestIdentifier;
    private int currentChunkPos;
    private int currentChunkSize;
    private boolean ended;
    private Map<String, List<String>> headers;
    private Map<String, List<String>> trailers;
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

        if (ended) {
            return -1;
        }

        int sizeToRead = Math.min(len, currentChunkSize - currentChunkPos);
        if (maxChunkSize > 0 && (sizeToRead + chunkSize) > maxChunkSize) {
            sizeToRead = Long.valueOf(maxChunkSize - chunkSize).intValue();
            ended = true;
        }

        int readBytes = 0;
        try {
            readBytes = super.read(b, off, sizeToRead);

            if (LOG.isDebugEnabled()) {
                int dumpLen = Math.min(sizeToRead, 256);
                LOG.debug(requestIdentifier + "Raw data (" + sizeToRead + " bytes, showing " + dumpLen + ")\n" + HexDump.getInstance().hexDump(new String(b, off, dumpLen)));
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
     * Get the trailer headers parsed after the last chunk (RFC 3507 §4.3.1).
     *
     * @return the trailer headers, or null if none present
     */
    public Map<String, List<String>> getTrailers() {
        return trailers;
    }
    
    
    /**
     * Read the header
     * 
     * @return the header
     * @throws IOException If an IO error occurs.
     */
    public Map<String, List<String>> readHeader() throws IOException {
        List<String> headerLines = new ArrayList<>();
        StringBuilder orgHeaderBuilder = new StringBuilder();
        int totalHeaderBytes = 0;
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        String line = null;
        do {
            lineBuffer.reset();
            line = readLine(lineBuffer);
            if (line != null && line.length() > 0) {
                headerLines.add(line);
                orgHeaderBuilder.append(line).append(NEWLINE);
                totalHeaderBytes += line.length() + 2;
                if (headerLines.size() > MAX_HEADER_COUNT) {
                    throw new IOException("Header count exceeds maximum of " + MAX_HEADER_COUNT);
                }
                if (totalHeaderBytes > MAX_HEADER_BYTES) {
                    throw new IOException("Header size exceeds maximum of " + MAX_HEADER_BYTES + " bytes");
                }
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
        
        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "HTTP headers:\n" + orgHeaderBuilder);
        }
        
        return headers;
    }

    
    /**
     * Read the next chunk.
     * 
     * @return the chunk size
     * @throws IOException If an IO error occurs.
     */
    protected int nextChunk() throws IOException {
        currentChunkPos = 0;
        currentChunkSize = 0;

        // skip trailing newlines from the previous chunk
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

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
            // Always read the ICAP chunk size after the HTTP response headers,
            // regardless of whether the HTTP response uses Transfer-Encoding or Content-Length.
            line = readLine(new ByteArrayOutputStream());
            if (line != null && line.length() == 0) {
                line = readLine(new ByteArrayOutputStream());
            }
        }

        if (line.toString().startsWith("GET") || line.toString().startsWith("POST")) {
            readHeader();
            line = readLine(new ByteArrayOutputStream());

            // In RESPMOD, response headers follow the request headers (RFC 3507 4.8.2).
            // In REQMOD with body only, the chunk size follows directly (RFC 3507 4.8.3).
            if (line != null && line.startsWith("HTTP")) {
                readHeader();
                line = readLine(new ByteArrayOutputStream());
                if (line != null && line.length() == 0) {
                    line = readLine(new ByteArrayOutputStream());
                }
            }
        }

        int semiIdx = line.indexOf(';');
        if (semiIdx >= 0) {
            line = line.substring(0, semiIdx);
        }
        
        try {
            currentChunkSize = Integer.parseInt(line.trim(), 16);
        } catch (NumberFormatException e) {
            throw new IOException("Bad chunk header [" + line + "]:" + e.getMessage());
        }

        // RFC 3507 §4.3.1: after the zero-length terminating chunk, read any trailer headers
        if (currentChunkSize == 0) {
            readTrailers();
            ended = true;
        }

        return currentChunkSize;
    }


    /**
     * Read trailer headers after the final zero-length chunk (RFC 3507 §4.3.1).
     * Trailers appear between the "0\r\n" and the final "\r\n".
     *
     * @throws IOException In case of an I/O error
     */
    private void readTrailers() throws IOException {
        List<String> trailerLines = new ArrayList<>();
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        String line;
        do {
            lineBuffer.reset();
            line = readLine(lineBuffer);
            if (line != null && !line.isEmpty()) {
                trailerLines.add(line);
            }
        } while (line != null && !line.isEmpty());

        if (!trailerLines.isEmpty()) {
            trailers = ICAPParser.getInstance().parseHeader(trailerLines);
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Trailer headers: " + trailers);
            }
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
            if (b != LF && b != -1) {
                buffer.write(b);
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
