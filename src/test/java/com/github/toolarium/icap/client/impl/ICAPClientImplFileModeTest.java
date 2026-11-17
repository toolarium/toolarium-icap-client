/*
 * ICAPClientImplFileModeTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.toolarium.icap.client.ICAPClientFactory;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.dto.ICAPServiceInformation;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;


/**
 * Tests the FILEMOD: the content is written to a temporary file which is checked by a local virus scanner (e.g. inside a container).
 * The ICAP server is not used. A deleted, blocked or modified file is a detected threat. The virus scanner is simulated.
 *
 * @author patrick
 */
public class ICAPClientImplFileModeTest {
    private static final String CONTENT = "Hello FILEMOD";
    private static final String FILE_DELETED_MESSAGE = "The temporary file must be deleted";
    private static final String EXPECTED_DELETED = "the file was deleted by a virus scanner";


    /**
     * A clean file is valid, the whole content is written and the file is deleted afterwards.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void cleanFileIsValid() throws Exception {
        RecordingClient client = new RecordingClient(null, Scanner.NONE);
        ICAPHeaderInformation result = client.validateResource(ICAPMode.FILEMOD, requestInformation(0), content());

        assertNotNull(result);
        assertEquals(1, client.createdFiles.size());
        assertEquals(CONTENT, client.fileContents.get(0));
        assertFalse(client.createdFiles.get(0).exists(), FILE_DELETED_MESSAGE);
    }


    /**
     * The result is reported as an ICAP server would do it: 204 for a valid resource, 200 for a threat.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void statusCodes() throws Exception {
        ICAPHeaderInformation result = new RecordingClient(null, Scanner.NONE).validateResource(ICAPMode.FILEMOD, requestInformation(0), content());
        assertEquals("ICAP", result.getProtocol());
        assertEquals("1.0", result.getVersion());
        assertEquals(204, result.getStatus());

        RecordingClient client = new RecordingClient(null, Scanner.DELETE);
        ContentBlockedException e = assertThrows(ContentBlockedException.class, () -> client.validateResource(ICAPMode.FILEMOD, requestInformation(0), content()));
        assertEquals(200, e.getICAPHeaderInformation().getStatus());
    }


    /**
     * A write which the virus scanner blocked is a detected threat.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void writeBlockedByVirusScannerIsBlocked() throws Exception {
        RecordingClient client = new RecordingClient(null, Scanner.BLOCK_WRITE);
        ContentBlockedException e = assertThrows(ContentBlockedException.class, () -> client.validateResource(ICAPMode.FILEMOD, requestInformation(0), content()));
        assertTrue(e.getMessage().contains("the write of the file was blocked by a virus scanner"), e.getMessage());
        assertFalse(client.createdFiles.get(0).exists(), FILE_DELETED_MESSAGE);
    }


    /**
     * A resource which exceeds the max size is rejected with an I/O error and the temporary file is deleted.
     */
    @Test
    public void maxSizeExceededIsRejected() {
        RecordingClient client = new RecordingClient(null, Scanner.NONE);
        IOException e = assertThrows(IOException.class, () -> client.validateResource(ICAPMode.FILEMOD, requestInformation(0).fileScanMaxSize(5L), content()));
        assertTrue(e.getMessage().contains("exceeds the max size"), e.getMessage());
        assertFalse(client.createdFiles.get(0).exists(), FILE_DELETED_MESSAGE);
    }


    /**
     * A read error of the resource is an I/O error and no threat.
     */
    @Test
    public void readErrorIsNoThreat() {
        java.io.InputStream failingStream = new java.io.InputStream() {
            /**
             * @see java.io.InputStream#read()
             */
            @Override
            public int read() throws IOException {
                throw new IOException("read error");
            }
        };

        RecordingClient client = new RecordingClient(null, Scanner.NONE);
        IOException e = assertThrows(IOException.class, () -> client.validateResource(ICAPMode.FILEMOD, requestInformation(0), new ICAPResource("test.txt", failingStream, 10)));
        assertTrue(e.getMessage().contains("read error"), e.getMessage());
        assertFalse(client.createdFiles.get(0).exists(), FILE_DELETED_MESSAGE);
    }


