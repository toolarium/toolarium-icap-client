/*
 * ICAPRemoteServiceConfigurationImplTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.dto.ICAPMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPRemoteServiceConfigurationImpl}.
 *
 * @author patrick
 */
public class ICAPRemoteServiceConfigurationImplTest {

    /**
     * Test default constructor values.
     */
    @Test
    public void defaultValues() {
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl();

        assertEquals(4096, config.getServerPreviewSize());
        assertFalse(config.isServerAllow204());
        assertNull(config.getOptionMethods());
        assertNull(config.getTimestamp());
        assertEquals(-1, config.getOptionsTTL());
        assertEquals(-1, config.getMaxConnections());
        assertNull(config.getServiceId());
        assertNull(config.getTransferPreview());
        assertNull(config.getTransferIgnore());
        assertNull(config.getTransferComplete());
    }


    /**
     * Test five-parameter constructor.
     */
    @Test
    public void fiveParamConstructor() {
        Instant now = Instant.now();
        ICAPMode[] methods = {ICAPMode.REQMOD, ICAPMode.RESPMOD};
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl(
                now, methods, 2048, true, new LinkedHashMap<>());

        assertEquals(2048, config.getServerPreviewSize());
        assertTrue(config.isServerAllow204());
        assertEquals(2, config.getOptionMethods().length);
        assertEquals(now, config.getTimestamp());
        assertEquals(-1, config.getOptionsTTL());
        assertEquals(-1, config.getMaxConnections());
        assertNull(config.getServiceId());
    }


    /**
     * Test Options-TTL setter.
     */
    @Test
    public void setOptionsTTL() {
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl();
        ICAPRemoteServiceConfigurationImpl result = config.setOptionsTTL(3600);

        assertEquals(3600, config.getOptionsTTL());
        assertEquals(config, result);
    }


    /**
     * Test Max-Connections setter.
     */
    @Test
    public void setMaxConnections() {
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl();
        config.setMaxConnections(10);

        assertEquals(10, config.getMaxConnections());
    }


    /**
     * Test Service-ID setter.
     */
    @Test
    public void setServiceId() {
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl();
        config.setServiceId("myservice-1");

        assertEquals("myservice-1", config.getServiceId());
    }


    /**
     * Test Transfer-Preview setter.
     */
    @Test
    public void setTransferPreview() {
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl();
        List<String> extensions = Arrays.asList("zip", "tar", "exe");
        config.setTransferPreview(extensions);

        assertNotNull(config.getTransferPreview());
        assertEquals(3, config.getTransferPreview().size());
        assertEquals("zip", config.getTransferPreview().get(0));
    }


    /**
     * Test Transfer-Ignore setter.
     */
    @Test
    public void setTransferIgnore() {
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl();
        List<String> extensions = Arrays.asList("gif", "png");
        config.setTransferIgnore(extensions);

        assertNotNull(config.getTransferIgnore());
        assertEquals(2, config.getTransferIgnore().size());
    }


    /**
     * Test Transfer-Complete setter.
     */
    @Test
    public void setTransferComplete() {
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl();
        List<String> extensions = Arrays.asList("*");
        config.setTransferComplete(extensions);

        assertNotNull(config.getTransferComplete());
        assertEquals(1, config.getTransferComplete().size());
        assertEquals("*", config.getTransferComplete().get(0));
    }


    /**
     * Test equals with new fields.
     */
    @Test
    public void equalsWithNewFields() {
        ICAPRemoteServiceConfigurationImpl a = new ICAPRemoteServiceConfigurationImpl();
        a.setOptionsTTL(300);
        a.setMaxConnections(5);
        a.setServiceId("svc1");
        a.setTransferPreview(Arrays.asList("zip"));

        ICAPRemoteServiceConfigurationImpl b = new ICAPRemoteServiceConfigurationImpl();
        b.setOptionsTTL(300);
        b.setMaxConnections(5);
        b.setServiceId("svc1");
        b.setTransferPreview(Arrays.asList("zip"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    /**
     * Test not equals with different new fields.
     */
    @Test
    public void notEqualsWithDifferentNewFields() {
        ICAPRemoteServiceConfigurationImpl a = new ICAPRemoteServiceConfigurationImpl();
        a.setOptionsTTL(300);

        ICAPRemoteServiceConfigurationImpl b = new ICAPRemoteServiceConfigurationImpl();
        b.setOptionsTTL(600);

        assertFalse(a.equals(b));
    }


    /**
     * Test toString includes new fields.
     */
    @Test
    public void toStringIncludesNewFields() {
        ICAPRemoteServiceConfigurationImpl config = new ICAPRemoteServiceConfigurationImpl();
        config.setOptionsTTL(300);
        config.setMaxConnections(10);
        config.setServiceId("svc1");

        String str = config.toString();
        assertTrue(str.contains("optionsTTL=300"));
        assertTrue(str.contains("maxConnections=10"));
        assertTrue(str.contains("serviceId=svc1"));
    }
}
