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
    private volatile boolean maxConnectionsLogged;
    
    
    /**
     * Private class, the only instance of the singelton which will be created by accessing the holder class.
     *
     * @author Patrick Meier
     */
    private static final class HOLDER {
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
     * Close all pooled connections and release resources.
     */
    public void closePool() {
        connectionManager.closePool();
    }


    /**
     * Get the ICAP client
     *
     * @param icapUrl the icap url, e.g. icap://localhost:1344/srv_clamav or icaps://localhost:1344/srv_clamav
     * @return the ICAP client
     * @throws IOException In case of an I/O error
     * @throws MalformedURLException In case of an invalid URL
     */
    public ICAPClient getICAPClient(String icapUrl) throws MalformedURLException, IOException {
        return getICAPClient(icapUrl, DEFAULT_MAX_CACHE_AGE);
    }

    
    /**
     * Get the ICAP client
     *
     * @param hostName the host name
     * @param servicePort the service port
     * @param serviceName the service name
     * @return the ICAP client
     * @throws IOException In case of an I/O error
     */
    public ICAPClient getICAPClient(String hostName, int servicePort, String serviceName) throws IOException {
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
     * @throws IOException In case of an I/O error
     */
    public ICAPClient getICAPClient(String hostName, int servicePort, String serviceName, boolean secureConnection) throws IOException {
        return getICAPClient(hostName, servicePort, serviceName, secureConnection, DEFAULT_MAX_CACHE_AGE);
    }

    
    /**
     * Get the ICAP client
     *
     * @param icapUrl the icap url, e.g. icap://localhost:1344/srv_clamav or icaps://localhost:1344/srv_clamav
     * @param cacheMaxAgeInSeconds the max age in seconds of the cache
     * @return the ICAP client
     * @throws MalformedURLException In case of an invalid URL
     * @throws IOException In case of an I/O error
     */
    public ICAPClient getICAPClient(String icapUrl, int cacheMaxAgeInSeconds) throws MalformedURLException, IOException {
        
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
        
        boolean secureConnection = icapUrl.toLowerCase().trim().startsWith("icaps:");
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
     * @throws IOException In case of an I/O error
     */
    public ICAPClient getICAPClient(String hostName, int servicePort, String serviceName, boolean secureConnection, int cacheMaxAgeInSeconds) throws IOException {
        ICAPServiceInformation serviceInformation = new ICAPServiceInformation(hostName, servicePort, secureConnection, serviceName, cacheMaxAgeInSeconds);
        ICAPRemoteServiceConfiguration remoteServiceConfiguration = serviceCache.get(serviceInformation);
        boolean isStale = isCacheStale(remoteServiceConfiguration, serviceInformation);

        if (isStale) {
            synchronized (serviceCache) {
                // double-check after acquiring lock
                remoteServiceConfiguration = serviceCache.get(serviceInformation);
                isStale = isCacheStale(remoteServiceConfiguration, serviceInformation);

                if (isStale) {
                    try {
                        ICAPClientImpl clientImpl = new ICAPClientImpl(getICAPConnectionManager(), serviceInformation, null);
                        remoteServiceConfiguration = clientImpl.options();
                        serviceCache.put(serviceInformation, remoteServiceConfiguration);

                        // Apply server-advertised Max-Connections to pool (RFC 3507 §4.10)
                        if (remoteServiceConfiguration.getMaxConnections() > 0 && connectionManager.isPoolingEnabled()) {
                            int serverMax = remoteServiceConfiguration.getMaxConnections();
                            int currentMax = connectionManager.getMaxPoolConnectionsPerHost();
                            if (currentMax > serverMax) {
                                if (!maxConnectionsLogged) {
                                    maxConnectionsLogged = true;
                                    LOG.warn("Pool setting (" + currentMax + ") exceeds server Max-Connections (" + serverMax + "), reducing to server limit.");
                                }
                                connectionManager.setMaxPoolConnectionsPerHost(serverMax);
                            } else if (currentMax < serverMax) {
                                if (!maxConnectionsLogged) {
                                    maxConnectionsLogged = true;
                                    LOG.info("Server allows Max-Connections: " + serverMax + ", keeping user-defined pool setting: " + currentMax + ".");
                                }
                            }
                        }

                        LOG.debug("Set remote service configuration cache: " + serviceInformation);
                    } catch (IOException e) {
                        LOG.debug("Could not get options from remote icap-server: " + e.getMessage(), e);
                        throw e;
                    }
                }
            }
        } else {
            if (LOG.isDebugEnabled()) {
                long effectiveTTL = getEffectiveTTL(remoteServiceConfiguration, serviceInformation);
                long diff = effectiveTTL - (Instant.now().getEpochSecond() - remoteServiceConfiguration.getTimestamp().getEpochSecond());
                LOG.debug("Found remote service configuration in cache (valid for " + diff + " seconds): " + serviceInformation);
            }
        }

        return new ICAPClientImpl(getICAPConnectionManager(), serviceInformation, remoteServiceConfiguration);
    }


    /**
     * Check if the cached configuration is stale.
     *
     * @param config the cached remote service configuration
     * @param serviceInformation the service information
     * @return true if the cache is stale and needs refresh
     */
    private boolean isCacheStale(ICAPRemoteServiceConfiguration config, ICAPServiceInformation serviceInformation) {
        if (config == null || config.getTimestamp() == null) {
            return true;
        }
        long effectiveTTL = getEffectiveTTL(config, serviceInformation);
        return (Instant.now().getEpochSecond() - config.getTimestamp().getEpochSecond()) > effectiveTTL;
    }


    /**
     * Get the effective TTL in seconds. Uses the server-advertised Options-TTL (RFC 3507 §4.10) if available,
     * otherwise falls back to the client-configured cacheMaxAgeInSeconds.
     *
     * @param config the remote service configuration
     * @param serviceInformation the service information
     * @return the effective TTL in seconds
     */
    private long getEffectiveTTL(ICAPRemoteServiceConfiguration config, ICAPServiceInformation serviceInformation) {
        if (config.getOptionsTTL() > 0) {
            return config.getOptionsTTL();
        }
        return serviceInformation.getCacheMaxAgeInSeconds();
    }
}
