/*
 * ICAPConstants.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client;

/**
 * Defines some constant
 * 
 * @author Patrick Meier
 */
public interface ICAPTestVirusConstants {
    String REQUEST_BODY_VIRUS = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*\r\n";
    String REQUEST_BODY_CLEAN = "X5O!P%@APX[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*\r\n";    
   
    // new String(Base64.getEncoder().encode(ICAPTestVirusConstants.REQUEST_BODY_VIRUS.getBytes()))
    String REQUEST_BODY_VIRUS_BASE64 = "WDVPIVAlQEFQWzRcUFpYNTQoUF4pN0NDKTd9JEVJQ0FSLVNUQU5EQVJELUFOVElWSVJVUy1URVNULUZJTEUhJEgrSCoNCg=="; 
}
