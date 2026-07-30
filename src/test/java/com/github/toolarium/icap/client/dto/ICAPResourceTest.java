/*
 * ICAPResourceTest.java
 *
 * Copyright by toolarium, all rights reserved.
 */
package com.github.toolarium.icap.client.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;


/**
 * Test the {@link ICAPResource}.
 *
 * @author patrick
 */
public class ICAPResourceTest {
    private static final String TEST_DATA = "test";


    /**
     * Test constructor with name, stream and length
     */
    @Test
    public void constructWithNameAndStream() {
        ByteArrayInputStream stream = new ByteArrayInputStream(TEST_DATA.getBytes());
        ICAPResource resource = new ICAPResource("myfile.txt", stream, 4);

        assertEquals("myfile.txt", resource.getResourceName());
        assertEquals(4, resource.getResourceLength());
        assertNotNull(resource.getResourceBody());
    }


    /**
     * Test constructor with null name defaults to "content"
     */
    @Test
    public void constructWithNullName() {
        ByteArrayInputStream stream = new ByteArrayInputStream(TEST_DATA.getBytes());
        ICAPResource resource = new ICAPResource(null, stream, 4);

        assertEquals("content", resource.getResourceName());
    }


    /**
     * Test constructor with blank name defaults to "content"
     */
    @Test
    public void constructWithBlankName() {
        ByteArrayInputStream stream = new ByteArrayInputStream(TEST_DATA.getBytes());
        ICAPResource resource = new ICAPResource("  ", stream, 4);

        assertEquals("content", resource.getResourceName());
    }


    /**
     * Test constructor with stream only (no name)
     */
    @Test
    public void constructWithStreamOnly() {
        ByteArrayInputStream stream = new ByteArrayInputStream(TEST_DATA.getBytes());
        ICAPResource resource = new ICAPResource(stream, 4);

        assertEquals("content", resource.getResourceName());
        assertEquals(4, resource.getResourceLength());
    }


    /**
     * Test Path constructor with non-existent file
     */
    @Test
    public void constructWithNonExistentPath() {
        assertThrows(FileNotFoundException.class, () -> {
            new ICAPResource(Paths.get("nonexistent_file_12345.txt"));
        });
    }


    /**
     * Test Path constructor with null
     */
    @Test
    public void constructWithNullPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ICAPResource(null);
        });
    }


    /**
     * Test equals and hashCode
     */
    @Test
    public void equalsAndHashCode() {
        ByteArrayInputStream s1 = new ByteArrayInputStream(TEST_DATA.getBytes());
        ByteArrayInputStream s2 = new ByteArrayInputStream(TEST_DATA.getBytes());
        ICAPResource a = new ICAPResource("file.txt", s1, 100);
        ICAPResource b = new ICAPResource("file.txt", s2, 100);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    /**
     * Test not equals with different length
     */
    @Test
    public void notEqualsLength() {
        ByteArrayInputStream s1 = new ByteArrayInputStream(TEST_DATA.getBytes());
        ByteArrayInputStream s2 = new ByteArrayInputStream(TEST_DATA.getBytes());
        ICAPResource a = new ICAPResource("file.txt", s1, 100);
        ICAPResource b = new ICAPResource("file.txt", s2, 200);

        assertFalse(a.equals(b));
    }


    /**
     * A freshly constructed resource is not consumed
     */
    @Test
    public void freshResourceIsNotConsumed() {
        ICAPResource resource = new ICAPResource("file.txt", new ByteArrayInputStream(TEST_DATA.getBytes()), 4);
        assertFalse(resource.isConsumed());
    }


    /**
     * markConsumed sets the consumed flag
     */
    @Test
    public void markConsumedSetsFlag() {
        ICAPResource resource = new ICAPResource("file.txt", new ByteArrayInputStream(TEST_DATA.getBytes()), 4);
        assertFalse(resource.isConsumed());
        resource.markConsumed();
        assertTrue(resource.isConsumed());
    }


    /**
     * markConsumed is idempotent
     */
    @Test
    public void markConsumedIsIdempotent() {
        ICAPResource resource = new ICAPResource("file.txt", new ByteArrayInputStream(TEST_DATA.getBytes()), 4);
        resource.markConsumed();
        resource.markConsumed();
        assertTrue(resource.isConsumed());
    }


    /**
     * Test toString
     */
    @Test
    public void testToString() {
        ByteArrayInputStream stream = new ByteArrayInputStream(TEST_DATA.getBytes());
        ICAPResource resource = new ICAPResource("file.txt", stream, 100);
        assertNotNull(resource.toString());
        assertEquals("ICAPResource [resourceName=file.txt, resourceLength=100]", resource.toString());
    }
}
