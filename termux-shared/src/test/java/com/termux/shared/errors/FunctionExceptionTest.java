package com.termux.shared.errors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for {@link FunctionException}. */
public class FunctionExceptionTest {

    @Test
    public void throwIfNullOrEmpty_throwsForNull() {
        FunctionException e = assertThrows(FunctionException.class, () ->
            FunctionException.throwIfNullOrEmpty(null, "file path", "someMethod"));
        assertTrue(e.getMessage().contains("file path"));
        assertTrue(e.getMessage().contains("someMethod"));
        assertTrue(e.getMessage().contains("null or empty"));
    }

    @Test
    public void throwIfNullOrEmpty_throwsForEmpty() {
        assertThrows(FunctionException.class, () ->
            FunctionException.throwIfNullOrEmpty("", "file path", "someMethod"));
    }

    @Test
    public void throwIfNullOrEmpty_doesNotThrowForNonEmpty() throws FunctionException {
        FunctionException.throwIfNullOrEmpty("value", "file path", "someMethod");
        // No exception means success.
    }

    @Test
    public void throwNullOrEmptyParameters_alwaysThrows() {
        FunctionException e = assertThrows(FunctionException.class, () ->
            FunctionException.throwNullOrEmptyParameters("context, resultConfig or resultData", "sendCommandResultData"));
        assertTrue(e.getMessage().contains("context, resultConfig or resultData"));
        assertTrue(e.getMessage().contains("sendCommandResultData"));
        assertTrue(e.getMessage().contains("are null or empty"));
    }

    @Test
    public void throwIfUnset_throwsForNull() {
        assertThrows(FunctionException.class, () ->
            FunctionException.throwIfUnset(null, "resultConfig", "someMethod"));
    }

    @Test
    public void throwIfUnset_doesNotThrowForNonNull() throws FunctionException {
        FunctionException.throwIfUnset(new Object(), "resultConfig", "someMethod");
    }

    @Test
    public void throwUnsetParameters_alwaysThrows() {
        assertThrows(FunctionException.class, () ->
            FunctionException.throwUnsetParameters("resultConfig.resultPendingIntent or resultConfig.resultDirectoryPath", "sendCommandResultData"));
    }

    @Test
    public void throwInvalidParameter_alwaysThrows() {
        FunctionException e = assertThrows(FunctionException.class, () ->
            FunctionException.throwInvalidParameter("days", "deleteFilesOlderThanXDays", " It must be >= 0."));
        assertTrue(e.getMessage().contains("days"));
        assertTrue(e.getMessage().contains("invalid"));
        assertTrue(e.getMessage().contains("It must be >= 0."));
    }

    @Test
    public void throwIfNotInstanceOf_throwsForWrongType() {
        assertThrows(FunctionException.class, () ->
            FunctionException.throwIfNotInstanceOf("a string", Integer.class, "value", "someMethod"));
    }

    @Test
    public void throwIfNotInstanceOf_doesNotThrowForCorrectType() throws FunctionException {
        FunctionException.throwIfNotInstanceOf("a string", String.class, "value", "someMethod");
    }

    @Test
    public void throwIfNotInstanceOf_throwsForNull() {
        assertThrows(FunctionException.class, () ->
            FunctionException.throwIfNotInstanceOf(null, String.class, "value", "someMethod"));
    }

    @Test
    public void throwNotInstanceOf_alwaysThrows() {
        FunctionException e = assertThrows(FunctionException.class, () ->
            FunctionException.throwNotInstanceOf("context", "requestPermissions", "Activity or AppCompatActivity"));
        assertTrue(e.getMessage().contains("Activity or AppCompatActivity"));
    }

    @Test
    public void isSubclassOfTermuxException() {
        assertEquals(TermuxException.class, FunctionException.class.getSuperclass());
    }
}
