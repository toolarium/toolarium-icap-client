/*
 * ICAPRequestInformationTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPRequestInformation}.
 *
 * @author patrick
 */
public class ICAPRequestInformationTest {

    /**
     * Test default constructor values
     */
    @Test
    public void defaultValues() {
        ICAPRequestInformation info = new ICAPRequestInformation();
        assertEquals(ICAPRequestInformation.USER_AGENT, info.getUserAgent());
        assertEquals(ICAPRequestInformation.API_VERSION, info.getApiVersion());
        assertNull(info.getUsername());
        assertNull(info.getRequestSource());
        assertNull(info.isAllow204());
        assertNull(info.getMaxConnectionTimeout());
        assertNull(info.getMaxReadTimeout());
        assertNull(info.getCustomHeaders());
    }


    /**
     * Test fluent setters
     */
    @Test
    public void fluentSetters() {
        ICAPRequestInformation info = new ICAPRequestInformation()
                .setUserAgent("MyAgent/1.0")
                .setApiVersion("2.0")
                .setUsername("user1")
                .setRequestSource("source1")
                .setAllow204(true)
                .maxConnectionTimeout(5000)
                .maxReadTimeout(10000);

        assertEquals("MyAgent/1.0", info.getUserAgent());
        assertEquals("2.0", info.getApiVersion());
        assertEquals("user1", info.getUsername());
        assertEquals("source1", info.getRequestSource());
        assertTrue(info.isAllow204());
        assertEquals(5000, info.getMaxConnectionTimeout());
        assertEquals(10000, info.getMaxReadTimeout());
    }


    /**
     * Test custom headers
     */
    @Test
    public void customHeaders() {
        ICAPRequestInformation info = new ICAPRequestInformation();
        assertNull(info.getCustomHeaders());

        info.addCustomHeader("X-Test", "value1");
        assertNotNull(info.getCustomHeaders());
        assertEquals(1, info.getCustomHeaders().size());
        assertEquals("value1", info.getCustomHeaders().get("X-Test"));

        info.addCustomHeader("X-Another", "value2");
        assertEquals(2, info.getCustomHeaders().size());
    }


    /**
     * Test prepareSourceRequest with all fields
     */
    @Test
    public void prepareSourceRequestAllFields() {
        ICAPRequestInformation info = new ICAPRequestInformation("user1", "source1");
        ByteArrayInputStream stream = new ByteArrayInputStream("test".getBytes());
        ICAPResource resource = new ICAPResource("file.txt", stream, 100);

        String result = info.prepareSourceRequest(resource);
        assertTrue(result.contains("username: user1"));
        assertTrue(result.contains("source: source1"));
        assertTrue(result.contains("resource: file.txt"));
        assertTrue(result.contains("length: 100"));
    }


    /**
     * Test prepareSourceRequest with no optional fields
     */
    @Test
    public void prepareSourceRequestMinimal() {
        ICAPRequestInformation info = new ICAPRequestInformation();
        ByteArrayInputStream stream = new ByteArrayInputStream("test".getBytes());
        ICAPResource resource = new ICAPResource("file.txt", stream, 100);

        String result = info.prepareSourceRequest(resource);
        assertFalse(result.contains("username:"));
        assertFalse(result.startsWith("source:"));
        assertTrue(result.contains("resource: file.txt"));
    }


    /**
     * Test prepareSourceRequest with null resource
     */
    @Test
    public void prepareSourceRequestNullResource() {
        ICAPRequestInformation info = new ICAPRequestInformation();
        String result = info.prepareSourceRequest(null);
        assertEquals("", result);
    }


    /**
     * Test authorization getter and setter
     */
    @Test
    public void authorizationGetterSetter() {
        ICAPRequestInformation info = new ICAPRequestInformation();
        assertNull(info.getAuthorization());

        ICAPRequestInformation result = info.setAuthorization("Basic dXNlcjpwYXNz");
        assertEquals("Basic dXNlcjpwYXNz", info.getAuthorization());
        assertEquals(info, result);
    }


    /**
     * Test authorization with Bearer token
     */
    @Test
    public void authorizationBearerToken() {
        ICAPRequestInformation info = new ICAPRequestInformation();
        info.setAuthorization("Bearer mytoken123");
        assertEquals("Bearer mytoken123", info.getAuthorization());
    }


    /**
     * Test authorization masked in toString
     */
    @Test
    public void authorizationMaskedInToString() {
        ICAPRequestInformation info = new ICAPRequestInformation();
        info.setAuthorization("Basic secret");

        String str = info.toString();
        assertTrue(str.contains("authorization=***"));
        assertFalse(str.contains("secret"));
    }


    /**
     * Test authorization null in toString
     */
    @Test
    public void authorizationNullInToString() {
        ICAPRequestInformation info = new ICAPRequestInformation();
        String str = info.toString();
        assertTrue(str.contains("authorization=null"));
    }


    /**
     * Test authorization included in equals
     */
    @Test
    public void authorizationInEquals() {
        ICAPRequestInformation a = new ICAPRequestInformation();
        a.setAuthorization("Basic abc");

        ICAPRequestInformation b = new ICAPRequestInformation();
        b.setAuthorization("Basic abc");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        ICAPRequestInformation c = new ICAPRequestInformation();
        c.setAuthorization("Basic xyz");
        assertFalse(a.equals(c));
    }


    /**
     * Test equals and hashCode
     */
    @Test
    public void equalsAndHashCode() {
        ICAPRequestInformation a = new ICAPRequestInformation("user", "source");
        ICAPRequestInformation b = new ICAPRequestInformation("user", "source");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    /**
     * Test not equals
     */
    @Test
    public void notEquals() {
        ICAPRequestInformation a = new ICAPRequestInformation("user1", "source");
        ICAPRequestInformation b = new ICAPRequestInformation("user2", "source");
        assertFalse(a.equals(b));
    }
}
