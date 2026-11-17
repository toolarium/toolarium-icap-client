/*
 * ICAPHeaderMapTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPHeaderMap}.
 *
 * @author patrick
 */
public class ICAPHeaderMapTest {

    /**
     * Header names are looked up case-insensitive.
     */
    @Test
    public void lookupIsCaseInsensitive() {
        ICAPHeaderMap map = new ICAPHeaderMap();
        map.put("x-virus-id", Arrays.asList("EICAR"));

        assertTrue(map.containsKey("X-Virus-ID"));
        assertEquals("EICAR", map.get("X-VIRUS-ID").get(0));
        assertEquals("EICAR", map.getOrDefault("X-Virus-Id", null).get(0));
    }


    /**
     * The insertion order and the first spelling of a key are kept.
     */
    @Test
    public void insertionOrderAndFirstSpellingAreKept() {
        ICAPHeaderMap map = new ICAPHeaderMap();
        map.put("Server", Arrays.asList("a"));
        map.put("Connection", Arrays.asList("close"));
        map.put("SERVER", Arrays.asList("b"));

        assertEquals("[Server, Connection]", "" + map.keySet());
        assertEquals("b", map.get("server").get(0));
    }


    /**
     * Remove works case-insensitive and a removed key can be added again.
     */
    @Test
    public void removeIsCaseInsensitive() {
        ICAPHeaderMap map = new ICAPHeaderMap();
        map.put("Encapsulated", Arrays.asList("null-body=0"));
        map.remove("ENCAPSULATED");

        assertFalse(map.containsKey("Encapsulated"));
        assertNull(map.get("encapsulated"));

        map.put("encapsulated", Arrays.asList("res-body=0"));
        assertTrue(map.containsKey("Encapsulated"));
        assertEquals("[encapsulated]", "" + map.keySet());
    }


    /**
     * The header information converts a plain map into a case-insensitive map.
     */
    @Test
    public void headerInformationIsCaseInsensitive() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("x-infection-found", Arrays.asList("Type=0"));

        ICAPHeaderInformation info = new ICAPHeaderInformation().setHeaders(headers);
        assertTrue(info.containsHeader(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND));
        assertEquals("Type=0", info.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND).get(0));
    }


    /**
     * Two maps with the same content are equal.
     */
    @Test
    public void equalsByContent() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Server", Arrays.asList("a"));

        assertEquals(headers, new ICAPHeaderMap(headers));
        assertEquals(new ICAPHeaderMap(headers), headers);
    }
}
