/*
 * ICAPSocketTimeoutTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import static org.junit.jupiter.api.Assertions.fail;

import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Test socket timeout
 * 
 * @author patrick
 */
public class ICAPSocketTimeoutTest {
    private static final String RESOURCE_COULD_NOT_BE_ACCESSED = "Resource could not be accessed: ";
    private static final String LOCALHOST = "localhost"; 
    private static final String SERVICENAME = "srv_clamav"; 
    private static final Logger LOG = LoggerFactory.getLogger(ICAPSocketTimeoutTest.class);

    /**
     * Socket timeout
     */
    @Test
    public void testSocketTimeout() {
        LOG.debug("START");
        ICAPClientFactory.getInstance().getICAPConnectionManager().setSocketTimeout(10);
        final int port = 1344;
                
        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(new byte[] {});
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, port, SERVICENAME)
                 .validateResource(ICAPMode.RESPMOD, 
                                   new ICAPRequestInformation("userb", "emptyfile"), 
                                   new ICAPResource("build/test-emptyfile.com", resourceInputStream, 0));
        } catch (Exception ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED + ioe.getMessage(), ioe);
            fail();
        }

        LOG.debug("END");
    }
}
