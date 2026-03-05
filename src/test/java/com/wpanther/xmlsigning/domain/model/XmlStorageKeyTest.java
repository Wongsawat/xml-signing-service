package com.wpanther.xmlsigning.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XmlStorageKeyTest {

    @Test
    void constructor_acceptsValidValue() {
        XmlStorageKey key = new XmlStorageKey("valid-key-value");
        assertEquals("valid-key-value", key.value());
    }

    @Test
    void constructor_rejectsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new XmlStorageKey(null)
        );
        assertTrue(exception.getMessage().contains("must not be blank"));
    }

    @Test
    void constructor_rejectsBlank() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new XmlStorageKey("   ")
        );
        assertTrue(exception.getMessage().contains("must not be blank"));
    }

    @Test
    void equalsAndHashCode_workCorrectly() {
        XmlStorageKey key1 = new XmlStorageKey("same-value");
        XmlStorageKey key2 = new XmlStorageKey("same-value");
        XmlStorageKey key3 = new XmlStorageKey("different-value");

        // Records implement equals/hashCode automatically
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, key3);
        assertNotEquals(key1.hashCode(), key3.hashCode());
    }
}
