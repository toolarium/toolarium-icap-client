/*
 * ICAPClientImpl.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import com.github.toolarium.icap.client.ICAPClient;
import com.github.toolarium.icap.client.ICAPConnectionManager;
import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.dto.ICAPServiceInformation;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import com.github.toolarium.icap.client.exception.ICAPRequestException;
import com.github.toolarium.icap.client.exception.UnknownIOException;
import com.github.toolarium.icap.client.impl.dto.ICAPRemoteServiceConfigurationImpl;
import com.github.toolarium.icap.client.util.ICAPClientUtil;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implements an ICAP client.
 *
 * @author Patrick Meier
 */
public class ICAPClientImpl implements ICAPClient {
    private static final Logger LOG = LoggerFactory.getLogger(ICAPClientImpl.class);
    private static final String NEWLINE = "\r\n";
    private static final String ICAP_END_SEPARATOR = NEWLINE + NEWLINE;
    private static final String HTTP_END_SEPARATOR = "0" + ICAP_END_SEPARATOR;
    private static final int DEFAULT_PREVIEW_SIZE = 4096; // RFC 3507 §4.5: clients SHOULD support at least 4096 bytes
    private static final int MAX_PREVIEW_SIZE = 1024 * 1024;
    private static final int MAX_THREAT_CONTENT_SIZE = 64 * 1024;
    private static final long MIN_RESPONSE_CONTENT_MARGIN = 16L * 1024 * 1024;
    private static final Pattern API_VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+");
    private static final String NO_ICAP_SERVER_MESSAGE = "No ICAP server url defined, only FILEMOD is supported!";
    private static final String FILE_DELETED_REASON = "the file was deleted by a virus scanner.";
    private static final long FILE_SCAN_POLL_INTERVAL = 50;
    private static final String DEFAULT_FILE_EXTENSION = "tmp";
    private static final int MAX_FILE_EXTENSION_LENGTH = 16;

    private final ICAPConnectionManager connectionManager;
    private final ICAPServiceInformation serviceInformation;
    private volatile ICAPRemoteServiceConfiguration remoteServiceConfiguration;
    private final int bufferSize = 8192;
    private final String messageDigestAlgorithm = "SHA-256";
    private volatile boolean supportCompareVerifyIdenticalContent;
    private volatile ICAPRequestInformation defaultRequestInformation;


