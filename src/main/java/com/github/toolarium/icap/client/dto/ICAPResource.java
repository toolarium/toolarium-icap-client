/*
 * ICAPResource.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;


/**
 * Defines the ICAP resource
 *
 * @author Patrick Meier
 */
public class ICAPResource implements Serializable {
    private static final long serialVersionUID = -1034290022203558461L;
    private String resourceName;
    private InputStream resourceInputStream;
    private long resourceLength;

    
    /**
     * Constructor for ICAPResource
     *
     * @param resource the resource
     * @throws IllegalArgumentException In case of invalid resource 
     * @throws FileNotFoundException In case the resource don't exist 
     */
    public ICAPResource(Path resource) throws FileNotFoundException {
        
        if (resource == null) {
            throw new IllegalArgumentException("Invalid resource!");
        }
        
        File file = resource.toFile();
        if (!file.exists()) {
            throw new FileNotFoundException("Could not find resource [" + file + "]!");
        }
        
        setResourceName(file.getName());
        setResourceBody(new FileInputStream(file));
        setResourceLength(file.length());
    }


    /**
     * Constructor for ICAPResource
     *
     * @param resourceInputStream the resource input stream
     * @param resourceLength  the resource length
     */
    public ICAPResource(InputStream resourceInputStream, long resourceLength) {
        this(null, resourceInputStream, resourceLength);
    }


    /**
     * Constructor for ICAPResource
     *
     * @param resourceName the name of the resource
     * @param resourceInputStream the resource input stream
     * @param resourceLength  the resource length
     */
    public ICAPResource(String resourceName, InputStream resourceInputStream, long resourceLength) {
        setResourceName(resourceName);
        setResourceBody(resourceInputStream);
        setResourceLength(resourceLength);
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
     * Set the name of the resource.
     *
     * @param resourceName the name of the resource to set
     * @return the ICAPResource
     */
    public ICAPResource setResourceName(String resourceName) {
        this.resourceName = resourceName;
        
        if (this.resourceName == null || this.resourceName.isBlank()) {
            this.resourceName = "content";
        }
        return this;
    }


    /**
     * Gets the resource input stream.
     *
     * @return the resource input stream
     */
    public InputStream getResourceBody() {
        return resourceInputStream;
    }


    /**
     * Set the resource input stream.
     *
     * @param resourceInputStream the resource input stream to set
     * @return the ICAPResource
     */
    public ICAPResource setResourceBody(InputStream resourceInputStream) {
        this.resourceInputStream = resourceInputStream;
        return this;
    }


    /**
     * Get the resource length.
     *
     * @return the resource length
     */
    public long getResourceLength() {
        return resourceLength;
    }


    /**
     * Set the resource length.
     *
     * @param resourceLength the resource length to set
     * @return the ICAPResource
     */
    public ICAPResource setResourceLength(long resourceLength) {
        this.resourceLength = resourceLength;
        return this;
    }


    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(resourceLength, resourceName);
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
        ICAPResource other = (ICAPResource) obj;
        return resourceLength == other.resourceLength && Objects.equals(resourceName, other.resourceName);
    }


    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ICAPResource [resourceName=" + resourceName + ", resourceLength=" + resourceLength + "]";
    }
}
