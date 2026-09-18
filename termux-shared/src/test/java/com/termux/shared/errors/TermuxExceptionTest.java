package com.termux.shared.errors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Unit tests for {@link TermuxException} and the {@link Error#throwIfFailed()} bridge. */
public class TermuxExceptionTest {

    @Test
    public void throwIfFailed_doesNotThrow_forNullError() throws TermuxException {
        TermuxException.throwIfFailed(null);
        // No exception means success.
    }

    @Test
    public void throwIfFailed_doesNotThrow_forSuccessError() throws TermuxException {
        Error success = Errno.ERRNO_SUCCESS.getError();
        assertFalse(success.isStateFailed());
        TermuxException.throwIfFailed(success);
        // No exception means success.
    }

    @Test
    public void throwIfFailed_throws_forFailedError() {
        Error failed = FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError("filePath", "someMethod");
        assertTrue(failed.isStateFailed());

        try {
            TermuxException.throwIfFailed(failed);
            fail("Expected TermuxException to be thrown for a failed Error");
        } catch (TermuxException e) {
            assertSame(failed, e.getError());
            assertEquals(failed.getMessage(), e.getMessage());
        }
    }

    @Test
    public void errorInstanceMethod_throwIfFailed_matchesStaticBridge() {
        Error failed = FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError("filePath", "someMethod");
        try {
            failed.throwIfFailed();
            fail("Expected TermuxException to be thrown for a failed Error");
        } catch (TermuxException e) {
            assertSame(failed, e.getError());
        }
    }

    @Test
    public void causeChain_isPreserved() {
        RuntimeException cause = new RuntimeException("boom");
        Error error = new Error("Something failed", cause);
        error.setStateFailed(Errno.ERRNO_FAILED.getCode(), "Something failed", cause);

        TermuxException exception = new TermuxException(error);
        assertSame(cause, exception.getCause());
        assertEquals(1, exception.getCauses().size());
        assertSame(cause, exception.getCauses().get(0));
    }

    @Test
    public void noCause_yieldsNullGetCauseAndEmptyGetCauses() {
        Error error = new Error("Errno Type", 500, "Something failed");
        TermuxException exception = new TermuxException(error);
        assertNull(exception.getCause());
        assertTrue(exception.getCauses().isEmpty());
    }
}
