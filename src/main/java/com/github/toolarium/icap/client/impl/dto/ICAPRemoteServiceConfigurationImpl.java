/*
 * ICAPRemoteServiceConfigurationImpl.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl.dto;

import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration;
import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Implements the {@link ICAPRemoteServiceConfiguration}.
 *  
 * @author patrick
 */
public class ICAPRemoteServiceConfigurationImpl implements ICAPRemoteServiceConfiguration, Serializable {
    private static final long serialVersionUID = -1296347334233061867L;
    private final int serverPreviewSize;
    private final boolean serverAllow204;
    private final ICAPMode[] optionMethods;
    private final Instant timestamp;
    private final Map<String, List<String>> headers;
    private int optionsTTL;
    private int maxConnections;
    private String serviceId;
    private List<String> transferPreview;
    private List<String> transferIgnore;
    private List<String> transferComplete;


    /**
     * Constructor for ICAPRemoteServiceConfigurationImpl
     */
    public ICAPRemoteServiceConfigurationImpl() {
        this(null, null, 4096, false, null);
    }


    /**
     * Constructor for RemoteServiceConfiguration
     *
     * @param timestamp the timestamp
     * @param optionMethods the option methods
     * @param serverPreviewSize the server preview size
     * @param serverAllow204 the server allow 204
     * @param headers the icap header information
     */
    public ICAPRemoteServiceConfigurationImpl(Instant timestamp, ICAPMode[] optionMethods, int serverPreviewSize, boolean serverAllow204, Map<String, List<String>> headers) {
        this.timestamp = timestamp;
        this.optionMethods = optionMethods;
        this.serverPreviewSize = serverPreviewSize;
        this.serverAllow204 = serverAllow204;
        this.headers = headers;
        this.optionsTTL = -1;
        this.maxConnections = -1;
    }


    /**
     * Set the Options-TTL in seconds (RFC 3507 §4.10).
     *
     * @param optionsTTL the options TTL in seconds
     * @return this instance
     */
    public ICAPRemoteServiceConfigurationImpl setOptionsTTL(int optionsTTL) {
        this.optionsTTL = optionsTTL;
        return this;
    }


    /**
     * Set the max connections (RFC 3507 §4.10).
     *
     * @param maxConnections the max connections
     * @return this instance
     */
    public ICAPRemoteServiceConfigurationImpl setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }


    /**
     * Set the Service-ID (RFC 3507 §4.10).
     *
     * @param serviceId the service id
     * @return this instance
     */
    public ICAPRemoteServiceConfigurationImpl setServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }


    /**
     * Set the Transfer-Preview extensions (RFC 3507 §4.10.2).
     *
     * @param transferPreview the transfer preview extensions
     * @return this instance
     */
    public ICAPRemoteServiceConfigurationImpl setTransferPreview(List<String> transferPreview) {
        this.transferPreview = transferPreview;
        return this;
    }


    /**
     * Set the Transfer-Ignore extensions (RFC 3507 §4.10.2).
     *
     * @param transferIgnore the transfer ignore extensions
     * @return this instance
     */
    public ICAPRemoteServiceConfigurationImpl setTransferIgnore(List<String> transferIgnore) {
        this.transferIgnore = transferIgnore;
        return this;
    }


    /**
     * Set the Transfer-Complete extensions (RFC 3507 §4.10.2).
     *
     * @param transferComplete the transfer complete extensions
     * @return this instance
     */
    public ICAPRemoteServiceConfigurationImpl setTransferComplete(List<String> transferComplete) {
        this.transferComplete = transferComplete;
        return this;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getServerPreviewSize()
     */
    @Override
    public int getServerPreviewSize() {
        return serverPreviewSize;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#isServerAllow204()
     */
    @Override
    public boolean isServerAllow204() {
        return serverAllow204;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getOptionMethods()
     */
    @Override
    public ICAPMode[] getOptionMethods() {
        return optionMethods;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getTimestamp()
     */
    @Override
    public Instant getTimestamp() {
        return timestamp;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getOptionsTTL()
     */
    @Override
    public int getOptionsTTL() {
        return optionsTTL;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getMaxConnections()
     */
    @Override
    public int getMaxConnections() {
        return maxConnections;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getServiceId()
     */
    @Override
    public String getServiceId() {
        return serviceId;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getTransferPreview()
     */
    @Override
    public List<String> getTransferPreview() {
        return transferPreview;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getTransferIgnore()
     */
    @Override
    public List<String> getTransferIgnore() {
        return transferIgnore;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getTransferComplete()
     */
    @Override
    public List<String> getTransferComplete() {
        return transferComplete;
    }


    /**
     * @see com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration#getHeaders()
     */
    @Override
    public Map<String, List<String>> getHeaders() {
        return headers;
    }


    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(optionMethods);
        result = prime * result + Objects.hash(headers, serverAllow204, serverPreviewSize, timestamp,
                optionsTTL, maxConnections, serviceId, transferPreview, transferIgnore, transferComplete);
        return result;
    }


    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        ICAPRemoteServiceConfigurationImpl other = (ICAPRemoteServiceConfigurationImpl) obj;
        return Objects.equals(headers, other.headers)
                && Arrays.equals(optionMethods, other.optionMethods) && serverAllow204 == other.serverAllow204
                && serverPreviewSize == other.serverPreviewSize && Objects.equals(timestamp, other.timestamp)
                && optionsTTL == other.optionsTTL && maxConnections == other.maxConnections
                && Objects.equals(serviceId, other.serviceId)
                && Objects.equals(transferPreview, other.transferPreview)
                && Objects.equals(transferIgnore, other.transferIgnore)
                && Objects.equals(transferComplete, other.transferComplete);
    }


    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ICAPRemoteServiceConfigurationImpl [serverPreviewSize=" + serverPreviewSize + ", serverAllow204="
                + serverAllow204 + ", optionMethods=" + Arrays.toString(optionMethods) + ", timestamp=" + timestamp
                + ", optionsTTL=" + optionsTTL + ", maxConnections=" + maxConnections + ", serviceId=" + serviceId
                + "]";
    }
}

