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
import java.util.Objects;


/**
 * Implements the {@link ICAPRemoteServiceConfiguration}.
 *  
 * @author patrick
 */
public class ICAPRemoteServiceConfigurationImpl implements ICAPRemoteServiceConfiguration, Serializable {
    private static final long serialVersionUID = -1296347334233061866L;
    private int serverPreviewSize;
    private boolean serverAllow204 = false;
    private ICAPMode[] optionMethods;
    private Instant timestamp;

    
    /**
     * Constructor for ICAPRemoteServiceConfigurationImpl
     */
    public ICAPRemoteServiceConfigurationImpl() {
        this(null, null, 1024, false);
    }


    /**
     * Constructor for RemoteServiceConfiguration
     * 
     * @param timestamp the timestamp
     * @param optionMethods the option methods
     * @param serverPreviewSize the server preview size
     * @param serverAllow204 the server allow 204
     */
    public ICAPRemoteServiceConfigurationImpl(Instant timestamp, ICAPMode[] optionMethods, int serverPreviewSize, boolean serverAllow204) {
        this.timestamp = timestamp;
        this.optionMethods = optionMethods;
        this.serverPreviewSize = serverPreviewSize;
        this.serverAllow204 = serverAllow204;
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
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(optionMethods);
        result = prime * result + Objects.hash(serverAllow204, serverPreviewSize, timestamp);
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
        return Arrays.equals(optionMethods, other.optionMethods) && serverAllow204 == other.serverAllow204
                && serverPreviewSize == other.serverPreviewSize && Objects.equals(timestamp, other.timestamp);
    }


    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ICAPRemoteServiceConfigurationImpl [serverPreviewSize=" + serverPreviewSize + ", serverAllow204="
                + serverAllow204 + ", optionMethods=" + Arrays.toString(optionMethods) + ", timestamp=" + timestamp
                + "]";
    }
}

