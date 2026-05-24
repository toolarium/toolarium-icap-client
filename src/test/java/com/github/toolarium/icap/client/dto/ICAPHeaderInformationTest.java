/*
 * ICAPHeaderInformationTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPHeaderInformation}.
 *
 * @author patrick
 */
public class ICAPHeaderInformationTest {

    /**
     * Test default constructor initializes headers to empty map (R10 fix)
     */
    @Test
    public void defaultConstructorHeadersNotNull() {
        ICAPHeaderInformation info = new ICAPHeaderInformation();
        assertNotNull(info.getHeaders());
        assertTrue(info.getHeaders().isEmpty());
    }


    /**
     * Test containsHeader on empty headers does not throw NPE (R10 fix)
     */
    @Test
    public void containsHeaderOnEmptyHeaders() {
        ICAPHeaderInformation info = new ICAPHeaderInformation();
        assertFalse(info.containsHeader("X-Test"));
    }


    /**
     * Test getHeaderValues on empty headers returns null (key not present)
     */
    @Test
    public void getHeaderValuesOnEmptyHeaders() {
        ICAPHeaderInformation info = new ICAPHeaderInformation();
        assertNull(info.getHeaderValues("X-Test"));
    }


    /**
     * Test setting and getting headers
     */
    @Test
    public void setAndGetHeaders() {
        ICAPHeaderInformation info = new ICAPHeaderInformation();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Server", Arrays.asList("C-ICAP/0.4.4"));
        headers.put("Methods", Arrays.asList("RESPMOD", "REQMOD"));
        info.setHeaders(headers);

        assertTrue(info.containsHeader("Server"));
        assertTrue(info.containsHeader("Methods"));
        assertEquals(1, info.getHeaderValues("Server").size());
        assertEquals(2, info.getHeaderValues("Methods").size());
    }


    /**
     * Test default values
     */
    @Test
    public void defaultValues() {
        ICAPHeaderInformation info = new ICAPHeaderInformation();
        assertEquals("ICAP", info.getProtocol());
        assertEquals("", info.getVersion());
        assertEquals(0, info.getStatus());
        assertEquals("", info.getMessage());
    }


    /**
     * Test fluent setters
     */
    @Test
    public void fluentSetters() {
        ICAPHeaderInformation info = new ICAPHeaderInformation()
                .setProtocol("ICAP")
                .setVersion("1.0")
                .setStatus(200)
                .setMessage("OK");

        assertEquals("ICAP", info.getProtocol());
        assertEquals("1.0", info.getVersion());
        assertEquals(200, info.getStatus());
        assertEquals("OK", info.getMessage());
    }


    /**
     * Test trailers default to null
     */
    @Test
    public void trailersDefaultNull() {
        ICAPHeaderInformation info = new ICAPHeaderInformation();
        assertNull(info.getTrailers());
    }


    /**
     * Test setting and getting trailers
     */
    @Test
    public void setAndGetTrailers() {
        ICAPHeaderInformation info = new ICAPHeaderInformation();
        Map<String, List<String>> trailers = new LinkedHashMap<>();
        trailers.put("X-Checksum", Arrays.asList("abc123"));
        info.setTrailers(trailers);

        assertNotNull(info.getTrailers());
        assertTrue(info.getTrailers().containsKey("X-Checksum"));
        assertEquals("abc123", info.getTrailers().get("X-Checksum").get(0));
    }


    /**
     * Test trailers included in equals
     */
    @Test
    public void trailersInEquals() {
        Map<String, List<String>> trailers = new LinkedHashMap<>();
        trailers.put("X-Trail", Arrays.asList("val"));

        ICAPHeaderInformation a = new ICAPHeaderInformation().setStatus(200);
        a.setTrailers(trailers);

        ICAPHeaderInformation b = new ICAPHeaderInformation().setStatus(200);
        b.setTrailers(trailers);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    /**
     * Test trailers in toString
     */
    @Test
    public void trailersInToString() {
        ICAPHeaderInformation info = new ICAPHeaderInformation();
        Map<String, List<String>> trailers = new LinkedHashMap<>();
        trailers.put("X-Trail", Arrays.asList("val"));
        info.setTrailers(trailers);

        assertTrue(info.toString().contains("trailers="));
    }


    /**
     * Test equals and hashCode
     */
    @Test
    public void equalsAndHashCode() {
        ICAPHeaderInformation a = new ICAPHeaderInformation().setStatus(200).setMessage("OK");
        ICAPHeaderInformation b = new ICAPHeaderInformation().setStatus(200).setMessage("OK");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    /**
     * Test not equals
     */
    @Test
    public void notEquals() {
        ICAPHeaderInformation a = new ICAPHeaderInformation().setStatus(200);
        ICAPHeaderInformation b = new ICAPHeaderInformation().setStatus(204);
        assertFalse(a.equals(b));
    }


    /**
     * Test toString
     */
    @Test
    public void testToString() {
        ICAPHeaderInformation info = new ICAPHeaderInformation().setStatus(200).setMessage("OK");
        assertNotNull(info.toString());
        assertTrue(info.toString().contains("200"));
    }
}
