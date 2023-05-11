/*
 * ICAPClient.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import java.io.IOException;


/**
 * Defines the ICAP client
 *
 * @author Patrick Meier
 */
public interface ICAPClient {
    
    /**
     * Get the ICAP options
     *
     * @return the ICAP remote service configuration
     * @throws IOException In case of an I/O error
     */
    ICAPRemoteServiceConfiguration options() throws IOException;

    
    /**
     * Get the ICAP options
     *
     * @param requestInformation the ICAP request information
     * @return the ICAP remote service configuration
     * @throws IOException In case of an I/O error
     */
    ICAPRemoteServiceConfiguration options(ICAPRequestInformation requestInformation) throws IOException;


    /**
     * Validate a resource
     *
     * @param mode the ICAP mode
     * @param resource the ICAP resource
     * @return the ICAP header information
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    ICAPHeaderInformation validateResource(ICAPMode mode, ICAPResource resource) throws IOException, ContentBlockedException;


    /**
     * Validate a resource
     *
     * @param mode the ICAP mode
     * @param requestInformation the ICAP request information
     * @param resource the ICAP resource
     * @return the ICAP header information
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    ICAPHeaderInformation validateResource(ICAPMode mode, ICAPRequestInformation requestInformation, ICAPResource resource) throws IOException, ContentBlockedException;

    
    /**
     * Define if the client support verify and compare input and output content
     *
     * @param supportCompareVerifyIdenticalContent true to support; otherwise false (by default = false)
     * @return this client
     */
    ICAPClient supportCompareVerifyIdenticalContent(boolean supportCompareVerifyIdenticalContent);
}
