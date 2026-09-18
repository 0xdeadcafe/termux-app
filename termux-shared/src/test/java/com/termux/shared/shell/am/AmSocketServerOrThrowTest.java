package com.termux.shared.shell.am;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.errors.TermuxException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for the parseAmCommandOrThrow/runAmCommandOrThrow sibling methods added for
 * beads-q2n (AmSocketServerErrno migration, part of the beads-xs0 Errno/Error deprecate-first
 * migration family).
 */
@RunWith(RobolectricTestRunner.class)
public class AmSocketServerOrThrowTest {

    @Test
    public void parseAmCommandOrThrow_doesNotThrowForNullOrEmptyString() throws TermuxException {
        List<String> args = new ArrayList<>();
        AmSocketServer.parseAmCommandOrThrow(null, args);
        AmSocketServer.parseAmCommandOrThrow("", args);
        assertTrue(args.isEmpty());
    }

    @Test
    public void parseAmCommandOrThrow_tokenizesQuotedArguments() throws TermuxException {
        List<String> args = new ArrayList<>();
        AmSocketServer.parseAmCommandOrThrow("start -n \"com.example/.MainActivity\" --user 0", args);
        assertEquals("start", args.get(0));
        assertEquals("-n", args.get(1));
        assertEquals("com.example/.MainActivity", args.get(2));
        assertEquals("--user", args.get(3));
        assertEquals("0", args.get(4));
    }

    @Test
    public void runAmCommandOrThrow_capturesOutputForUnknownCommand() throws TermuxException {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        // termux-am-library's Am.run() writes usage/error info to the provided PrintStreams for
        // an unrecognized command rather than throwing, so this should not throw and should
        // capture something in stdout/stderr instead.
        AmSocketServer.runAmCommandOrThrow(RuntimeEnvironment.getApplication(),
            new String[] {"definitely-not-a-real-am-command"}, stdout, stderr, false);

        assertTrue((stdout.length() + stderr.length()) > 0);
    }
}
