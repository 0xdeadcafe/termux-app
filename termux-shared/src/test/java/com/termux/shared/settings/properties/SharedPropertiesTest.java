package com.termux.shared.settings.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for the Guava-free replacements added to {@link SharedProperties} for beads-e6w:
 * {@link SharedProperties#mapOf(Map.Entry[])}/{@link SharedProperties#entry(Object, Object)}
 * (replacing {@code ImmutableBiMap.Builder}), {@link SharedProperties#getDefaultIfNotInMap}
 * (replacing {@code BiMap#inverse()}), and the wrapper-type check in
 * {@link SharedProperties#putToMap} (replacing {@code Primitives#isWrapperType}).
 */
public class SharedPropertiesTest {

    // -----------------------------------------------------------------------
    // mapOf / entry
    // -----------------------------------------------------------------------

    @Test
    public void mapOf_buildsMapWithGivenEntries() {
        Map<String, Integer> map = SharedProperties.mapOf(
            SharedProperties.entry("a", 1),
            SharedProperties.entry("b", 2));

        assertEquals(2, map.size());
        assertEquals(Integer.valueOf(1), map.get("a"));
        assertEquals(Integer.valueOf(2), map.get("b"));
    }

    @Test
    public void mapOf_preservesInsertionOrder() {
        Map<String, Integer> map = SharedProperties.mapOf(
            SharedProperties.entry("z", 1),
            SharedProperties.entry("a", 2));

        assertEquals("[z, a]", map.keySet().toString());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void mapOf_returnsUnmodifiableMap() {
        Map<String, Integer> map = SharedProperties.mapOf(SharedProperties.entry("a", 1));
        map.put("b", 2);
    }

    // -----------------------------------------------------------------------
    // MAP_GENERIC_BOOLEAN / MAP_GENERIC_INVERTED_BOOLEAN (used by getBooleanValueForStringValue)
    // -----------------------------------------------------------------------

    @Test
    public void mapGenericBoolean_hasCorrectMapping() {
        assertEquals(Boolean.TRUE, SharedProperties.MAP_GENERIC_BOOLEAN.get("true"));
        assertEquals(Boolean.FALSE, SharedProperties.MAP_GENERIC_BOOLEAN.get("false"));
    }

    @Test
    public void mapGenericInvertedBoolean_hasInvertedMapping() {
        assertEquals(Boolean.FALSE, SharedProperties.MAP_GENERIC_INVERTED_BOOLEAN.get("true"));
        assertEquals(Boolean.TRUE, SharedProperties.MAP_GENERIC_INVERTED_BOOLEAN.get("false"));
    }

    @Test
    public void getBooleanValueForStringValue_parsesTrueAndFalse() {
        assertEquals(Boolean.TRUE, SharedProperties.getBooleanValueForStringValue("true"));
        assertEquals(Boolean.FALSE, SharedProperties.getBooleanValueForStringValue("false"));
        assertNull(SharedProperties.getBooleanValueForStringValue("invalid"));
    }

    // -----------------------------------------------------------------------
    // getDefaultIfNotInMap (replaces BiMap#inverse() for the error-message reverse lookup)
    // -----------------------------------------------------------------------

    @Test
    public void getDefaultIfNotInMap_returnsMappedValueWhenPresent() {
        Map<String, Integer> map = SharedProperties.mapOf(
            SharedProperties.entry("one", 1),
            SharedProperties.entry("two", 2));

        Object result = SharedProperties.getDefaultIfNotInMap("key", map, "one", 99, false, "TAG");
        assertEquals(Integer.valueOf(1), result);
    }

    @Test
    public void getDefaultIfNotInMap_returnsDefaultWhenInputValueNotFound() {
        Map<String, Integer> map = SharedProperties.mapOf(
            SharedProperties.entry("one", 1),
            SharedProperties.entry("two", 2));

        Object result = SharedProperties.getDefaultIfNotInMap("key", map, "missing", 2, false, "TAG");
        assertEquals(Integer.valueOf(2), result);
    }

    @Test
    public void getDefaultIfNotInMap_stillReturnsDefaultEvenWhenDefaultNotInMapValues() {
        // Exercises the reverse-lookup-for-error-message-only branch (defaultOutputValue not
        // found as any value in the map) without throwing -- it should just log and still
        // return defaultOutputValue as-is.
        Map<String, Integer> map = SharedProperties.mapOf(SharedProperties.entry("one", 1));

        Object result = SharedProperties.getDefaultIfNotInMap("key", map, "missing", 999, false, "TAG");
        assertEquals(Integer.valueOf(999), result);
    }

    // -----------------------------------------------------------------------
    // putToMap (replaces Primitives#isWrapperType)
    // -----------------------------------------------------------------------

    @Test
    public void putToMap_acceptsBoxedPrimitiveValues() {
        HashMap<String, Object> map = new HashMap<>();
        assertTrue(SharedProperties.putToMap(map, "boolKey", true));
        assertTrue(SharedProperties.putToMap(map, "intKey", 42));
        assertTrue(SharedProperties.putToMap(map, "longKey", 42L));
        assertTrue(SharedProperties.putToMap(map, "floatKey", 4.2f));
        assertTrue(SharedProperties.putToMap(map, "doubleKey", 4.2));
        assertTrue(SharedProperties.putToMap(map, "byteKey", (byte) 1));
        assertTrue(SharedProperties.putToMap(map, "shortKey", (short) 1));
        assertTrue(SharedProperties.putToMap(map, "charKey", 'c'));
        assertEquals(8, map.size());
    }

    @Test
    public void putToMap_acceptsStringValue() {
        HashMap<String, Object> map = new HashMap<>();
        assertTrue(SharedProperties.putToMap(map, "key", "a string value"));
        assertEquals("a string value", map.get("key"));
    }

    @Test
    public void putToMap_acceptsNullValue() {
        HashMap<String, Object> map = new HashMap<>();
        assertTrue(SharedProperties.putToMap(map, "key", null));
        assertTrue(map.containsKey("key"));
        assertNull(map.get("key"));
    }

    @Test
    public void putToMap_rejectsNonPrimitiveNonStringValue() {
        HashMap<String, Object> map = new HashMap<>();
        assertFalse(SharedProperties.putToMap(map, "key", new Object()));
        assertFalse(map.containsKey("key"));
    }

    @Test
    public void putToMap_rejectsNullKey() {
        HashMap<String, Object> map = new HashMap<>();
        assertFalse(SharedProperties.putToMap(map, null, "value"));
    }

}