    /**
     * The temporary file keeps the extension of the resource name.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void temporaryFileKeepsExtension() throws Exception {
        assertTrue(createdFileName("invoice.docx").endsWith(".docx"));
        assertTrue(createdFileName("archive.tar.gz").endsWith(".gz"));
        assertTrue(createdFileName("noextension").endsWith(".tmp"));
        assertTrue(createdFileName("bad.ex$e").endsWith(".exe"));
        assertTrue(createdFileName("dot.").endsWith(".tmp"));
    }


    /**
     * A resource with length 0 is not changed by the client, it is only marked as consumed.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void unknownLengthResourceIsNotChanged() throws Exception {
        ByteArrayInputStream stream = new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8));
        ICAPResource resource = new ICAPResource("test.txt", stream, 0);
        RecordingClient client = new RecordingClient(null, Scanner.NONE);
        client.validateResource(ICAPMode.FILEMOD, requestInformation(0), resource);

        assertTrue(resource.getResourceBody() == stream, "The stream of the resource must not be replaced");
        assertTrue(resource.isConsumed());
        assertEquals(CONTENT, client.fileContents.get(0));
    }


    /**
     * FILEMOD does not use the ICAP server, also if an ICAP url is defined.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void icapServerIsNotUsed() throws Exception {
        // port 1: nothing listens there, a connection would fail
        RecordingClient client = new RecordingClient(new ICAPServiceInformation("localhost", 1, false, "srv", 3600), Scanner.NONE);
        assertNotNull(client.validateResource(ICAPMode.FILEMOD, requestInformation(0), content()));
        assertEquals(CONTENT, client.fileContents.get(0));
    }


    /**
     * The whole stream is written, also if the declared length is smaller.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void wholeStreamIsWritten() throws Exception {
        RecordingClient client = new RecordingClient(null, Scanner.NONE);
        byte[] data = CONTENT.getBytes(StandardCharsets.UTF_8);
        client.validateResource(ICAPMode.FILEMOD, requestInformation(0), new ICAPResource("test.txt", new ByteArrayInputStream(data), 3));
        assertEquals(CONTENT, client.fileContents.get(0));
    }


    /**
     * A file which the virus scanner deleted immediately is a detected threat.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void fileDeletedByVirusScannerIsBlocked() throws Exception {
        RecordingClient client = new RecordingClient(null, Scanner.DELETE);
        ContentBlockedException e = assertThrows(ContentBlockedException.class, () -> client.validateResource(ICAPMode.FILEMOD, requestInformation(0), content()));
        assertTrue(e.getMessage().contains(EXPECTED_DELETED), e.getMessage());
        assertFalse(client.createdFiles.get(0).exists(), FILE_DELETED_MESSAGE);
    }


    /**
     * A file which the virus scanner deleted during the wait time is a detected threat.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void fileDeletedWithDelayIsBlocked() throws Exception {
        RecordingClient client = new RecordingClient(null, Scanner.DELETE_DELAYED);
        ContentBlockedException e = assertThrows(ContentBlockedException.class, () -> client.validateResource(ICAPMode.FILEMOD, requestInformation(2000), content()));
        assertTrue(e.getMessage().contains(EXPECTED_DELETED), e.getMessage());
    }


    /**
     * A file which the virus scanner modified (e.g. cleaned) is a detected threat.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void fileModifiedByVirusScannerIsBlocked() throws Exception {
        RecordingClient client = new RecordingClient(null, Scanner.MODIFY);
        ContentBlockedException e = assertThrows(ContentBlockedException.class, () -> client.validateResource(ICAPMode.FILEMOD, requestInformation(0), content()));
        assertTrue(e.getMessage().contains("the file was modified by a virus scanner"), e.getMessage());
        assertFalse(client.createdFiles.get(0).exists(), FILE_DELETED_MESSAGE);
    }


    /**
     * By default the client waits 1 second for the virus scanner.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void defaultWaitTimeIsOneSecond() throws Exception {
        assertEquals(1000, new ICAPRequestInformation().getFileScanWaitTime().intValue());

        long start = System.currentTimeMillis();
        new RecordingClient(null, Scanner.NONE).validateResource(ICAPMode.FILEMOD, new ICAPRequestInformation(), content());
        assertTrue(System.currentTimeMillis() - start >= 900, "The client must wait for the virus scanner");
    }


    /**
     * The factory returns a client without ICAP server for an empty url, which supports FILEMOD.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void factoryClientWithoutIcapServer() throws Exception {
        assertNotNull(ICAPClientFactory.getInstance().getICAPClient((String) null).validateResource(ICAPMode.FILEMOD, requestInformation(0), content()));
        assertNotNull(ICAPClientFactory.getInstance().getICAPClient("").validateResource(ICAPMode.FILEMOD, requestInformation(0), content()));
        assertNotNull(ICAPClientFactory.getInstance().getICAPClient("   ").validateResource(ICAPMode.FILEMOD, requestInformation(0), content()));
    }


    /**
     * Without ICAP server other modes are rejected.
     */
    @Test
    public void otherModesWithoutIcapServerAreRejected() {
        RecordingClient client = new RecordingClient(null, Scanner.NONE);
        assertThrows(java.net.MalformedURLException.class, () -> client.validateResource(ICAPMode.REQMOD, requestInformation(0), content()));
        assertThrows(java.net.MalformedURLException.class, () -> client.validateResource(ICAPMode.RESPMOD, requestInformation(0), content()));
        assertEquals(0, client.createdFiles.size());
    }


