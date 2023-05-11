/*
 * ICAPClientUtilTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPClientUtil}. 
 * @author patrick
 */
public class ICAPClientUtilTest {
    private static final String DATA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz01234567890";


    /**
     * Test copy 
     *
     * @throws IOException In case of an error
     */
    @Test
    public void copyTest() throws IOException {
        assertEquals("", assertCopy(""));
        assertEquals(DATA, assertCopy(DATA));
        
        String data = new RandomGenerator().getRandomString(4 * ICAPClientUtil.INTERNAL_BUFFER_SIZE);
        assertEquals(data, assertCopy(data));
    }


    /**
     * Assert copy
     *
     * @param input the input
     * @return the result
     * @throws IOException In case of an error
     */
    protected String assertCopy(String input) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        long copiedBytes = new ByteArrayInputStream(input.getBytes("UTF-8")).transferTo(stream);
        String result = new String(stream.toByteArray(), "UTF-8");
        
        assertEquals(result.length(), copiedBytes);
        assertEquals(input, result);
        return result;
    }
}
