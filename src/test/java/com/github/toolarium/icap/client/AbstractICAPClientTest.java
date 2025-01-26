/*
 * AbstractICAPClientTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Base test class
 * 
 * @author Patrick Meier
 */
public abstract class AbstractICAPClientTest { 
    protected static final String SERVICE = "srv_clamav";
    protected static final String SRC_TEST_RESOURCES = "src/test/resources/";
    private static final Logger LOG = LoggerFactory.getLogger(AbstractICAPClientTest.class);
    private Boolean allow204 = null;
    
    
    /**
     * Set allow 204
     * 
     * @param allow204 true to allow 204
     */
    protected void setAllow204(Boolean allow204) {
        this.allow204 = allow204;
    }
    
    
    /**
     * Get the ICAP client
     *
     * @return the client
     * @throws IOException In case of an I/O error
     */
    protected ICAPClient getICAPClient() throws IOException {
        return ICAPClientFactory.getInstance().getICAPClient("localhost", 1344, SERVICE).supportCompareVerifyIdenticalContent(true);
    }   

    
    /**
     * Validate the resource
     *
     * @param mode the mode
     * @param resourceName the resource name
     * @return the ICAP response
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    protected ICAPHeaderInformation validateResource(ICAPMode mode, String resourceName) throws IOException, ContentBlockedException {
        File file = Paths.get(resourceName.trim()).toFile();
        FileInputStream resourceInputStream = new FileInputStream(file);
        
        return validateResource(mode, "Buffer", resourceName, resourceInputStream, file.length());
    }


    /**
     * Validate the resource
     *
     * @param mode the mode
     * @param resourceName the resource name
     * @param content the content
     * @return the ICAP response
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    protected ICAPHeaderInformation validateResource(ICAPMode mode, String resourceName, String content) throws IOException, ContentBlockedException {
        ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(content.getBytes());
        return validateResource(mode, "File", resourceName, resourceInputStream, content.length());
    }

    
    /**
     * Validate the resource
     *
     * @param mode the mode
     * @param source the source
     * @param resourceName the resource name
     * @param resourceInputStream the stream
     * @param contentLength the content length
     * @return the ICAP header information
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    protected ICAPHeaderInformation validateResource(ICAPMode mode, String source, String resourceName, InputStream resourceInputStream, long contentLength) throws IOException, ContentBlockedException {
        try { 
            ICAPHeaderInformation icapHeaderInformation = getICAPClient().validateResource(mode, new ICAPRequestInformation(ICAPRequestInformation.USER_AGENT, ICAPRequestInformation.API_VERSION, "testUser", source, allow204), 
                    new ICAPResource(resourceName, resourceInputStream, contentLength));
            printResponse(icapHeaderInformation);
            return icapHeaderInformation;
        } catch (ContentBlockedException e) {            
            printResponse(e.getICAPHeaderInformation());
            if (e.getContent() != null) {
                LOG.debug("---> Response content: \n" + e.getContent());
            }
            
            throw e;
        }
    }

    
    /**
     * Print response
     *
     * @param icapHeaderInformation the ICAP header information
     */
    protected void printResponse(ICAPHeaderInformation icapHeaderInformation) {
        LOG.debug("---> Response status: " + icapHeaderInformation.getStatus());
        LOG.debug("---> Response protocol: " + icapHeaderInformation.getProtocol());
        LOG.debug("---> Response version: " + icapHeaderInformation.getVersion());
        LOG.debug("---> Response message: " + icapHeaderInformation.getMessage());        
        LOG.debug("---> Response header entries: " + icapHeaderInformation.getHeaders());
    }


    /**
     * Assert umodified file
     *
     * @param icapHeaderInformation the ICAP header information
     * @return the ICAP header information
     */
    protected ICAPHeaderInformation assertAllow204Unmodified(ICAPHeaderInformation icapHeaderInformation) {
        assertEquals("ICAP", icapHeaderInformation.getProtocol());
        assertEquals(204, icapHeaderInformation.getStatus());
        assertEquals("1.0", icapHeaderInformation.getVersion());
        assertEquals("Unmodified", icapHeaderInformation.getMessage());
        assertEquals("[Server, Connection, ISTag]", "" + icapHeaderInformation.getHeaders().keySet());
        assertEquals("[C-ICAP/0.4.4]", "" + icapHeaderInformation.getHeaders().get("Server"));
        assertTrue(icapHeaderInformation.getHeaders().get("ISTag").toString().startsWith("[CI0001-"));
        return icapHeaderInformation; 
    }


    /**
     * Assert umodified file
     *
     * @param icapHeaderInformation the ICAP header information
     * @return the ICAP header information
     * @throws IOException In case of an I/O error
     */
    protected ICAPHeaderInformation assertUnmodifiedFile(ICAPHeaderInformation icapHeaderInformation) throws IOException {
        assertEquals("ICAP", icapHeaderInformation.getProtocol());
        assertEquals(200, icapHeaderInformation.getStatus());
        assertEquals("1.0", icapHeaderInformation.getVersion());
        assertEquals("OK", icapHeaderInformation.getMessage());
        assertEquals("[C-ICAP/0.4.4]", "" + icapHeaderInformation.getHeaders().get("Server"));
        
        if (icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST)) {
            assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_SERVER));
            assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_CONNECTION));
            assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ISTAG));
            assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ENCAPSULATED));
            assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST));
            assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST));
            assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST).get(0).length() > 0);
            assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST).get(0).length() > 0);
            
            if (icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT)) {
                assertTrue(Boolean.valueOf(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).get(0)));
            }
        } else {
            assertEquals("[Server, Connection, ISTag, Encapsulated]", "" + icapHeaderInformation.getHeaders().keySet());
        }
        
        assertTrue(icapHeaderInformation.getHeaders().get("ISTag").toString().startsWith("[CI0001-"));
        return icapHeaderInformation; 
    }
    
    
    /**
     * Assert eicar test virus
     *
     * @param icapHeaderInformation the ICAP header information
     * @return the ICAP header information
     * @throws IOException In case of an I/O error
     */
    protected ICAPHeaderInformation assertEicar(ICAPHeaderInformation icapHeaderInformation) throws IOException {
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND));
        assertEquals(3, icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND).size());
        assertEquals("Type=0", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND).get(0));
        assertEquals("Resolution=2", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND).get(1));
        assertEquals("Threat=Eicar-Signature", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND).get(2));
        assertEquals(5, icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND).size());
        assertEquals("1", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND).get(0));
        assertEquals("-", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND).get(1));
        assertEquals("Eicar-Signature", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND).get(2));
        assertEquals("0", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND).get(3));
        assertEquals("0", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND).get(4));
        return icapHeaderInformation;
    }
}