    /**
     * Get the name of the temporary file which is created for a resource name
     *
     * @param resourceName the resource name
     * @return the name of the temporary file
     * @throws Exception In case of an error
     */
    private String createdFileName(String resourceName) throws Exception {
        RecordingClient client = new RecordingClient(null, Scanner.NONE);
        byte[] data = CONTENT.getBytes(StandardCharsets.UTF_8);
        client.validateResource(ICAPMode.FILEMOD, requestInformation(0), new ICAPResource(resourceName, new ByteArrayInputStream(data), data.length));
        return client.createdFiles.get(0).getName();
    }


    /**
     * Create the request information
     *
     * @param waitTime the wait time for the virus scanner
     * @return the request information
     */
    private ICAPRequestInformation requestInformation(int waitTime) {
        return new ICAPRequestInformation().fileScanWaitTime(waitTime);
    }


    /**
     * Create a test resource
     *
     * @return the resource
     */
    private ICAPResource content() {
        byte[] data = CONTENT.getBytes(StandardCharsets.UTF_8);
        return new ICAPResource("test.txt", new ByteArrayInputStream(data), data.length);
    }


    /**
     * The behavior of the simulated virus scanner
     *
     * @author patrick
     */
    private enum Scanner {
        NONE,
        BLOCK_WRITE,
        DELETE,
        DELETE_DELAYED,
        MODIFY
    }


    /**
     * Client which simulates a virus scanner and records the temporary files and their content before they are deleted.
     *
     * @author patrick
     */
    private static final class RecordingClient extends ICAPClientImpl {
        private final List<File> createdFiles = new ArrayList<>();
        private final List<String> fileContents = new ArrayList<>();
        private final Scanner scanner;


        /**
         * Constructor for RecordingClient
         *
         * @param serviceInformation the service information or null
         * @param scanner the behavior of the simulated virus scanner
         */
        RecordingClient(ICAPServiceInformation serviceInformation, Scanner scanner) {
            super(new ICAPConnectionManagerImpl(), serviceInformation, null);
            this.scanner = scanner;
        }


        /**
         * @see com.github.toolarium.icap.client.impl.ICAPClientImpl#createResponseFile(java.lang.String)
         */
        @Override
        protected File createResponseFile(String resourceName) throws IOException {
            File file = super.createResponseFile(resourceName);
            if (Scanner.BLOCK_WRITE.equals(scanner)) {
                // a directory can not be written as file: the write fails like a write blocked by the virus scanner
                Files.delete(file.toPath());
                file = Files.createDirectory(file.toPath()).toFile();
            } else if (Scanner.DELETE.equals(scanner) || Scanner.MODIFY.equals(scanner)) {
                file = new ScannedFile(file.getPath(), scanner);
            } else if (Scanner.DELETE_DELAYED.equals(scanner)) {
                final File delayedFile = file;
                Thread thread = new Thread(() -> {
                    try {
                        Thread.sleep(300);
                        Files.deleteIfExists(delayedFile.toPath());
                    } catch (Exception e) {
                        // NOP
                    }
                });
                thread.setDaemon(true);
                thread.start();
            }

            createdFiles.add(file);
            return file;
        }


        /**
         * @see com.github.toolarium.icap.client.impl.ICAPClientImpl#deleteResponseFile(java.lang.String, java.io.File, boolean)
         */
        @Override
        protected void deleteResponseFile(String requestIdentifier, File resourceResponse, boolean threatFound) {
            try {
                fileContents.add(new String(Files.readAllBytes(resourceResponse.toPath()), StandardCharsets.UTF_8));
            } catch (IOException e) {
                fileContents.add(null);
            }
            super.deleteResponseFile(requestIdentifier, resourceResponse, threatFound);
        }
    }


    /**
     * File which simulates a virus scanner: it acts when the client checks the written file.
     *
     * @author patrick
     */
    private static final class ScannedFile extends File {
        private static final long serialVersionUID = 1L;
        private final transient Scanner scanner;
        private boolean scanned;


        /**
         * Constructor for ScannedFile
         *
         * @param path the path
         * @param scanner the behavior of the simulated virus scanner
         */
        ScannedFile(String path, Scanner scanner) {
            super(path);
            this.scanner = scanner;
        }


        /**
         * @see java.io.File#exists()
         */
        @Override
        public boolean exists() {
            if (!scanned) {
                scanned = true;
                try {
                    if (Scanner.DELETE.equals(scanner)) {
                        Files.deleteIfExists(toPath());
                    } else if (Scanner.MODIFY.equals(scanner)) {
                        Files.write(toPath(), "cleaned".getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    // NOP
                }
            }
            return super.exists();
        }
    }
}
