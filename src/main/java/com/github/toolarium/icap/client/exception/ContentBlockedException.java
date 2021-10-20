/*
 * BlockedContentException.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.exception;

import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;


/**
 * The content blocked exception.
 *
 * @author Patrick Meier
 */
public class ContentBlockedException extends Exception {
    private static final long serialVersionUID = -4765885919770078429L;
    private final ICAPHeaderInformation icapHeaderInformation;
    private final String content;


    /**
     * Constructor for ICAPException
     *
     * @param message the message
     * @param icapHeaderInformation the ICAP header information
     */
    public ContentBlockedException(String message, ICAPHeaderInformation icapHeaderInformation) {
        this(message, icapHeaderInformation, null);
    }


    /**
     * Constructor for ICAPException
     *
     * @param message the message
     * @param icapHeaderInformation the ICAP header information
     * @param content the content
     */
    public ContentBlockedException(String message, ICAPHeaderInformation icapHeaderInformation, String content) {
        super(message);
        this.icapHeaderInformation = icapHeaderInformation;
        this.content = content;
    }


    /**
     * Get the ICAP response
     *
     * @return the ICAP response
     */
    public ICAPHeaderInformation getICAPHeaderInformation() {
        return icapHeaderInformation;
    }


    /**
     * Get the content
     *
     * @return the content
     */
    public String getContent() {
        return content;
    }
}
