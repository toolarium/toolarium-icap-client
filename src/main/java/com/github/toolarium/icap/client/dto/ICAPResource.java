/*
 * ICAPResource.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Defines the ICAP resource
 *
 * @author Patrick Meier
 */
public class ICAPResource implements Serializable, Closeable {
    private static final long serialVersionUID = -1034290022203558461L;
    private static final int STATE_FRESH = 0;
    private static final int STATE_IN_USE = 1;
    private static final int STATE_CONSUMED = 2;
    private String resourceName;
    // a stream can not be serialized, a deserialized resource has no content and is rejected
    private transient InputStream resourceInputStream;
    private long resourceLength;
    private boolean streamOwner;
    private final AtomicInteger state = new AtomicInteger(STATE_FRESH);

    
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
        this.streamOwner = true;
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
     * Returns true if the resource body stream has already been read by a
     * previous {@code validateResource} call. A consumed resource cannot be
     * reused: the stream position is past the start, so sending it again would
     * transmit truncated content while declaring the original length.
     *
     * @return true if the stream has been consumed
     */
    public boolean isConsumed() {
        return state.get() == STATE_CONSUMED;
    }


    /**
     * Marks this resource as consumed. Called by the ICAP client immediately
     * before the first read from the stream. Once marked, any subsequent call
     * to {@code validateResource} with this resource throws {@link java.io.IOException}.
     *
     * @return this instance
     */
    public ICAPResource markConsumed() {
        state.set(STATE_CONSUMED);
        return this;
    }


    /**
     * Atomically claims this resource for exclusive use by one {@code validateResource} call.
     *
     * @return true if the resource could be claimed; false if it is already in use or consumed
     */
    public boolean acquire() {
        return state.compareAndSet(STATE_FRESH, STATE_IN_USE);
    }


    /**
     * Releases a claim of {@link #acquire()} in case the stream was not read, so the resource can be used for a retry.
     * It has no effect on a consumed resource.
     */
    public void release() {
        state.compareAndSet(STATE_IN_USE, STATE_FRESH);
    }


    /**
     * Check if the resource stream was opened by this resource (see {@link #ICAPResource(Path)}) and therefore
     * is closed by the ICAP client after it was consumed.
     *
     * @return true if this resource owns the stream
     */
    public boolean isStreamOwner() {
        return streamOwner;
    }


    /**
     * Closes the resource input stream.
     *
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() throws IOException {
        if (resourceInputStream != null) {
            resourceInputStream.close();
        }
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
