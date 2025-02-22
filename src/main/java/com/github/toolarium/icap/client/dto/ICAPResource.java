/*
 * ICAPResource.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.io.InputStream;
import java.io.Serializable;
import java.util.Objects;


/**
 * Defines the ICAP resource
 *
 * @author Patrick Meier
 */
public class ICAPResource implements Serializable {
    private static final long serialVersionUID = -1034290022203558461L;

    private final String resourceName;
    private final InputStream resourceInputStream;
    private final long resourceLength;

    /**
     * Constructor
     * @param name of the resource
     * @param body of the resource
     * @param length of the resource
     */
    public ICAPResource(String name, InputStream body, long length) {
        this.resourceName = name;
        this.resourceInputStream = body;
        this.resourceLength = length;
    }

    /**
     * Get the name of the resource.
     *
     * @return the name of the resource
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * Get the resource input stream.
     *
     * @return the resource input stream
     */
    public InputStream getResourceBody() {
        return resourceInputStream;
    }

    /**
     * Get the resource length.
     *
     * @return the resource length
     */
    public long getResourceLength() {
        return resourceLength;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceLength, resourceName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ICAPResource other = (ICAPResource) obj;
        return resourceLength == other.resourceLength && Objects.equals(resourceName, other.resourceName);
    }

    @Override
    public String toString() {
        return "ICAPResource [resourceName=" + resourceName + ", resourceLength=" + resourceLength + "]";
    }
}