    /**
     * Constructor for ICAPClientImpl
     *
     * @param serviceInformation the service information
     * @param remoteServiceConfiguration the remote service configuration
     * @param connectionManager the connection manager
     */
    public ICAPClientImpl(ICAPConnectionManager connectionManager, ICAPServiceInformation serviceInformation, ICAPRemoteServiceConfiguration remoteServiceConfiguration) {
        this.connectionManager = connectionManager;
        this.serviceInformation = serviceInformation;
        this.remoteServiceConfiguration = remoteServiceConfiguration;
        this.supportCompareVerifyIdenticalContent = false;
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPClient#supportCompareVerifyIdenticalContent(boolean)
     */
    @Override
    public ICAPClient supportCompareVerifyIdenticalContent(boolean supportCompareVerifyIdenticalContent) {
        this.supportCompareVerifyIdenticalContent = supportCompareVerifyIdenticalContent;
        return this;
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPClient#setDefaultRequestInformation(com.github.toolarium.icap.client.dto.ICAPRequestInformation)
     */
    @Override
    public ICAPClient setDefaultRequestInformation(ICAPRequestInformation defaultRequestInformation) {
        this.defaultRequestInformation = defaultRequestInformation;
        return this;
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPClient#options()
     */
    @Override
    public ICAPRemoteServiceConfiguration options() throws IOException {
        remoteServiceConfiguration = null;
        return options(getEffectiveRequestInformation(null));
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPClient#options()
     */
    @Override
    public ICAPRemoteServiceConfiguration options(final ICAPRequestInformation requestInformation) throws IOException {
        ICAPRemoteServiceConfiguration currentConfig = remoteServiceConfiguration;
        if (currentConfig != null) {
            return currentConfig;
        }

        validateRequestInformation(requestInformation);
        if (serviceInformation == null) {
            throw new MalformedURLException(NO_ICAP_SERVER_MESSAGE);
        }
        validateServiceInformation();
        final String requestIdentifier = createRequestIdentifier("options", null);
        ICAPSocket icapSocket = null;
        try {
            icapSocket = new ICAPSocket(connectionManager, requestIdentifier, serviceInformation.getHostName(), serviceInformation.getServicePort(), serviceInformation.getServiceName(),
                                        serviceInformation.isSecureConnection(), requestInformation.getMaxConnectionTimeout(), requestInformation.getMaxReadTimeout(), requestInformation.getMaxRequestTimeout());
            icapSocket.write("OPTIONS icap://" + formatHost(serviceInformation.getHostName()) + ":" + serviceInformation.getServicePort() + "/" + serviceInformation.getServiceName() + " ICAP/" + requestInformation.getApiVersion() + NEWLINE
                             + "Host: " + formatHost(serviceInformation.getHostName()) + NEWLINE
                             + "User-Agent: " + sanitizeHeaderValue(requestInformation.getUserAgent()) + NEWLINE
                             + createAuthorizationHeader(requestInformation)
                             + createCustomHeaders(requestInformation)
                             + ICAPConstants.HEADER_KEY_ENCAPSULATED + ": null-body=0" + NEWLINE + NEWLINE);
            icapSocket.flush();

            ICAPHeaderInformation icapHeaderInformation = icapSocket.readICAPResponse(requestIdentifier, ICAP_END_SEPARATOR, bufferSize);
            if (icapHeaderInformation.getStatus() != 200) {
                throw new IOException("Could not resolve options!");
            }

            int serverPreviewSize = DEFAULT_PREVIEW_SIZE;
            String previewValue = getFirstHeaderValue(icapHeaderInformation, ICAPConstants.HEADER_KEY_PREVIEW);
            if (previewValue != null) {
                try {
                    serverPreviewSize = Integer.parseInt(previewValue.trim());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(requestIdentifier + "Server preview size: " + serverPreviewSize);
                    }
                } catch (NumberFormatException e) {
                    LOG.warn(requestIdentifier + "Could not parse server preview size [" + previewValue + "]: " + e.getMessage());
                }

                if (serverPreviewSize < 0) {
                    LOG.warn(requestIdentifier + "Invalid server preview size [" + serverPreviewSize + "], use default " + DEFAULT_PREVIEW_SIZE + ".");
                    serverPreviewSize = DEFAULT_PREVIEW_SIZE;
                } else if (serverPreviewSize > MAX_PREVIEW_SIZE) {
                    LOG.warn(requestIdentifier + "Server preview size [" + serverPreviewSize + "] exceeds maximum, use " + MAX_PREVIEW_SIZE + ".");
                    serverPreviewSize = MAX_PREVIEW_SIZE;
                }
            }

            boolean serverAllow204 = false;
            List<String> allowValues = icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ALLOW);
            if (allowValues != null) {
                for (String allowValue : allowValues) {
                    if (allowValue != null && allowValue.trim().equalsIgnoreCase("204")) {
                        serverAllow204 = true;
                    }
                }
            }

            // Parse Options-TTL (RFC 3507 §4.10)
            int optionsTTL = -1;
            String optionsTTLValue = getFirstHeaderValue(icapHeaderInformation, ICAPConstants.HEADER_KEY_OPTIONS_TTL);
            if (optionsTTLValue != null) {
                try {
                    optionsTTL = Integer.parseInt(optionsTTLValue.trim());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(requestIdentifier + "Server Options-TTL: " + optionsTTL + " seconds");
                    }
                } catch (NumberFormatException e) {
                    LOG.warn(requestIdentifier + "Could not parse Options-TTL [" + optionsTTLValue + "]: " + e.getMessage());
                }
            }

            // Parse Max-Connections (RFC 3507 §4.10)
            int maxConnections = -1;
            String maxConnectionsValue = getFirstHeaderValue(icapHeaderInformation, ICAPConstants.HEADER_KEY_MAX_CONNECTIONS);
            if (maxConnectionsValue != null) {
                try {
                    maxConnections = Integer.parseInt(maxConnectionsValue.trim());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(requestIdentifier + "Server Max-Connections: " + maxConnections);
                    }
                } catch (NumberFormatException e) {
                    LOG.warn(requestIdentifier + "Could not parse Max-Connections [" + maxConnectionsValue + "]: " + e.getMessage());
                }
            }

            // Methods is mandatory in an OPTIONS response (RFC 3507 §4.10.2)
            List<String> methods = icapHeaderInformation.getHeaderValues("Methods");
            if (methods == null || methods.isEmpty()) {
                throw new IOException("Invalid options response, missing Methods header!");
            }

            LOG.info(requestIdentifier + "Valid service ["
                     + icapHeaderInformation.getStatus() + "/" + icapHeaderInformation.getMessage() + "], "
                     + "allow 204: " + serverAllow204 + ", "
                     + "available methods: " + methods);

            List<ICAPMode> modes = new ArrayList<>();
            for (String method : methods) {
                try {
                    modes.add(ICAPMode.valueOf(method.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    LOG.warn(requestIdentifier + "Ignore unknown method [" + method + "]");
                }
            }

            ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl(
                    Instant.now(), modes.toArray(new ICAPMode[0]), serverPreviewSize, serverAllow204,
                    icapHeaderInformation.getHeaders());
            config.setOptionsTTL(optionsTTL);
            config.setMaxConnections(maxConnections);

            // Parse Service-ID (RFC 3507 §4.10)
            String serviceId = getFirstHeaderValue(icapHeaderInformation, ICAPConstants.HEADER_KEY_SERVICE_ID);
            if (serviceId != null) {
                config.setServiceId(serviceId.trim());
            }

            // Parse Transfer-Preview, Transfer-Ignore, Transfer-Complete (RFC 3507 §4.10.2)
            config.setTransferPreview(parseTransferExtensions(icapHeaderInformation, ICAPConstants.HEADER_KEY_TRANSFER_PREVIEW));
            config.setTransferIgnore(parseTransferExtensions(icapHeaderInformation, ICAPConstants.HEADER_KEY_TRANSFER_IGNORE));
            config.setTransferComplete(parseTransferExtensions(icapHeaderInformation, ICAPConstants.HEADER_KEY_TRANSFER_COMPLETE));
            config.freeze();
            remoteServiceConfiguration = config;
            return config;
        } catch (IOException | RuntimeException e) {
            if (icapSocket != null) {
                icapSocket.markUnhealthy();
            }
            throw e;
        } finally {
            if (icapSocket != null) {
                icapSocket.close();
            }
        }
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPClient#validateResource(com.github.toolarium.icap.client.dto.ICAPMode, com.github.toolarium.icap.client.dto.ICAPResource)
     */
    @Override
    public ICAPHeaderInformation validateResource(final ICAPMode mode, final ICAPResource resource) throws IOException, ContentBlockedException {
        return validateResource(mode, getEffectiveRequestInformation(null), resource);
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPClient#validateResource(com.github.toolarium.icap.client.dto.ICAPMode, com.github.toolarium.icap.client.dto.ICAPRequestInformation, com.github.toolarium.icap.client.dto.ICAPResource)
     */
    @Override
    public ICAPHeaderInformation validateResource(final ICAPMode inputMode, final ICAPRequestInformation requestInformation, final ICAPResource resource) throws IOException, ContentBlockedException {
        validateRequestInformation(requestInformation);

        // without ICAP server url only FILEMOD is supported
        if (serviceInformation == null && !ICAPMode.FILEMOD.equals(inputMode)) {
            throw new MalformedURLException(NO_ICAP_SERVER_MESSAGE);
        }

        if (resource == null) {
            throw new IOException("Invalid input resource!");
        }

        // a resource without stream has nothing to scan
        if (resource.getResourceLength() == 0 && resource.getResourceBody() == null) {
            return new ICAPHeaderInformation();
        }
        validateICAPResource(resource);

        // claim the resource atomically, so concurrent calls can not read the same stream
        if (!resource.acquire()) {
            if (resource.isConsumed()) {
                throw new IOException("ICAPResource stream has already been consumed by a previous validateResource call. "
                        + "Create a new ICAPResource with a fresh InputStream to retry.");
            }
            throw new IOException("ICAPResource is in use by another validateResource call.");
        }

        try {
            // a length of 0 means unknown length: an empty stream has nothing to scan, otherwise the stream is scanned
            ICAPResource processingResource = resource;
            if (resource.getResourceLength() == 0) {
                PushbackInputStream content = readAhead(resource.getResourceBody());
                if (content == null) {
                    return new ICAPHeaderInformation();
                }

                // the caller's resource is not changed: an internal copy is processed with the read ahead stream
                resource.markConsumed();
                processingResource = new ICAPResource(resource.getResourceName(), content, 0);
            }
            return processValidation(inputMode, requestInformation, processingResource);
        } finally {
            // in case the stream was not read (e.g. the connection failed) the resource can be used for a retry
            resource.release();
            if (resource.isConsumed() && resource.isStreamOwner()) {
                closeQuietly(resource);
            }
        }
    }


    /**
     * Process the validation of a claimed resource.
     *
     * @param inputMode the mode
     * @param requestInformation the request information
     * @param resource the resource
     * @return the ICAP header information
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    private ICAPHeaderInformation processValidation(final ICAPMode inputMode, final ICAPRequestInformation requestInformation, final ICAPResource resource) throws IOException, ContentBlockedException {
        ICAPMode icapMode = ICAPMode.REQMOD;
        if (inputMode != null) {
            icapMode = inputMode;
        }

        // read the setting once, a concurrent change must not affect a running request
        final boolean compareContent = supportCompareVerifyIdenticalContent;
        final String sourceRequest = sanitizeLogValue(requestInformation.prepareSourceRequest(resource));
        final String requestIdentifier = createRequestIdentifier(icapMode.name(), sourceRequest);
        LOG.info(requestIdentifier + "Validate resource (" + sourceRequest + ")");

        // FILEMOD: the content is checked by a local virus scanner, the ICAP server is not used
        if (ICAPMode.FILEMOD.equals(icapMode)) {
            return processFileScan(requestIdentifier, sourceRequest, requestInformation, resource);
        }

        validateServiceInformation();

        // validate the service availability — capture a local reference to avoid TOCTOU races
        // on the volatile field if a concurrent options() call nulls and re-sets it.
        ICAPRemoteServiceConfiguration currentConfig = remoteServiceConfiguration;
        if (currentConfig == null) {
            currentConfig = options(requestInformation);
        }

        ResponseContent responseContent = new ResponseContent(MAX_THREAT_CONTENT_SIZE);
        ICAPSocket icapSocket = null;
        try {
            icapSocket = new ICAPSocket(connectionManager, requestIdentifier, serviceInformation.getHostName(), serviceInformation.getServicePort(), serviceInformation.getServiceName(),
                                        serviceInformation.isSecureConnection(), requestInformation.getMaxConnectionTimeout(), requestInformation.getMaxReadTimeout(), requestInformation.getMaxRequestTimeout());
            ICAPHeaderInformation icapHeaderInformation = processResource(requestIdentifier, icapSocket, icapMode, requestInformation, currentConfig, resource, responseContent, compareContent);
            icapHeaderInformation.getHeaders().remove(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE);

            if (icapHeaderInformation.getStatus() == 200) {
                StringBuilder threadInformationBuilder = new StringBuilder();

                // the header values come from the server, they are logged
                for (Map.Entry<String, List<String>> e: icapHeaderInformation.getHeaders().entrySet()) {
                    if (e.getKey().toLowerCase().startsWith("x-")) {
                        threadInformationBuilder.append("- ").append(sanitizeLogValue(e.getKey())).append(": ").append(sanitizeLogValue("" + e.getValue())).append("\n");
                    }
                }
                String threadInformation = threadInformationBuilder.toString().trim();

                // verify if there is a thread is found taken from header
                if (hasThreadHeaderInformation(icapHeaderInformation)) {
                    String threadHeaderInformation = readThreadHeaderInformation(icapMode, icapHeaderInformation, responseContent);
                    String msg = "Threat found in resource (" + sourceRequest + ", http-status: " + icapHeaderInformation.getStatus() + "):\n" + threadInformation;
                    LOG.info(requestIdentifier + msg);
                    throw new ContentBlockedException(msg, icapHeaderInformation, threadHeaderInformation);
                } else if (ICAPMode.REQMOD.equals(icapMode) && hasEncapsulatedEntry(icapHeaderInformation, "res-hdr")) {
                    // RFC 3507 §4.8.2: a REQMOD response with an HTTP response means the server answered the request itself (e.g. block page)
                    String threadHeaderInformation = readThreadHeaderInformation(icapMode, icapHeaderInformation, responseContent);
                    String msg = "Request blocked by the ICAP server (" + sourceRequest + ", http-status: " + icapHeaderInformation.getStatus() + "):\n" + threadInformation;
                    LOG.info(requestIdentifier + msg);
                    throw new ContentBlockedException(msg, icapHeaderInformation, threadHeaderInformation);
                } else if (ICAPMode.RESPMOD.equals(icapMode) && isBlockingHttpStatus(responseContent.getHttpStatus())) {
                    // the server replaced the HTTP response, e.g. by a block page
                    String threadHeaderInformation = readThreadHeaderInformation(icapMode, icapHeaderInformation, responseContent);
                    String msg = "Response blocked by the ICAP server (" + sourceRequest + ", http-status: " + responseContent.getHttpStatus() + "):\n" + threadInformation;
                    LOG.info(requestIdentifier + msg);
                    throw new ContentBlockedException(msg, icapHeaderInformation, threadHeaderInformation);
                }
            }

            LOG.info(requestIdentifier + "Valid resource (" + sourceRequest + ", http-status: " + icapHeaderInformation.getStatus() + ").");
            return icapHeaderInformation;
        } catch (ContentBlockedException cbe) {
            if (icapSocket != null) {
                icapSocket.markUnhealthy();
            }
            throw cbe;
        } catch (IOException eio) {
            if (icapSocket != null) {
                icapSocket.markUnhealthy();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Could not access to ICAP server: " + eio.getMessage(), eio);
            }
            throw eio;
        } catch (RuntimeException re) {
            if (icapSocket != null) {
                icapSocket.markUnhealthy();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Unexpected error while processing resource: " + re.getMessage(), re);
            }
            throw re;
        } finally {
            if (icapSocket != null) {
                icapSocket.close();
            }
        }
    }


    /**
     * FILEMOD: write the whole content to a temporary file and check it after the local virus scanner had the chance
     * to scan it. A deleted, blocked or modified file means the local virus scanner found a threat.
     *
     * @param requestIdentifier the request identifier
     * @param sourceRequest the source request
     * @param requestInformation the request information
     * @param resource the resource
     * @return the empty ICAP header information, there is no ICAP server
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the local virus scanner found a threat
     */
    private ICAPHeaderInformation processFileScan(final String requestIdentifier, final String sourceRequest, final ICAPRequestInformation requestInformation,
                                                  final ICAPResource resource) throws IOException, ContentBlockedException {
        final File file = createResponseFile(resource.getResourceName());
        String threat = null;
        try {
            MessageDigest writtenDigest = ICAPClientUtil.getInstance().createMessageDigest(messageDigestAlgorithm);
            resource.markConsumed();
            threat = writeScanFile(requestIdentifier, resource.getResourceBody(), file, writtenDigest, requestInformation.getFileScanMaxSize());

            if (threat == null) {
                String writtenHash = ICAPClientUtil.getInstance().messageDigestToString(messageDigestAlgorithm, writtenDigest);
                threat = checkScannedFile(requestIdentifier, file, writtenHash, getFileScanWaitTime(requestInformation));
            }

            // the result is reported as an ICAP server would do it: 204 for a valid resource, 200 for a threat
            ICAPHeaderInformation icapHeaderInformation = new ICAPHeaderInformation().setProtocol("ICAP").setVersion(ICAPRequestInformation.API_VERSION);
            if (threat != null) {
                icapHeaderInformation.setStatus(200).setMessage("OK");
                String msg = "Threat found in resource (" + sourceRequest + "): " + threat;
                LOG.info(requestIdentifier + msg);
                throw new ContentBlockedException(msg, icapHeaderInformation);
            }

            icapHeaderInformation.setStatus(204).setMessage("Unmodified");
            LOG.info(requestIdentifier + "Valid resource (" + sourceRequest + ", checked by the local virus scanner).");
            return icapHeaderInformation;
        } finally {
            deleteResponseFile(requestIdentifier, file, threat != null);
        }
    }


    /**
     * Write the content to the file for the local virus scanner. A blocked write or close of the file means the virus scanner
     * found a threat; a read error of the resource or a resource which exceeds the max size is an I/O error.
     *
     * @param requestIdentifier the request identifier
     * @param inputstream the resource stream
     * @param file the file
     * @param digest the digest of the written content
     * @param maxSize the max size in bytes or null for no limit
     * @return the reason of the threat or null if the file was written
     * @throws IOException In case of a read error or if the resource exceeds the max size
     */
    private String writeScanFile(final String requestIdentifier, final InputStream inputstream, final File file, final MessageDigest digest, final Long maxSize) throws IOException {
        OutputStream outputstream;
        try {
            outputstream = new DigestOutputStream(new BufferedOutputStream(new FileOutputStream(file)), digest);
        } catch (IOException e) {
            return getWriteBlockedReason(requestIdentifier, e);
        }

        String threat = null;
        try {
            byte[] buffer = new byte[bufferSize];
            long total = 0;
            int readBytes;
            while (threat == null && (readBytes = inputstream.read(buffer)) != -1) {
                total += readBytes;
                if (maxSize != null && maxSize.longValue() >= 0 && total > maxSize.longValue()) {
                    throw new IOException("Invalid input resource, the resource exceeds the max size of " + maxSize + " bytes for the local virus scan!");
                }

                try {
                    outputstream.write(buffer, 0, readBytes);
                } catch (IOException e) {
                    threat = getWriteBlockedReason(requestIdentifier, e);
                }
            }
        } finally {
            try {
                outputstream.close();
            } catch (IOException e) {
                if (threat == null) {
                    threat = getWriteBlockedReason(requestIdentifier, e);
                }
            }
        }
        return threat;
    }


    /**
     * Get the reason of a write which was blocked, some virus scanners block the write or the close of an infected file.
     *
     * @param requestIdentifier the request identifier
     * @param e the exception of the write
     * @return the reason
     */
    private String getWriteBlockedReason(final String requestIdentifier, final IOException e) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Could not write the file: " + e.getMessage(), e);
        }
        return "the write of the file was blocked by a virus scanner: " + e.getMessage();
    }


    /**
     * Check the written file: it must still exist, be readable and unchanged until the wait time is over.
     *
     * @param requestIdentifier the request identifier
     * @param file the file
     * @param writtenHash the hash of the written content
     * @param waitTime the time in milliseconds to wait for the local virus scanner
     * @return the reason of the threat or null if the file is valid
     * @throws InterruptedIOException In case the thread was interrupted
     * @throws IOException In case of an I/O error
     */
    private String checkScannedFile(final String requestIdentifier, final File file, final String writtenHash, final long waitTime) throws IOException {
        if (!file.exists()) {
            return FILE_DELETED_REASON;
        }

        // reading the file lets an on-access scanner check it, an infected file is blocked
        String readHash;
        try {
            readHash = ICAPClientUtil.getInstance().hashFile(messageDigestAlgorithm, file);
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Could not read the scanned file: " + e.getMessage(), e);
            }
            return "the access to the file was blocked by a virus scanner.";
        }

        if (!writtenHash.equals(readHash)) {
            return "the file was modified by a virus scanner.";
        }

        // some virus scanners delete an infected file with a delay
        long end = System.currentTimeMillis() + waitTime;
        long remaining = waitTime;
        while (remaining > 0) {
            if (!file.exists()) {
                return FILE_DELETED_REASON;
            }

            try {
                Thread.sleep(Math.min(FILE_SCAN_POLL_INTERVAL, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting for the local virus scanner!");
            }
            remaining = end - System.currentTimeMillis();
        }

        if (!file.exists()) {
            return FILE_DELETED_REASON;
        }
        return null;
    }


    /**
     * Get the time to wait for the local virus scanner in FILEMOD.
     *
     * @param requestInformation the request information
     * @return the wait time in milliseconds
     */
    private long getFileScanWaitTime(final ICAPRequestInformation requestInformation) {
        Integer waitTime = requestInformation.getFileScanWaitTime();
        if (waitTime != null && waitTime.intValue() >= 0) {
            return waitTime.intValue();
        }
        return ICAPRequestInformation.DEFAULT_FILE_SCAN_WAIT_TIME.longValue();
    }


    /**
     * Create the temporary file for FILEMOD. The file keeps the extension of the resource name, because some virus
     * scanners decide by the extension how a file is scanned.
     *
     * @param resourceName the name of the resource
     * @return the file
     * @throws IOException In case the file could not be created
     */
    protected File createResponseFile(final String resourceName) throws IOException {
        return Files.createTempFile("icap-", "." + getFileExtension(resourceName)).toFile();
    }


    /**
     * Delete the temporary file of FILEMOD.
     *
     * @param requestIdentifier the request identifier
     * @param resourceResponse the file
     * @param threatFound true if the virus scanner found a threat, then it may hold the file (e.g. quarantine)
     */
    protected void deleteResponseFile(final String requestIdentifier, final File resourceResponse, final boolean threatFound) {
        if (!resourceResponse.exists() || resourceResponse.delete()) {
            return;
        }

        if (threatFound) {
            // the virus scanner holds the infected file, it removes it itself
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Could not delete temp file [" + resourceResponse + "], it is held by the virus scanner.");
            }
            return;
        }

        resourceResponse.deleteOnExit();
        LOG.warn(requestIdentifier + "Could not delete temp file [" + resourceResponse + "], scheduled for deletion on exit.");
    }


    /**
     * Get the file extension of a resource name: only letters and digits, at most 16 characters.
     *
     * @param resourceName the name of the resource
     * @return the file extension, by default tmp
     */
    private String getFileExtension(final String resourceName) {
        if (resourceName == null) {
            return DEFAULT_FILE_EXTENSION;
        }

        int idx = resourceName.lastIndexOf('.');
        if (idx < 0 || idx == resourceName.length() - 1) {
            return DEFAULT_FILE_EXTENSION;
        }

        StringBuilder extension = new StringBuilder();
        for (char c : resourceName.substring(idx + 1).toCharArray()) {
            if (c < 128 && Character.isLetterOrDigit(c) && extension.length() < MAX_FILE_EXTENSION_LENGTH) {
                extension.append(c);
            }
        }

        if (extension.length() == 0) {
            return DEFAULT_FILE_EXTENSION;
        }
        return extension.toString();
    }


    /**
     * Read ahead one byte to check if the stream contains data. The read byte is pushed back, so the content stays complete.
     *
     * @param inputstream the stream
     * @return the stream with the pushed back byte or null if the stream is empty
     * @throws IOException In case of an I/O error
     */
    private PushbackInputStream readAhead(final InputStream inputstream) throws IOException {
        PushbackInputStream pushbackInputStream = new PushbackInputStream(inputstream, 1);
        int b = pushbackInputStream.read();
        if (b == -1) {
            return null;
        }

        pushbackInputStream.unread(b);
        return pushbackInputStream;
    }


    /**
     * Create the Authorization header if set (RFC 3507 §7.1).
     *
     * @param requestInformation the ICAP request information
     * @return the authorization header line or empty string
     */
    private String createAuthorizationHeader(final ICAPRequestInformation requestInformation) {
        if (requestInformation.getAuthorization() == null || requestInformation.getAuthorization().isBlank()) {
            return "";
        }

        if (!serviceInformation.isSecureConnection() && LOG.isDebugEnabled()) {
            LOG.debug("The authorization is sent unencrypted to [" + serviceInformation.getHostName() + ":" + serviceInformation.getServicePort() + "], use a secure connection (icaps).");
        }
        return ICAPConstants.HEADER_KEY_AUTHORIZATION + ": " + sanitizeHeaderValue(requestInformation.getAuthorization()) + NEWLINE;
    }


    /**
     * Create custom headers
     *
     * @param requestInformation the ICAP request information
     * @return the customer headers
     */
    private String createCustomHeaders(final ICAPRequestInformation requestInformation) {
        if (requestInformation.getCustomHeaders() == null || requestInformation.getCustomHeaders().isEmpty()) {
            return "";
        }

        final StringBuilder headers = new StringBuilder();
        for (Map.Entry<String, String> e : requestInformation.getCustomHeaders().entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalid customer header [" + e.getKey() + "] with value [" + e.getValue() + "], ignore!");
                }
                continue;
            }

            final String key = sanitizeHeaderValue(e.getKey().trim());
            final String value = sanitizeHeaderValue(e.getValue().trim());

            if (key.isEmpty() || key.indexOf(':') >= 0) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalid customer header name [" + key + "], ignore!");
                }
            } else if (key.equalsIgnoreCase("Host") || key.equalsIgnoreCase("Connection") || key.equalsIgnoreCase("User-Agent")
                    || key.equalsIgnoreCase("Preview") || key.equalsIgnoreCase("Encapsulated") || key.equalsIgnoreCase("Allow")
                    || key.equalsIgnoreCase("Transfer-Encoding") || key.equalsIgnoreCase("Content-Length")
                    || key.equalsIgnoreCase("Authorization")) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalid customer header [" + key + "], it's not allowed, ignore!");
                }
            } else if (!value.isEmpty()) {
                headers.append(key).append(": ").append(value).append(NEWLINE);
            }
        }

        return headers.toString();
    }


    /**
     * Sanitize a header value by removing CR and LF characters to prevent CRLF injection.
     *
     * @param value the value to sanitize
     * @return the sanitized value
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "").replace("\n", "");
    }


    /**
     * Sanitize a value for safe log output by replacing CR and LF characters.
     *
     * @param value the value to sanitize
     * @return the sanitized value
     */
    private String sanitizeLogValue(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r') {
                result.append("\\r");
            } else if (c == '\n') {
                result.append("\\n");
            } else if (c < 0x20 || c == 0x7F) {
                // other control characters, e.g. terminal escape sequences
                result.append('?');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }


    /**
     * Check if a value can be used in the ICAP request line: no whitespace and no control characters.
     *
     * @param value the value
     * @return true if it is valid
     */
    private boolean isValidRequestLineValue(String value) {
        if (value == null) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c == 0x7F) {
                return false;
            }
        }
        return true;
    }


    /**
     * Validate the service information, it is used in the ICAP request line.
     *
     * @throws IOException In case of an invalid service information
     */
    private void validateServiceInformation() throws IOException {
        if (serviceInformation == null || serviceInformation.getHostName() == null || serviceInformation.getHostName().isEmpty()
                || !isValidRequestLineValue(serviceInformation.getHostName())
                || (serviceInformation.getServiceName() != null && !serviceInformation.getServiceName().isEmpty() && !isValidRequestLineValue(serviceInformation.getServiceName()))) {
            String info = "";
            if (serviceInformation != null) {
                info = serviceInformation.toString();
            }
            throw new IOException("Invalid service information: " + sanitizeLogValue(info));
        }
    }


    /**
     * Format a host for an URI or Host header, IPv6 literals are enclosed in brackets.
     *
     * @param host the host
     * @return the formatted host
     */
    private String formatHost(String host) {
        if (host != null && host.indexOf(':') >= 0 && !host.startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }


    /**
     * Get the length in bytes of a string as it is sent
     *
     * @param value the string
     * @return the length in bytes
     */
    private int byteLength(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }


    /**
     * Get the first value of a header
     *
     * @param icapHeaderInformation the ICAP header information
     * @param headerKey the header key
     * @return the first value or null
     */
    private String getFirstHeaderValue(ICAPHeaderInformation icapHeaderInformation, String headerKey) {
        List<String> values = icapHeaderInformation.getHeaderValues(headerKey);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }


    /**
     * Check if the status of an encapsulated HTTP response means the server blocked the content, e.g. 403 or a redirect to a block page.
     *
     * @param httpStatus the HTTP status or 0 if unknown
     * @return true if the status is not successful
     */
    private boolean isBlockingHttpStatus(int httpStatus) {
        return httpStatus > 0 && (httpStatus < 200 || httpStatus >= 300);
    }


    /**
     * Check if the Encapsulated header contains a given entry, e.g. res-hdr
     *
     * @param icapHeaderInformation the ICAP header information
     * @param entryKey the entry key
     * @return true if the entry exists
     */
    private boolean hasEncapsulatedEntry(ICAPHeaderInformation icapHeaderInformation, String entryKey) {
        List<String> values = icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ENCAPSULATED);
        if (values == null) {
            return false;
        }

        for (String entry : values) {
            if (entry != null && entry.split("=")[0].trim().equalsIgnoreCase(entryKey)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Check if there are thread header information
     *
     * @param icapHeaderInformation the ICAP header information
     * @return true if a thread was detected
     */
    private boolean hasThreadHeaderInformation(ICAPHeaderInformation icapHeaderInformation) {
        return icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND)
               || icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND)
               || icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_BLOCKED)        // used by Sophos
               || icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_VIRUS_ID)       // used by Sophos, Kaspersky, Trenxd Micro, ESET, McAfee, C-ICAP
               || icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_VIRUS_NAME)     // used by McAfee
               || icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_BLOCK_REASON)   // used by McAfee
               || icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_BLOCK_RESULT);  // used by McAfee
    }


    /**
     * Read the thread reason
     *
     * @param icapMode the icap mode
     * @param icapHeaderInformation the ICAP header information
     * @param responseContent the response content
     * @return the thread content information
     */
    private String readThreadHeaderInformation(ICAPMode icapMode, ICAPHeaderInformation icapHeaderInformation, ResponseContent responseContent) {
        String threadHeaderInformation = null;

        if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_ENCAPSULATED) && !icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ENCAPSULATED).isEmpty()
            && responseContent != null && responseContent.getLength() > 0) {
            for (int i = 0; i < icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ENCAPSULATED).size(); i++) {
                String entry = icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(i);
                String[] split = entry.split("=");
                // RFC 3507 §4.4.1: REQMOD response may contain req-body or res-body (error response)
                String entryKey = split[0].trim();
                if (split.length > 1 && (entryKey.equalsIgnoreCase(icapMode.getTag() + "-body") || entryKey.equalsIgnoreCase("res-body"))) {
                    threadHeaderInformation = responseContent.getContent().trim();
                    break;
                }
            }
        }

        if ((threadHeaderInformation == null || threadHeaderInformation.isBlank()) && icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_BLOCKED)) {
            // used by Sophos
            threadHeaderInformation = "" + icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_BLOCKED);
        }

        if ((threadHeaderInformation == null || threadHeaderInformation.isBlank()) && icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_VIRUS_ID)) {
            // used by Sophos, Kaspersky, Trenxd Micro, ESET, McAfee, C-ICAP
            threadHeaderInformation = "" + icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIRUS_ID);
        }

        if ((threadHeaderInformation == null || threadHeaderInformation.isBlank()) && icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_VIRUS_NAME)) {
            // used by McAfee
            threadHeaderInformation = "" + icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIRUS_NAME);
        }

        if (threadHeaderInformation == null || threadHeaderInformation.isBlank()) {
            threadHeaderInformation = "n/a";
        }

        return threadHeaderInformation;
    }


    /**
     * Validate resource
     *
     * @param resource the resource
     * @throws IOException In case of an invalid resource
     */
    protected void validateICAPResource(final ICAPResource resource) throws IOException {
        if (resource == null
                || resource.getResourceName() == null || resource.getResourceName().isBlank()
                || resource.getResourceBody() == null
                || resource.getResourceLength() < 0) {
            throw new IOException("Invalid input resource!");
        }
    }


    /**
     * Process a resource
     *
     * @param requestIdentifier the request identifier
     * @param icapSocket The icap socket
     * @param icapMode the icap mode
     * @param requestInformation the ICAP request information
     * @param currentConfig the remote service configuration
     * @param resource the ICAP resource
     * @param responseContent the response content
     * @param compareContent true to compare the request and response content
     * @return the ICAP header information
     * @throws IOException In case of an I/O error
     * @throws UnknownIOException In case of an unknown or unrecognized ICAP status code
     * @throws ContentBlockedException In case the content is blocked
     */
    protected ICAPHeaderInformation processResource(final String requestIdentifier,
                                                    final ICAPSocket icapSocket,
                                                    final ICAPMode icapMode,
                                                    final ICAPRequestInformation requestInformation,
                                                    final ICAPRemoteServiceConfiguration currentConfig,
                                                    final ICAPResource resource,
                                                    final ResponseContent responseContent,
                                                    final boolean compareContent) throws IOException, ContentBlockedException {
        if (currentConfig == null) {
            throw new IOException("Remote service configuration is not available");
        }

        // first part of header — encapsulated HTTP request (RFC 3507 §4.3: Via header for surrogate identification)
        final String httpMethod = "GET";
        final String viaHeader = "Via: 1.1 " + sanitizeHeaderValue(requestInformation.getUserAgent()) + NEWLINE;
        final String header = httpMethod + " /" + URLEncoder.encode(resource.getResourceName().trim(), StandardCharsets.UTF_8.name()) + " HTTP/1.1" + NEWLINE
                              + "Host: " + sanitizeHeaderValue(requestInformation.getRequestSource()) + NEWLINE
                              + viaHeader + NEWLINE;

        // prepare preview size, a resource length of 0 means unknown length
        final long declaredLength = resource.getResourceLength();
        int previewSize = Math.max(0, Math.min(currentConfig.getServerPreviewSize(), MAX_PREVIEW_SIZE));
        if (declaredLength > 0 && declaredLength < previewSize) {
            previewSize = (int) declaredLength;
        }

        MessageDigest inputMessageDigest = null;
        InputStream resourceStream;
        if (compareContent) {
            inputMessageDigest = ICAPClientUtil.getInstance().createMessageDigest(messageDigestAlgorithm);
            resourceStream = new DigestInputStream(resource.getResourceBody(), inputMessageDigest);
        } else {
            resourceStream = resource.getResourceBody();
        }
        PushbackInputStream inputstream = new PushbackInputStream(resourceStream, 1);

        // read the preview completely before the header is written, a single read may return less than requested.
        // The end of the content is detected on the stream, the declared length may be wrong: the whole stream is scanned.
        resource.markConsumed();
        byte[] preview = inputstream.readNBytes(previewSize);
        boolean endOfContent = true;
        if (preview.length < previewSize) {
            if (declaredLength > 0 && LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Resource stream ended after " + preview.length + " bytes, expected length: " + declaredLength + ".");
            }
        } else if (declaredLength > previewSize) {
            // more content is declared, it is sent after the preview: no read ahead needed
            endOfContent = false;
        } else {
            // the declared length fits into the preview or is unknown: read ahead to detect more content
            int next = inputstream.read();
            if (next != -1) {
                inputstream.unread(next);
                endOfContent = false;
            }
        }

        // the content length is unknown if the stream continues and no length was declared
        long contentLength = -1;
        if (endOfContent) {
            contentLength = preview.length;
        } else if (declaredLength > 0) {
            contentLength = declaredLength;
        }

        String contentLengthHeader = "";
        if (contentLength >= 0) {
            contentLengthHeader = ICAPConstants.HEADER_KEY_CONTENT_LENGTH + ": " + contentLength + NEWLINE;
        }

        String body;
        if (ICAPMode.RESPMOD.equals(icapMode)) {
            body = header + "HTTP/1.1 200 OK" + NEWLINE + ICAPConstants.HEADER_KEY_TRANSFER_ENCODING + ": chunked" + NEWLINE
                   + contentLengthHeader + NEWLINE;
        } else {
            body = header;
        }

        // the Encapsulated offsets are byte offsets (RFC 3507 §4.4.1)
        String encapsulated;
        if (ICAPMode.RESPMOD.equals(icapMode)) {
            encapsulated = "req-hdr=0, " + icapMode.getTag() + "-hdr=" + byteLength(header) + ", " + icapMode.getTag() + "-body=" + byteLength(body);
        } else {
            encapsulated = "req-hdr=0, " + icapMode.getTag() + "-body=" + byteLength(body);
        }

        String requestBuffer = "" + icapMode.name() + " icap://" + formatHost(serviceInformation.getHostName()) + ":" + serviceInformation.getServicePort()
                             + "/" + serviceInformation.getServiceName() + " ICAP/" + requestInformation.getApiVersion() + NEWLINE
                             + "Host: " + formatHost(serviceInformation.getHostName()) + NEWLINE
                             + "Connection: " + getConnectionMode() + NEWLINE
                             + "User-Agent: " + sanitizeHeaderValue(requestInformation.getUserAgent()) + NEWLINE
                             + createAuthorizationHeader(requestInformation)
                             + createCustomHeaders(requestInformation)
                             + supportAllow204(requestIdentifier, currentConfig, requestInformation.isAllow204())
                             + "Preview: " + preview.length + NEWLINE
                             + "Encapsulated: " + encapsulated + NEWLINE + NEWLINE
                             + body;
        // send the preview; the response might not be "100 continue" if the whole content was sent, then this is the final response.
        ICAPHeaderInformation icapHeaderInformation = sendPreview(requestIdentifier, icapSocket, requestBuffer, preview, endOfContent);
        long requestLength = preview.length;
        boolean continueTransfer = !endOfContent && icapHeaderInformation.getStatus() == 100;
        if (continueTransfer) {
            requestLength = sendRemainingContent(requestIdentifier, icapSocket, inputstream, declaredLength, preview.length);
        }

        // as in the previous versions, the stream is closed after the whole content was sent if the content is compared
        if (compareContent && (endOfContent || continueTransfer)) {
            inputstream.close();
        }

        if (continueTransfer) {
            icapHeaderInformation = icapSocket.readICAPResponse(requestIdentifier, ICAP_END_SEPARATOR, bufferSize);
        }

        responseContent.setRequestLength(requestLength);
        return processResponse(requestIdentifier, icapSocket, icapMode, requestInformation, icapHeaderInformation, inputMessageDigest, resource, responseContent, compareContent);
    }


    /**
     * Send the request with the preview and read the first response. If the connection was closed by the server before
     * any response was received, e.g. an idle pooled connection, the request is sent once again on a new connection.
     *
     * @param requestIdentifier the request identifier
     * @param icapSocket the ICAP socket
     * @param requestBuffer the request header
     * @param preview the preview content
     * @param endOfContent true if the preview contains the whole content
     * @return the first response
     * @throws IOException In case of an I/O error
     */
    private ICAPHeaderInformation sendPreview(final String requestIdentifier, final ICAPSocket icapSocket, final String requestBuffer, final byte[] preview, final boolean endOfContent) throws IOException {
        try {
            return writePreview(requestIdentifier, icapSocket, requestBuffer, preview, endOfContent);
        } catch (SocketTimeoutException e) {
            throw e;
        } catch (IOException e) {
            if (!connectionManager.isPoolingEnabled() || icapSocket.hasReceivedData()) {
                throw e;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Connection was closed by the server, send the request again on a new connection: " + e.getMessage(), e);
            }
            icapSocket.reconnect();
            return writePreview(requestIdentifier, icapSocket, requestBuffer, preview, endOfContent);
        }
    }


    /**
     * Write the request with the preview and read the first response.
     *
     * @param requestIdentifier the request identifier
     * @param icapSocket the ICAP socket
     * @param requestBuffer the request header
     * @param preview the preview content
     * @param endOfContent true if the preview contains the whole content
     * @return the first response
     * @throws UnknownIOException In case the server closed the connection without response
     * @throws IOException In case of an I/O error
     */
    private ICAPHeaderInformation writePreview(final String requestIdentifier, final ICAPSocket icapSocket, final String requestBuffer, final byte[] preview, final boolean endOfContent) throws IOException {
        icapSocket.write(requestBuffer);

        // sending preview or, if smaller than previewSize, the whole file.
        if (preview.length > 0) {
            icapSocket.write(Integer.toHexString(preview.length) + NEWLINE);
            icapSocket.write(preview, 0, preview.length);
            icapSocket.write(NEWLINE);
        }

        if (endOfContent) {
            icapSocket.write("0; ieof" + ICAP_END_SEPARATOR);
        } else {
            icapSocket.write(HTTP_END_SEPARATOR);
        }
        icapSocket.flush();

        ICAPHeaderInformation icapHeaderInformation = icapSocket.readICAPResponse(requestIdentifier, ICAP_END_SEPARATOR, bufferSize);
        if (!icapSocket.hasReceivedData()) {
            // no response: reported as unknown status code (0) as in the previous versions
            throwStatusCodeException(icapHeaderInformation);
        }
        return icapHeaderInformation;
    }


    /**
     * Send the content after the preview.
     *
     * @param requestIdentifier the request identifier
     * @param icapSocket the ICAP socket
     * @param inputstream the resource stream
     * @param declaredLength the declared length of the resource, 0 if unknown
     * @param alreadySent the number of bytes which were already sent
     * @return the total number of sent bytes
     * @throws IOException In case of an I/O error
     */
    private long sendRemainingContent(final String requestIdentifier, final ICAPSocket icapSocket, final InputStream inputstream, final long declaredLength, final long alreadySent) throws IOException {
        long totalReadBytes = alreadySent;
        byte[] buffer = new byte[bufferSize];
        int readBytes = -1;
        while ((readBytes = inputstream.read(buffer)) != -1) {
            if (readBytes == 0) {
                continue;
            }

            totalReadBytes += readBytes;
            if (LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Send next block of " + readBytes + " bytes (total sent: " + totalReadBytes + " bytes)...");
            }
            icapSocket.write((Integer.toHexString(readBytes) + NEWLINE));
            icapSocket.write(buffer, 0, readBytes);
            icapSocket.write(NEWLINE);
        }

        if (declaredLength > 0 && totalReadBytes != declaredLength && LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Resource stream has " + totalReadBytes + " bytes, expected length: " + declaredLength + ".");
        }

        // closing resource transfer.
        icapSocket.write(HTTP_END_SEPARATOR);
        icapSocket.flush();
        return totalReadBytes;
    }


    /**
     * Process the final response of the server.
     *
     * @param requestIdentifier the request identifier
     * @param icapSocket the ICAP socket
     * @param icapMode the icap mode
     * @param requestInformation the ICAP request information
     * @param icapHeaderInformation the ICAP response header
     * @param inputMessageDigest the input message digest (may be null)
     * @param resource the ICAP resource
     * @param responseContent the response content
     * @param compareContent true to compare the request and response content
     * @return the ICAP header information
     * @throws IOException In case of an I/O error or an error status code
     */
    private ICAPHeaderInformation processResponse(final String requestIdentifier, final ICAPSocket icapSocket, final ICAPMode icapMode, final ICAPRequestInformation requestInformation,
                                                  final ICAPHeaderInformation icapHeaderInformation, final MessageDigest inputMessageDigest, final ICAPResource resource,
                                                  final ResponseContent responseContent, final boolean compareContent) throws IOException {
        if (icapHeaderInformation.getStatus() == 204) { // unmodified
            return icapHeaderInformation;
        }

        if (icapHeaderInformation.getStatus() != 200) {
            throwStatusCodeException(icapHeaderInformation);
        }

        // OK - The ICAP status is ok, but the encapsulated HTTP status will likely be different
        if ((requestInformation.isAllow204() != null && !requestInformation.isAllow204()) && ICAPMode.REQMOD.equals(icapMode)) {
            return icapHeaderInformation;
        }

        if (!icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_ENCAPSULATED)) {
            LOG.warn("Missing " + ICAPConstants.HEADER_KEY_ENCAPSULATED + " information!");
            return icapHeaderInformation;
        }

        // no body to read, otherwise the read blocks until the read timeout on a keep-alive connection
        if (hasEncapsulatedEntry(icapHeaderInformation, "null-body")) {
            return icapHeaderInformation;
        }

        processResponseContent(requestIdentifier, icapSocket, icapHeaderInformation, inputMessageDigest, resource, responseContent, compareContent);
        return icapHeaderInformation;
    }


    /**
     * Process the response content, compute digests and capture trailers. Only the beginning of the response
     * content is kept in memory (it is used as threat information), the rest is only counted and digested.
     *
     * @param requestIdentifier the request identifier
     * @param icapSocket the ICAP socket
     * @param icapHeaderInformation the ICAP header information
     * @param inputMessageDigest the input message digest (may be null)
     * @param resource the ICAP resource
     * @param responseContent the response content
     * @param compareContent true to compare the request and response content
     * @throws IOException In case of an I/O error
     */
    private void processResponseContent(final String requestIdentifier, final ICAPSocket icapSocket,
                                         final ICAPHeaderInformation icapHeaderInformation,
                                         final MessageDigest inputMessageDigest,
                                         final ICAPResource resource, final ResponseContent responseContent,
                                         final boolean compareContent) throws IOException {
        // a server which sends endless content must not keep the request running forever
        final long requestLength = Math.max(resource.getResourceLength(), responseContent.getRequestLength());
        final long maxContentSize = Math.max(2 * requestLength, requestLength + MIN_RESPONSE_CONTENT_MARGIN);
        boolean couldProcessFullContent;
        MessageDigest outputMessageDigest = null;
        if (compareContent) {
            outputMessageDigest = ICAPClientUtil.getInstance().createMessageDigest(messageDigestAlgorithm);
            try (DigestOutputStream outputstream = new DigestOutputStream(responseContent, outputMessageDigest)) {
                couldProcessFullContent = (icapSocket.processContent(outputstream, maxContentSize) >= 0);
                outputstream.flush();
            }
        } else {
            try (ResponseContent outputstream = responseContent) {
                couldProcessFullContent = (icapSocket.processContent(outputstream, maxContentSize) >= 0);
                outputstream.flush();
            }
        }
        responseContent.setHttpStatus(icapSocket.getEncapsulatedHttpStatus());

        // Capture trailer headers if present (RFC 3507 §4.3.1)
        if (icapSocket.getTrailers() != null) {
            icapHeaderInformation.setTrailers(icapSocket.getTrailers());
        }

        if (compareContent) {
            String inputMsg = ICAPClientUtil.getInstance().messageDigestToString(messageDigestAlgorithm, inputMessageDigest);
            icapHeaderInformation.getHeaders().put(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST, Arrays.asList(inputMsg));
            String outputMsg = ICAPClientUtil.getInstance().messageDigestToString(messageDigestAlgorithm, outputMessageDigest);
            icapHeaderInformation.getHeaders().put(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST, Arrays.asList(outputMsg));

            // the result is always set, so the caller can see if the server modified the content
            boolean identicalContent = couldProcessFullContent && responseContent.getRequestLength() == responseContent.getLength() && inputMsg.equals(outputMsg);
            icapHeaderInformation.getHeaders().put(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT, Arrays.asList("" + identicalContent));
            if (identicalContent && LOG.isDebugEnabled()) {
                LOG.debug(requestIdentifier + "Input and output are equal -> allow, it's a valid response!");
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Resource length: " + resource.getResourceLength() + ", Response length: " + responseContent.getLength() + "?");
        }
    }


    /**
     * Throw an appropriate exception based on the ICAP status code (RFC 3507 §4.3.3).
     * 4xx codes throw ICAPRequestException, 5xx and unknown codes throw UnknownIOException.
     *
     * @param icapHeaderInformation the ICAP header information
     * @throws ICAPRequestException for 4xx client errors
     * @throws UnknownIOException for 5xx server errors and unknown status codes
     * @throws IOException in all cases (always throws)
     */
    private void throwStatusCodeException(ICAPHeaderInformation icapHeaderInformation) throws IOException {
        int status = icapHeaderInformation.getStatus();
        switch (status) {
            // 4xx client errors
            case 400: throw new ICAPRequestException(status, "Bad request", icapHeaderInformation);
            case 404: throw new ICAPRequestException(status, "ICAP service not found", icapHeaderInformation);
            case 405: throw new ICAPRequestException(status, "Method not allowed for service", icapHeaderInformation);
            case 408: throw new ICAPRequestException(status, "Request timeout", icapHeaderInformation);
            // 5xx server errors
            case 500: throw new UnknownIOException(status, "Server error", icapHeaderInformation);
            case 501: throw new UnknownIOException(status, "Method not implemented", icapHeaderInformation);
            case 502: throw new UnknownIOException(status, "Bad gateway", icapHeaderInformation);
            case 503: throw new UnknownIOException(status, "Service overloaded", icapHeaderInformation);
            case 505: throw new UnknownIOException(status, "ICAP version not supported", icapHeaderInformation);
            default:
                if (status >= 400 && status < 500) {
                    throw new ICAPRequestException(status, "Client error", icapHeaderInformation);
                }
                throw new UnknownIOException(status, "Unknown status code", icapHeaderInformation);
        }
    }


    /**
     * Check allow 204 support
     *
     * @param requestIdentifier the equest identifier
     * @param currentConfig the remote service configuration
     * @param isAllow204 the request information
     * @return the request string
     */
    protected String supportAllow204(final String requestIdentifier, final ICAPRemoteServiceConfiguration currentConfig, final Boolean isAllow204) {
        boolean serverAllow204 = currentConfig != null && currentConfig.isServerAllow204();

        String serverReason = "suppported by the icap-server";
        if (!serverAllow204) {
            serverReason = "not " + serverReason;
        }

        String requestReason = "requested";
        if (isAllow204 == null) {
            requestReason = "auto select";
        } else if (!isAllow204.booleanValue()) {
            requestReason = "not " + requestReason;
        }

        String selectAllow204Reason = "Not use allow 204";
        String allow204Request = "";
        if (serverAllow204 && (isAllow204 == null || isAllow204.booleanValue())) {
            selectAllow204Reason = "Use allow 204";
            allow204Request = "Allow: 204" + NEWLINE;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + selectAllow204Reason + ": " + requestReason + " (" + serverReason + ")");
        }
        return allow204Request;
    }


    /**
     * Get the connection mode based on whether pooling is enabled.
     *
     * @return "keep-alive" if pooling is enabled, "close" otherwise
     */
    private String getConnectionMode() {
        if (connectionManager.isPoolingEnabled()) {
            return "keep-alive";
        }
        return "close";
    }


    /**
     * Create request identifier
     *
     * @param mode the mode
     * @param sourceRequest the source request
     * @return the request identifier
     */
    protected String createRequestIdentifier(final String mode, final String sourceRequest) {
        // only used to correlate log messages, no secure random number needed
        return Long.toHexString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE).toUpperCase() + " - ";
    }


    /**
     * Get the effective request information. Returns the provided one if not null,
     * otherwise falls back to the default, or creates a new instance.
     *
     * @param requestInformation the provided request information, may be null
     * @return the effective request information, never null
     */
    private ICAPRequestInformation getEffectiveRequestInformation(final ICAPRequestInformation requestInformation) {
        if (requestInformation != null) {
            return requestInformation;
        }

        ICAPRequestInformation currentDefault = defaultRequestInformation;
        if (currentDefault != null) {
            return currentDefault;
        }
        return new ICAPRequestInformation();
    }


    /**
     * Validate the request information
     *
     * @param requestInformation the request information
     * @throws IOException In case of an invalid request information
     */
    protected void validateRequestInformation(final ICAPRequestInformation requestInformation) throws IOException {
        if (requestInformation == null) {
            throw new IOException("Invalid request information!");
        }

        // the api version is part of the ICAP request line
        if (requestInformation.getApiVersion() == null || !API_VERSION_PATTERN.matcher(requestInformation.getApiVersion()).matches()) {
            throw new IOException("Invalid request information, invalid api version [" + sanitizeLogValue(requestInformation.getApiVersion()) + "]!");
        }
    }


    /**
     * Parse transfer extension list from an ICAP header (RFC 3507 §4.10.2).
     * Values are comma-separated file extensions (e.g. "zip, tar, exe").
     *
     * @param icapHeaderInformation the ICAP header information
     * @param headerKey the header key
     * @return the list of extensions, or null if header not present
     */
    private List<String> parseTransferExtensions(ICAPHeaderInformation icapHeaderInformation, String headerKey) {
        if (!icapHeaderInformation.containsHeader(headerKey)
                || icapHeaderInformation.getHeaderValues(headerKey) == null
                || icapHeaderInformation.getHeaderValues(headerKey).isEmpty()) {
            return null;
        }

        List<String> extensions = new ArrayList<>();
        for (String value : icapHeaderInformation.getHeaderValues(headerKey)) {
            for (String ext : value.split(",")) {
                String trimmed = ext.trim();
                if (!trimmed.isEmpty()) {
                    extensions.add(trimmed);
                }
            }
        }
        return extensions;
    }


    /**
     * Close a resource quietly
     *
     * @param resource the resource
     */
    private void closeQuietly(ICAPResource resource) {
        try {
            resource.close();
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not close resource stream: " + e.getMessage());
            }
        }
    }


    /**
     * Captures the beginning of the response content up to a limit and counts the total length.
     *
     * @author patrick
     */
    protected static final class ResponseContent extends OutputStream {
        private final ByteArrayOutputStream captured;
        private final int limit;
        private long length;
        private long requestLength;
        private int httpStatus;


        /**
         * Constructor for ResponseContent
         *
         * @param limit the max number of bytes to keep in memory
         */
        ResponseContent(int limit) {
            this.captured = new ByteArrayOutputStream();
            this.limit = limit;
            this.length = 0;
        }


        /**
         * @see java.io.OutputStream#write(int)
         */
        @Override
        public void write(int b) {
            write(new byte[] {(byte) b}, 0, 1);
        }


        /**
         * @see java.io.OutputStream#write(byte[], int, int)
         */
        @Override
        public void write(byte[] b, int off, int len) {
            length += len;
            int toCapture = Math.min(len, limit - captured.size());
            if (toCapture > 0) {
                captured.write(b, off, toCapture);
            }
        }


        /**
         * Get the total length of the response content
         *
         * @return the total length
         */
        long getLength() {
            return length;
        }


        /**
         * Get the number of content bytes which were sent to the server
         *
         * @return the number of sent content bytes
         */
        long getRequestLength() {
            return requestLength;
        }


        /**
         * Set the number of content bytes which were sent to the server
         *
         * @param requestLength the number of sent content bytes
         */
        void setRequestLength(long requestLength) {
            this.requestLength = requestLength;
        }


        /**
         * Get the status code of the encapsulated HTTP response
         *
         * @return the status code or 0 if unknown
         */
        int getHttpStatus() {
            return httpStatus;
        }


        /**
         * Set the status code of the encapsulated HTTP response
         *
         * @param httpStatus the status code or 0 if unknown
         */
        void setHttpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
        }


        /**
         * Get the captured response content
         *
         * @return the captured content
         */
        String getContent() {
            return new String(captured.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
