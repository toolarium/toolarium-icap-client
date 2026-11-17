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
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile("(?im)^((?:Proxy-)?Authorization:).*$");
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static final long MAX_CONNECT_RETRY_TIME_MS = 60000;
    private static final long WATCHDOG_INTERVAL_MS = 1000;
    private static final ScheduledThreadPoolExecutor WATCHDOG = createWatchdog();
    private static volatile boolean poolingUnsupportedWarned;

    private final ICAPConnectionManager connectionManager;
    private final String requestIdentifier;
    private final String connection;
    private final String connectionLabel;
    private final String host;
    private final int port;
    private final boolean secureConnection;
    private final Integer maxConnectionTimeout;
    private final Integer maxReadTimeout;
    private final long deadline;
    private volatile Socket socket;
    private ChunkedInputStream is;
    private OutputStream os;
    private boolean healthy;
    private boolean serverKeepAlive;
    private boolean responseComplete;
    private volatile boolean receivedData;
    private volatile long writeStartedAt;
    private volatile boolean writeTimedOut;
    private ScheduledFuture<?> watchdog;


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
        this(connectionManager, requestIdentifier, host, port, service, secureConnection, maxConnectionTimeout, maxReadTimeout, null);
    }


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
     * @param maxRequestTimeout the max total time in milliseconds of the whole request including connect, send and receive. A timeout of null or zero means no limit.
     * @throws IOException In case of an I/O error
     */
    public ICAPSocket(ICAPConnectionManager connectionManager, String requestIdentifier, String host, int port, String service, boolean secureConnection,
                      Integer maxConnectionTimeout, Integer maxReadTimeout, Integer maxRequestTimeout) throws IOException {
        this.connectionManager = connectionManager;
        this.requestIdentifier = requestIdentifier;
        this.host = host;
        this.port = port;
        this.secureConnection = secureConnection;
        this.maxConnectionTimeout = maxConnectionTimeout;
        this.maxReadTimeout = maxReadTimeout;
        this.connection = "" + host + ":" + port + "/" + service;
        this.connectionLabel = "[" + connection + "]";
        this.healthy = true;
        this.serverKeepAlive = false;
        this.responseComplete = false;
        this.receivedData = false;
        this.writeTimedOut = false;

        if (maxRequestTimeout != null && maxRequestTimeout.intValue() > 0) {
            this.deadline = System.currentTimeMillis() + maxRequestTimeout.intValue();
        } else {
            this.deadline = 0;
        }

        connect();
    }


    /**
     * Write content
     *
     * @param content the content to write
     * @throws IOException In case of an I/O error
     */
    public void write(String content) throws IOException {
        if (LOG.isDebugEnabled() && content.length() > 10) {
            LOG.debug(requestIdentifier + "Send request:\n" + AUTHORIZATION_PATTERN.matcher(content).replaceAll("$1 ***"));
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
        guardWrite(() -> os.write(bytes));
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
        guardWrite(() -> os.write(bytes, offset, length));
    }


    /**
     * Flush the output stream
     *
     * @throws IOException In case of an I/O error
     */
    public void flush() throws IOException {
        guardWrite(() -> os.flush());

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
        return processContent(outputStream, Long.MAX_VALUE);
    }


    /**
     * Write the server response.
     *
     * @param outputStream the output stream
     * @param maxContentSize the max size of the response content, a bigger response is rejected
     * @return the copied bytes
     * @throws IOException In case of an I/O error or if the response content exceeds the max size
     */
    public long processContent(OutputStream outputStream, long maxContentSize) throws IOException {
        if (is == null || outputStream == null) {
            return 0;
        }

        long totalSize = 0;

        try {
            byte[] buf = new byte[ICAPClientUtil.INTERNAL_BUFFER_SIZE];
            int length;
            while ((length = is.read(buf)) > 0) {
                totalSize += length;
                if (totalSize > maxContentSize) {
                    throw new IOException("Response content of [" + connection + "] exceeds the maximum of " + maxContentSize + " bytes");
                }
                outputStream.write(buf, 0, length);
            }
        } catch (RuntimeException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Could not transfer all bytes from input to output stream: " + ex.getMessage(), ex);
            }
            throw ex;
        }

        responseComplete = is.isComplete();
        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Process content [" + connection + "] copied bytes " + totalSize + " (complete: " + responseComplete + ")");
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
     * Get the status code of the encapsulated HTTP response of the server.
     *
     * @return the status code or 0 if the response contains no HTTP response
     */
    public int getEncapsulatedHttpStatus() {
        if (is != null) {
            return is.getHttpStatus();
        }
        return 0;
    }


    /**
     * Check if any data was received from the server on the current connection.
     *
     * @return true if data was received
     */
    public boolean hasReceivedData() {
        return receivedData;
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

        // the response is complete if there is no encapsulated body to read (RFC 3507 §4.4.1)
        responseComplete = hasNullBody(header);
        return icapHeaderInformation;
    }


    /**
     * Mark the socket as unhealthy so it will be closed instead of returned to pool.
     */
    public void markUnhealthy() {
        this.healthy = false;
    }


    /**
     * Close the current connection and establish a new one. The pooled connections of the host are closed as well,
     * because they were idle at least as long as the current one which turned out to be closed by the server.
     *
     * @throws IOException In case of an I/O error
     */
    public void reconnect() throws IOException {
        destroySocket();
        if (connectionManager != null) {
            connectionManager.evictPool(host, port, secureConnection);
        }

        healthy = true;
        serverKeepAlive = false;
        responseComplete = false;
        receivedData = false;
        writeTimedOut = false;
        connect();
    }


    /**
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() throws IOException {
        stopWatchdog();

        if (healthy && serverKeepAlive && responseComplete && connectionManager != null && socket != null && !socket.isClosed()) {
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
        } else if (serverKeepAlive && !responseComplete) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Closing socket of [" + connection + "], response was not read completely");
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Closing socket of [" + connection + "]");
            }
        }

        destroySocket();
    }


    /**
     * Establish the connection, connection failures are retried.
     *
     * @throws IOException In case of an I/O error
     */
    private void connect() throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Send create socket to [" + connection + "]");
        }

        final long start = System.currentTimeMillis();
        IOException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                checkDeadline();

                // the connect and the TLS handshake must not exceed the request deadline
                Integer connectTimeout = maxConnectionTimeout;
                Integer readTimeout = maxReadTimeout;
                if (deadline > 0) {
                    long remaining = deadline - System.currentTimeMillis();
                    connectTimeout = limitTimeout(resolveTimeout(maxConnectionTimeout, connectionManager.getDefaultSocketConnectionTimeout()), remaining);
                    readTimeout = limitTimeout(resolveTimeout(maxReadTimeout, connectionManager.getDefaultSocketReadTimeout()), remaining);
                }

                socket = connectionManager.createSocket(host, port, secureConnection, connectTimeout, readTimeout);
                try {
                    is = new ChunkedInputStream(requestIdentifier, new DeadlineInputStream(socket.getInputStream(), socket, socket.getSoTimeout()));
                    os = new BufferedOutputStream(socket.getOutputStream(), 8192);
                } catch (IOException | RuntimeException e) {
                    closeSocketQuietly();
                    throw e;
                }
                return;
            } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_DELAY_MS * (attempt + 1) + ThreadLocalRandom.current().nextLong(RETRY_DELAY_MS / 2 + 1);
                    long now = System.currentTimeMillis();
                    if ((now + delay - start) > MAX_CONNECT_RETRY_TIME_MS || (deadline > 0 && now + delay >= deadline)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(requestIdentifier + "Connection attempt " + (attempt + 1) + " to [" + connection + "] failed: " + e.getMessage() + ", retry time exceeded.", e);
                        }
                        break;
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug(requestIdentifier + "Connection attempt " + (attempt + 1) + " to [" + connection + "] failed: " + e.getMessage() + ", retrying...", e);
                    }
                    LOG.warn(requestIdentifier + "Connection attempt " + (attempt + 1) + " to [" + connection + "] failed: " + e.getMessage() + ", retrying...");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(requestIdentifier + "Could not connect to [" + connection + "]: " + e.getMessage(), e);
                }
                throw e;
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Could not connect to [" + connection + "]: " + lastException.getMessage(), lastException);
        }
        throw lastException;
    }


    /**
     * Resolve a timeout: the request specific timeout overrules the default.
     *
     * @param timeout the request specific timeout or null
     * @param defaultTimeout the default timeout or null
     * @return the timeout in milliseconds, 0 means infinite
     */
    private int resolveTimeout(Integer timeout, Integer defaultTimeout) {
        if (timeout != null && timeout.intValue() >= 0) {
            return timeout.intValue();
        }
        if (defaultTimeout != null && defaultTimeout.intValue() >= 0) {
            return defaultTimeout.intValue();
        }
        return 0;
    }


    /**
     * Limit a timeout to the remaining time of the deadline.
     *
     * @param timeout the timeout in milliseconds, 0 means infinite
     * @param remaining the remaining time in milliseconds
     * @return the limited timeout
     * @throws SocketTimeoutException In case there is no remaining time
     */
    private Integer limitTimeout(int timeout, long remaining) throws SocketTimeoutException {
        if (remaining <= 0) {
            throw new SocketTimeoutException("Max request timeout exceeded for " + connectionLabel);
        }

        if (timeout == 0 || timeout > remaining) {
            return Integer.valueOf((int) Math.min(remaining, Integer.MAX_VALUE));
        }
        return Integer.valueOf(timeout);
    }


    /**
     * Run a write operation guarded by the watchdog: a socket write has no timeout and blocks as long as the server
     * does not read. If a max request timeout is defined, the watchdog closes the socket when a write exceeds the deadline.
     *
     * @param operation the write operation
     * @throws IOException In case of an I/O error or a timeout
     */
    private void guardWrite(IOOperation operation) throws IOException {
        checkDeadline();
        startWatchdog();
        writeStartedAt = System.currentTimeMillis();
        try {
            operation.run();
        } catch (IOException e) {
            if (writeTimedOut) {
                SocketTimeoutException timeoutException = new SocketTimeoutException("Write timed out to " + connectionLabel);
                timeoutException.initCause(e);
                throw timeoutException;
            }
            throw e;
        } finally {
            writeStartedAt = 0;
        }
    }


    /**
     * Start the watchdog if there is a deadline (max request timeout), without deadline a write has no timeout
     */
    private void startWatchdog() {
        if (watchdog != null || deadline <= 0) {
            return;
        }

        long interval = WATCHDOG_INTERVAL_MS;
        long remaining = deadline - System.currentTimeMillis();
        if (remaining < 2 * WATCHDOG_INTERVAL_MS) {
            interval = Math.max(10, remaining / 2);
        }
        watchdog = WATCHDOG.scheduleWithFixedDelay(this::checkWrite, interval, interval, TimeUnit.MILLISECONDS);
    }


    /**
     * Stop the watchdog
     */
    private void stopWatchdog() {
        if (watchdog != null) {
            watchdog.cancel(false);
            watchdog = null;
        }
    }


    /**
     * Check if a running write exceeds the deadline, in this case the socket is closed.
     */
    private void checkWrite() {
        long started = writeStartedAt;
        if (started <= 0) {
            return;
        }

        if (deadline > 0 && System.currentTimeMillis() >= deadline) {
            writeTimedOut = true;
            healthy = false;
            Socket currentSocket = socket;
            if (currentSocket != null) {
                try {
                    currentSocket.close();
                } catch (IOException e) {
                    // NOP
                }
            }
        }
    }


    /**
     * Check if the encapsulated header declares a null-body
     *
     * @param header the header
     * @return true if there is no body
     */
    private boolean hasNullBody(Map<String, List<String>> header) {
        List<String> encapsulated = header.get(ICAPConstants.HEADER_KEY_ENCAPSULATED);
        if (encapsulated == null) {
            return false;
        }

        for (String entry : encapsulated) {
            if (entry != null && entry.trim().toLowerCase().startsWith("null-body")) {
                return true;
            }
        }
        return false;
    }


    /**
     * Check if the request deadline is exceeded
     *
     * @throws SocketTimeoutException In case the deadline is exceeded
     */
    private void checkDeadline() throws SocketTimeoutException {
        if (deadline > 0 && System.currentTimeMillis() >= deadline) {
            throw new SocketTimeoutException("Max request timeout exceeded for " + connectionLabel);
        }
    }


    /**
     * Close quietly
     *
     * @param c the closeable
     */
    private void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                // NOP
            }
        }
    }


    /**
     * Close the socket quietly
     */
    private void closeSocketQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            // NOP
        }
        socket = null;
    }


    /**
     * Destroy the socket by closing all streams and the socket itself.
     */
    private void destroySocket() {
        try {
            closeQuietly(is);
        } finally {
            try {
                if (os != null && !writeTimedOut) {
                    os.flush();
                }
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(requestIdentifier + "Could not flush output stream of [" + connection + "]: " + e.getMessage());
                }
            } finally {
                try {
                    closeQuietly(os);
                } finally {
                    closeSocketQuietly();
                }
            }
        }
    }


    /**
     * Create the watchdog executor
     *
     * @return the watchdog executor
     */
    private static ScheduledThreadPoolExecutor createWatchdog() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "icap-client-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }


    /**
     * An I/O operation
     *
     * @author Patrick Meier
     */
    @FunctionalInterface
    private interface IOOperation {

        /**
         * Run the operation
         *
         * @throws IOException In case of an I/O error
         */
        void run() throws IOException;
    }


    /**
     * Input stream which limits each blocking read to the remaining time of the request deadline and tracks if data was received.
     *
     * @author patrick
     */
    private final class DeadlineInputStream extends FilterInputStream {
        private final Socket streamSocket;
        private final int readTimeout;


        /**
         * Constructor for DeadlineInputStream
         *
         * @param in the input stream
         * @param streamSocket the socket of the stream
         * @param readTimeout the read timeout of the socket
         */
        DeadlineInputStream(InputStream in, Socket streamSocket, int readTimeout) {
            super(in);
            this.streamSocket = streamSocket;
            this.readTimeout = readTimeout;
        }


        /**
         * @see java.io.FilterInputStream#read()
         */
        @Override
        public int read() throws IOException {
            adjustTimeout();
            int b = super.read();
            if (b >= 0) {
                receivedData = true;
            }
            return b;
        }


        /**
         * @see java.io.FilterInputStream#read(byte[], int, int)
         */
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            adjustTimeout();
            int readBytes = super.read(b, off, len);
            if (readBytes > 0) {
                receivedData = true;
            }
            return readBytes;
        }


        /**
         * Adjust the socket timeout to the remaining time of the deadline
         *
         * @throws SocketTimeoutException In case the deadline is exceeded
         * @throws IOException In case the socket timeout could not be set
         */
        private void adjustTimeout() throws IOException {
            if (deadline <= 0) {
                return;
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new SocketTimeoutException("Max request timeout exceeded");
            }

            int timeout = (int) Math.min(remaining, Integer.MAX_VALUE);
            if (readTimeout > 0 && readTimeout < timeout) {
                timeout = readTimeout;
            }
            streamSocket.setSoTimeout(timeout);
        }
    }
}
