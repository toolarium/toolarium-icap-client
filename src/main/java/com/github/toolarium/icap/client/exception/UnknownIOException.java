/*
 * UnknownIOException.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.exception;

import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import java.io.IOException;


/**
 * The unknown IO exception which provides access to the ICAP header information
 * returned by the server in error cases (e.g. HTTP 500).
 *
 * @author Michael Farley
 */
public class UnknownIOException extends IOException {
    private static final long serialVersionUID = 5711527857339125278L;
    private final int statusCode;
    private final ICAPHeaderInformation icapHeaderInformation;


    /**
     * Constructor for UnknownIOException
     *
     * @param message the message
     * @param icapHeaderInformation the ICAP header information
     */
    public UnknownIOException(String message, ICAPHeaderInformation icapHeaderInformation) {
        this(0, message, icapHeaderInformation);
    }


    /**
     * Constructor for UnknownIOException
     *
     * @param statusCode the ICAP status code
     * @param message the message
     * @param icapHeaderInformation the ICAP header information
     */
    public UnknownIOException(int statusCode, String message, ICAPHeaderInformation icapHeaderInformation) {
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
