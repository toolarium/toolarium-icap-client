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
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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
    private static final long MIN_OPTIONS_TTL = 10;
    private static final long MAX_CACHE_ENTRY_AGE = 2L * DEFAULT_MAX_CACHE_AGE;
    private static final long REFRESH_BACKOFF_MS = 30000;
    private final Map<String, Long> refreshFailures = new ConcurrentHashMap<String, Long>();
    private final Map<String, ICAPRemoteServiceConfiguration> serviceCache;
    private final ConcurrentHashMap<String, CompletableFuture<ICAPRemoteServiceConfiguration>> pendingRefresh;
    private volatile ICAPConnectionManager connectionManager;
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
        serviceCache = new ConcurrentHashMap<String, ICAPRemoteServiceConfiguration>();
        pendingRefresh = new ConcurrentHashMap<String, CompletableFuture<ICAPRemoteServiceConfiguration>>();
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

        // the pooled connections of the replaced manager are not used anymore
        ICAPConnectionManager previousConnectionManager = this.connectionManager;
        this.connectionManager = connectionManager;
        if (previousConnectionManager != null && previousConnectionManager != connectionManager) {
            previousConnectionManager.closePool();
        }
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
     * @param icapUrl the icap url, e.g. icap://localhost:1344/srv_clamav or icaps://localhost:1344/srv_clamav; null, empty or blank for a client without ICAP server which supports only FILEMOD
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
     * @param icapUrl the icap url, e.g. icap://localhost:1344/srv_clamav or icaps://localhost:1344/srv_clamav; null, empty or blank for a client without ICAP server which supports only FILEMOD
     * @param cacheMaxAgeInSeconds the max age in seconds of the cache
     * @return the ICAP client
     * @throws MalformedURLException In case of an invalid URL
     * @throws IOException In case of an I/O error
     */
    public ICAPClient getICAPClient(String icapUrl, int cacheMaxAgeInSeconds) throws MalformedURLException, IOException {
        
        if (icapUrl == null || icapUrl.isBlank()) {
            // without ICAP server only FILEMOD is supported: the content is checked by a local virus scanner
            return new ICAPClientImpl(getICAPConnectionManager(), null, null);
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
        String portPart = null;
        if (hostName.startsWith("[")) {
            // IPv6 literal, e.g. icap://[::1]:1344/srv_clamav
            idx = hostName.indexOf(']');
            if (idx < 0) {
                throw new MalformedURLException("Invalid icap url, missing closing bracket of IPv6 address!");
            }

            String rest = hostName.substring(idx + 1).trim();
            hostName = hostName.substring(1, idx).trim();
            if (rest.startsWith(":")) {
                portPart = rest.substring(1).trim();
            } else if (!rest.isEmpty()) {
                throw new MalformedURLException("Invalid icap url, unexpected characters after IPv6 address!");
            }
        } else {
            idx = hostName.indexOf(':');
            if (idx >= 0) {
                portPart = hostName.substring(idx + 1).trim();
                hostName = hostName.substring(0, idx).trim();
            }
        }

        if (hostName.isEmpty()) {
            throw new MalformedURLException("Invalid icap url, missing host name!");
        }

        int servicePort = 1344;
        if (portPart != null) {
            try {
                servicePort = Integer.parseInt(portPart);
            } catch (NumberFormatException e) {
                throw new MalformedURLException("Invalid icap url, invalid port [" + portPart + "]!");
            }

            if (servicePort < 1 || servicePort > 65535) {
                throw new MalformedURLException("Invalid icap url, port out of range [" + servicePort + "]!");
            }
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
        final String cacheKey = createCacheKey(serviceInformation);
        ICAPRemoteServiceConfiguration remoteServiceConfiguration = serviceCache.get(cacheKey);
        boolean staleUsable = remoteServiceConfiguration != null && getAge(remoteServiceConfiguration) <= MAX_CACHE_ENTRY_AGE;

        if (isCacheStale(remoteServiceConfiguration, serviceInformation)) {
            if (staleUsable && isRefreshBackoff(cacheKey)) {
                // a refresh failed recently, don't delay every call with a new attempt
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Use stale configuration, the last refresh failed recently: " + serviceInformation);
                }
            } else {
                try {
                    remoteServiceConfiguration = refreshServiceConfiguration(serviceInformation);
                    refreshFailures.remove(cacheKey);
                } catch (IOException e) {
                    if (!staleUsable) {
                        throw e;
                    }

                    // serve the stale configuration instead of failing all requests
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Could not refresh options from remote icap-server " + serviceInformation + ": " + e.getMessage(), e);
                    }
                    if (markRefreshFailure(cacheKey)) {
                        LOG.warn("Could not refresh options from remote icap-server " + serviceInformation + ", use stale configuration: " + e.getMessage());
                    }
                }
            }
        } else {
            if (LOG.isDebugEnabled()) {
                long effectiveTTL = getEffectiveTTL(remoteServiceConfiguration, serviceInformation);
                long diff = effectiveTTL - getAge(remoteServiceConfiguration);
                LOG.debug("Found remote service configuration in cache (valid for " + diff + " seconds): " + serviceInformation);
            }
        }

        return new ICAPClientImpl(getICAPConnectionManager(), serviceInformation, remoteServiceConfiguration);
    }


    /**
     * Refresh the remote service configuration. Only one refresh per service runs at the same time; concurrent callers
     * of the same service wait for its result, callers of other services are not blocked.
     *
     * @param serviceInformation the service information
     * @return the remote service configuration
     * @throws IOException In case of an I/O error
     */
    private ICAPRemoteServiceConfiguration refreshServiceConfiguration(ICAPServiceInformation serviceInformation) throws IOException {
        final String cacheKey = createCacheKey(serviceInformation);
        CompletableFuture<ICAPRemoteServiceConfiguration> future = new CompletableFuture<>();
        CompletableFuture<ICAPRemoteServiceConfiguration> inFlight = pendingRefresh.putIfAbsent(cacheKey, future);
        if (inFlight != null) {
            return awaitRefresh(inFlight);
        }

        try {
            // double-check: another thread may have refreshed the cache in between
            ICAPRemoteServiceConfiguration remoteServiceConfiguration = serviceCache.get(cacheKey);
            if (isCacheStale(remoteServiceConfiguration, serviceInformation)) {
                remoteServiceConfiguration = fetchServiceConfiguration(serviceInformation);
            }

            future.complete(remoteServiceConfiguration);
            return remoteServiceConfiguration;
        } catch (IOException | RuntimeException e) {
            future.completeExceptionally(e);
            throw e;
        } finally {
            pendingRefresh.remove(cacheKey, future);
        }
    }


    /**
     * Wait for a refresh of another thread.
     *
     * @param inFlight the refresh in progress
     * @return the remote service configuration
     * @throws InterruptedIOException In case the thread was interrupted while waiting
     * @throws IOException In case the refresh failed
     */
    private ICAPRemoteServiceConfiguration awaitRefresh(CompletableFuture<ICAPRemoteServiceConfiguration> inFlight) throws IOException {
        try {
            return inFlight.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for options of remote icap-server");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IOException(cause);
        }
    }


    /**
     * Fetch the remote service configuration from the server and update the cache.
     *
     * @param serviceInformation the service information
     * @return the remote service configuration
     * @throws IOException In case of an I/O error
     */
    private ICAPRemoteServiceConfiguration fetchServiceConfiguration(ICAPServiceInformation serviceInformation) throws IOException {
        ICAPClientImpl clientImpl = new ICAPClientImpl(getICAPConnectionManager(), serviceInformation, null);
        ICAPRemoteServiceConfiguration remoteServiceConfiguration = clientImpl.options();
        serviceCache.put(createCacheKey(serviceInformation), remoteServiceConfiguration);
        evictOldCacheEntries();

        // Apply server-advertised Max-Connections to the pool of this host only (RFC 3507 §4.10)
        ICAPConnectionManager currentConnectionManager = connectionManager;
        int serverMax = remoteServiceConfiguration.getMaxConnections();
        currentConnectionManager.setServerMaxConnections(serviceInformation.getHostName(), serviceInformation.getServicePort(), serviceInformation.isSecureConnection(), serverMax);
        if (serverMax > 0 && currentConnectionManager.isPoolingEnabled() && !maxConnectionsLogged) {
            int currentMax = currentConnectionManager.getMaxPoolConnectionsPerHost();
            if (currentMax > serverMax) {
                maxConnectionsLogged = true;
                LOG.warn("Pool setting (" + currentMax + ") exceeds server Max-Connections (" + serverMax + ") of " + serviceInformation + ", limit the pool of this server.");
            } else if (currentMax < serverMax) {
                maxConnectionsLogged = true;
                LOG.info("Server allows Max-Connections: " + serverMax + ", keeping user-defined pool setting: " + currentMax + ".");
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Set remote service configuration cache: " + serviceInformation);
        }
        return remoteServiceConfiguration;
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
        return getAge(config) > effectiveTTL;
    }


    /**
     * Get the effective TTL in seconds. The server-advertised Options-TTL (RFC 3507 §4.10) can only shorten the
     * client-configured cacheMaxAgeInSeconds, but not below a minimum, to avoid an options request on every call.
     *
     * @param config the remote service configuration
     * @param serviceInformation the service information
     * @return the effective TTL in seconds
     */
    private long getEffectiveTTL(ICAPRemoteServiceConfiguration config, ICAPServiceInformation serviceInformation) {
        long effectiveTTL = serviceInformation.getCacheMaxAgeInSeconds();
        if (config.getOptionsTTL() > 0) {
            long serverTTL = Math.max(config.getOptionsTTL(), MIN_OPTIONS_TTL);
            if (serverTTL < effectiveTTL) {
                effectiveTTL = serverTTL;
            }
        }
        return effectiveTTL;
    }


    /**
     * Get the age in seconds of a configuration
     *
     * @param config the remote service configuration
     * @return the age in seconds
     */
    private long getAge(ICAPRemoteServiceConfiguration config) {
        if (config.getTimestamp() == null) {
            return Long.MAX_VALUE;
        }
        return Instant.now().getEpochSecond() - config.getTimestamp().getEpochSecond();
    }


    /**
     * Check if a refresh failed within the backoff time.
     *
     * @param cacheKey the cache key
     * @return true if a new refresh should not be tried yet
     */
    private boolean isRefreshBackoff(String cacheKey) {
        Long failedAt = refreshFailures.get(cacheKey);
        return failedAt != null && (System.currentTimeMillis() - failedAt.longValue()) < REFRESH_BACKOFF_MS;
    }


    /**
     * Mark a failed refresh.
     *
     * @param cacheKey the cache key
     * @return true if it is the first failure within the backoff time, so it should be logged
     */
    private boolean markRefreshFailure(String cacheKey) {
        long now = System.currentTimeMillis();
        Long previous = refreshFailures.put(cacheKey, now);
        return previous == null || (now - previous.longValue()) >= REFRESH_BACKOFF_MS;
    }


    /**
     * Remove cache entries which are too old to be used, even as fallback.
     */
    private void evictOldCacheEntries() {
        serviceCache.entrySet().removeIf(e -> getAge(e.getValue()) > MAX_CACHE_ENTRY_AGE);
        refreshFailures.entrySet().removeIf(e -> !serviceCache.containsKey(e.getKey()));
    }


    /**
     * Create the cache key of a service. The cache max age is not part of the key, the options of a service are
     * the same for all callers.
     *
     * @param serviceInformation the service information
     * @return the cache key
     */
    private String createCacheKey(ICAPServiceInformation serviceInformation) {
        String scheme = "icap";
        if (serviceInformation.isSecureConnection()) {
            scheme = "icaps";
        }
        return scheme + "://" + serviceInformation.getHostName().toLowerCase() + ":" + serviceInformation.getServicePort() + "/" + serviceInformation.getServiceName();
    }
}
