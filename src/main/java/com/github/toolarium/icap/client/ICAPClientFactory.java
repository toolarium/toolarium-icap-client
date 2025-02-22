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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final Map<ICAPServiceInformation, ICAPRemoteServiceConfiguration> serviceCache;
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
    ICAPClient getICAPClient(String hostName, int servicePort, String serviceName, boolean secureConnection) throws IOException {
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

        // Regex breakdown:
        //   ^(icap[s]?)://+        -> Matches protocol "icap" or "icaps" followed by "://", allowing extra slashes.
        //   ([^:/]+)               -> Captures the hostname (any characters except ':' or '/').
        //   (?::(\\d+))?           -> Optionally captures the port number (digits after a colon).
        //   (?:/(.*))?             -> Optionally captures the service name/path following a slash.
        //   $                      -> End of string.
        Pattern pattern = Pattern.compile("^(icap[s]?)://+([^:/]+)(?::(\\d+))?(?:/(.*))?$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(icapUrl.trim());

        if (!matcher.matches()) {
            throw new MalformedURLException("Invalid icap url, expected url starts with icap protocol, e.g. icap://...!");
        }

        String protocol = matcher.group(1);
        String hostName = matcher.group(2);
        String portStr = matcher.group(3);
        String serviceName = "";
        if (matcher.group(4) != null) {
            serviceName = matcher.group(4).trim();
        }

        int servicePort = 1344;
        if (portStr != null && !portStr.isEmpty()) {
            servicePort = Integer.parseInt(portStr.trim());
        }
        boolean secureConnection = protocol.equalsIgnoreCase("icaps");

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

        if (remoteServiceConfiguration == null
                || remoteServiceConfiguration.getTimestamp() == null
                || ((Instant.now().getEpochSecond() - remoteServiceConfiguration.getTimestamp().getEpochSecond()) > serviceInformation.getCacheMaxAgeInSeconds())) {
            try {
                ICAPClientImpl clientImpl = new ICAPClientImpl(getICAPConnectionManager(), serviceInformation, remoteServiceConfiguration);
                remoteServiceConfiguration = clientImpl.options();
                serviceCache.put(serviceInformation, remoteServiceConfiguration);
                LOG.debug("Set remote service configuration cache: {}", serviceInformation);
            } catch (IOException e) {
                LOG.debug("Could not get options from remote icap-server: {}", e.getMessage(), e);
                throw e;
            }
        } else {
            String logCacheDuration = "";
            if (remoteServiceConfiguration.getTimestamp() != null) {
                long diff = serviceInformation.getCacheMaxAgeInSeconds() + (Instant.now().getEpochSecond() - remoteServiceConfiguration.getTimestamp().getEpochSecond());
                logCacheDuration = "(valid for " + diff + " seconds)";
            }

            LOG.debug("Found remote service configuration in cache {}: {}", logCacheDuration, serviceInformation);
        }

        return new ICAPClientImpl(getICAPConnectionManager(), serviceInformation, remoteServiceConfiguration);
    }
}
