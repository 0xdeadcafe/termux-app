package com.termux.shared.net.socket.local;

import static org.junit.Assert.assertThrows;

import com.termux.shared.errors.TermuxException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Unit tests for the startOrThrow/stopOrThrow sibling methods added for beads-jw0 (LocalSocketErrno
 * migration, part of the beads-xs0 Errno/Error deprecate-first migration family).
 *
 * <p>{@link LocalSocketManager#start()} loads a real JNI native library ("local-socket") that is
 * not present in this JVM/Robolectric test environment, so it deterministically fails with an
 * UnsatisfiedLinkError caught inside start() and converted to an Error -- this is used here as a
 * reliable "always fails" test case, not a flaky one, since the library will never be loadable
 * under this test runner regardless of environment quirks.
 */
@RunWith(RobolectricTestRunner.class)
public class LocalSocketManagerOrThrowTest {

    private LocalSocketManager newManager() {
        ILocalSocketManager client = new LocalSocketManagerClientBase() {
            @Override
            protected String getLogTag() {
                return "test";
            }
        };
        // Abstract namespace socket path (leading null byte) avoids touching the filesystem via
        // FileUtils at all (see beads-94h), keeping this test fully deterministic.
        LocalSocketRunConfig config = new LocalSocketRunConfig("test", "\0test-socket", client);
        return new LocalSocketManager(RuntimeEnvironment.getApplication(), config);
    }

    @Test
    public void startOrThrow_throwsWhenNativeLibraryUnavailable() {
        LocalSocketManager manager = newManager();
        assertThrows(TermuxException.class, manager::startOrThrow);
    }

    @Test
    public void stopOrThrow_doesNotThrowWhenNeverStarted() throws TermuxException {
        LocalSocketManager manager = newManager();
        manager.stopOrThrow();
    }
}
