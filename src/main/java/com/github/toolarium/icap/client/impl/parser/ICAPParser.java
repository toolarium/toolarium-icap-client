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
    private static final Pattern LINE_STATUS_PATTERN = Pattern.compile("(ICAP)/(1.0)\\s(\\d{3})\\s(.*)");


    /**
     * Private class, the only instance of the singleton which will be created by accessing the holder class.
     *
     * @author patrick
     */
    private static class HOLDER {
        static final ICAPParser INSTANCE = new ICAPParser();
    }


    /**
     * Private Constructor to prevent instantiation
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
        var icapHeaderInformationBuilder = new ICAPHeaderInformation.Builder();

        // parse ICAP protocol header line
        if (protocolHeaderLine != null && !protocolHeaderLine.isBlank()) {
            Matcher matcher = LINE_STATUS_PATTERN.matcher(protocolHeaderLine);
            if (matcher.matches()) {
                icapHeaderInformationBuilder
                    .withProtocol(matcher.group(1))
                    .withVersion(matcher.group(2))
                    .withStatus(Integer.parseInt(matcher.group(3)))
                    .withMessage(matcher.group(4));
            }
        }

        return icapHeaderInformationBuilder.build();
    }


    /**
     * Parse a raw header
     *
     * @param headerLines The raw header lines
     * @return the parsed header
     */
    public Map<String, List<String>> parseHeader(List<String> headerLines) {
        /* ****SAMPLE:****
         ICAP/1.0 204 Unmodified
         Server: C-ICAP/0.1.6
         Connection: keep-alive
         ISTag: CI0001-000-0978-6918203
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

            List<String> valueList = headers.computeIfAbsent(key, k -> new ArrayList<>());

            if (key.equalsIgnoreCase("Date")) {
                valueList.add(value);
            } else if (key.equalsIgnoreCase(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND)) {
                add(valueList, value.split(";"));
            } else if (key.equalsIgnoreCase(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND)) {
                add(valueList, value.split("\n"));
            } else {
                add(valueList, value.split(","));
            }
        }

        return headers;
    }


    /**
     * Add values to valueList
     *
     * @param valueList the value list to add the values to
     * @param values the values to be added
     */
    private void add(List<String> valueList, String[] values) {
        for (String value : values) {
            valueList.add(value.trim());
        }
    }
}
