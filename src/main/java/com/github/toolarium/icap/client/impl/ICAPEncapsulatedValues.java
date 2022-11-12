/*
 * EncapsulatedValues.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.impl;

import com.github.toolarium.icap.client.dto.ICAPConstants;
import com.github.toolarium.icap.client.dto.ICAPHeaderInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * ICAP encapsulated values
 *  
 * @author patrick
 */
public class ICAPEncapsulatedValues {
    private static final Logger LOG = LoggerFactory.getLogger(ICAPEncapsulatedValues.class);
    private int offset;
    private int length;

    
    /**
     * Constructor for EncapsulatedValues
     *
     * @param icapHeaderInformation the icap header information
     */
    public ICAPEncapsulatedValues(ICAPHeaderInformation icapHeaderInformation) {
        offset = 0;
        length = 0;
        
        if (icapHeaderInformation.containsHeader(ICAPConstants.HEADER_KEY_ENCAPSULATED)) {
            for (String expressionLine : icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_ENCAPSULATED)) {
                parseLine(expressionLine);
            }
        }
    }

    
    /**
     * Constructor for EncapsulatedValues
     *
     * @param expressionLine the expression line, e.g. req-hdr=0, req-body=182
     */
    public ICAPEncapsulatedValues(String expressionLine) {
        offset = 0;
        length = 0;
        
        parseLine(expressionLine);
    }
    
    
    /**
     * Get the offset
     *
     * @return the offset
     */
    public int getOffset() {
        return offset;
    }

    
    /**
     * Get the length
     *
     * @return the length
     */
    public int getLength() {
        return length;
    }


    /**
     * Parse an expression line
     *
     * @param expressionLine the expression line, e.g. req-hdr=0, req-body=182
     * @return true if it could be parsed
     */
    private boolean parseLine(String expressionLine) {
        if (expressionLine == null || expressionLine.isBlank()) {
            return false;
        }

        if (!expressionLine.contains(",")) {
            if (expressionLine.contains("hdr")) {
                Integer parsedOffset = parseKeyValue("hdr", expressionLine.trim());
                if (parsedOffset != null) {
                    offset = parsedOffset.intValue();
                }
            }
            
            if (expressionLine.contains("body")) {
                Integer parsedLength = parseKeyValue("body", expressionLine.trim());
                if (parsedLength != null) {
                    length = parsedLength.intValue();
                }
            }
        } else {
            LOG.debug("Parse encapsulated expression line [" + expressionLine + "].");
            String[] valueSplit = expressionLine.trim().split(",");
            for (int i = 0; i < valueSplit.length; i++) {
                Integer parsedOffset = parseKeyValue("hdr", valueSplit[i].trim());
                if (parsedOffset != null) {
                    offset = parsedOffset.intValue();
                }
                
                Integer parsedLength = parseKeyValue("body", valueSplit[i].trim());
                if (parsedLength != null) {
                    length = parsedLength.intValue();
                }
            }
        }
        
        return true;
    }


    /**
     * Parse key value
     *
     * @param tag the key tag
     * @param keyValue the expression to parse
     * @return the value
     */
    private Integer parseKeyValue(String tag, String keyValue) {
        LOG.debug("Parse encapsulated expression [" + keyValue + "].");
        int idx = keyValue.indexOf('=');
        if (idx > 0 && keyValue.substring(0, idx).contains("-" + tag)) {
            String value = keyValue.substring(idx + 1);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOG.debug("Invalid number [" + value + "]");
            }
        }
        
        return null;
    }


    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ICAPEncapsulatedValues [offset=" + offset + ", length=" + length + "]";
    }
}
