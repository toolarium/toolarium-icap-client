/*
 * ServiceInformation.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * Desines the service information 
 * @author patrick
 */
public class ICAPServiceInformation implements Serializable {
    private static final long serialVersionUID = 8790709509062253127L;
    private String hostName;
    private int servicePort;
    private boolean secureConnection;
    private String serviceName;
    private int cacheMaxAgeInSeconds;
    
    
    /**
     * Constructor for ICAPServiceInformation
     *
     * @param hostName the host name
     * @param servicePort the service port
     * @param secureConnection true establish a secured connection
     * @param serviceName the service name
     * @param cacheMaxAgeInSeconds the max age in seconds of the cache
     */
    public ICAPServiceInformation(String hostName, int servicePort, boolean secureConnection, String serviceName, int cacheMaxAgeInSeconds) {
        this.hostName = hostName;
        this.servicePort = servicePort;
        this.secureConnection = secureConnection;
        this.serviceName = serviceName;
        this.cacheMaxAgeInSeconds = cacheMaxAgeInSeconds;
    }
    
    
    /**
     * Get the host name
     *
     * @return the host name
     */
    public String getHostName() {
        return hostName;
    }
    
    
    /**
     * Get the service port
     *
     * @return the service port
     */
    public int getServicePort() {
        return servicePort;
    }

    
    /**
     * Is is a secure connection
     *
     * @return true if it is a secure connection
     */
    public boolean isSecureConnection() {
        return secureConnection;
    }

    
    /**
     * Get the service name
     *
     * @return the service name
     */
    public String getServiceName() {
        return serviceName;
    }


    /**
     * Get the max age in seconds of the cache
     *
     * @return the max age in seconds of the cache
     */
    public int getCacheMaxAgeInSeconds() {
        return cacheMaxAgeInSeconds;
    }


    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(cacheMaxAgeInSeconds, hostName, secureConnection, serviceName, servicePort);
    }


    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)  {
            return true;
        }
        
        if (obj == null) {
            return false;
        }
        
        if (getClass() != obj.getClass()) {            
            return false;
        }
        
        ICAPServiceInformation other = (ICAPServiceInformation) obj;
        return cacheMaxAgeInSeconds == other.cacheMaxAgeInSeconds && Objects.equals(hostName, other.hostName)
                && secureConnection == other.secureConnection && Objects.equals(serviceName, other.serviceName)
                && servicePort == other.servicePort;
    }


    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ICAPServiceInformation [hostName=" + hostName + ", servicePort=" + servicePort + ", secureConnection="
                + secureConnection + ", serviceName=" + serviceName + ", cacheMaxAgeInSeconds=" + cacheMaxAgeInSeconds
                + "]";
    }
}
