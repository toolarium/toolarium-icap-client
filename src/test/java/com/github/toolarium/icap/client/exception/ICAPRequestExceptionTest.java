/*
 * ICAPRequestExceptionTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPRequestException}.
 *
 * @author patrick
 */
public class ICAPRequestExceptionTest {

    /**
     * Test that ICAPRequestException carries status code and headers.
     */
    @Test
    public void exceptionCarriesStatusAndHeaders() {
        ICAPHeaderInformation headers = new ICAPHeaderInformation()
                .setStatus(400)
                .setMessage("Bad Request");
        headers.getHeaders().put("X-Error-Detail", Arrays.asList("Malformed encapsulated header"));

        ICAPRequestException ex = new ICAPRequestException(400, "Bad request", headers);

        assertEquals("400: Bad request", ex.getMessage());
        assertEquals(400, ex.getStatusCode());
        assertNotNull(ex.getICAPHeaderInformation());
        assertEquals(400, ex.getICAPHeaderInformation().getStatus());
        assertEquals("Bad Request", ex.getICAPHeaderInformation().getMessage());
        assertTrue(ex.getICAPHeaderInformation().containsHeader("X-Error-Detail"));
    }


    /**
     * Test 404 service not found.
     */
    @Test
    public void serviceNotFound() {
        ICAPHeaderInformation headers = new ICAPHeaderInformation().setStatus(404);
        ICAPRequestException ex = new ICAPRequestException(404, "ICAP service not found", headers);

        assertEquals(404, ex.getStatusCode());
        assertEquals("404: ICAP service not found", ex.getMessage());
    }


    /**
     * Test 405 method not allowed.
     */
    @Test
    public void methodNotAllowed() {
        ICAPHeaderInformation headers = new ICAPHeaderInformation().setStatus(405);
        ICAPRequestException ex = new ICAPRequestException(405, "Method not allowed for service", headers);

        assertEquals(405, ex.getStatusCode());
    }


    /**
     * Test 408 request timeout.
     */
    @Test
    public void requestTimeout() {
        ICAPHeaderInformation headers = new ICAPHeaderInformation().setStatus(408);
        ICAPRequestException ex = new ICAPRequestException(408, "Request timeout", headers);

        assertEquals(408, ex.getStatusCode());
    }


    /**
     * Test that ICAPRequestException extends IOException.
     */
    @Test
    public void extendsIOException() {
        ICAPRequestException ex = new ICAPRequestException(400, "Bad request", new ICAPHeaderInformation());
        assertTrue(ex instanceof IOException);
    }


    /**
     * Test with null headers.
     */
    @Test
    public void nullHeaders() {
        ICAPRequestException ex = new ICAPRequestException(400, "Bad request", null);

        assertEquals("400: Bad request", ex.getMessage());
        assertEquals(400, ex.getStatusCode());
        assertNull(ex.getICAPHeaderInformation());
    }
}
