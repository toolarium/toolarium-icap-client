/*
 * ICAPRequestException.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.exception;

import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import java.io.IOException;


/**
 * Exception thrown when the ICAP server returns a 4xx client error status code (RFC 3507 §4.3.3).
 * This indicates a problem with the client request, not the server itself.
 *
 * @author Patrick Meier
 */
public class ICAPRequestException extends IOException {
    private static final long serialVersionUID = 7294618253901847632L;
    private final int statusCode;
    private final ICAPHeaderInformation icapHeaderInformation;


    /**
     * Constructor for ICAPRequestException
     *
     * @param statusCode the ICAP status code
     * @param message the message
     * @param icapHeaderInformation the ICAP header information
     */
    public ICAPRequestException(int statusCode, String message, ICAPHeaderInformation icapHeaderInformation) {
        super(statusCode + ": " + message);
        this.statusCode = statusCode;
        this.icapHeaderInformation = icapHeaderInformation;
    }


    /**
     * Get the ICAP status code
     *
     * @return the status code
     */
    public int getStatusCode() {
        return statusCode;
    }


    /**
     * Get the ICAP header information
     *
     * @return the ICAP header information
     */
    public ICAPHeaderInformation getICAPHeaderInformation() {
        return icapHeaderInformation;
    }
}
