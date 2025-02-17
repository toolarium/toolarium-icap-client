/*
 * ICAPRemoteServiceConfiguration.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;


/**
 * Defines the remote service configuration
 *  
 * @author patrick
 */
public interface ICAPRemoteServiceConfiguration {
    
    /**
     * Get the server preview size
     *
     * @return the server preview size
     */
    int getServerPreviewSize();

    
    /**
     * Define if server allow 204
     *
     * @return true if server allow 204
     */
    boolean isServerAllow204();

    
    /**
     * Get option methods
     *
     * @return the option methods
     */
    ICAPMode[] getOptionMethods();

    
    /**
     * Get the timestamp of the request
     *
     * @return the timestamp of the request
     */
    Instant getTimestamp();
    
    
    /**
     * Get the header entries
     *
     * @return the header entries
     */
    Map<String, List<String>> getHeaders();
}
