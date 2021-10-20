/*
 * ICAPConstants.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;


/**
 * Defines the ICAP constants
 *
 * @author Patrick Meier
 */
public interface ICAPConstants {
    // HTTP header headers
    String HEADER_KEY_CONTENT_LENGTH = "Content-Length";
    String HEADER_KEY_TRANSFER_ENCODING = "Transfer-Encoding";
    String HEADER_KEY_ENCAPSULATED = "Encapsulated";
    
    // ICAP header headers
    String HEADER_KEY_PREVIEW = "Preview";
    String HEADER_KEY_ALLOW = "Allow";
    String HEADER_KEY_X_VIOLATIONS_FOUND = "X-Violations-Found";
    String HEADER_KEY_X_INFECTION_FOUND = "X-Infection-Found";    
    String HEADER_KEY_X_BLOCKED = "X-Blocked";    
    
    // ICAP client library headers
    String HEADER_KEY_X_ICAP_STATUSLINE = "X-ICAP-Statusline";     
    String HEADER_KEY_X_REQUEST_MESSAGE_DIGEST = "X-Request-Message-Digest";    
    String HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST = "X-Response-Message-Digest";
    String HEADER_KEY_X_IDENTICAL_CONTENT = "X-Resource-Identical-Content";
    
}
