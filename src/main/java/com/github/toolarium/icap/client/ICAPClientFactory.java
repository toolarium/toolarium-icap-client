/*
 * ICAPClientFactory.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration;
import com.github.toolarium.icap.client.dto.ICAPServiceInformation;
import com.github.toolarium.icap.client.impl.ICAPClientImpl;
import com.github.toolarium.icap.client.impl.ICAPConnectionManagerImpl;
import java.io.IOException;
import java.net.MalformedURLException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * ICAP client factory
 *
 * @author Patrick Meier
 */
public final class ICAPClientFactory {
    private static final int DEFAULT_MAX_CACHE_AGE = 12 * 60 * 60;
    private static final Logger LOG = LoggerFactory.getLogger(ICAPClientFactory.class);
    private Map<ICAPServiceInformation, ICAPRemoteServiceConfiguration> serviceCache;
    private ICAPConnectionManager connectionManager;
    
    
    /**
     * Private class, the only instance of the singelton which will be created by accessing the holder class.
     *
     * @author Patrick Meier
     */
    private static class HOLDER {
        static final ICAPClientFactory INSTANCE = new ICAPClientFactory();
    }


    /**
     * Constructor
     */
    private ICAPClientFactory() {
        serviceCache = new ConcurrentHashMap<ICAPServiceInformation, ICAPRemoteServiceConfiguration>();
        connectionManager = new ICAPConnectionManagerImpl();
    }

    
    /**
     * Get the instance
     *
     * @return the instance
     */
    public static ICAPClientFactory getInstance() {
        return HOLDER.INSTANCE;
    }

    
    /**
     * Gets the current connection manager
     *
     * @return the connection manager   
     */
    public ICAPConnectionManager getICAPConnectionManager() {
        return connectionManager;
    }

    
    /**
     * Sets the connection manager
     *
     * @param connectionManager sets a connection manager in which the establishment of the connection can take place in a controlled manner   
     * @throws IllegalArgumentException In case of an invalid connection manager
     */
    public void setICAPConnectionManager(ICAPConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("Invalid connection manager!");
        }
        
        this.connectionManager = connectionManager;
    }
    
    
    /**
     * Get the ICAP client
     *
     * @param icapUrl the icap url, e.g. icap://localhost:1344/srv_clamav or icaps://localhost:1344/srv_clamav
     * @return the ICAP client
     * @throws MalformedURLException In case of an invalid URL
     */
    public ICAPClient getICAPClient(String icapUrl) throws MalformedURLException {
        return getICAPClient(icapUrl, DEFAULT_MAX_CACHE_AGE);
    }

    
    /**
     * Get the ICAP client
     *
     * @param hostName the host name
     * @param servicePort the service port
     * @param serviceName the service name
     * @return the ICAP client
     */
    public ICAPClient getICAPClient(String hostName, int servicePort, String serviceName) {
        return getICAPClient(hostName, servicePort, serviceName, false, DEFAULT_MAX_CACHE_AGE);
    }

    
    /**
     * Get the ICAP client
     *
     * @param hostName the host name
     * @param servicePort the service port
     * @param serviceName the service name
     * @param secureConnection true to use secure ssl connection; otherwise false
     * @return the ICAP client
     */
    public ICAPClient getICAPClient(String hostName, int servicePort, String serviceName, boolean secureConnection) {
        return getICAPClient(hostName, servicePort, serviceName, secureConnection, DEFAULT_MAX_CACHE_AGE);
    }

    
    /**
     * Get the ICAP client
     *
     * @param icapUrl the icap url, e.g. icap://localhost:1344/srv_clamav or icaps://localhost:1344/srv_clamav
     * @param cacheMaxAgeInSeconds the max age in seconds of the cache
     * @return the ICAP client
     * @throws MalformedURLException In case of an invalid URL
     */
    public ICAPClient getICAPClient(String icapUrl, int cacheMaxAgeInSeconds) throws MalformedURLException {
        
        if (icapUrl == null || icapUrl.isBlank()) {
            throw new MalformedURLException("Invalid icap url!");
        }
        
        String url = icapUrl.trim();
        int idx = url.indexOf(':');
        if (idx < 0 || !(url.toLowerCase().startsWith("icap:") || url.toLowerCase().startsWith("icaps:"))) {
            throw new MalformedURLException("Invalid icap url, expected url starts with icap prototcol, e.g. icap://...!");
        }
        
        url = url.substring(idx + 1).trim();
        while (!url.isEmpty() && url.startsWith("/")) {
            url = url.substring(1);
        }

        String serviceName = "";
        idx = url.indexOf('/');
        if (idx > 0) {
            serviceName = url.substring(idx + 1).trim();
            url = url.substring(0, idx).trim();
        }
        
        String hostName = url.trim();
        int servicePort = 1344;
        idx = url.indexOf(':');
        if (idx > 0) {
            hostName = url.substring(0, idx).trim();
            servicePort = Integer.parseInt(url.substring(idx + 1).trim());
        }
        
        boolean secureConnection = url.toLowerCase().startsWith("icaps:");
        return getICAPClient(hostName, servicePort, serviceName, secureConnection, cacheMaxAgeInSeconds);
    }
    

    /**
     * Get the ICAP client
     *
     * @param hostName the host name
     * @param servicePort the service port
     * @param serviceName the service name
     * @param secureConnection true to use icaps connection (secured SSLSocket connection)
     * @param cacheMaxAgeInSeconds the max age in seconds of the cache
     * @return the ICAP client
     */
    public ICAPClient getICAPClient(String hostName, int servicePort, String serviceName, boolean secureConnection, int cacheMaxAgeInSeconds) {
        ICAPServiceInformation serviceInformation = new ICAPServiceInformation(hostName, servicePort, secureConnection, serviceName, cacheMaxAgeInSeconds);
        
        ICAPRemoteServiceConfiguration remoteServiceConfiguration = serviceCache.get(serviceInformation);

        if (remoteServiceConfiguration == null 
                || remoteServiceConfiguration.getTimestamp() == null 
                || ((Instant.now().getEpochSecond() - remoteServiceConfiguration.getTimestamp().getEpochSecond()) > serviceInformation.getCacheMaxAgeInSeconds())) { 
            try {
                ICAPClientImpl clientImpl = new ICAPClientImpl(getICAPConnectionManager(), serviceInformation, remoteServiceConfiguration);
                remoteServiceConfiguration = clientImpl.options();
                serviceCache.put(serviceInformation, remoteServiceConfiguration);
                LOG.debug("Set remote service configuration cache: " + serviceInformation);
            } catch (IOException e) {
                LOG.debug("Could not get options from remote icap-server: " + e.getMessage(), e);
            }
        } else {
            String logCacheDuration = "";
            Long diff = null;
            if (remoteServiceConfiguration.getTimestamp() != null) {
                diff = serviceInformation.getCacheMaxAgeInSeconds() + (Instant.now().getEpochSecond() - remoteServiceConfiguration.getTimestamp().getEpochSecond());
                logCacheDuration = "(valid for " + diff + " seconds)";
            }
            
            LOG.debug("Found remote service configuration in cache " + logCacheDuration + ": " + serviceInformation);
        }
        
        return new ICAPClientImpl(getICAPConnectionManager(), serviceInformation, remoteServiceConfiguration);
    }
}
