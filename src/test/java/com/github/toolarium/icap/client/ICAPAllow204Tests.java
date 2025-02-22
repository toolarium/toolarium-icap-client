/*
 * ICAPAllow204Tests.java
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
 * Test ICAP with allow 204
 *
 * @author patrick
 */
public class ICAPAllow204Tests extends AbstractICAPClientTest {
    /**
     * Enable allow 204
     */
    @BeforeEach
    public void enableAllow204() {
        setAllow204(true);
    }


    /**
     * Test valid
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testValidRequest() throws IOException, ContentBlockedException {
        assertAllow204Unmodified(validateResource(ICAPMode.REQMOD, "testValidRequestResource", "ABCDEFG"));
        assertAllow204Unmodified(validateResource(ICAPMode.RESPMOD, "testValidRequestResource", "ABCDEFG"));
    }


    /**
     * Test valid
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testValidResponseRequest() throws IOException, ContentBlockedException {
        assertAllow204Unmodified(validateResource(ICAPMode.REQMOD, "testValidRequestResource", "ABCDEFG"));
        assertAllow204Unmodified(validateResource(ICAPMode.RESPMOD, "testValidRequestResource", "ABCDEFG"));
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
        assertEquals("[C-ICAP/0.4.4]", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_SERVER));
        assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ISTAG).toString().startsWith("[CI0001-"));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_SERVER));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_CONNECTION));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ISTAG));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ENCAPSULATED));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST));
        assertEicar(icapHeaderInformation);
        assertFalse(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST).get(0).isEmpty());
        assertFalse(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST).get(0).isEmpty());

        if (icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT)) {
            assertFalse(Boolean.parseBoolean(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).get(0)));
        }

        assertEquals(2, icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).size());
        assertEquals("res-hdr=0", icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(0));
        assertEquals("res-body=108", icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(1));
        assertEquals(3, ex.getContent().length());
        assertEquals("n/a", ex.getContent());

        ex = assertThrows(ContentBlockedException.class, () -> {
            validateResource(ICAPMode.RESPMOD, "testDetectVirusResource", ICAPTestVirusConstants.REQUEST_BODY_VIRUS);
        });

        icapHeaderInformation = ex.getICAPHeaderInformation();
        assertEquals("ICAP", icapHeaderInformation.getProtocol());
        assertEquals(200, icapHeaderInformation.getStatus());
        assertEquals("1.0", icapHeaderInformation.getVersion());
        assertEquals("OK", icapHeaderInformation.getMessage());
        assertEquals("[C-ICAP/0.4.4]", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_SERVER));
        assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ISTAG).toString().startsWith("[CI0001-"));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_SERVER));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_CONNECTION));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ISTAG));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ENCAPSULATED));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST));
        assertEicar(icapHeaderInformation);
        assertFalse(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST).get(0).isEmpty());
        assertFalse(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST).get(0).isEmpty());

        if (icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT)) {
            assertFalse(Boolean.parseBoolean(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).get(0)));
        }

        assertEquals(2, icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).size());
        assertEquals("res-hdr=0", icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(0));
        assertEquals("res-body=170", icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(1));
        assertEquals(447, ex.getContent().length());
    }


    /**
     * Test invalidate
     *
     * @throws IOException In case of an I/O error
     */
    @Test
    public void testDetectVirusResponseRequest() throws IOException {
        ContentBlockedException ex = assertThrows(ContentBlockedException.class, () -> {
            validateResource(ICAPMode.REQMOD, "testDetectVirusResource", ICAPTestVirusConstants.REQUEST_BODY_VIRUS);
        });

        ICAPHeaderInformation icapHeaderInformation = ex.getICAPHeaderInformation();
        assertEquals("ICAP", icapHeaderInformation.getProtocol());
        assertEquals(200, icapHeaderInformation.getStatus());
        assertEquals("1.0", icapHeaderInformation.getVersion());
        assertEquals("OK", icapHeaderInformation.getMessage());
        assertEquals("[C-ICAP/0.4.4]", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_SERVER));
        assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ISTAG).toString().startsWith("[CI0001-"));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_SERVER));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_CONNECTION));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ISTAG));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ENCAPSULATED));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST));
        assertEicar(icapHeaderInformation);
        assertEquals(2, icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).size());

        assertFalse(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST).get(0).isEmpty());
        assertFalse(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST).get(0).isEmpty());

        if (icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT)) {
            assertFalse(Boolean.parseBoolean(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).get(0)));
        }

        assertEquals("res-hdr=0", icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(0));
        assertEquals("res-body=108", icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(1));
        assertEquals(3, ex.getContent().length());
        assertEquals("n/a", ex.getContent());


        ex = assertThrows(ContentBlockedException.class, () -> {
            validateResource(ICAPMode.RESPMOD, "testDetectVirusResource", ICAPTestVirusConstants.REQUEST_BODY_VIRUS);
        });

        icapHeaderInformation = ex.getICAPHeaderInformation();
        assertEquals("ICAP", icapHeaderInformation.getProtocol());
        assertEquals(200, icapHeaderInformation.getStatus());
        assertEquals("1.0", icapHeaderInformation.getVersion());
        assertEquals("OK", icapHeaderInformation.getMessage());
        assertEquals("[C-ICAP/0.4.4]", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_SERVER));
        assertTrue(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ISTAG).toString().startsWith("[CI0001-"));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_SERVER));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_CONNECTION));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ISTAG));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_ENCAPSULATED));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST));
        assertTrue(icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST));
        assertEicar(icapHeaderInformation);
        assertEquals(2, icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).size());

        assertFalse(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_REQUEST_MESSAGE_DIGEST).get(0).isEmpty());
        assertFalse(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST).get(0).isEmpty());

        if (icapHeaderInformation.getHeaders().containsKey(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT)) {
            assertFalse(Boolean.parseBoolean(icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT).get(0)));
        }

        assertEquals("res-hdr=0", icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(0));
        assertEquals("res-body=170", icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_ENCAPSULATED).get(1));
        assertEquals(447, ex.getContent().length());
    }
}
