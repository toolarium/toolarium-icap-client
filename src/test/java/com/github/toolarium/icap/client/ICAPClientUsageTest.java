/*
 * ICAPClientUsageTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import static org.junit.jupiter.api.Assertions.fail;

import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implements the usage test cases
 * 
 * @author patrick
 */
public class ICAPClientUsageTest extends AbstractICAPClientTest {
    private static final Logger LOG = LoggerFactory.getLogger(ICAPClientUsageTest.class);
    
    
    /**
     * The usage how to use the client library 
     */
    @Test
    public void usage() {
        // the ICAP-Server information
        final String hostName = "localhost";
        final int port = 1344;
        final String serviceName = "srv_clamav";
        
        // the user, request source and the resource
        final String username = "user";
        final String requestSource = "file";
        final File file = new File("build/test-file.com");
        
        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_CLEAN.getBytes());
            ICAPClientFactory.getInstance().getICAPClient(hostName, port, serviceName)
                 .validateResource(ICAPMode.REQMOD, 
                                   new ICAPRequestInformation(username, requestSource), 
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_CLEAN.length()));
            
            // If no exception is thrown the resource can be used and is valid. 

            // log output looks like:
            // DD8DEE46 - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // 30AC31B0 - Validate resource (username: user, source: file, resource: test-file.com, length: 71)
            // 30AC31B0 - Valid resource (username: user, source: file, resource: test-file.com, length: 71, http-status: 204).

        } catch (IOException ioe) { // I/O error
            LOG.warn("Resource could not be accessed: " + ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {
            
            // !!! The resource has to be blocked !!! 
            
            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage(); 

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);
            
            // The e.getContent contains the returned error information from the ICAP-Server. 
            // It can be ignored as long as the resource is blocked; otherwise it gives a well structured response.
            e.getContent();
            
            fail();
        }
    }


    /**
     * The usage how to use the client library 
     */
    @Test
    public void usageVirusFound() {
        // the ICAP-Server information
        final String hostName = "localhost";
        final int port = 1344;
        final String serviceName = "srv_clamav";
        
        // the user, request source and the resource
        final String username = "user";
        final String requestSource = "file";
        final File file = new File("build/test-virus-file.com");
        
        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_VIRUS.getBytes());
            ICAPClientFactory.getInstance().getICAPClient(hostName, port, serviceName)
                 .validateResource(ICAPMode.REQMOD, 
                                   new ICAPRequestInformation(username, requestSource), 
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_VIRUS.length()));
            
            // If no exception is thrown the resource can be used and is valid.           
            fail();
            
            // log output looks like:
            // E1C57BCF - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // DA054425 - Validate resource (username: user, source: file, resource: test-virus-file.com, length: 70)
            // DA054425 - Thread found in resource (username: user, source: file, resource: test-virus-file.com, length: 70, http-status: 200):
            // - X-Infection-Found: [Type=0, Resolution=2, Threat=Eicar-Signature]
            // - X-Violations-Found: [1, -, Eicar-Signature, 0, 0]
            // - X-Request-Message-Digest: [{SHA-256}8b3f191819931d1f2cef7289239b5f77c00b079847b9c2636e56854d1e5eff71]
            // - X-Response-Message-Digest: [{SHA-256}2e124ff42640aafcc7e267269dd495f35411ce469ec2a64c9af56ccd74bed32f]
            // - X-Resource-Identical-Content: [false]
            
        } catch (IOException ioe) { // I/O error
            LOG.warn("Resource could not be accessed: " + ioe.getMessage(), ioe);
            
        } catch (ContentBlockedException e) {
            
            // !!! The resource has to be blocked !!! 
            
            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage(); 

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);
            
            // The e.getContent contains the returned error information from the ICAP-Server. 
            // It can be ignored as long as the resource is blocked; otherwise it gives a well structured response.
            e.getContent();
        }
    }

    
    /**
     * The usage how to use the client library 
     */
    @Test
    public void usageVirusFoundAsBase64() {
        // the ICAP-Server information
        final String hostName = "localhost";
        final int port = 1344;
        final String serviceName = "srv_clamav";
        
        // the user, request source and the resource
        final String username = "user";
        final String requestSource = "file";
        final File file = new File("build/test-virus-file.com");

        try {
            byte[] eicarContent = Base64.getDecoder().decode(ICAPTestVirusConstants.REQUEST_BODY_VIRUS_BASE64);
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(eicarContent);
            ICAPClientFactory.getInstance().getICAPClient(hostName, port, serviceName)
                 .validateResource(ICAPMode.REQMOD, 
                                   new ICAPRequestInformation(username, requestSource), 
                                   new ICAPResource(file.getName(), resourceInputStream, eicarContent.length));
            
            // If no exception is thrown the resource can be used and is valid.           
            fail();
            
            // log output looks like:
            // E1C57BCF - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // DA054425 - Validate resource (username: user, source: file, resource: test-virus-file.com, length: 70)
            // DA054425 - Thread found in resource (username: user, source: file, resource: test-virus-file.com, length: 70, http-status: 200):
            // - X-Infection-Found: [Type=0, Resolution=2, Threat=Eicar-Signature]
            // - X-Violations-Found: [1, -, Eicar-Signature, 0, 0]
            // - X-Request-Message-Digest: [{SHA-256}8b3f191819931d1f2cef7289239b5f77c00b079847b9c2636e56854d1e5eff71]
            // - X-Response-Message-Digest: [{SHA-256}2e124ff42640aafcc7e267269dd495f35411ce469ec2a64c9af56ccd74bed32f]
            // - X-Resource-Identical-Content: [false]
            
        } catch (IOException ioe) { // I/O error
            LOG.warn("Resource could not be accessed: " + ioe.getMessage(), ioe);
            
        } catch (ContentBlockedException e) {
            
            // !!! The resource has to be blocked !!! 
            
            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage(); 

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);
            
            // The e.getContent contains the returned error information from the ICAP-Server. 
            // It can be ignored as long as the resource is blocked; otherwise it gives a well structured response.
            e.getContent();
        }
    }


    /**
     * The usage how to use the client library 
     */
    @Test
    public void usageWithURL() {
        // the ICAP-Server information
        final String icapUrl = "icap://localhost:1344/srv_clamav";
        
        // the user, request source and the resource
        final String username = "user";
        final String requestSource = "file";
        final File file = new File("build/test-file.com");
        
        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_CLEAN.getBytes());
            ICAPClientFactory.getInstance().getICAPClient(icapUrl)                    
                 .validateResource(ICAPMode.REQMOD, 
                                   new ICAPRequestInformation(username, requestSource), 
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_CLEAN.length()));
            
            // If no exception is thrown the resource can be used and is valid. 

            // log output looks like:
            // DD8DEE46 - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // 30AC31B0 - Validate resource (username: user, source: file, resource: test-file.com, length: 71)
            // 30AC31B0 - Valid resource (username: user, source: file, resource: test-file.com, length: 71, http-status: 204).

        } catch (IOException ioe) { // I/O error
            LOG.warn("Resource could not be accessed: " + ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {
            
            // !!! The resource has to be blocked !!! 
            
            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage(); 

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);
            
            // The e.getContent contains the returned error information from the ICAP-Server. 
            // It can be ignored as long as the resource is blocked; otherwise it gives a well structured response.
            e.getContent();
            
            fail();
        }
    }
}
