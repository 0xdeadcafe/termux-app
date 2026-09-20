package com.termux.shared.net.socket.local;

import static org.junit.Assert.assertThrows;

import com.termux.shared.errors.TermuxException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Unit tests for the read/send/available/setReadTimeout/setWriteTimeout/closeClientSocket/
 * readDataOnInputStream/sendDataToOutputStream OrThrow siblings added for beads-jw0.
 *
 * <p>All tests use fd=-1 (the value for a never-connected/already-closed client socket), which
 * lets every method's "invalid fd" branch be exercised without needing the real JNI native
 * library ("local-socket") that isn't available in this test environment. This is deterministic,
 * not a workaround for a Robolectric limitation like beads-94h.
 */
@RunWith(RobolectricTestRunner.class)
public class LocalClientSocketOrThrowTest {

    private LocalClientSocket newClientSocketWithInvalidFd() {
        LocalSocketManagerClientBase client = new LocalSocketManagerClientBase() {
            @Override
            protected String getLogTag() {
                return "test";
            }
        };
        LocalSocketRunConfig config = new LocalSocketRunConfig("test", "\0test-socket", client);
        LocalSocketManager manager = new LocalSocketManager(RuntimeEnvironment.getApplication(), config);
        // fd = -1 -> setFD() in the constructor normalizes any negative value to -1.
        return new LocalClientSocket(manager, -1, new PeerCred());
    }

    @Test
    public void readOrThrow_throwsForInvalidFd() {
        LocalClientSocket socket = newClientSocketWithInvalidFd();
        assertThrows(TermuxException.class, () ->
            socket.readOrThrow(new byte[8], new LocalClientSocket.MutableInt(0)));
    }

    @Test
    public void sendOrThrow_throwsForInvalidFd() {
        LocalClientSocket socket = newClientSocketWithInvalidFd();
        assertThrows(TermuxException.class, () -> socket.sendOrThrow(new byte[]{1, 2, 3}));
    }

    @Test
    public void availableOrThrow_oneArg_throwsForInvalidFd() {
        LocalClientSocket socket = newClientSocketWithInvalidFd();
        assertThrows(TermuxException.class, () ->
            socket.availableOrThrow(new LocalClientSocket.MutableInt(0)));
    }

    @Test
    public void availableOrThrow_twoArg_throwsForInvalidFd() {
        LocalClientSocket socket = newClientSocketWithInvalidFd();
        assertThrows(TermuxException.class, () ->
            socket.availableOrThrow(new LocalClientSocket.MutableInt(0), false));
    }

    @Test
    public void setReadTimeoutOrThrow_doesNotThrowForInvalidFd() throws TermuxException {
        // setReadTimeout() only attempts the native call if fd >= 0; for fd == -1 it's a no-op
        // success, so this exercises the OrThrow "no error" path without needing the native lib.
        LocalClientSocket socket = newClientSocketWithInvalidFd();
        socket.setReadTimeoutOrThrow();
    }

    @Test
    public void setWriteTimeoutOrThrow_doesNotThrowForInvalidFd() throws TermuxException {
        LocalClientSocket socket = newClientSocketWithInvalidFd();
        socket.setWriteTimeoutOrThrow();
    }

    @Test
    public void closeClientSocketOrThrow_doesNotThrowForInvalidFd() throws TermuxException {
        // close() only attempts the native call if fd >= 0; for fd == -1 it's a no-op success.
        LocalClientSocket socket = newClientSocketWithInvalidFd();
        socket.closeClientSocketOrThrow(true);
    }

    @Test
    public void readDataOnInputStreamOrThrow_throwsForInvalidFd() {
        LocalClientSocket socket = newClientSocketWithInvalidFd();
        assertThrows(TermuxException.class, () ->
            socket.readDataOnInputStreamOrThrow(new StringBuilder(), true));
    }

    @Test
    public void sendDataToOutputStreamOrThrow_throwsForInvalidFd() {
        LocalClientSocket socket = newClientSocketWithInvalidFd();
        assertThrows(TermuxException.class, () ->
            socket.sendDataToOutputStreamOrThrow("hello", true));
    }
}
