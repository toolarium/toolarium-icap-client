/*
 * RandomGenerator.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Defines a random generator
 * 
 * @author patrick
 */
public final class RandomGenerator {
    private SecureRandom random;
    

    /**
     * Constructor
     */
    public RandomGenerator() {
        random = new SecureRandom();
    }


    /**
     * Create a random file
     *
     * @param filename the filename
     * @param size the file size
     * @param overwrite true to overwrite
     * @param binaryContent true to create binary content
     * @return the file
     * @throws IOException In case of an I/O error
     */
    public File createRandomFile(String filename, int size, boolean overwrite, boolean binaryContent) throws IOException {
        File file = new File(filename);
        if (file.exists() && file.length() == size && !overwrite) {
            return file;
        }

        final int blockSize = 1024;
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(file))) {
            for (int i = 0; size > i;) {
                byte[] bytes = new byte[blockSize];
                random.nextBytes(bytes);

                int len = blockSize;
                if (size < (i + blockSize)) {
                    len = size - i;
                }

                if (binaryContent) {
                    stream.write(bytes, 0, len);
                } else {                   
                    stream.write(new String(Base64.getEncoder().encode(bytes)).getBytes(), 0, len);
                }
                
                i += len;
            }
        }
        
        return file;
    }
    
    
    /** 
     * Generate a random lowercase letter
     * @return  a random lowercase letter
     */
    public char getRandomLowerCaseLetter() {
        return getRandomCharacter('a', 'z');
    }

    
    /** 
     * Generate a random uppercase letter
     * @return  a random uppercase letter
     */
    public char getRandomUpperCaseLetter() {
        return getRandomCharacter('A', 'Z');
    }

    
    /** 
     * Generate a random digit character
     * @return  a random digit character
     */
    public char getRandomDigitCharacter() {
        return getRandomCharacter('0', '9');
    }

    
    /** 
     * Generate a random character
     * @return  a random character
     */
    public char getRandomCharacter() {
        return getRandomCharacter('\u0000', '\uFFFF');
    }

    
    /** 
     * Generate a random character between ch1 and ch2
     *  
     * @param ch1 the character 
     * @param ch2 the character
     * @return the generated character 
     */
    public char getRandomCharacter(char ch1, char ch2) {
        return (char) (ch1 + random.nextDouble() * (ch2 - ch1 + 1));
    }

    
    /** 
     * Generate a random byte
     * @return  a random byte
     */
    public byte getRandomByte() {
        return (byte)random.nextInt();
    }
}
