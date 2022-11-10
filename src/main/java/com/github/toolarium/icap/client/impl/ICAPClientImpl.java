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
import com.github.toolarium.icap.client.impl.dto.ICAPRemoteServiceConfigurationImpl;
import com.github.toolarium.icap.client.util.ICAPClientUtil;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
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
    private ICAPRemoteServiceConfiguration remoteServiceConfiguration;
    private int bufferSize = 8192;
    private String messageDigestAlgorithm = "SHA-256";


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
    }

    
    /**
     * @see com.github.toolarium.icap.client.ICAPClient#options()
     */
    @Override    
    public ICAPRemoteServiceConfiguration options() throws IOException {
        return options(new ICAPRequestInformation());
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
        try (ICAPSocket icapSocket = new ICAPSocket(connectionManager, requestIdentifier, serviceInformation.getHostName(), serviceInformation.getServicePort(), serviceInformation.getServiceName(), serviceInformation.isSecureConnection())) {
            icapSocket.write("OPTIONS icap://" + serviceInformation.getHostName() + "/" + serviceInformation.getServiceName() + " ICAP/" + requestInformation.getApiVersion() + NEWLINE 
                           + "Host: " + serviceInformation.getHostName() + NEWLINE 
                           + "User-Agent: " + requestInformation.getUserAgent() + NEWLINE 
                           + ICAPConstants.HEADER_KEY_ENCAPSULATED + ": null-body=0" + NEWLINE + NEWLINE);
            icapSocket.flush();

            ICAPHeaderInformation icapHeaderInformation = icapSocket.readICAPResponse(requestIdentifier, ICAP_END_SEPARATOR, bufferSize); 
            if (icapHeaderInformation.getStatus() != 200) {
                throw new IOException("Could not resolve options!");
            }
            
            int serverPreviewSize = 1024;
            if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_PREVIEW) 
                    && icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_PREVIEW) != null 
                    && icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_PREVIEW).size() > 0) {
                try {
                    serverPreviewSize = Integer.parseInt(icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_PREVIEW).get(0));
                    LOG.debug(requestIdentifier + "Server preview size: " + serverPreviewSize);
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
            
            LOG.info(requestIdentifier + "Valid service [" 
                     + icapHeaderInformation.getStatus() + "/" + icapHeaderInformation.getMessage() + "], "
                     + "allow 204: " + serverAllow204 + ", "
                     + "available methods: " + icapHeaderInformation.getHeaderValues("Methods"));
            
            int i = 0;
            ICAPMode[] result = new ICAPMode[icapHeaderInformation.getHeaderValues("Methods").size()];
            for (String method : icapHeaderInformation.getHeaderValues("Methods")) {
                result[i++] = ICAPMode.valueOf(method.trim());
            }

            remoteServiceConfiguration = new ICAPRemoteServiceConfigurationImpl(Instant.now(), result, serverPreviewSize, serverAllow204);
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
        return validateResource(mode, new ICAPRequestInformation(), resource);
    }


    /**
     * @see com.github.toolarium.icap.client.ICAPClient#validateResource(com.github.toolarium.icap.client.dto.ICAPMode, com.github.toolarium.icap.client.dto.ICAPRequestInformation, com.github.toolarium.icap.client.dto.ICAPResource)
     */
    @Override
    public ICAPHeaderInformation validateResource(final ICAPMode inputMode, final ICAPRequestInformation requestInformation, final ICAPResource resource) throws IOException, ContentBlockedException {
        validateRequestInformation(requestInformation);
        validateResource(resource);

        ICAPMode mode = ICAPMode.REQMOD;
        if (inputMode != null) {
            mode = inputMode;
        }

        String resourceName = null;
        Long resourceLength = null;
        if (resource != null) {
            resourceName = resource.getResourceName().trim();
            resourceLength = resource.getResourceLength();
        }
        
        final String sourceRequest = requestInformation.prepareSourceRequest(resourceName, resourceLength);
        final String requestIdentifier = createRequestIdentifier(mode.name(), sourceRequest);
        LOG.info(requestIdentifier + "Validate resource (" + sourceRequest + ")");

        // validate the service availability
        if (remoteServiceConfiguration == null) {
            options(requestInformation);
        }

        // prepare preview size
        int previewSize = remoteServiceConfiguration.getServerPreviewSize();
        if (resource.getResourceLength() < previewSize) {
            previewSize = (int)resource.getResourceLength();
        }

        File resourceResponse = File.createTempFile(requestIdentifier, ".tmp");
        try (ICAPSocket icapSocket = new ICAPSocket(connectionManager, requestIdentifier, serviceInformation.getHostName(), serviceInformation.getServicePort(), serviceInformation.getServiceName(), serviceInformation.isSecureConnection())) {
            ICAPHeaderInformation icapHeaderInformation = processResource(requestIdentifier, icapSocket, requestInformation, resource, resourceResponse);
            icapHeaderInformation.getHeaders().remove(ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE);
            
            if (icapHeaderInformation.getStatus() == 200) {
                String threadInformation = "";

                for (Map.Entry<String, List<String>> e: icapHeaderInformation.getHeaders().entrySet()) {
                    if (e.getKey().toLowerCase().startsWith("x-")) {
                        threadInformation += "- " + e.getKey() + ": " + e.getValue() + "\n";
                    }
                }
                
                if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND) || icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND)) {
                    String errorContent = "";
                    if (resourceResponse != null 
                            && icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ENCAPSULATED) 
                            && !icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).isEmpty()
                            && resourceResponse.length() > 0 && resourceResponse.exists()) {                    
                        for (int i = 0; i < icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).size(); i++) {
                            String entry = icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(i);
                            String[] split = entry.split("=");
                            if (split.length > 1 && split[0].trim().equalsIgnoreCase("res-body")) {
                                errorContent = new String(ICAPClientUtil.getInstance().readFile(resourceResponse), Charset.forName("UTF-8")).trim();
                                break;
                            }
                        }
                    }

                    String msg = "Threat found in resource (" + sourceRequest + ", http-status: " + icapHeaderInformation.getStatus() + "):\n" + threadInformation.trim();
                    LOG.info(requestIdentifier + msg);
                    throw new ContentBlockedException(msg, icapHeaderInformation, errorContent);                    
                } else if (icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT)
                        && !icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).isEmpty()
                        && !Boolean.valueOf(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).get(0))) {
                    String msg = "Not identical resource (" + sourceRequest + ", http-status: " + icapHeaderInformation.getStatus() + "):\n" + threadInformation.trim();
                    LOG.info(requestIdentifier + msg);
                    throw new ContentBlockedException(msg, icapHeaderInformation);                    
                }
            }

            LOG.info(requestIdentifier + "Valid resource (" + sourceRequest + ", http-status: " + icapHeaderInformation.getStatus() + ").");
            return icapHeaderInformation;
        } catch (IOException eio) {
            LOG.warn(requestIdentifier + "Could not access to ICAP server: " + eio.getMessage());
            throw eio;
        } finally {
            if (resourceResponse != null && resourceResponse.exists()) {
                resourceResponse.delete();
            }
        }
    }

    
    /**
     * Validate resource
     * 
     * @param resource the resource
     * @throws IOException In case of an invalid resource
     */
    protected void validateResource(final ICAPResource resource) throws IOException {
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
     * @param requestInformation the ICAP request information
     * @param resource the ICAP resource
     * @param resourceResponse the resource response
     * @return the ICAP header information
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    protected ICAPHeaderInformation processResource(final String requestIdentifier, 
                                                    final ICAPSocket icapSocket, 
                                                    final ICAPRequestInformation requestInformation, 
                                                    final ICAPResource resource,
                                                    final File resourceResponse) throws IOException, ContentBlockedException {

        // first part of header
        String resHeader = "GET /" + URLEncoder.encode(resource.getResourceName().trim(), StandardCharsets.UTF_8.name()) + " HTTP/1.1" + NEWLINE 
                + "Host: " + serviceInformation.getHostName() + ":" + serviceInformation.getServicePort() + NEWLINE + NEWLINE;
        String resBody = resHeader + "HTTP/1.1 200 OK" + NEWLINE + ICAPConstants.HEADER_KEY_TRANSFER_ENCODING + ": chunked" + NEWLINE 
                + ICAPConstants.HEADER_KEY_CONTENT_LENGTH + ": " + resource.getResourceLength() + NEWLINE + NEWLINE;

        int previewSize = remoteServiceConfiguration.getServerPreviewSize();
        if (resource.getResourceLength() < previewSize) {
            previewSize = (int) resource.getResourceLength();
        }

        String requestBuffer = "RESPMOD icap://" + serviceInformation.getHostName() + "/" + serviceInformation.getServiceName() + " ICAP/" + requestInformation.getApiVersion() + NEWLINE 
                        + "Host: " + serviceInformation.getHostName() + NEWLINE
                        + "Connection:  close" + NEWLINE 
                        + "User-Agent: " + requestInformation.getUserAgent() + NEWLINE 
                        + supportAllow204(requestIdentifier, requestInformation.isAllow204())
                        + "Preview: " + previewSize + NEWLINE 
                        + "Encapsulated: req-hdr=0, res-hdr=" + resHeader.length() + ", res-body=" + resBody.length() + NEWLINE + NEWLINE 
                        + resBody
                        + Integer.toHexString(previewSize) + NEWLINE;
        icapSocket.write(requestBuffer);

        // sending preview or, if smaller than previewSize, the whole file.
        byte[] chunk = new byte[previewSize];
        
        MessageDigest inputMessageDigest = ICAPClientUtil.getInstance().createMessageDigest(messageDigestAlgorithm);
        DigestInputStream inputstream = new DigestInputStream(resource.getResourceBody(), inputMessageDigest); 
        int readBytes = inputstream.read(chunk);
        long totalReadBytes = readBytes;
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
                case 404: throw new IOException("404: ICAP Service not found");
                default: throw new IOException("Server returned unknown status code:" + icapHeaderInformation.getStatus());
            }
        }

        // sending remaining part of file
        if (resource.getResourceLength() > previewSize) {
            byte[] buffer = new byte[bufferSize];
            readBytes = -1;
            while ((readBytes = inputstream.read(buffer)) != -1) {
                totalReadBytes += readBytes;                    
                LOG.debug(requestIdentifier + "Send next block of " + readBytes + " bytes (total sent: " + totalReadBytes + " bytes)...");
                icapSocket.write((Integer.toHexString(readBytes) + NEWLINE));
                icapSocket.write(buffer, 0, readBytes);
                icapSocket.write(NEWLINE);
            }
            
            // closing resource transfer.
            icapSocket.write(HTTP_END_SEPARATOR);
            icapSocket.flush();
        }
        
        ICAPHeaderInformation icapHeaderInformation = icapSocket.readICAPResponse(requestIdentifier, ICAP_END_SEPARATOR, bufferSize);
        if (icapHeaderInformation.getStatus() == 204) { // unmodified
            return icapHeaderInformation;
        } 

        if (icapHeaderInformation.getStatus() == 200) { // OK - The ICAP status is ok, but the encapsulated HTTP status will likely be different
            MessageDigest outputMessageDigest = ICAPClientUtil.getInstance().createMessageDigest(messageDigestAlgorithm);
            try (DigestOutputStream outputstream = new DigestOutputStream(new BufferedOutputStream(new FileOutputStream(resourceResponse)), outputMessageDigest)) {
                icapSocket.processContent(outputstream);
            }
            
            String inputMsg = ICAPClientUtil.getInstance().messageDigestToString(messageDigestAlgorithm, inputMessageDigest);
            icapHeaderInformation.getHeaders().put(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST, Arrays.asList(inputMsg));
            String outputMsg = ICAPClientUtil.getInstance().messageDigestToString(messageDigestAlgorithm, outputMessageDigest);            
            icapHeaderInformation.getHeaders().put(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST, Arrays.asList(outputMsg));

            boolean identicalContent = resource.getResourceLength() == resourceResponse.length() && inputMsg.equals(outputMsg);
            icapHeaderInformation.getHeaders().put(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT, Arrays.asList("" + identicalContent));
            if (identicalContent) {
                LOG.debug(requestIdentifier + "Input and output are equal -> allow, it's a valid response!");
            }

            return icapHeaderInformation;
        }
        
        throw new IOException("Unrecognized or no status code in response header: " + icapHeaderInformation.getStatus() + "!");
    }


    /**
     * Check allow 204 support
     * 
     * @param requestIdentifier the equest identifier
     * @param isAllow204 the request information
     * @return the request string
     */
    protected String supportAllow204(final String requestIdentifier, final Boolean isAllow204) {
        
        String serverReason = "suppported by the icap-server";    
        if (!remoteServiceConfiguration.isServerAllow204()) {
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
        if (remoteServiceConfiguration.isServerAllow204() && (isAllow204 == null || isAllow204.booleanValue())) {
            selectAllow204Reason = "Use allow 204";
            allow204Request = "Allow: 204" + NEWLINE;
        }
        
        LOG.debug(requestIdentifier + selectAllow204Reason + ": " + requestReason + " (" + serverReason + ")");
        return allow204Request;
    }

    
    /**
     * Create request identifier
     * 
     * @param mode the mode
     * @param sourceRequest the source request
     * @return the request identifier
     */
    protected String createRequestIdentifier(final String mode, final String sourceRequest) {
        return Integer.toHexString(("" + Instant.now() + "|" + mode + "|" + sourceRequest).hashCode()).toUpperCase() + " - ";
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
}
