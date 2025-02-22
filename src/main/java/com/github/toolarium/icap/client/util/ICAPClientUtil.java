/*
 * ICAPClientUtil.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;


/**
 * Defines the ICAP client utitlity
 *
 * @author Patrick Meier
 */
public final class ICAPClientUtil {
    /** INTERNAL COPY BUFFER */
    public static final int INTERNAL_BUFFER_SIZE = 1024;


    /**
     * Private class, the only instance of the singelton which will be created by accessing the holder class.
     *
     * @author Patrick Meier
     */
    private static class HOLDER {
        static final ICAPClientUtil INSTANCE = new ICAPClientUtil();
    }


    /**
     * Constructor
     */
    private ICAPClientUtil() {
        // NOP
    }


    /**
     * Get the instance
     *
     * @return the instance
     */
    public static ICAPClientUtil getInstance() {
        return HOLDER.INSTANCE;
    }


    /**
     * Read the file content
     *
     * @param file the file
     * @return the content
     * @throws IOException In case of an I/O error
     */
    public byte[] readFile(File file) throws IOException {
        return readResourceAndClose(new FileInputStream(file));
    }


    /**
     * Read the resource content
     *
     * @param resource the resource name
     * @return the content
     * @throws IOException In case of an I/O error
     */
    public byte[] readURL(URL resource) throws IOException {
        return readResourceAndClose(resource.openStream());
    }


    /**
     * Read from stream
     *
     * @param is the input stream
     * @return the content
     * @throws IOException In case of an I/O error
     */
    public byte[] readResourceAndClose(InputStream is) throws IOException {
        try (is) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            is.transferTo(out);
            return out.toByteArray();
        }
    }


    /**
     * Convert a long into a human readable string
     *
     * @param inputBytes the bytes
     * @return the string
     */
    public String bytesToStringSI(long inputBytes) {
        long bytes = inputBytes;
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }

        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }


    /**
     * Convert a long into a human readable string
     *
     * @param inputBytes the bytes
     * @return the string
     */
    public String bytesToString(long inputBytes) {
        long absB;
        if (inputBytes == Long.MIN_VALUE) {
            absB = Long.MAX_VALUE;
        } else {
            absB = Math.abs(inputBytes);
        }

        if (absB < 1024) {
            return inputBytes + " B";
        }

        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }

        value *= Long.signum(inputBytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }


    /**
     * Create a message digest
     *
     * @param algorithm the algorithm
     * @return the algorithm
     * @throws IOException In case the message digest could not be created
     */
    public MessageDigest createMessageDigest(String algorithm) throws IOException {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e.getMessage(), e);
        }
    }


    /**
     * Create a hash
     *
     * @param file the file
     * @return the algorithm
     * @throws IOException In case of an I/O error
     */
    public String hashFile(File file) throws IOException {
        return hashFile("SHA-256", file);
    }


    /**
     * Create a hash
     *
     * @param algorithm the algorithm
     * @param file the file
     * @return the algorithm
     * @throws IOException In case of an I/O error
     */
    public String hashFile(String algorithm, File file) throws IOException {
        MessageDigest messageDigest = createMessageDigest(algorithm);
        try (BufferedInputStream in = new BufferedInputStream((new FileInputStream(file)));
                DigestOutputStream out = new DigestOutputStream(OutputStream.nullOutputStream(), messageDigest)) {
            in.transferTo(out);
        }

        return messageDigestToString(algorithm, messageDigest);
    }


    /**
     * Convert a message digest into a string
     *
     * @param algorithm the algorithm
     * @param messageDigest the message digest
     * @return the message digest as string
     */
    public String messageDigestToString(String algorithm, MessageDigest messageDigest) {
        return String.format("{%s}%0" + (messageDigest.getDigestLength() * 2) + "x", algorithm, new BigInteger(1, messageDigest.digest()));
    }
}
