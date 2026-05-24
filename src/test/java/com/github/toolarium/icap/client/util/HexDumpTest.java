/*
 * HexDumpTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * Test the {@link HexDump}.
 *
 * @author patrick
 */
public class HexDumpTest {

    /**
     * Test hex dump of simple ASCII string
     */
    @Test
    public void hexDumpSimpleString() {
        String result = HexDump.getInstance().hexDump("AB");
        assertNotNull(result);
        assertTrue(result.contains("41"));
        assertTrue(result.contains("42"));
    }


    /**
     * Test hex dump of empty input
     */
    @Test
    public void hexDumpEmpty() {
        String result = HexDump.getInstance().hexDump(new byte[0]);
        assertEquals("", result);
    }


    /**
     * Test hex dump of single byte
     */
    @Test
    public void hexDumpSingleByte() {
        String result = HexDump.getInstance().hexDump(new byte[]{0x41});
        assertNotNull(result);
        assertTrue(result.contains("41"));
        assertTrue(result.contains("|A"));
    }


    /**
     * Test toString with byte array
     */
    @Test
    public void toStringBytes() {
        String result = HexDump.getInstance().toString(new byte[]{(byte) 0xCA, (byte) 0xFE}, 0, 2);
        assertEquals("CA:FE", result);
    }


    /**
     * Test toString with null
     */
    @Test
    public void toStringNull() {
        assertEquals("(null)", HexDump.getInstance().toString(null, 0, 0));
    }


    /**
     * Test hex dump with non-printable characters
     */
    @Test
    public void hexDumpNonPrintable() {
        String result = HexDump.getInstance().hexDump(new byte[]{0x01, 0x02, 0x03});
        assertNotNull(result);
        // non-printable characters should be replaced with '.'
        assertTrue(result.contains("..."));
    }
}
