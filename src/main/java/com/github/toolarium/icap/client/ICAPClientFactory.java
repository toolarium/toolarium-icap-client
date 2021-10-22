/*
 * ICAPClientFactory.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration;
import com.github.toolarium.icap.client.dto.ICAPServiceInformation;
import com.github.toolarium.icap.client.impl.ICAPClientImpl;
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
     * Get the ICAP client
     *
     * @param icapUrl the icap url, e.g. icap://localhost:1344/srv_clamav
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
        return getICAPClient(hostName, servicePort, serviceName, DEFAULT_MAX_CACHE_AGE);
    }

    
    /**
     * Get the ICAP client
     *
     * @param icapUrl the icap url, e.g. icap://localhost:1344/srv_clamav
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
        if (idx < 0 || !url.toLowerCase().startsWith("icap:")) {
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
        
        return getICAPClient(hostName, servicePort, serviceName, DEFAULT_MAX_CACHE_AGE);
    }
    

    /**
     * Get the ICAP client
     *
     * @param hostName the host name
     * @param servicePort the service port
     * @param serviceName the service name
     * @param cacheMaxAgeInSeconds the max age in seconds of the cache
     * @return the ICAP client
     */
    public ICAPClient getICAPClient(String hostName, int servicePort, String serviceName, int cacheMaxAgeInSeconds) {
        ICAPServiceInformation serviceInformation = new ICAPServiceInformation(hostName, servicePort, serviceName, cacheMaxAgeInSeconds);
        
        ICAPRemoteServiceConfiguration remoteServiceConfiguration = serviceCache.get(serviceInformation);

        if (remoteServiceConfiguration == null 
                || remoteServiceConfiguration.getTimestamp() == null 
                || ((Instant.now().getEpochSecond() - remoteServiceConfiguration.getTimestamp().getEpochSecond()) > serviceInformation.getCacheMaxAgeInSeconds())) { 
            try {
                ICAPClientImpl clientImpl = new ICAPClientImpl(serviceInformation, remoteServiceConfiguration);
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
        
        return new ICAPClientImpl(serviceInformation, remoteServiceConfiguration);
    }
}
