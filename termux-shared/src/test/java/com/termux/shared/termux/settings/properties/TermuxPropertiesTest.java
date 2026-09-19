package com.termux.shared.termux.settings.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for the static validation helpers on {@link TermuxProperties} and the
 * map-builder helpers on {@link TermuxPropertyConstants} (beads-vql: three-layer property
 * system collapsed into a single class).
 */
public class TermuxPropertiesTest {

    // -----------------------------------------------------------------------
    // TermuxPropertyConstants.mapOf / entry
    // -----------------------------------------------------------------------

    @Test
    public void mapOf_buildsMapWithGivenEntries() {
        Map<String, Integer> map = TermuxPropertyConstants.mapOf(
            TermuxPropertyConstants.entry("a", 1),
            TermuxPropertyConstants.entry("b", 2));

        assertEquals(2, map.size());
        assertEquals(Integer.valueOf(1), map.get("a"));
        assertEquals(Integer.valueOf(2), map.get("b"));
    }

    @Test
    public void mapOf_preservesInsertionOrder() {
        Map<String, Integer> map = TermuxPropertyConstants.mapOf(
            TermuxPropertyConstants.entry("z", 1),
            TermuxPropertyConstants.entry("a", 2));

        assertEquals("[z, a]", map.keySet().toString());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void mapOf_returnsUnmodifiableMap() {
        Map<String, Integer> map = TermuxPropertyConstants.mapOf(TermuxPropertyConstants.entry("a", 1));
        map.put("b", 2);
    }

    // -----------------------------------------------------------------------
    // MAP_GENERIC_BOOLEAN / MAP_GENERIC_INVERTED_BOOLEAN
    // -----------------------------------------------------------------------

    @Test
    public void mapGenericBoolean_hasCorrectMapping() {
        assertEquals(Boolean.TRUE,  TermuxProperties.MAP_GENERIC_BOOLEAN.get("true"));
        assertEquals(Boolean.FALSE, TermuxProperties.MAP_GENERIC_BOOLEAN.get("false"));
    }

    @Test
    public void mapGenericInvertedBoolean_hasInvertedMapping() {
        assertEquals(Boolean.FALSE, TermuxProperties.MAP_GENERIC_INVERTED_BOOLEAN.get("true"));
        assertEquals(Boolean.TRUE,  TermuxProperties.MAP_GENERIC_INVERTED_BOOLEAN.get("false"));
    }

    @Test
    public void getBooleanValueForStringValue_parsesTrueAndFalse() {
        assertEquals(Boolean.TRUE,  TermuxProperties.getBooleanValueForStringValue("true"));
        assertEquals(Boolean.FALSE, TermuxProperties.getBooleanValueForStringValue("false"));
        assertNull(TermuxProperties.getBooleanValueForStringValue("invalid"));
    }

    // -----------------------------------------------------------------------
    // getDefaultIfNotInMap
    // -----------------------------------------------------------------------

    @Test
    public void getDefaultIfNotInMap_returnsMappedValueWhenPresent() {
        Map<String, Integer> map = TermuxPropertyConstants.mapOf(
            TermuxPropertyConstants.entry("one", 1),
            TermuxPropertyConstants.entry("two", 2));

        Object result = TermuxProperties.getDefaultIfNotInMap("key", map, "one", 99, false, "TAG");
        assertEquals(Integer.valueOf(1), result);
    }

    @Test
    public void getDefaultIfNotInMap_returnsDefaultWhenInputValueNotFound() {
        Map<String, Integer> map = TermuxPropertyConstants.mapOf(
            TermuxPropertyConstants.entry("one", 1),
            TermuxPropertyConstants.entry("two", 2));

        Object result = TermuxProperties.getDefaultIfNotInMap("key", map, "missing", 2, false, "TAG");
        assertEquals(Integer.valueOf(2), result);
    }

    @Test
    public void getDefaultIfNotInMap_stillReturnsDefaultEvenWhenDefaultNotInMapValues() {
        Map<String, Integer> map = TermuxPropertyConstants.mapOf(TermuxPropertyConstants.entry("one", 1));

        Object result = TermuxProperties.getDefaultIfNotInMap("key", map, "missing", 999, false, "TAG");
        assertEquals(Integer.valueOf(999), result);
    }
}
