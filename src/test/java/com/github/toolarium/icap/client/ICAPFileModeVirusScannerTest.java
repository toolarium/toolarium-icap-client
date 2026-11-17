/*
 * ICAPFileModeVirusScannerTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Tests FILEMOD with a real local virus scanner, e.g. Windows Defender real-time protection or an on-access scanner in a container.
 * The EICAR test file is used as virus. The tests are skipped if no active virus scanner is detected.
 *
 * @author patrick
 */
public class ICAPFileModeVirusScannerTest {
    private static final Logger LOG = LoggerFactory.getLogger(ICAPFileModeVirusScannerTest.class);
    private static final int SCANNER_WAIT_TIME = 5000;
    private static final String NO_SCANNER = "No active local virus scanner detected";
    private static boolean scannerActive;


    /**
     * Detect an active local virus scanner: it must delete or block a written EICAR test file.
     *
     * @throws Exception In case of an error
     */
    @BeforeAll
    public static void detectVirusScanner() throws Exception {
        File probe = Files.createTempFile("icap-probe-", ".tmp").toFile();
        try {
            try {
                Files.write(probe.toPath(), ICAPTestVirusConstants.REQUEST_BODY_VIRUS.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                LOG.info("Local virus scanner blocked the write of the probe file: " + e.getMessage());
                scannerActive = true;
                return;
            }

            long end = System.currentTimeMillis() + SCANNER_WAIT_TIME;
            while (System.currentTimeMillis() < end) {
                if (!probe.exists()) {
                    LOG.info("Local virus scanner deleted the probe file.");
                    scannerActive = true;
                    return;
                }

                try {
                    Files.readAllBytes(probe.toPath());
                } catch (Exception e) {
                    LOG.info("Local virus scanner blocked the read of the probe file: " + e.getMessage());
                    scannerActive = true;
                    return;
                }
                Thread.sleep(100);
            }

            LOG.info(NO_SCANNER + ", the tests are skipped.");
        } finally {
            try {
                Files.deleteIfExists(probe.toPath());
            } catch (Exception e) {
                // NOP
            }
        }
    }


    /**
     * A clean file is valid.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void cleanFileIsValid() throws Exception {
        assumeTrue(scannerActive, NO_SCANNER);
        assertNotNull(ICAPClientFactory.getInstance().getICAPClient((String) null)
                .validateResource(ICAPMode.FILEMOD, requestInformation(), resource("This is a clean file.")));
    }


    /**
     * The EICAR test file is blocked by the local virus scanner.
     */
    @Test
    public void virusIsBlocked() {
        assumeTrue(scannerActive, NO_SCANNER);
        assertThrows(ContentBlockedException.class, () -> ICAPClientFactory.getInstance().getICAPClient((String) null)
                .validateResource(ICAPMode.FILEMOD, requestInformation(), resource(ICAPTestVirusConstants.REQUEST_BODY_VIRUS)));
    }


    /**
     * FILEMOD works the same way with a valid ICAP url, the ICAP server is not used.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void validIcapUrl() throws Exception {
        assumeTrue(scannerActive, NO_SCANNER);
        ICAPClient client = ICAPClientFactory.getInstance().getICAPClient("icap://localhost:1344/srv_clamav");
        assertNotNull(client.validateResource(ICAPMode.FILEMOD, requestInformation(), resource("This is a clean file.")));
        assertThrows(ContentBlockedException.class, () -> ICAPClientFactory.getInstance().getICAPClient("icap://localhost:1344/srv_clamav")
                .validateResource(ICAPMode.FILEMOD, requestInformation(), resource(ICAPTestVirusConstants.REQUEST_BODY_VIRUS)));
    }


    /**
     * An invalid ICAP url is still rejected as before: only a null, empty or blank url creates a client without ICAP server.
     *
     * @throws Exception In case of an error
     */
    @Test
    public void invalidIcapUrl() throws Exception {
        assertThrows(java.io.IOException.class, () -> ICAPClientFactory.getInstance().getICAPClient("icap://localhost:1/srv_clamav"));
        assertThrows(java.net.MalformedURLException.class, () -> ICAPClientFactory.getInstance().getICAPClient("http://localhost:1344/srv_clamav"));

        assumeTrue(scannerActive, NO_SCANNER);
        assertNotNull(ICAPClientFactory.getInstance().getICAPClient("").validateResource(ICAPMode.FILEMOD, requestInformation(), resource("This is a clean file.")));
        assertThrows(ContentBlockedException.class, () -> ICAPClientFactory.getInstance().getICAPClient("   ")
                .validateResource(ICAPMode.FILEMOD, requestInformation(), resource(ICAPTestVirusConstants.REQUEST_BODY_VIRUS)));
    }


    /**
     * Create the request information
     *
     * @return the request information
     */
    private ICAPRequestInformation requestInformation() {
        return new ICAPRequestInformation("user", "test").fileScanWaitTime(SCANNER_WAIT_TIME);
    }


    /**
     * Create a resource
     *
     * @param content the content
     * @return the resource
     */
    private ICAPResource resource(String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        return new ICAPResource("test.txt", new ByteArrayInputStream(data), data.length);
    }
}
