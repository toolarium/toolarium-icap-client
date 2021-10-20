/*
 * ICAPRequestInformation.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * Defines the request header information
 *
 * @author Patrick Meier
 */
public class ICAPRequestInformation implements Serializable {
    /** Default user agent */
    public static final String USER_AGENT = "toolarium ICAP-Client/1.1";
    
    /** API version */
    public static final String API_VERSION = "1.0";

    private static final String SEPARATOR = ", ";
    private static final long serialVersionUID = -39683746075058963L;
    private String userAgent = USER_AGENT;
    private String apiVersion = API_VERSION;
    private String username;
    private String requestSource;
    private Boolean allow204;


    /**
     * Constructor for ICAPRequestInformation
     */
    public ICAPRequestInformation() {
        this(USER_AGENT, API_VERSION, null, null, null);
    }


    /**
     * Constructor for ICAPRequestInformation
     *
     * @param username the username
     * @param requestSource the reuqest source
     */
    public ICAPRequestInformation(String username, String requestSource) {
        this(USER_AGENT, API_VERSION, username, requestSource, null);
    }


    /**
     * Constructor for ICAPRequestInformation
     *
     * @param userAgent the user agent
     * @param apiVersion the api version
     * @param username the username
     * @param requestSource the reuqest source
     * @param allow204 allow 204 status
     */
    public ICAPRequestInformation(String userAgent, String apiVersion, String username, String requestSource, Boolean allow204) {
        this.userAgent = userAgent;
        this.apiVersion = apiVersion;
        this.username = username;
        this.requestSource = requestSource;
        this.allow204 = allow204;
    }


    /**
     * Get the user agent
     *
     * @return the user agent
     */
    public String getUserAgent() {
        return userAgent;
    }


    /**
     * Set the user agent
     *
     * @param userAgent the user agent
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }


    /**
     * Get the api version
     *
     * @return the api version
     */
    public String getApiVersion() {
        return apiVersion;
    }


    /**
     * Sets the api version
     *
     * @param apiVersion the api version
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }


    /**
     * Get the user name.
     *
     * @return the use rname
     */
    public String getUsername() {
        return username;
    }


    /**
     * Sets the use rname.
     *
     * @param username the user name to set
     */
    public void setUsername(String username) {
        this.username = username;
    }


    /**
     * Get the request source, e.g. service or ip-address.
     *
     * @return the request source
     */
    public String getRequestSource() {
        return requestSource;
    }


    /**
     * Set the request source, e.g. service or ip-address.
     *
     * @param requestSource the request source to set
     */
    public void setRequestSource(String requestSource) {
        this.requestSource = requestSource;
    }

    

    /**
     * Check if allow 204 status is enabled
     *
     * @return define allow 204 status is enabled
     */
    public Boolean isAllow204() {
        return allow204;
    }


    /**
     * Set allow 204 status
     *
     * @param allow204 allow 204 status; if it is set to null it will automatically selected if the server allows it.
     */
    public void setAllow204(Boolean allow204) {
        this.allow204 = allow204;
    }


    @Override
    public int hashCode() {
        return Objects.hash(allow204, apiVersion, requestSource, userAgent, username);
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
        
        ICAPRequestInformation other = (ICAPRequestInformation) obj;
        return Objects.equals(allow204, other.allow204) && Objects.equals(apiVersion, other.apiVersion)
                && Objects.equals(requestSource, other.requestSource) && Objects.equals(userAgent, other.userAgent)
                && Objects.equals(username, other.username);
    }


    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ICAPRequestHeader [userAgent=" + userAgent + ", apiVersion=" + apiVersion + ", username=" + username + ", requestSource=" + requestSource + "]";
    }
    
    
    /**
     * Prepare the source request
     *
     * @param resourceName the resource name
     * @param resourceLength the resource length
     * @return the prepared source request
     */
    public String prepareSourceRequest(final String resourceName, final Long resourceLength) {
        String sourceRequest = "";
        if (getUsername() != null) {
            if (!sourceRequest.isEmpty()) {
                sourceRequest += SEPARATOR;
            }

            sourceRequest += "username: " + getUsername();
        }
        
        if (getRequestSource() != null) {
            if (!sourceRequest.isEmpty()) {
                sourceRequest += SEPARATOR;
            }
            sourceRequest += "source: " + getRequestSource();
        }

        if (resourceName != null) {
            if (!sourceRequest.isEmpty()) {
                sourceRequest += SEPARATOR;
            }
            sourceRequest += "resource: " + resourceName;
        }

        if (resourceLength != null && resourceLength.longValue() > 0) {
            if (!sourceRequest.isEmpty()) {
                sourceRequest += SEPARATOR;
            }
            sourceRequest += "length: " + resourceLength;
        }
        return sourceRequest;
    }
}
