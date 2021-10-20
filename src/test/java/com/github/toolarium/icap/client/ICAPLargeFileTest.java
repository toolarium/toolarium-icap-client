/*
 * ICAPLargeFileTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import com.github.toolarium.icap.client.util.RandomGenerator;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;


/**
 * Test scan files
 * 
 * @author patrick
 */
public class ICAPLargeFileTest extends AbstractICAPClientTest {

    
    /**
     * Test pdf file 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testPDFFileRequest() throws IOException, ContentBlockedException {
        setAllow204(false);
        assertAllow204Unmodified(validateResource(ICAPMode.REQMOD, SRC_TEST_RESOURCES + "FileNeedsToBeSanitized.pdf "));
        setAllow204(true);
        assertAllow204Unmodified(validateResource(ICAPMode.REQMOD, SRC_TEST_RESOURCES + "FileNeedsToBeSanitized.pdf"));
        setAllow204(null);
        assertAllow204Unmodified(validateResource(ICAPMode.REQMOD, SRC_TEST_RESOURCES + "FileNeedsToBeSanitized.pdf"));
    }    

    
    /**
     * Test pdf file 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testPDFFileResponse() throws IOException, ContentBlockedException {
        setAllow204(false);
        assertAllow204Unmodified(validateResource(ICAPMode.RESPMOD, SRC_TEST_RESOURCES + "FileNeedsToBeSanitized.pdf"));
        setAllow204(true);
        assertAllow204Unmodified(validateResource(ICAPMode.RESPMOD, SRC_TEST_RESOURCES + "FileNeedsToBeSanitized.pdf"));
        setAllow204(null);
        assertAllow204Unmodified(validateResource(ICAPMode.RESPMOD, SRC_TEST_RESOURCES + "FileNeedsToBeSanitized.pdf"));
    }    

    
    /**
     * Test pdf file 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testPDFRequest2() throws IOException, ContentBlockedException {
        assertAllow204Unmodified(validateResource(ICAPMode.REQMOD, SRC_TEST_RESOURCES + "FileNeedsToBeSanitized2.pdf"));
    }    

    
    /**
     * Test pdf file 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testPDFFileResponse2() throws IOException, ContentBlockedException {
        assertAllow204Unmodified(validateResource(ICAPMode.RESPMOD, SRC_TEST_RESOURCES + "FileNeedsToBeSanitized2.pdf"));
    }    

    
    /**
     * Test pdf file 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     */
    @Test
    public void testPDFFileResponse3() throws IOException, ContentBlockedException {
        assertAllow204Unmodified(validateResource(ICAPMode.RESPMOD, SRC_TEST_RESOURCES + "FileNeedsToBeSanitized-Virus.pdf"));
    }    

    

    /**
     * Test large text file and allow 204 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     * @throws NoSuchAlgorithmException In case of not supported algorithm 
     */
    @Test
    public void testLargeTextFileAllow204() throws IOException, ContentBlockedException, NoSuchAlgorithmException {
        File file = new RandomGenerator().createRandomFile("build/large-scan-file.txt", 3 * 1024 * 1024, false, false);   
        setAllow204(true);

        assertUnmodifiedFile(validateResource(ICAPMode.REQMOD, "File", file.getName(), new FileInputStream(file), file.length()));
    }    


    /**
     * Test large text file and disabled allow 204 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     * @throws NoSuchAlgorithmException In case of not supported algorithm 
     */
    @Test
    public void testLargeTextFile() throws IOException, ContentBlockedException, NoSuchAlgorithmException {
        File file = new RandomGenerator().createRandomFile("build/large-scan-file.txt", 3 * 1024 * 1024, false, false);   
        setAllow204(false);

        assertUnmodifiedFile(validateResource(ICAPMode.REQMOD, "File", file.getName(), new FileInputStream(file), file.length()));
    }    

    
    /**
     * Test large binary file and allow 204 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     * @throws NoSuchAlgorithmException In case of not supported algorithm 
     */
    @Test
    public void testLargeBinaryFileAllow204() throws IOException, ContentBlockedException, NoSuchAlgorithmException {
        File file = new RandomGenerator().createRandomFile("build/large-scan-file.bin", 3 * 1024 * 1024, false, true);   
        setAllow204(false);
        assertUnmodifiedFile(validateResource(ICAPMode.REQMOD, "File", file.getName(), new FileInputStream(file), file.length()));
    }    


    /**
     * Test large binary file and disabled allow 204 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     * @throws NoSuchAlgorithmException In case of not supported algorithm 
     */
    @Test
    public void testLargeBinaryFile() throws IOException, ContentBlockedException, NoSuchAlgorithmException {
        File file = new RandomGenerator().createRandomFile("build/large-scan-file.bin", 3 * 1024 * 1024, false, true);   
        setAllow204(false);

        assertUnmodifiedFile(validateResource(ICAPMode.REQMOD, "File", file.getName(), new FileInputStream(file), file.length()));
    }    


    /**
     * Test large binary file and disabled allow 204 
     *
     * @throws IOException In case of an I/O error
     * @throws ContentBlockedException In case the content is blocked
     * @throws NoSuchAlgorithmException In case of not supported algorithm 
     */
    @Test
    public void testLargeBinaryWithVirusFile() throws IOException, ContentBlockedException, NoSuchAlgorithmException {
        File file = new RandomGenerator().createRandomFile("build/large-scan-file-virus.bin", 3 * 1024 * 1024, true, false);
        try (FileOutputStream outputstream = new FileOutputStream(file, true)) {
            outputstream.write((byte)'\r');
            outputstream.write((byte)'\n');
            outputstream.write(ICAPTestVirusConstants.REQUEST_BODY_VIRUS.getBytes());
        }
        
        setAllow204(false);
        assertUnmodifiedFile(validateResource(ICAPMode.REQMOD, "File", file.getName(), new FileInputStream(file), file.length()));
    }    
}
