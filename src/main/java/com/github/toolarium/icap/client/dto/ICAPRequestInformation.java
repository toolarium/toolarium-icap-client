/*
 * ICAPRequestInformation.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines the request header information
 *
 * @author Patrick Meier
 */
public final class ICAPRequestInformation implements Serializable {
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
    private Integer maxConnectionTimeout;
    private Integer maxReadTimeout;
    private Map<String, String> customHeaders;


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
        this.maxConnectionTimeout = null;
        this.maxReadTimeout = null;
        this.customHeaders = null;
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
     * @return the ICAPRequestInformation
     */
    public ICAPRequestInformation setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
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
     * @return the ICAPRequestInformation
     */
    public ICAPRequestInformation setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
        return this;
    }


    /**
     * Get the username.
     *
     * @return the use rname
     */
    public String getUsername() {
        return username;
    }


    /**
     * Sets the use rname.
     *
     * @param username the username to set
     * @return the ICAPRequestInformation
     */
    public ICAPRequestInformation setUsername(String username) {
        this.username = username;
        return this;
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
     * @return the ICAPRequestInformation
     */
    public ICAPRequestInformation setRequestSource(String requestSource) {
        this.requestSource = requestSource;
        return this;
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
     * @param allow204 allow 204 status; if set to null it will automatically be selected if the server allows it.
     * @return the ICAPRequestInformation
     */
    public ICAPRequestInformation setAllow204(Boolean allow204) {
        this.allow204 = allow204;
        return this;
    }


    /**
     * Get the max connection timeout in milliseconds. By default, there is no timeout set (null).
     * Any positive value will be set to the socket connection of the ICAP connection.
     * A timeout of zero is interpreted as an infinite timeout. The connection will then block
     * until established or an error occurs.
     *
     * @return the max request timeout
     */
    public Integer getMaxConnectionTimeout() {
        return maxConnectionTimeout;
    }


    /**
     * Set max connection timeout in milliseconds. By default, there is no timeout set (null).
     * Any positive value will be set to the socket connection of the ICAP connection.
     * A timeout of zero is interpreted as an infinite timeout. The connection will then block
     * until established or an error occurs.
     *
     * @param maxConnectionTimeout the max request timeout
     * @return the ICAPRequestInformation
     */
    public ICAPRequestInformation maxConnectionTimeout(Integer maxConnectionTimeout) {
        this.maxConnectionTimeout = maxConnectionTimeout;
        return this;
    }


    /**
     * Get the max request timeout in milliseconds. By default, there is no timeout set (null).
     * Any positive value will be set to the socket connection of the ICAP connection.
     * A timeout of zero is interpreted as an infinite timeout. The connection will then block
     * until established or an error occurs.
     *
     * @return the max request timeout
     */
    public Integer getMaxReadTimeout() {
        return maxReadTimeout;
    }


    /**
     * Set max request timeout in milliseconds. By default, there is no timeout set (null).
     * Any positive value will be set to the socket connection of the ICAP connection.
     * A timeout of zero is interpreted as an infinite timeout. The connection will then block
     * until established or an error occurs.
     *
     * @param maxReadTimeout the max read timeout
     * @return the ICAPRequestInformation
     */
    public ICAPRequestInformation maxReadTimeout(Integer maxReadTimeout) {
        this.maxReadTimeout = maxReadTimeout;
        return this;
    }


    /**
     * Get the custom headers
     *
     * @return the custom headers
     */
    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }


    /**
     * Set the custom headers
     *
     * @param customHeaders the custom headers
     * @return the ICAPRequestInformation
     */
    public ICAPRequestInformation setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
        return this;
    }


    /**
     * Add custom header
     *
     * @param key the key
     * @param value the value
     * @return the ICAPRequestInformation
     */
    public ICAPRequestInformation addCustomHeader(String key, String value) {
        if (customHeaders == null) {
            customHeaders = new ConcurrentHashMap<>();
        }

        customHeaders.put(key, value);
        return this;
    }


    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(allow204, apiVersion, customHeaders, maxConnectionTimeout, maxReadTimeout, requestSource, userAgent, username);
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
                && Objects.equals(customHeaders, other.customHeaders)
                && Objects.equals(maxConnectionTimeout, other.maxConnectionTimeout)
                && Objects.equals(maxReadTimeout, other.maxReadTimeout)
                && Objects.equals(requestSource, other.requestSource) && Objects.equals(userAgent, other.userAgent)
                && Objects.equals(username, other.username);
    }


    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ICAPRequestInformation [userAgent=" + userAgent + ", apiVersion=" + apiVersion + ", username="
                + username + ", requestSource=" + requestSource + ", allow204=" + allow204 + ", maxConnectionTimeout="
                + maxConnectionTimeout + ", maxReadTimeout=" + maxReadTimeout + ", customHeaders=" + customHeaders
                + "]";
    }


    /**
     * Prepare the source request
     *
     * @param resource the resource
     * @return the prepared source request
     */
    public String prepareSourceRequest(final ICAPResource resource) {
        String sourceRequest = "";
        if (getUsername() != null) {
            sourceRequest += "username: " + getUsername();
        }

        if (getRequestSource() != null) {
            if (!sourceRequest.isEmpty()) {
                sourceRequest += SEPARATOR;
            }
            sourceRequest += "source: " + getRequestSource();
        }

        String resourceName = null;
        Long resourceLength = null;
        if (resource != null) {
            resourceName = resource.getResourceName().trim();
            resourceLength = resource.getResourceLength();
        }

        if (resourceName != null) {
            if (!sourceRequest.isEmpty()) {
                sourceRequest += SEPARATOR;
            }
            sourceRequest += "resource: " + resourceName;
        }

        if (resourceLength != null && resourceLength > 0) {
            sourceRequest += SEPARATOR;
            sourceRequest += "length: " + resourceLength;
        }
        return sourceRequest;
    }
}
