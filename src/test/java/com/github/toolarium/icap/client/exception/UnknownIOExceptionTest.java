/*
 * UnknownIOExceptionTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link UnknownIOException}.
 *
 * @author patrick
 */
public class UnknownIOExceptionTest {

    /**
     * Test that UnknownIOException carries header information.
     */
    @Test
    public void exceptionCarriesHeaders() {
        ICAPHeaderInformation headers = new ICAPHeaderInformation()
                .setStatus(500)
                .setMessage("Internal Server Error");
        headers.getHeaders().put("X-Error-Reason", Arrays.asList("Scan engine unavailable"));

        UnknownIOException ex = new UnknownIOException(500, "Server error", headers);

        assertEquals("500: Server error", ex.getMessage());
        assertEquals(500, ex.getStatusCode());
        assertNotNull(ex.getICAPHeaderInformation());
        assertEquals(500, ex.getICAPHeaderInformation().getStatus());
        assertEquals("Internal Server Error", ex.getICAPHeaderInformation().getMessage());
        assertTrue(ex.getICAPHeaderInformation().containsHeader("X-Error-Reason"));
        assertEquals("Scan engine unavailable", ex.getICAPHeaderInformation().getHeaderValues("X-Error-Reason").get(0));
    }


    /**
     * Test that UnknownIOException extends IOException.
     */
    @Test
    public void extendsIOException() {
        ICAPHeaderInformation headers = new ICAPHeaderInformation().setStatus(503);
        UnknownIOException ex = new UnknownIOException(503, "Service overloaded", headers);

        assertTrue(ex instanceof IOException);
        assertEquals(503, ex.getStatusCode());
    }


    /**
     * Test with null headers.
     */
    @Test
    public void nullHeaders() {
        UnknownIOException ex = new UnknownIOException(500, "Error", null);

        assertEquals("500: Error", ex.getMessage());
        assertEquals(500, ex.getStatusCode());
        assertEquals(null, ex.getICAPHeaderInformation());
    }
}
