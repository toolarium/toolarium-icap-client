/*
 * ICAPMode.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;


/**
 * Defines the ICAP modes
 *
 * @author Patrick Meier
 */
public enum ICAPMode {
    RESPMOD("res"),
    REQMOD("req"),
    FILEMOD("file");
    
    private String tag;
    
    /**
     * Constructor for ICAPMode
     *
     * @param tag the tag
     */
    ICAPMode(String tag) {
        this.tag = tag;
    }

    
    /**
     * Get the tag 
     *
     * @return the tag
     */
    public String getTag() {
        return tag;
    }
}
