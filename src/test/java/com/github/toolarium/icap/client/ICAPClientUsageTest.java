/*
 * ICAPClientUsageTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import com.github.toolarium.icap.client.dto.ICAPMode;
import com.github.toolarium.icap.client.dto.ICAPRequestInformation;
import com.github.toolarium.icap.client.dto.ICAPResource;
import com.github.toolarium.icap.client.exception.ContentBlockedException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implements the usage test cases
 *
 * @author patrick
 */
public class ICAPClientUsageTest extends AbstractICAPClientTest {
    private static final String RESOURCE_COULD_NOT_BE_ACCESSED = "Resource could not be accessed: {}";
    private static final Logger LOG = LoggerFactory.getLogger(ICAPClientUsageTest.class);
    private static final String LOCALHOST = "localhost";
    private static final int ICAP_PORT = 1344;
    private static final String SERVICE_NAME = "srv_clamav";


    /**
     * The usage how to use the client library
     */
    @Test
    public void usage_RESPMOD() {
        // the user, request source and the resource
        final String username = "usera";
        final String requestSource = "filea";
        final File file = new File("build/test-file.com");

        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_CLEAN.getBytes());
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, ICAP_PORT, SERVICE_NAME)
                 .validateResource(ICAPMode.RESPMOD,
                                   new ICAPRequestInformation(username, requestSource),
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_CLEAN.length()));

            // If no exception is thrown the resource can be used and is valid.

            // log output looks like:
            // DD8DEE46 - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // 30AC31B0 - Validate resource (username: user, source: file, resource: test-file.com, length: 71)
            // 30AC31B0 - Valid resource (username: user, source: file, resource: test-file.com, length: 71, http-status: 204).

        } catch (IOException ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {

            // !!! The resource has to be blocked !!!

            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage();

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

            // The e.getContent contains the returned error information from the ICAP-Server.
            // It can be ignored as long as the resource is blocked; otherwise it gives a well-structured response.
            var ignored = e.getContent();

            fail();
        }
    }


    /**
     * The usage how to use the client library
     */
    @Test
    public void usage_REQMOD() {
        // the user, request source and the resource
        final String username = "userb";
        final String requestSource = "fileb";
        final File file = new File("build/test-file.com");

        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_CLEAN.getBytes());
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, ICAP_PORT, SERVICE_NAME)
                 .validateResource(ICAPMode.REQMOD,
                         new ICAPRequestInformation(username, requestSource),
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_CLEAN.length()));

            // If no exception is thrown the resource can be used and is valid.

            // log output looks like:
            // DD8DEE46 - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // 30AC31B0 - Validate resource (username: user, source: file, resource: test-file.com, length: 71)
            // 30AC31B0 - Valid resource (username: user, source: file, resource: test-file.com, length: 71, http-status: 204).

        } catch (IOException ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {

            // !!! The resource has to be blocked !!!

            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage();

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

            // The e.getContent contains the returned error information from the ICAP-Server.
            // It can be ignored as long as the resource is blocked; otherwise it gives a well-structured response.
            var ignored = e.getContent();

            fail();
        }
    }


    /**
     * The usage how to use the client library
     */
    @Test
    public void usageVirusFound_RESPMOD() {
        // the user, request source and the resource
        final String username = "userc";
        final String requestSource = "filec";
        final File file = new File("build/test-virus-file.com");

        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_VIRUS.getBytes());
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, ICAP_PORT, SERVICE_NAME)
                 .validateResource(ICAPMode.RESPMOD,
                                   new ICAPRequestInformation(username, requestSource),
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_VIRUS.length()));

            // If no exception is thrown the resource can be used and is valid.
            fail();

            // log output looks like:
            // E1C57BCF - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // DA054425 - Validate resource (username: user, source: file, resource: test-virus-file.com, length: 70)
            // DA054425 - Threat found in resource (username: user, source: file, resource: test-virus-file.com, length: 70, http-status: 200):
            // - X-Infection-Found: [Type=0, Resolution=2, Threat=Eicar-Signature]
            // - X-Violations-Found: [1, -, Eicar-Signature, 0, 0]
            // - X-Request-Message-Digest: [{SHA-256}8b3f191819931d1f2cef7289239b5f77c00b079847b9c2636e56854d1e5eff71]
            // - X-Response-Message-Digest: [{SHA-256}2e124ff42640aafcc7e267269dd495f35411ce469ec2a64c9af56ccd74bed32f]
            // - X-Resource-Identical-Content: [false]

        } catch (IOException ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {

            // !!! The resource has to be blocked !!!

            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage();

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

            // The e.getContent contains the returned error information from the ICAP-Server.
            // It can be ignored as long as the resource is blocked; otherwise it gives a well-structured response.
            var ignored = e.getContent();
        }
    }


    /**
     * The usage how to use the client library
     */
    @Test
    public void usageVirusFound_REQMOD() {
        // the user, request source and the resource
        final String username = "userd";
        final String requestSource = "filed";
        final File file = new File("build/test-virus-file.com");

        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_VIRUS.getBytes());
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, ICAP_PORT, SERVICE_NAME)
                 .validateResource(ICAPMode.REQMOD,
                                   new ICAPRequestInformation(username, requestSource),
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_VIRUS.length()));

            // If no exception is thrown the resource can be used and is valid.
            fail();

            // log output looks like:
            // E1C57BCF - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // DA054425 - Validate resource (username: user, source: file, resource: test-virus-file.com, length: 70)
            // DA054425 - Threat found in resource (username: user, source: file, resource: test-virus-file.com, length: 70, http-status: 200):
            // - X-Infection-Found: [Type=0, Resolution=2, Threat=Eicar-Signature]
            // - X-Violations-Found: [1, -, Eicar-Signature, 0, 0]
            // - X-Request-Message-Digest: [{SHA-256}8b3f191819931d1f2cef7289239b5f77c00b079847b9c2636e56854d1e5eff71]
            // - X-Response-Message-Digest: [{SHA-256}2e124ff42640aafcc7e267269dd495f35411ce469ec2a64c9af56ccd74bed32f]
            // - X-Resource-Identical-Content: [false]

        } catch (IOException ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {

            // !!! The resource has to be blocked !!!

            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage();

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

            // The e.getContent contains the returned error information from the ICAP-Server.
            // It can be ignored as long as the resource is blocked; otherwise it gives a well-structured response.
            var ignored = e.getContent();
        }
    }


    /**
     * The usage how to use the client library
     */
    @Test
    public void usageVirusFoundAsBase64_RESPMOD() {
        // the user, request source and the resource
        final String username = "usere";
        final String requestSource = "filee";
        final File file = new File("build/test-virus-file.com");

        try {
            byte[] eicarContent = Base64.getDecoder().decode(ICAPTestVirusConstants.REQUEST_BODY_VIRUS_BASE64);
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(eicarContent);
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, ICAP_PORT, SERVICE_NAME)
                 .validateResource(ICAPMode.RESPMOD,
                                   new ICAPRequestInformation(username, requestSource),
                                   new ICAPResource(file.getName(), resourceInputStream, eicarContent.length));

            // If no exception is thrown the resource can be used and is valid.
            fail();

            // log output looks like:
            // E1C57BCF - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // DA054425 - Validate resource (username: user, source: file, resource: test-virus-file.com, length: 70)
            // DA054425 - Threat found in resource (username: user, source: file, resource: test-virus-file.com, length: 70, http-status: 200):
            // - X-Infection-Found: [Type=0, Resolution=2, Threat=Eicar-Signature]
            // - X-Violations-Found: [1, -, Eicar-Signature, 0, 0]
            // - X-Request-Message-Digest: [{SHA-256}8b3f191819931d1f2cef7289239b5f77c00b079847b9c2636e56854d1e5eff71]
            // - X-Response-Message-Digest: [{SHA-256}2e124ff42640aafcc7e267269dd495f35411ce469ec2a64c9af56ccd74bed32f]
            // - X-Resource-Identical-Content: [false]

        } catch (IOException ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {

            // !!! The resource has to be blocked !!!

            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage();

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

            // The e.getContent contains the returned error information from the ICAP-Server.
            // It can be ignored as long as the resource is blocked; otherwise it gives a well-structured response.
            var ignored = e.getContent();
        }
    }


    /**
     * The usage how to use the client library
     */
    @Test
    public void usageVirusFoundAsBase64_REQMODE() {
        // the user, request source and the resource
        final String username = "usere";
        final String requestSource = "filee";
        final File file = new File("build/test-virus-file.com");

        try {
            byte[] eicarContent = Base64.getDecoder().decode(ICAPTestVirusConstants.REQUEST_BODY_VIRUS_BASE64);
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(eicarContent);
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, ICAP_PORT, SERVICE_NAME)
                 .validateResource(ICAPMode.REQMOD,
                                   new ICAPRequestInformation(username, requestSource),
                                   new ICAPResource(file.getName(), resourceInputStream, eicarContent.length));

            // If no exception is thrown the resource can be used and is valid.
            fail();

            // log output looks like:
            // E1C57BCF - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // DA054425 - Validate resource (username: user, source: file, resource: test-virus-file.com, length: 70)
            // DA054425 - Threat found in resource (username: user, source: file, resource: test-virus-file.com, length: 70, http-status: 200):
            // - X-Infection-Found: [Type=0, Resolution=2, Threat=Eicar-Signature]
            // - X-Violations-Found: [1, -, Eicar-Signature, 0, 0]
            // - X-Request-Message-Digest: [{SHA-256}8b3f191819931d1f2cef7289239b5f77c00b079847b9c2636e56854d1e5eff71]
            // - X-Response-Message-Digest: [{SHA-256}2e124ff42640aafcc7e267269dd495f35411ce469ec2a64c9af56ccd74bed32f]
            // - X-Resource-Identical-Content: [false]

        } catch (IOException ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {

            // !!! The resource has to be blocked !!!

            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage();

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

            // The e.getContent contains the returned error information from the ICAP-Server.
            // It can be ignored as long as the resource is blocked; otherwise it gives a well-structured response.
            var ignored = e.getContent();
        }
    }


    /**
     * The usage how to use the client library
     */
    @Test
    public void usageWithURL_RESPMOD() {
        // the ICAP-Server information
        final String icapUrl = String.format("icap://%s:%d/%s", LOCALHOST, ICAP_PORT, SERVICE_NAME);

        // the user, request source and the resource
        final String username = "userf";
        final String requestSource = "filef";
        final File file = new File("build/test-file.com");

        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_CLEAN.getBytes());
            ICAPClientFactory.getInstance().getICAPClient(icapUrl)
                 .validateResource(ICAPMode.RESPMOD,
                                   new ICAPRequestInformation(username, requestSource),
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_CLEAN.length()));

            // If no exception is thrown the resource can be used and is valid.

            // log output looks like:
            // DD8DEE46 - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // 30AC31B0 - Validate resource (username: user, source: file, resource: test-file.com, length: 71)
            // 30AC31B0 - Valid resource (username: user, source: file, resource: test-file.com, length: 71, http-status: 204).

        } catch (IOException ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {

            // !!! The resource has to be blocked !!!

            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage();

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

            // The e.getContent contains the returned error information from the ICAP-Server.
            // It can be ignored as long as the resource is blocked; otherwise it gives a well-structured response.
            var ignored = e.getContent();

            fail();
        }
    }


    /**
     * The usage how to use the client library
     */
    @Test
    public void usageWithURL_REQMOD() {
        // the ICAP-Server information
        final String icapUrl = "icap://localhost:1344/srv_clamav";

        // the user, request source and the resource
        final String username = "userg";
        final String requestSource = "fileg";
        final File file = new File("build/test-file.com");

        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_CLEAN.getBytes());
            ICAPClientFactory.getInstance().getICAPClient(icapUrl)
                 .validateResource(ICAPMode.REQMOD,
                                   new ICAPRequestInformation(username, requestSource),
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_CLEAN.length()));

            // If no exception is thrown the resource can be used and is valid.

            // log output looks like:
            // DD8DEE46 - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // 30AC31B0 - Validate resource (username: user, source: file, resource: test-file.com, length: 71)
            // 30AC31B0 - Valid resource (username: user, source: file, resource: test-file.com, length: 71, http-status: 204).

        } catch (IOException ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {

            // !!! The resource has to be blocked !!!

            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage();

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

            // The e.getContent contains the returned error information from the ICAP-Server.
            // It can be ignored as long as the resource is blocked; otherwise it gives a well-structured response.
            var ignored = e.getContent();

            fail();
        }
    }


    /**
     * The usage how to use the client library
     */
    @Test
    public void usage_SupportCompareVerifyIdenticalContent() {
        // the user, request source and the resource
        final String username = "usera";
        final String requestSource = "filea";
        final File file = new File("build/test-file.com");

        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(ICAPTestVirusConstants.REQUEST_BODY_CLEAN.getBytes());
            ICAPHeaderInformation icapHeaderInformation = ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, ICAP_PORT, SERVICE_NAME).supportCompareVerifyIdenticalContent(true)
                 .validateResource(ICAPMode.RESPMOD,
                                   new ICAPRequestInformation(username, requestSource).setAllow204(false),
                                   new ICAPResource(file.getName(), resourceInputStream, ICAPTestVirusConstants.REQUEST_BODY_CLEAN.length()));

            // If no exception is thrown the resource can be used and is valid.

            // log output looks like:
            // DD8DEE46 - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
            // 30AC31B0 - Validate resource (username: user, source: file, resource: test-file.com, length: 71)
            // 30AC31B0 - Valid resource (username: user, source: file, resource: test-file.com, length: 71, http-status: 204).
            assertTrue(icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT));
            assertEquals("[true]", "" + icapHeaderInformation.getHeaders().get(ICAPConstants.HEADER_KEY_X_IDENTICAL_CONTENT));
        } catch (IOException ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        } catch (ContentBlockedException e) {

            // !!! The resource has to be blocked !!!

            // The e.getMessage() gives technical the proper information. It's already logged by the library.
            @SuppressWarnings("unused")
            String msg = e.getMessage();

            // The ICAP header contains structured information about virus.
            ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
            icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

            // The e.getContent contains the returned error information from the ICAP-Server.
            // It can be ignored as long as the resource is blocked; otherwise it gives a well-structured response.
            var ignored = e.getContent();

            fail();
        }
    }


    /**
     * The usage empty file case
     */
    @Test
    public void usage_TestEmptyFile() {
        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(new byte[] {});
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, ICAP_PORT, SERVICE_NAME)
                 .validateResource(ICAPMode.RESPMOD,
                                   new ICAPRequestInformation("userb", "emptyfile"),
                                   new ICAPResource("build/test-emptyfile.com", resourceInputStream, 0));
        } catch (Exception ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        }
    }


    /**
     * The usage empty file case
     */
    @Test
    public void usage_TestSmallFile() {
        try {
            ByteArrayInputStream resourceInputStream = new ByteArrayInputStream(new byte[] {(byte)'a'});
            ICAPClientFactory.getInstance().getICAPClient(LOCALHOST, ICAP_PORT, SERVICE_NAME)
                 .validateResource(ICAPMode.RESPMOD,
                                   new ICAPRequestInformation("userb", "smallfile"),
                                   new ICAPResource("build/test-smallfile.com", resourceInputStream, 1));
        } catch (Exception ioe) { // I/O error
            LOG.warn(RESOURCE_COULD_NOT_BE_ACCESSED, ioe.getMessage(), ioe);
            fail();
        }
    }
}
