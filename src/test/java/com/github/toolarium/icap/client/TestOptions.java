/*
 * TestOptions.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRemoteServiceConfiguration;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;


/**
 * Test options
 * 
 * @author Patrick Meier
 */
public class TestOptions extends AbstractICAPClientTest {
    
    /**
     * Test options
     *
     * @throws IOException In case of an I/O error
     */
    @Test
    public void testValidOptions() throws IOException {
        ICAPRemoteServiceConfiguration remoteServiceConfiguration = getICAPClient().options();
        assertTrue(Arrays.equals(ICAPMode.values(), remoteServiceConfiguration.getOptionMethods()));
        assertEquals(1024, remoteServiceConfiguration.getServerPreviewSize());
    }

    
    /**
     * Test options
     */
    @Test
    public void testInvalidRequest()  {
        assertThrows(IOException.class, () -> {
            ICAPClientFactory.getInstance().getICAPClient("localhost", 1345, SERVICE).options();
        });
    }
    

    /**
     * Test options
     *
     * @throws IOException In case of an I/O error
     */
    @Test
    public void testOptions() throws IOException {
        testInvalidRequest();
        testValidOptions();
        testInvalidRequest();
        testValidOptions();
    }
}
