/*
 * ICAPConstants.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;


/**
 * Defines the ICAP constants
 *
 * @author Patrick Meier
 */
public interface ICAPConstants {
    // HTTP header headers
    String HEADER_KEY_SERVER = "Server";
    String HEADER_KEY_CONNECTION = "Connection";
    String HEADER_KEY_ISTAG = "ISTag";
    String HEADER_KEY_CONTENT_LENGTH = "Content-Length";
    String HEADER_KEY_TRANSFER_ENCODING = "Transfer-Encoding";
    String HEADER_KEY_ENCAPSULATED = "Encapsulated";
    
    // ICAP header headers
    String HEADER_KEY_PREVIEW = "Preview";
    String HEADER_KEY_ALLOW = "Allow";
    String HEADER_KEY_X_VIOLATIONS_FOUND = "X-Violations-Found";
    String HEADER_KEY_X_INFECTION_FOUND = "X-Infection-Found";    
    String HEADER_KEY_X_BLOCKED = "X-Blocked"; // used by Sophos
    String HEADER_KEY_X_VIRUS_ID = "X-Virus-ID"; // used by Sophos, Kaspersky, Trenxd Micro, ESET, McAfee, C-ICAP
    String HEADER_KEY_X_VIRUS_NAME = "X-Virus-Name"; // used by McAfee
    String HEADER_KEY_X_BLOCK_REASON = "X-Block-Reason";
    String HEADER_KEY_X_BLOCK_RESULT = "X-WWBlockResult";
    
    // ICAP client library headers
    String HEADER_KEY_X_ICAP_STATUSLINE = "X-ICAP-Statusline";     
    String HEADER_KEY_X_REQUEST_MESSAGE_DIGEST = "X-Request-Message-Digest";    
    String HEADER_KEY_X_RESPONSE_MESSAGE_DIGEST = "X-Response-Message-Digest";
    String HEADER_KEY_X_IDENTICAL_CONTENT = "X-Resource-Identical-Content";
    

    /*
    Generic Strings: ok
        icap: X-Infection-Found: Type=0; Resolution=2; Threat=Troj/DocDl-OYC;
        icap: X-Infection-Found: Type=0; Resolution=2; Threat=W97M.Downloader;

      Symantec String: ok
        icap: X-Infection-Found: Type=2; Resolution=2; Threat=Container size violation
        icap: X-Infection-Found: Type=2; Resolution=2; Threat=Encrypted container violation;

      Sophos Strings: ok
        icap: X-Virus-ID: Troj/DocDl-OYC
        http: X-Blocked: Virus found during virus scan
        http: X-Blocked-By: Sophos Anti-Virus

      Kaspersky Web Traffic Security Strings: ok
        icap: X-Virus-ID: HEUR:Backdoor.Java.QRat.gen
        icap: X-Response-Info: blocked
        icap: X-Virus-ID: no threats
        icap: X-Response-Info: blocked
        icap: X-Response-Info: passed
        http: HTTP/1.1 403 Forbidden

      Kaspersky Scan Engine 2.0 (ICAP mode): ok
        icap: X-Virus-ID: EICAR-Test-File
        http: HTTP/1.0 403 Forbidden

      Trend Micro Strings: ok
        icap: X-Virus-ID: Trojan.W97M.POWLOAD.SMTHF1
        icap: X-Infection-Found: Type=0; Resolution=2; Threat=Trojan.W97M.POWLOAD.SMTHF1;
        http: HTTP/1.1 403 Forbidden (TMWS Blocked)
        http: HTTP/1.1 403 Forbidden

      F-Secure Internet Gatekeeper Strings:
        icap: X-FSecure-Scan-Result: infected
        icap: X-FSecure-Infection-Name: "Malware.W97M/Agent.32584203"
        icap: X-FSecure-Infected-Filename: "virus.doc"

      ESET File Security for Linux 7.0: ok
        icap: X-Infection-Found: Type=0; Resolution=0; Threat=VBA/TrojanDownloader.Agent.JOA;
        icap: X-Virus-ID: Trojaner
        icap: X-Response-Info: Blocked

      McAfee Web Gateway 10/11 (Headers must be activated with personal extra Rules): ok
        icap: X-Virus-ID: EICAR test file
        icap: X-Media-Type: text/plain
        icap: X-Block-Result: 80
        icap: X-Block-Reason: Malware found
        icap: X-Block-Reason: Archive not supported
        icap: X-Block-Reason: Media Type (Block List)
        http: HTTP/1.0 403 VirusFound

      C-ICAP Squidclamav: ok
        icap/http: X-Infection-Found: Type=0; Resolution=2; Threat={HEX}EICAR.TEST.3.UNOFFICIAL;
        icap/http: X-Virus-ID: {HEX}EICAR.TEST.3.UNOFFICIAL
        http: HTTP/1.0 307 Temporary Redirect
    */
}
