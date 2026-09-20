package com.termux.shared.net.socket.local;

import static org.junit.Assert.assertThrows;

import com.termux.shared.errors.TermuxException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Unit tests for the startOrThrow/stopOrThrow/closeServerSocketOrThrow sibling methods added for
 * beads-jw0. See LocalSocketManagerOrThrowTest for why startOrThrow() deterministically throws
 * under this test runner (missing native "local-socket" JNI library, not a flaky failure).
 */
@RunWith(RobolectricTestRunner.class)
public class LocalServerSocketOrThrowTest {

    private LocalServerSocket newServerSocket() {
        LocalSocketManagerClientBase client = new LocalSocketManagerClientBase() {
            @Override
            protected String getLogTag() {
                return "test";
            }
        };
        // Abstract namespace socket path avoids touching the filesystem via FileUtils entirely
        // (see beads-94h), keeping this test fully deterministic.
        LocalSocketRunConfig config = new LocalSocketRunConfig("test", "\0test-socket", client);
        LocalSocketManager manager = new LocalSocketManager(RuntimeEnvironment.getApplication(), config);
        return manager.getServerSocket();
    }

    @Test
    public void startOrThrow_throwsWhenNativeLibraryUnavailable() {
        LocalServerSocket serverSocket = newServerSocket();
        assertThrows(TermuxException.class, serverSocket::startOrThrow);
    }

    @Test
    public void closeServerSocketOrThrow_doesNotThrowWhenNeverStarted() throws TermuxException {
        LocalServerSocket serverSocket = newServerSocket();
        serverSocket.closeServerSocketOrThrow(true);
    }

    @Test
    public void stopOrThrow_doesNotThrowWhenNeverStartedAndAbstractNamespace() throws TermuxException {
        // fd is -1 (never started), so closeServerSocket() is a no-op, and deleteServerSocketFile()
        // returns immediately for an abstract namespace socket without touching FileUtils at all.
        LocalServerSocket serverSocket = newServerSocket();
        serverSocket.stopOrThrow();
    }
}
