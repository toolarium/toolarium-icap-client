/*
 * HexDump.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.util;

/**
 * Hex dump
 *
 * @author patrick
 */
public final class HexDump {
    private static final char[] HEXDIGITS = "0123456789ABCDEF".toCharArray();

    
    /**
     * Private class, the only instance of the singelton which will be created by accessing the holder class.
     *
     * @author patrick
     */
    private static class HOLDER {
        static final HexDump INSTANCE = new HexDump();
    }

    
    /**
     * Constructor
     */
    private HexDump() {
        // NOP
    }

    
    /**
     * Get the instance
     *
     * @return the instance
     */
    public static HexDump getInstance() {
        return HOLDER.INSTANCE;
    }

    
    /**
     * Generates a hexdump from a given input
     * @param buffer the buffer to dump
     * @return the hex dump
     */
    public String hexDump(String buffer) {
        return hexDump(buffer.getBytes());
    }
    

    
    
    /**
     * Generates a hexdump from a given input
     * @param buffer the buffer to dump
     * @return the hex dump
     */
    public String hexDump(byte[] buffer) {
        return dump(buffer, 16, true, " : ", true, true, "|", '.', true);
    }
    
    
    /**
     * Generates a hexdump from a given input
     * @param array the stream where the ZipOutpustream should written to
     * @param bufferSize the buffer size of the line
     * @param showHexnumber show hex number at begin
     * @param hexSep the hex number separator
     * @param showHex show hex dump
     * @param showChar show ascii dump
     * @param charSep the character separator
     * @param notPrintableCharacter the character of non printable characters
     * @param addNewLine add new lines on end of line
     * @return the dump
     */
    protected String dump(byte[] array, int bufferSize, boolean showHexnumber, String hexSep, boolean showHex,
            boolean showChar, String charSep, char notPrintableCharacter, boolean addNewLine)    {
        StringBuilder line = null;
        StringBuilder hexDump = null;
        StringBuilder charDump = null;
        StringBuilder hexNumber = null;
        int currentBufferPos = 0;
        int bufferstep = bufferSize;
        
        if (bufferstep <= 0) {
            bufferstep = array.length;
        }
            
        StringBuilder dump = new StringBuilder();        
        while (currentBufferPos < array.length) {
            line = new StringBuilder();

            if (showHex) {
                hexDump = new StringBuilder();
            }
            
            if (showChar) {
                charDump = new StringBuilder();
            }
                
            for (int j = 0; ((j < bufferstep) && (currentBufferPos + j < array.length)); j++) {
                if (showHexnumber) {
                    hexNumber = new StringBuilder();
                    hexNumber.append(Integer.toHexString(currentBufferPos));

                    while (hexNumber.length() < 6) {
                        hexNumber.insert(0, '0');
                    }
                }

                byte b = array[currentBufferPos + j];

                if (showHex) {
                    if (hexDump != null) {
                        hexDump.append(toString(new byte[] {b}, 0, 1));
                        hexDump.append(" ");
                    }
                }

                if (showChar) {
                    if (charDump != null) {
                        if (b >= 0x20 && b <= 0xFF) {
                            charDump.append((char) b);
                        } else {
                            charDump.append(notPrintableCharacter);
                        }
                    }
                }
            }

            if (showHexnumber) {
                line.append(hexNumber);
                line.append(hexSep);
            }

            if (showHex && (bufferSize > 0)) {
                if (hexDump != null) {
                    // file up hex dump with spaces
                    while (hexDump.length() < (bufferSize * 3)) {
                        hexDump.append("   ");
                    }
                    line.append(hexDump);
                }
            }

            if (showChar) {
                line.append(charSep);

                if (charDump != null) {
                    // file up char dump with spaces
                    while (charDump.length() < bufferSize) {
                        charDump.append(" ");
                    }
                    line.append(charDump);
                    line.append(charSep);
                }
            }

            if (addNewLine) {
                line.append("\n");
            }
            
            currentBufferPos += bufferstep;
            dump.append(line);
        }

        return dump.toString();
    }

    
    /**
     * Converts a given byte array to a string representation
     * 
     * @param b the bytes
     * @param ofs the offset
     * @param len the length of the bytes
     * @return bytes as string
     */
    public String toString(byte[] b, int ofs, int len) {
        if (b == null) {
            return "(null)";
        }
            
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            int c = b[ofs + i] & 0xff;

            sb.append(HEXDIGITS[c >> 4]);
            sb.append(HEXDIGITS[c & 15]);
            if (i != len - 1) {
                sb.append(':');
            }
        }
        
        return sb.toString();
    }
}