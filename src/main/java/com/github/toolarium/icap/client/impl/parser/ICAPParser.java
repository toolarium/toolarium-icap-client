/*
 * ICAPParser.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl.parser;

import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Defines the ICAP parser
 *  
 * @author patrick
 */
public final class ICAPParser {
    private static final Pattern LINE_STATUS_PATTERN = Pattern.compile("(ICAP)\\/(1.0)\\s(\\d{3})\\s(.*)");

    
    /**
     * Private class, the only instance of the singelton which will be created by accessing the holder class.
     *
     * @author patrick
     */
    private static class HOLDER {
        static final ICAPParser INSTANCE = new ICAPParser();
    }

    
    /**
     * Constructor
     */
    private ICAPParser() {
        // NOP
    }

    
    /**
     * Get the instance
     *
     * @return the instance
     */
    public static ICAPParser getInstance() {
        return HOLDER.INSTANCE;
    }
    
    
    /**
     * Parse the protocol line
     *
     * @param protocolHeaderLine the protocol line
     * @return the parsed header information
     */
    public ICAPHeaderInformation parseICAPHeaderInformation(String protocolHeaderLine) {
        ICAPHeaderInformation headerInformation = new ICAPHeaderInformation();
        
        // parse ICAP protocol header line
        if (protocolHeaderLine != null && !protocolHeaderLine.isBlank()) {
            Matcher matcher = LINE_STATUS_PATTERN.matcher(protocolHeaderLine);
            if (matcher.matches()) {
                headerInformation.setProtocol(matcher.group(1));
                headerInformation.setVersion(matcher.group(2));
                headerInformation.setStatus(Integer.parseInt(matcher.group(3)));
                headerInformation.setMessage(matcher.group(4));
            }
        }

        return headerInformation;
    }

    
    /**
     * Parse a raw header 
     * 
     * @param headerLines The raw header lines
     * @return the parsed header
     */
    public Map<String, List<String>> parseHeader(List<String> headerLines) {
        /****SAMPLE:****
         * ICAP/1.0 204 Unmodified
         * Server: C-ICAP/0.1.6
         * Connection: keep-alive
         * ISTag: CI0001-000-0978-6918203
         */
        Map<String,List<String>> headers = new LinkedHashMap<>();
        String key = ICAPConstants.HEADER_KEY_X_ICAP_STATUSLINE;
        for (String line : headerLines) {
            int idx = line.indexOf(':');
            String value = line.substring(idx + 1).trim();
            if (idx > 0) {                
                key = line.substring(0, idx);
                value = line.substring(idx + 1).trim();
            }
            
            List<String> valueList = headers.get(key);
            if (valueList == null) {
                valueList = new ArrayList<>();
                headers.put(key, valueList);
            }

            if (key.equalsIgnoreCase("Date")) {
                valueList.add(value);
            } else if (key.equalsIgnoreCase(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND)) {
                add(valueList, value.split(";"));
            } else if (key.equalsIgnoreCase(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND)) {
                add(valueList, value.split("\n"));
            } else {
                add(valueList, value.split("\\,"));
            }
        }
        
        return headers;
    }


    /**
     * Add
     *
     * @param valueList the value list
     * @param values the values
     */
    private void add(List<String> valueList, String[] values) {
        for (int i = 0; i < values.length; i++) {
            valueList.add(values[i].trim());
        }
    }
}
