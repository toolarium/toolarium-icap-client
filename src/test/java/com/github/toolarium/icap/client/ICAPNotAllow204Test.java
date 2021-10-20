/*
 * ClientTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Test ICAP client
 * 
 * @author Patrick Meier
 */
public class ICAPNotAllow204Test extends AbstractICAPClientTest {

    /**
     * Disable allow 204
     */
    @BeforeEach
    public void disableAllow204() {
        setAllow204(false);
    }

    
    /**
     * Test valid 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testValidRequest() throws IOException, ContentBlockedException {
        assertUnmodifiedFile(validateResource(ICAPMode.REQMOD, "testValidRequestResource", "ABCDEFG"));
    }

    
    /**
     * Test valid 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testValidRequestWihtout204Support() throws IOException, ContentBlockedException {
        assertUnmodifiedFile(validateResource(ICAPMode.REQMOD, "testValidRequestResource", "ABCDEFG"));
    }

    
    /**
     * Test valid 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testValidResponseRequest() throws IOException, ContentBlockedException {
        assertUnmodifiedFile(validateResource(ICAPMode.RESPMOD, "testValidRequestResource", "ABCDEFG"));
    }

    
    /**
     * Test valid 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testValidResponseWihtout204Support() throws IOException, ContentBlockedException {
        assertUnmodifiedFile(validateResource(ICAPMode.RESPMOD, "testValidRequestResource", "ABCDEFG"));
    }

    
    /**
     * Test invalidate 
     *
     * @throws IOException In case of an I/O error
     */
    @Test
    public void testDetectVirusRequest() throws IOException {
        ContentBlockedException ex = assertThrows(ContentBlockedException.class, () -> {
            validateResource(ICAPMode.REQMOD, "testDetectVirusResource", ICAPTestVirusConstants.REQUEST_BODY_VIRUS);
        });
        
        ICAPHeaderInformation icapHeaderInformation = ex.getICAPHeaderInformation();
        assertEquals("ICAP", icapHeaderInformation.getProtocol());
        assertEquals(200, icapHeaderInformation.getStatus());
        assertEquals("1.0", icapHeaderInformation.getVersion());
        assertEquals("OK", icapHeaderInformation.getMessage());
        assertEquals("[C-ICAP/0.4.4]", "" + icapHeaderInformation.getHeaders().get("Server"));
        assertTrue(icapHeaderInformation.getHeaders().get("ISTag").toString().startsWith("[CI0001-"));        
        assertEquals("[Server, Connection, ISTag, X-Infection-Found, X-Violations-Found, Encapsulated, X-Request-Message-Digest, X-Response-Message-Digest, X-Resource-Identical-Content]", "" + icapHeaderInformation.getHeaders().keySet());
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
        
        assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST).get(0).length() > 0);
        assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST).get(0).length() > 0);
        assertFalse(Boolean.valueOf(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).get(0)));

        assertEquals(2, icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).size());
        assertEquals("res-hdr=0", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(0));
        assertEquals("res-body=170", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(1));
        assertEquals(457, ex.getContent().length());
    }    


    /**
     * Test invalidate 
     *
     * @throws IOException In case of an I/O error
     */
    @Test
    public void testDetectVirusResponseRequest() throws IOException {
        ContentBlockedException ex = assertThrows(ContentBlockedException.class, () -> {
            validateResource(ICAPMode.RESPMOD, "testDetectVirusResource", ICAPTestVirusConstants.REQUEST_BODY_VIRUS);
        });
        
        ICAPHeaderInformation icapHeaderInformation = ex.getICAPHeaderInformation();
        assertEquals("ICAP", icapHeaderInformation.getProtocol());
        assertEquals(200, icapHeaderInformation.getStatus());
        assertEquals("1.0", icapHeaderInformation.getVersion());
        assertEquals("OK", icapHeaderInformation.getMessage());
        assertEquals("[C-ICAP/0.4.4]", "" + icapHeaderInformation.getHeaders().get("Server"));
        assertTrue(icapHeaderInformation.getHeaders().get("ISTag").toString().startsWith("[CI0001-"));
        assertEquals("[Server, Connection, ISTag, X-Infection-Found, X-Violations-Found, Encapsulated, X-Request-Message-Digest, X-Response-Message-Digest, X-Resource-Identical-Content]", "" + icapHeaderInformation.getHeaders().keySet());
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
        assertEquals(2, icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).size());
        assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST).get(0).length() > 0);
        assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST).get(0).length() > 0);
        assertFalse(Boolean.valueOf(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).get(0)));
        
        assertEquals("res-hdr=0", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(0));
        assertEquals("res-body=170", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(1));
        assertEquals(457, ex.getContent().length());
    }    
}
