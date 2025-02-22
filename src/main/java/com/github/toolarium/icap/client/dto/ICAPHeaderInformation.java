/*
 * ICAPHeaderInformation.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The ICAP header information
 *
 * @author Patrick Meier
 */
public final class ICAPHeaderInformation implements Serializable {
    private static final long serialVersionUID = -2307425606272132956L;

    private final String protocol;
    private final String version;
    private final int status;
    private final String message;
    private final Map<String, List<String>> headers;

    /**
     * Private constructor to enforce the use of the Builder.
     *
     * @param builder the builder to create the header information from
     */
    private ICAPHeaderInformation(Builder builder) {
        this.protocol = builder.protocol;
        this.version = builder.version;
        this.status = builder.status;
        this.message = builder.message;
        this.headers = new LinkedHashMap<>(); // LinkedHashMap preserves order of insertion
        headers.putAll(builder.headers);
    }

    /**
     * Get the protocol.
     * @return the protocol.
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Get the version.
     * @return the version.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Get the status.
     * @return the status.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Get the message.
     * @return the message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get the headers.
     * @return an unmodifiable map of headers.
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * Check if a specific header exists.
     * @param header the header name.
     * @return true if it exists.
     */
    public boolean containsHeader(String header) {
        return headers.containsKey(header);
    }

    /**
     * Get the header values.
     * @param header the header name.
     * @return the header values or an empty list if not found.
     */
    public List<String> getHeaderValues(String header) {
        return headers.getOrDefault(header, Collections.emptyList());
    }

    /**
     * Set the header values
     *
     * @param headers to set
     */
    public void setHeaders(Map<String, List<String>> headers) {
        this.headers.putAll(headers);
    }

    /**
     * Builder for ICAPHeaderInformation.
     */
    public static class Builder {
        private String protocol = "ICAP";
        private String version = "";
        private int status = 0;
        private String message = "";
        private final Map<String, List<String>> headers = new LinkedHashMap<>();

        /**
         * Set the protocol
         *
         * @param protocol to be set
         * @return the builder
         */
        public Builder withProtocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Set the version
         *
         * @param version to be set
         * @return the builder
         */
        public Builder withVersion(String version) {
            this.version = version;
            return this;
        }

        /**
         * Set the status
         *
         * @param status to be set
         * @return the builder
         */
        public Builder withStatus(int status) {
            this.status = status;
            return this;
        }

        /**
         * Set the message
         *
         * @param message to be set
         * @return the builder
         */
        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        /**
         * Set the headers
         *
         * @param headers to be set
         * @return the builder
         */
        public Builder withHeaders(Map<String, List<String>> headers) {
            this.headers.putAll(headers);
            return this;
        }

        /**
         * Set a single header
         *
         * @param header key to be set
         * @param values values to be set
         * @return the builder
         */
        public Builder withHeader(String header, List<String> values) {
            this.headers.put(header, values);
            return this;
        }

        /**
         * Build the ICAPInformation instance
         *
         * @return a new ICAPInformation instance
         */
        public ICAPHeaderInformation build() {
            return new ICAPHeaderInformation(this);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(headers, message, protocol, status, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ICAPHeaderInformation other = (ICAPHeaderInformation) obj;
        return status == other.status
            && Objects.equals(protocol, other.protocol)
            && Objects.equals(version, other.version)
            && Objects.equals(message, other.message)
            && Objects.equals(headers, other.headers);
    }

    @Override
    public String toString() {
        return String.format(
            "ICAPHeaderInformation [protocol=%s, version=%s, status=%d, message=%s, headers=%s]",
            protocol, version, status, message, headers);
    }
}
