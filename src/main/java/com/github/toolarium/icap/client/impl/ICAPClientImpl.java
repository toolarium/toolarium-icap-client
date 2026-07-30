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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    private ICAPConnectionManager connectionManager;
    private ICAPServiceInformation serviceInformation;
    private volatile ICAPRemoteServiceConfiguration remoteServiceConfiguration;
    private int bufferSize = 8192;
    private String messageDigestAlgorithm = "SHA-256";
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
        if (remoteServiceConfiguration != null) {
            return remoteServiceConfiguration;
        }

        validateRequestInformation(requestInformation);
        final String requestIdentifier = createRequestIdentifier("options", null);
        try (ICAPSocket icapSocket = new ICAPSocket(connectionManager, requestIdentifier, serviceInformation.getHostName(), serviceInformation.getServicePort(), 
                                                    serviceInformation.getServiceName(), serviceInformation.isSecureConnection(), requestInformation.getMaxConnectionTimeout(), requestInformation.getMaxReadTimeout())) {
            icapSocket.write("OPTIONS icap://" + serviceInformation.getHostName() + ":" + serviceInformation.getServicePort() + "/" + serviceInformation.getServiceName() + " ICAP/" + requestInformation.getApiVersion() + NEWLINE
                             + "Host: " + serviceInformation.getHostName() + NEWLINE
                             + "User-Agent: " + sanitizeHeaderValue(requestInformation.getUserAgent()) + NEWLINE
                             + createAuthorizationHeader(requestInformation)
                             + createCustomHeaders(requestInformation)
                             + ICAPConstants.HEADER_KEY_ENCAPSULATED + ": null-body=0" + NEWLINE + NEWLINE);
            icapSocket.flush();

            ICAPHeaderInformation icapHeaderInformation = icapSocket.readICAPResponse(requestIdentifier, ICAP_END_SEPARATOR, bufferSize); 
            if (icapHeaderInformation.getStatus() != 200) {
                throw new IOException("Could not resolve options!");
            }
            
            int serverPreviewSize = 4096; // RFC 3507 §4.5: clients SHOULD support at least 4096 bytes
            if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_PREVIEW)
                    && icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_PREVIEW) != null
                    && icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_PREVIEW).size() > 0) {
                try {
                    serverPreviewSize = Integer.parseInt(icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_PREVIEW).get(0));
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(requestIdentifier + "Server preview size: " + serverPreviewSize);
                    }
                } catch (NumberFormatException e) {
                    LOG.warn(requestIdentifier + "Could not parse server preview size [" + icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_PREVIEW).get(0) + "]: " + e.getMessage());
                }
            }

            boolean serverAllow204 = false;
            if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_ALLOW)
                    && icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ALLOW) != null
                    && icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ALLOW).size() > 0) {
                serverAllow204 = Boolean.valueOf(icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ALLOW).get(0).equalsIgnoreCase("204"));
            }

            // Parse Options-TTL (RFC 3507 §4.10)
            int optionsTTL = -1;
            if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_OPTIONS_TTL)
                    && icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_OPTIONS_TTL) != null
                    && !icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_OPTIONS_TTL).isEmpty()) {
                try {
                    optionsTTL = Integer.parseInt(icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_OPTIONS_TTL).get(0).trim());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(requestIdentifier + "Server Options-TTL: " + optionsTTL + " seconds");
                    }
                } catch (NumberFormatException e) {
                    LOG.warn(requestIdentifier + "Could not parse Options-TTL [" + icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_OPTIONS_TTL).get(0) + "]: " + e.getMessage());
                }
            }

            // Parse Max-Connections (RFC 3507 §4.10)
            int maxConnections = -1;
            if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_MAX_CONNECTIONS)
                    && icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_MAX_CONNECTIONS) != null
                    && !icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_MAX_CONNECTIONS).isEmpty()) {
                try {
                    maxConnections = Integer.parseInt(icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_MAX_CONNECTIONS).get(0).trim());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(requestIdentifier + "Server Max-Connections: " + maxConnections);
                    }
                } catch (NumberFormatException e) {
                    LOG.warn(requestIdentifier + "Could not parse Max-Connections [" + icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_MAX_CONNECTIONS).get(0) + "]: " + e.getMessage());
                }
            }

            LOG.info(requestIdentifier + "Valid service ["
                     + icapHeaderInformation.getStatus() + "/" + icapHeaderInformation.getMessage() + "], "
                     + "allow 204: " + serverAllow204 + ", "
                     + "available methods: " + icapHeaderInformation.getHeaderValues("Methods"));

            int i = 0;
            ICAPMode[] result = new ICAPMode[icapHeaderInformation.getHeaderValues("Methods").size()];
            for (String method : icapHeaderInformation.getHeaderValues("Methods")) {
                result[i++] = ICAPMode.valueOf(method.trim());
            }

            ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl(
                    Instant.now(), result, serverPreviewSize, serverAllow204,
                    icapHeaderInformation.getHeaders());
            config.setOptionsTTL(optionsTTL);
            config.setMaxConnections(maxConnections);

            // Parse Service-ID (RFC 3507 §4.10)
            if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_SERVICE_ID)
                    && icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_SERVICE_ID) != null
                    && !icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_SERVICE_ID).isEmpty()) {
                config.setServiceId(icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_SERVICE_ID).get(0).trim());
            }

            // Parse Transfer-Preview, Transfer-Ignore, Transfer-Complete (RFC 3507 §4.10.2)
            config.setTransferPreview(parseTransferExtensions(icapHeaderInformation, ICAPConstants.HEADER_KEY_TRANSFER_PREVIEW));
            config.setTransferIgnore(parseTransferExtensions(icapHeaderInformation, ICAPConstants.HEADER_KEY_TRANSFER_IGNORE));
            config.setTransferComplete(parseTransferExtensions(icapHeaderInformation, ICAPConstants.HEADER_KEY_TRANSFER_COMPLETE));
            remoteServiceConfiguration = config;
            return remoteServiceConfiguration;
        } catch (IOException e) {
            remoteServiceConfiguration = null;
            throw e;
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
        if (resource.getResourceLength() == 0) {
            return new ICAPHeaderInformation();
        }
        validateICAPResource(resource);

        if (resource.isConsumed()) {
            throw new IOException("ICAPResource stream has already been consumed by a previous validateResource call. "
                    + "Create a new ICAPResource with a fresh InputStream to retry.");
        }

        ICAPMode icapMode = ICAPMode.REQMOD;
        if (inputMode != null) {
            icapMode = inputMode;
        }

        final String sourceRequest = sanitizeLogValue(requestInformation.prepareSourceRequest(resource));
        final String requestIdentifier = createRequestIdentifier(icapMode.name(), sourceRequest);
        LOG.info(requestIdentifier + "Validate resource (" + sourceRequest + ")");

        // validate the service availability — capture a local reference to avoid TOCTOU races
        // on the volatile field if a concurrent options() call nulls and re-sets it.
        ICAPRemoteServiceConfiguration currentConfig = remoteServiceConfiguration;
        if (currentConfig == null) {
            currentConfig = options(requestInformation);
        }

        // prepare preview size
        int previewSize = currentConfig.getServerPreviewSize();
        if (resource.getResourceLength() < previewSize) {
            previewSize = (int)resource.getResourceLength();
        }

        File resourceResponse = java.nio.file.Files.createTempFile("icap-", ".tmp").toFile();
        ICAPSocket icapSocket = null;
        try {
            icapSocket = new ICAPSocket(connectionManager, requestIdentifier, serviceInformation.getHostName(), serviceInformation.getServicePort(),
                                        serviceInformation.getServiceName(), serviceInformation.isSecureConnection(), requestInformation.getMaxConnectionTimeout(), requestInformation.getMaxReadTimeout());
            ICAPHeaderInformation icapHeaderInformation = processResource(requestIdentifier, icapSocket, icapMode, requestInformation, resource, resourceResponse);
            icapHeaderInformation.getHeaders().remove(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE);

            if (icapHeaderInformation.getStatus() == 200) {
                StringBuilder threadInformationBuilder = new StringBuilder();

                for (Map.Entry<String, List<String>> e: icapHeaderInformation.getHeaders().entrySet()) {
                    if (e.getKey().toLowerCase().startsWith("x-")) {
                        threadInformationBuilder.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                    }
                }
                String threadInformation = threadInformationBuilder.toString().trim();

                // verify if there is a thread is found taken from header
                if (hasThreadHeaderInformation(icapHeaderInformation)) {
                    String threadHeaderInformation = readThreadHeaderInformation(icapMode, icapHeaderInformation, resourceResponse);
                    String msg = "Threat found in resource (" + sourceRequest + ", http-status: " + icapHeaderInformation.getStatus() + "):\n" + threadInformation;
                    LOG.info(requestIdentifier + msg);
                    throw new ContentBlockedException(msg, icapHeaderInformation, threadHeaderInformation);
                } else if (supportCompareVerifyIdenticalContent
                        && icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT) && !icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).isEmpty()
                        && !Boolean.valueOf(icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).get(0))) {
                    String msg = "Not identical resource (" + sourceRequest + ", http-status: " + icapHeaderInformation.getStatus() + "):\n" + threadInformation;
                    LOG.info(requestIdentifier + msg);
                    throw new ContentBlockedException(msg, icapHeaderInformation);
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
            LOG.warn(requestIdentifier + "Could not access to ICAP server: " + eio.getMessage());
            throw eio;
        } finally {
            if (icapSocket != null) {
                icapSocket.close();
            }
            if (resourceResponse != null && resourceResponse.exists()) {
                if (!resourceResponse.delete()) {
                    resourceResponse.deleteOnExit();
                    LOG.warn(requestIdentifier + "Could not delete temp file [" + resourceResponse + "], scheduled for deletion on exit.");
                }
            }
        }
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
            final String key = sanitizeHeaderValue(e.getKey().trim());
            final String value = sanitizeHeaderValue(e.getValue().trim());

            if (key.equalsIgnoreCase("Host") || key.equalsIgnoreCase("Connection") || key.equalsIgnoreCase("User-Agent")
                    || key.equalsIgnoreCase("Preview") || key.equalsIgnoreCase("Encapsulated") || key.equalsIgnoreCase("Allow")
                    || key.equalsIgnoreCase("Transfer-Encoding") || key.equalsIgnoreCase("Content-Length")
                    || key.equalsIgnoreCase("Authorization")) {
                LOG.warn("Invalid customer header [" + key + "], it's not allowed, ignore!");
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
     * Read a file with a size limit to avoid loading large files entirely into memory.
     *
     * @param file the file to read
     * @param maxBytes the maximum number of bytes to read
     * @return the file content as string
     * @throws IOException In case of an I/O error
     */
    private String readFileLimited(File file, int maxBytes) throws IOException {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            int size = (int) Math.min(file.length(), maxBytes);
            byte[] buffer = new byte[size];
            int read = fis.read(buffer);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
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
        return value.replace("\r", "\\r").replace("\n", "\\n");
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
     * @param resourceResponse the resource response
     * @param icapHeaderInformation the ICAP header information
     * @return the thread content information
     */
    private String readThreadHeaderInformation(ICAPMode icapMode, ICAPHeaderInformation icapHeaderInformation, File resourceResponse) {
        String threadHeaderInformation = null;

        if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_ENCAPSULATED) && !icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ENCAPSULATED).isEmpty()
            && resourceResponse != null && resourceResponse.length() > 0 && resourceResponse.exists()) {                    
            for (int i = 0; i < icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ENCAPSULATED).size(); i++) {
                String entry = icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(i);
                String[] split = entry.split("=");
                // RFC 3507 §4.4.1: REQMOD response may contain req-body or res-body (error response)
                String entryKey = split[0].trim();
                if (split.length > 1 && (entryKey.equalsIgnoreCase(icapMode.getTag() + "-body") || entryKey.equalsIgnoreCase("res-body"))) {
                    try {
                        threadHeaderInformation = readFileLimited(resourceResponse, 64 * 1024).trim();
                    } catch (IOException e) {
                        LOG.warn("Could not read resource response: " + e.getMessage(), e);
                    }
                    
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
                || resource.getResourceLength() <= 0) {
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
     * @param resource the ICAP resource
     * @param resourceResponse the resource response
     * @return the ICAP header information
     * @throws IOException In case of an I/O error
     * @throws UnknownIOException In case of an unknown or unrecognized ICAP status code
     * @throws ContentBlockedException In case the content is blocked
     */
    protected ICAPHeaderInformation processResource(final String requestIdentifier,
                                                    final ICAPSocket icapSocket, 
                                                    final ICAPMode icapMode,
                                                    final ICAPRequestInformation requestInformation, 
                                                    final ICAPResource resource,
                                                    final File resourceResponse) throws IOException, ContentBlockedException {

        // first part of header — encapsulated HTTP request (RFC 3507 §4.3: Via header for surrogate identification)
        String httpMethod = "GET";
        String viaHeader = "Via: 1.1 " + sanitizeHeaderValue(requestInformation.getUserAgent()) + NEWLINE;
        String header = httpMethod + " /" + URLEncoder.encode(resource.getResourceName().trim(), StandardCharsets.UTF_8.name()) + " HTTP/1.1" + NEWLINE
                        + "Host: " + sanitizeHeaderValue(requestInformation.getRequestSource()) + NEWLINE
                        + viaHeader + NEWLINE;
        String reqHdr = "";
        String bodyHdr = "";
        if (ICAPMode.RESPMOD.equals(icapMode)) {
            reqHdr = "req-hdr=0, ";
            bodyHdr = icapMode.getTag() + "-hdr=" + header.length() + ", ";
        } else {
            reqHdr = "req-hdr=0, ";
        }

        // Capture a local snapshot of the volatile field so every read in this method
        // sees a consistent non-null value even if a concurrent options() refresh runs.
        ICAPRemoteServiceConfiguration currentConfig = remoteServiceConfiguration;
        if (currentConfig == null) {
            throw new IOException("Remote service configuration is not available");
        }
        int previewSize = currentConfig.getServerPreviewSize();
        if (resource.getResourceLength() < previewSize) {
            previewSize = (int) resource.getResourceLength();
        }
        String body;
        if (ICAPMode.RESPMOD.equals(icapMode)) {
            body = header + "HTTP/1.1 200 OK" + NEWLINE + ICAPConstants.HEADER_KEY_TRANSFER_ENCODING + ": chunked" + NEWLINE
                   + ICAPConstants.HEADER_KEY_CONTENT_LENGTH + ": " + resource.getResourceLength() + NEWLINE + NEWLINE;
        } else {
            body = header;
        }

        String requestBuffer = "" + icapMode.name() + " icap://" + serviceInformation.getHostName() + ":" + serviceInformation.getServicePort() + "/" + serviceInformation.getServiceName() + " ICAP/" + requestInformation.getApiVersion() + NEWLINE
                             + "Host: " + serviceInformation.getHostName() + NEWLINE
                             + "Connection: " + getConnectionMode() + NEWLINE
                             + "User-Agent: " + sanitizeHeaderValue(requestInformation.getUserAgent()) + NEWLINE
                             + createAuthorizationHeader(requestInformation)
                             + createCustomHeaders(requestInformation)
                             + supportAllow204(requestIdentifier, requestInformation.isAllow204())
                             + "Preview: " + previewSize + NEWLINE 
                             + "Encapsulated: " + reqHdr + bodyHdr + icapMode.getTag() + "-body=" + body.length() + NEWLINE + NEWLINE 
                             + body
                             + Integer.toHexString(previewSize) + NEWLINE;
        icapSocket.write(requestBuffer);

        // sending preview or, if smaller than previewSize, the whole file.
        byte[] chunk = new byte[previewSize];
        
        MessageDigest inputMessageDigest = null;
        InputStream inputstream;
        if (supportCompareVerifyIdenticalContent) {
            inputMessageDigest = ICAPClientUtil.getInstance().createMessageDigest(messageDigestAlgorithm);
            inputstream = new DigestInputStream(resource.getResourceBody(), inputMessageDigest);
        } else {
            inputstream = resource.getResourceBody();
        }
        resource.markConsumed();
        int readBytes = inputstream.read(chunk);
        icapSocket.write(chunk, 0, readBytes);
        icapSocket.write(NEWLINE);
        if (resource.getResourceLength() <= previewSize) {
            icapSocket.write("0; ieof" + ICAP_END_SEPARATOR);
            icapSocket.flush();
        } else if (previewSize != 0) {
            icapSocket.write(HTTP_END_SEPARATOR);
            icapSocket.flush();
        }

        // parse the response; it might not be "100 continue" if fileSize < previewSize, then this is actually the respond otherwise it is a "go" for the rest of the file.
        if (resource.getResourceLength() > previewSize) {
            ICAPHeaderInformation icapHeaderInformation = icapSocket.readICAPResponse(requestIdentifier, ICAP_END_SEPARATOR, bufferSize);
            switch (icapHeaderInformation.getStatus()) {
                case 100: break; // continue transfer
                case 200: return icapHeaderInformation;
                case 204: return icapHeaderInformation;
                default: throwStatusCodeException(icapHeaderInformation);
            }
        }

        // sending remaining part of file
        if (resource.getResourceLength() > previewSize) {
            long totalReadBytes = readBytes;
            byte[] buffer = new byte[bufferSize];
            readBytes = -1;
            while ((readBytes = inputstream.read(buffer)) != -1) {
                totalReadBytes += readBytes;
                if (LOG.isDebugEnabled()) {
                    LOG.debug(requestIdentifier + "Send next block of " + readBytes + " bytes (total sent: " + totalReadBytes + " bytes)...");
                }
                icapSocket.write((Integer.toHexString(readBytes) + NEWLINE));
                icapSocket.write(buffer, 0, readBytes);
                icapSocket.write(NEWLINE);
            }
            
            // closing resource transfer.
            icapSocket.write(HTTP_END_SEPARATOR);
            icapSocket.flush();
        }

        if (supportCompareVerifyIdenticalContent && inputstream instanceof DigestInputStream) {
            inputstream.close();
        }

        ICAPHeaderInformation icapHeaderInformation = icapSocket.readICAPResponse(requestIdentifier, ICAP_END_SEPARATOR, bufferSize);
        if (icapHeaderInformation.getStatus() == 204) { // unmodified
            return icapHeaderInformation;
        } 

        if (icapHeaderInformation.getStatus() == 200) { // OK - The ICAP status is ok, but the encapsulated HTTP status will likely be different
            if ((requestInformation.isAllow204() != null && !requestInformation.isAllow204()) && ICAPMode.REQMOD.equals(icapMode)) {
                return icapHeaderInformation;
            }
            
            if (!icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_ENCAPSULATED)) {
                LOG.warn("Missing " + ICAPConstants.HEADER_KEY_ENCAPSULATED + " information!");
                return icapHeaderInformation;
            }

            processResponseContent(requestIdentifier, icapSocket, icapHeaderInformation, inputMessageDigest, resource, resourceResponse);
            return icapHeaderInformation;
        }
        
        throwStatusCodeException(icapHeaderInformation);
        return null; // unreachable, throwStatusCodeException always throws
    }


    /**
     * Process the response content, compute digests and capture trailers.
     *
     * @param requestIdentifier the request identifier
     * @param icapSocket the ICAP socket
     * @param icapHeaderInformation the ICAP header information
     * @param inputMessageDigest the input message digest (may be null)
     * @param resource the ICAP resource
     * @param resourceResponse the resource response file
     * @throws IOException In case of an I/O error
     */
    private void processResponseContent(final String requestIdentifier, final ICAPSocket icapSocket,
                                         final ICAPHeaderInformation icapHeaderInformation,
                                         final MessageDigest inputMessageDigest,
                                         final ICAPResource resource, final File resourceResponse) throws IOException {
        boolean couldProcessFullContent;
        MessageDigest outputMessageDigest = null;
        if (supportCompareVerifyIdenticalContent) {
            outputMessageDigest = ICAPClientUtil.getInstance().createMessageDigest(messageDigestAlgorithm);
            try (DigestOutputStream outputstream = new DigestOutputStream(new BufferedOutputStream(new FileOutputStream(resourceResponse)), outputMessageDigest)) {
                couldProcessFullContent = (icapSocket.processContent(outputstream) >= 0);
                outputstream.flush();
            }
        } else {
            try (BufferedOutputStream outputstream = new BufferedOutputStream(new FileOutputStream(resourceResponse))) {
                couldProcessFullContent = (icapSocket.processContent(outputstream) >= 0);
                outputstream.flush();
            }
        }
        icapSocket.flush();

        // Capture trailer headers if present (RFC 3507 §4.3.1)
        if (icapSocket.getTrailers() != null) {
            icapHeaderInformation.setTrailers(icapSocket.getTrailers());
        }

        if (supportCompareVerifyIdenticalContent) {
            String inputMsg = ICAPClientUtil.getInstance().messageDigestToString(messageDigestAlgorithm, inputMessageDigest);
            icapHeaderInformation.getHeaders().put(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST, Arrays.asList(inputMsg));
            String outputMsg = ICAPClientUtil.getInstance().messageDigestToString(messageDigestAlgorithm, outputMessageDigest);
            icapHeaderInformation.getHeaders().put(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST, Arrays.asList(outputMsg));

            boolean identicalContent = couldProcessFullContent && resource.getResourceLength() == resourceResponse.length() && inputMsg.equals(outputMsg);
            if (identicalContent) {
                icapHeaderInformation.getHeaders().put(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT, Arrays.asList("" + identicalContent));
                if (LOG.isDebugEnabled()) {
                    LOG.debug(requestIdentifier + "Input and output are equal -> allow, it's a valid response!");
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(requestIdentifier + "Resource length: " + resource.getResourceLength() + ", Response length: " + resourceResponse.length() + "?");
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
     * @param isAllow204 the request information
     * @return the request string
     */
    protected String supportAllow204(final String requestIdentifier, final Boolean isAllow204) {
        // Capture once to avoid reading the volatile field multiple times inconsistently.
        ICAPRemoteServiceConfiguration currentConfig = remoteServiceConfiguration;
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
        return Long.toHexString(java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE).toUpperCase() + " - ";
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
        if (defaultRequestInformation != null) {
            return defaultRequestInformation;
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
}
