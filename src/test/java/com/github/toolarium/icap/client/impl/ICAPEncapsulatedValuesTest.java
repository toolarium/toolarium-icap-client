/*
 * ICAPEncapsulatedValuesTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPEncapsulatedValues}.
 *
 * @author patrick
 */
public class ICAPEncapsulatedValuesTest {

    /**
     * Test parsing req-hdr and req-body
     */
    @Test
    public void parseReqHdrAndBody() {
        ICAPEncapsulatedValues values = new ICAPEncapsulatedValues("req-hdr=0, req-body=182");
        assertEquals(0, values.getOffset());
        assertEquals(182, values.getLength());
    }


    /**
     * Test parsing res-hdr and res-body
     */
    @Test
    public void parseResHdrAndBody() {
        ICAPEncapsulatedValues values = new ICAPEncapsulatedValues("res-hdr=0, res-body=108");
        assertEquals(0, values.getOffset());
        assertEquals(108, values.getLength());
    }


    /**
     * Test parsing body only (no header)
     */
    @Test
    public void parseBodyOnly() {
        ICAPEncapsulatedValues values = new ICAPEncapsulatedValues("req-body=256");
        assertEquals(0, values.getOffset());
        assertEquals(256, values.getLength());
    }


    /**
     * Test parsing header only (no body)
     */
    @Test
    public void parseHdrOnly() {
        ICAPEncapsulatedValues values = new ICAPEncapsulatedValues("req-hdr=42");
        assertEquals(42, values.getOffset());
        assertEquals(0, values.getLength());
    }


    /**
     * Test parsing null expression
     */
    @Test
    public void parseNullExpression() {
        ICAPEncapsulatedValues values = new ICAPEncapsulatedValues((String) null);
        assertEquals(0, values.getOffset());
        assertEquals(0, values.getLength());
    }


    /**
     * Test parsing empty expression
     */
    @Test
    public void parseEmptyExpression() {
        ICAPEncapsulatedValues values = new ICAPEncapsulatedValues("");
        assertEquals(0, values.getOffset());
        assertEquals(0, values.getLength());
    }


    /**
     * Test toString
     */
    @Test
    public void testToString() {
        ICAPEncapsulatedValues values = new ICAPEncapsulatedValues("req-hdr=0, req-body=182");
        assertEquals("ICAPEncapsulatedValues [offset=0, length=182]", values.toString());
    }
}
