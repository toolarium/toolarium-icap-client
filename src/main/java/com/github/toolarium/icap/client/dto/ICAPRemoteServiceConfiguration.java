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
     * Get the Options-TTL in seconds as advertised by the server (RFC 3507 §4.10).
     * Returns -1 if the server did not advertise an Options-TTL.
     *
     * @return the options TTL in seconds, or -1 if not specified
     */
    int getOptionsTTL();


    /**
     * Get the max connections as advertised by the server (RFC 3507 §4.10).
     * Returns -1 if the server did not advertise Max-Connections.
     *
     * @return the max connections, or -1 if not specified
     */
    int getMaxConnections();


    /**
     * Get the Service-ID as advertised by the server (RFC 3507 §4.10).
     *
     * @return the service id, or null if not specified
     */
    String getServiceId();


    /**
     * Get the Transfer-Preview file extensions (RFC 3507 §4.10.2).
     *
     * @return the transfer preview extensions, or null if not specified
     */
    List<String> getTransferPreview();


    /**
     * Get the Transfer-Ignore file extensions (RFC 3507 §4.10.2).
     *
     * @return the transfer ignore extensions, or null if not specified
     */
    List<String> getTransferIgnore();


    /**
     * Get the Transfer-Complete file extensions (RFC 3507 §4.10.2).
     *
     * @return the transfer complete extensions, or null if not specified
     */
    List<String> getTransferComplete();


    /**
     * Get the header entries
     *
     * @return the header entries
     */
    Map<String, List<String>> getHeaders();
}
