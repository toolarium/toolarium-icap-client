/*
 * ICAPHeaderInformation.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * The ICAP header information
 *
 * @author Patrick Meier
 */
public class ICAPHeaderInformation implements Serializable {
    private static final long serialVersionUID = -2307425606272132956L;   
    private String protocol;
    private String version;
    private int status;
    private String message;
    private Map<String, List<String>> headers;


    /**
     * Constructor for ICAPHeaderInformation
     */
    public ICAPHeaderInformation() {
        protocol = "ICAP";
        version = "";
        status = 0;
        message = "";
        headers = null;
    }


    /**
     * Get the protocol
     *
     * @return the protocol
     */
    public String getProtocol() {
        return protocol;
    }


    /**
     * Set the protocol
     *
     * @param protocol the protocol
     * @return the ICAPHeaderInformation
     */
    public ICAPHeaderInformation setProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }


    /**
     * Get the version
     *
     * @return the version
     */
    public String getVersion() {
        return version;
    }



    /**
     * Set the version
     *
     * @param version the version
     * @return the ICAPHeaderInformation
     */
    public ICAPHeaderInformation setVersion(String version) {
        this.version = version;
        return this;
    }


    /**
     * Get the status
     *
     * @return the status
     */
    public int getStatus() {
        return status;
    }


    /**
     * Set the status
     *
     * @param status the status
     * @return the ICAPHeaderInformation
     */
    public ICAPHeaderInformation setStatus(int status) {
        this.status = status;
        return this;
    }


    /**
     * Get the message
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }


    /**
     * Set the message
     *
     * @param message the message
     * @return the ICAPHeaderInformation
     */
    public ICAPHeaderInformation setMessage(String message) {
        this.message = message;
        return this;
    }


    /**
     * Set the header entries
     *
     * @param headers the headers
     * @return the ICAPHeaderInformation
     */
    public ICAPHeaderInformation setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
        return this;
    }


    /**
     * Get the header entries
     *
     * @return the header entries
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    
    /**
     * Check if a specific header exists
     *
     * @param header the header
     * @return true if it exists
     */
    public boolean containsHeader(String header) {
        return headers.containsKey(header);
    }


    /**
     * Get the header values
     *
     * @param header the header
     * @return the header values
     */
    public List<String> getHeaderValues(String header) {
        return headers.get(header);
    }


    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hash(headers, message, protocol, status, version);
        return result;
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
        ICAPHeaderInformation other = (ICAPHeaderInformation) obj;
        return Objects.equals(headers, other.headers)
                && Objects.equals(message, other.message) && Objects.equals(protocol, other.protocol)
                && status == other.status && Objects.equals(version, other.version);
    }


    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ICAPHeaderInformation [protocol=" + protocol + ", version=" + version + ", status=" + status + ", message=" + message + ", headers=" + headers + "]";
    }
}
