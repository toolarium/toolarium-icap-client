/*
 * ICAPParserTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPParser}.
 *
 * @author patrick
 */
public class ICAPParserTest {

    /**
     * Test parsing a valid ICAP status line
     */
    @Test
    public void parseICAPResponseStatus200() {
        ICAPHeaderInformation info = ICAPParser.getInstance().parseICAPHeaderInformation("ICAP/1.0 200 OK");
        assertEquals("ICAP", info.getProtocol());
        assertEquals("1.0", info.getVersion());
        assertEquals(200, info.getStatus());
        assertEquals("OK", info.getMessage());
    }


    /**
     * Test parsing a 204 Unmodified status line
     */
    @Test
    public void parseICAPResponseStatus204() {
        ICAPHeaderInformation info = ICAPParser.getInstance().parseICAPHeaderInformation("ICAP/1.0 204 Unmodified");
        assertEquals("ICAP", info.getProtocol());
        assertEquals("1.0", info.getVersion());
        assertEquals(204, info.getStatus());
        assertEquals("Unmodified", info.getMessage());
    }


    /**
     * Test parsing a 100 Continue status line
     */
    @Test
    public void parseICAPResponseStatus100() {
        ICAPHeaderInformation info = ICAPParser.getInstance().parseICAPHeaderInformation("ICAP/1.0 100 Continue");
        assertEquals(100, info.getStatus());
        assertEquals("Continue", info.getMessage());
    }


    /**
     * Test parsing null input
     */
    @Test
    public void parseICAPResponseStatusNull() {
        ICAPHeaderInformation info = ICAPParser.getInstance().parseICAPHeaderInformation(null);
        assertEquals(0, info.getStatus());
    }


    /**
     * Test parsing empty input
     */
    @Test
    public void parseICAPResponseStatusEmpty() {
        ICAPHeaderInformation info = ICAPParser.getInstance().parseICAPHeaderInformation("");
        assertEquals(0, info.getStatus());
    }


    /**
     * Test parsing invalid input
     */
    @Test
    public void parseICAPResponseStatusInvalid() {
        ICAPHeaderInformation info = ICAPParser.getInstance().parseICAPHeaderInformation("GARBAGE");
        assertEquals(0, info.getStatus());
    }


    /**
     * Test parsing header with comma-separated values
     */
    @Test
    public void parseHeaderCommaSeparated() {
        List<String> lines = new ArrayList<>();
        lines.add("Methods: RESPMOD, REQMOD");
        lines.add("Server: C-ICAP/0.4.4");

        Map<String, List<String>> headers = ICAPParser.getInstance().parseHeader(lines);
        assertNotNull(headers);
        assertEquals(2, headers.get("Methods").size());
        assertEquals("RESPMOD", headers.get("Methods").get(0));
        assertEquals("REQMOD", headers.get("Methods").get(1));
        assertEquals(1, headers.get("Server").size());
        assertEquals("C-ICAP/0.4.4", headers.get("Server").get(0));
    }


    /**
     * Test parsing X-Infection-Found header with semicolon-separated values
     */
    @Test
    public void parseHeaderInfectionFound() {
        List<String> lines = new ArrayList<>();
        lines.add(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND + ": Type=0; Resolution=2; Threat=Eicar-Signature;");

        Map<String, List<String>> headers = ICAPParser.getInstance().parseHeader(lines);
        assertNotNull(headers);
        assertTrue(headers.containsKey(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND));
        List<String> values = headers.get(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);
        assertEquals(3, values.size());
        assertEquals("Type=0", values.get(0));
        assertEquals("Resolution=2", values.get(1));
        assertEquals("Threat=Eicar-Signature", values.get(2));
    }


    /**
     * Test parsing Date header is not split on commas
     */
    @Test
    public void parseHeaderDateNotSplit() {
        List<String> lines = new ArrayList<>();
        lines.add("Date: Mon, 01 Jan 2024 00:00:00 GMT");

        Map<String, List<String>> headers = ICAPParser.getInstance().parseHeader(lines);
        assertEquals(1, headers.get("Date").size());
        assertEquals("Mon, 01 Jan 2024 00:00:00 GMT", headers.get("Date").get(0));
    }


    /**
     * Test parsing X-Violations-Found header with newline-separated values
     */
    @Test
    public void parseHeaderViolationsFound() {
        List<String> lines = new ArrayList<>();
        lines.add(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND + ": 1\n-\nEicar-Signature\n0\n0");

        Map<String, List<String>> headers = ICAPParser.getInstance().parseHeader(lines);
        assertTrue(headers.containsKey(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND));
        List<String> values = headers.get(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
        assertEquals(5, values.size());
        assertEquals("1", values.get(0));
        assertEquals("-", values.get(1));
        assertEquals("Eicar-Signature", values.get(2));
    }


    /**
     * Test parsing empty header list
     */
    @Test
    public void parseHeaderEmpty() {
        Map<String, List<String>> headers = ICAPParser.getInstance().parseHeader(new ArrayList<>());
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }
}
