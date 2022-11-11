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
        assertEquals(DATA.substring(10, 20), assertCopy(DATA, 10, 20));
        
        String data = new RandomGenerator().getRandomString(4 * ICAPClientUtil.INTERNAL_BUFFER_SIZE);
        assertEquals(data, assertCopy(data));
        assertEquals(data.substring(10, 20), assertCopy(data, 10, 20));
       
    }


    /**
     * Assert copy
     *
     * @param input the input
     * @return the result
     * @throws IOException In case of an error
     */
    protected String assertCopy(String input) throws IOException {
        return assertCopy(input, 0, -1);
    }

    
    /**
     * Assert copy
     *
     * @param input the input
     * @param offset the offset
     * @param inputLength the length
     * @return the result
     * @throws IOException In case of an error
     */
    protected String assertCopy(String input, int offset, int inputLength) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        long copiedBytes = ICAPClientUtil.getInstance().copy(new ByteArrayInputStream(input.getBytes("UTF-8")), stream, offset, inputLength);
        String result = new String(stream.toByteArray(), "UTF-8");
        
        int length = inputLength;
        if (length < 0) {
            length = input.length();
        }

        assertEquals(result.length(), copiedBytes);
        assertEquals(input.substring(offset, length), result);
        return result;
    }
}
