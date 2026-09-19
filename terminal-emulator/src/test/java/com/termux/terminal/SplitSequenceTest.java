package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/**
 * Verifies that the VT100 / ANSI escape-sequence parser produces identical output whether a
 * sequence arrives in a single {@link TerminalEmulator#append} call or is split across many
 * one-byte calls (simulating TCP/PTY read-boundary fragmentation).
 *
 * <p>Every test follows the pattern:
 * <ol>
 *   <li>Run the sequence normally via {@link #enterString} (single append).</li>
 *   <li>Run the same sequence via {@link #enterStringSplit} (one byte per append).</li>
 *   <li>Assert both produce the same screen state.</li>
 * </ol>
 */
public class SplitSequenceTest extends TerminalTestCase {

    // -----------------------------------------------------------------------
    // SGR — Select Graphic Rendition (colour / attribute codes)
    // -----------------------------------------------------------------------

    /** Basic 8-colour SGR: ESC [ 3 1 m  →  foreground red. */
    public void testSplitSgrForegroundRed() {
        withTerminalSized(10, 3);
        enterString("\033[31mHi\033[0m");
        int redRow0Col0 = TextStyle.decodeForeColor(getStyleAt(0, 0));
        int redRow0Col1 = TextStyle.decodeForeColor(getStyleAt(0, 1));
        assertLineIs(0, "Hi        ");

        withTerminalSized(10, 3);
        enterStringSplit("\033[31mHi\033[0m");
        assertEquals(redRow0Col0, TextStyle.decodeForeColor(getStyleAt(0, 0)));
        assertEquals(redRow0Col1, TextStyle.decodeForeColor(getStyleAt(0, 1)));
        assertLineIs(0, "Hi        ");
    }

    /** Multi-parameter SGR: ESC [ 1 ; 3 1 m  →  bold + red. */
    public void testSplitSgrBoldRed() {
        withTerminalSized(10, 3);
        enterString("\033[1;31mX\033[0m");
        long styleNormal = getStyleAt(0, 0);

        withTerminalSized(10, 3);
        enterStringSplit("\033[1;31mX\033[0m");
        assertEquals(styleNormal, getStyleAt(0, 0));
        assertLineIs(0, "X         ");
    }

    /** True-colour (24-bit) SGR: ESC [ 38 ; 2 ; 255 ; 0 ; 0 m — longer sequence, more split points. */
    public void testSplitSgrTrueColor() {
        withTerminalSized(10, 3);
        enterString("\033[38;2;200;100;50mA\033[0m");
        long styleNormal = getStyleAt(0, 0);
        int fgNormal     = TextStyle.decodeForeColor(styleNormal);

        withTerminalSized(10, 3);
        enterStringSplit("\033[38;2;200;100;50mA\033[0m");
        assertEquals(fgNormal, TextStyle.decodeForeColor(getStyleAt(0, 0)));
        assertLineIs(0, "A         ");
    }

    // -----------------------------------------------------------------------
    // Cursor movement CSI sequences
    // -----------------------------------------------------------------------

    /** ESC [ 3 A  →  cursor up 3. */
    public void testSplitCursorUp() {
        withTerminalSized(10, 5);
        enterString("abc\r\ndef\r\nghi\r\nXXX\033[3AQ");
        assertCursorAt(0, 1); // moved up 3 from row 3 → row 0, col advanced by Q
        assertLineIs(0, "QbcXXX    ");

        withTerminalSized(10, 5);
        enterStringSplit("abc\r\ndef\r\nghi\r\nXXX\033[3AQ");
        assertCursorAt(0, 1);
        assertLineIs(0, "QbcXXX    ");
    }

    /** ESC [ 2 ; 5 H  →  CUP (cursor position) to row 2, col 5. */
    public void testSplitCursorPosition() {
        withTerminalSized(10, 5);
        enterString("\033[2;5HX");
        assertCursorAt(1, 5);

        withTerminalSized(10, 5);
        enterStringSplit("\033[2;5HX");
        assertCursorAt(1, 5);
    }

    // -----------------------------------------------------------------------
    // DECSET / DECRST private mode sequences
    // -----------------------------------------------------------------------

    /** ESC [ ? 2 5 h  →  show cursor. Split at every byte. */
    public void testSplitDecsetShowCursor() {
        // Just verify no corrupted state is left after a DECSET split — if the
        // parser gets confused it will emit garbage characters to the screen.
        withTerminalSized(10, 3);
        enterString("\033[?25h");
        assertLineIs(0, "          ");

        withTerminalSized(10, 3);
        enterStringSplit("\033[?25h");
        assertLineIs(0, "          ");
    }

    // -----------------------------------------------------------------------
    // OSC — Operating System Command
    // -----------------------------------------------------------------------

    /** ESC ] 0 ; t i t l e BEL  →  set window title. */
    public void testSplitOscTitle() {
        withTerminalSized(10, 3);
        enterString("\033]0;Hello\007");
        assertEquals("Hello", mTerminal.getTitle());

        withTerminalSized(10, 3);
        enterStringSplit("\033]0;Hello\007");
        assertEquals("Hello", mTerminal.getTitle());
    }

    /** OSC with ST (ESC \) terminator instead of BEL, split across the ESC \ boundary. */
    public void testSplitOscWithStringTerminator() {
        withTerminalSized(10, 3);
        enterString("\033]0;World\033\\");
        assertEquals("World", mTerminal.getTitle());

        withTerminalSized(10, 3);
        enterStringSplit("\033]0;World\033\\");
        assertEquals("World", mTerminal.getTitle());
    }

    // -----------------------------------------------------------------------
    // UTF-8 multi-byte characters split across reads
    // -----------------------------------------------------------------------

    /** Two-byte UTF-8 sequence (U+00E9 é) split between the two bytes. */
    public void testSplitUtf8TwoByte() {
        withTerminalSized(5, 2);
        enterString("caf\u00e9");          // café — é is U+00E9, UTF-8: 0xC3 0xA9
        assertLineIs(0, "caf\u00e9 ");

        withTerminalSized(5, 2);
        byte[] bytes = "caf\u00e9".getBytes(StandardCharsets.UTF_8);
        // split at every boundary, including inside the 2-byte 'é'
        for (int i = 0; i < bytes.length; i++) {
            mTerminal.append(bytes, i, 1);
        }
        assertInvariants();
        assertLineIs(0, "caf\u00e9 ");
    }

    /** Three-byte UTF-8 sequence (U+4E2D 中) split at each of the 3 byte positions. */
    public void testSplitUtf8ThreeByte() {
        withTerminalSized(5, 2);
        enterString("\u4e2d");  // 中 — UTF-8: 0xE4 0xB8 0xAD
        String expected0;
        {
            char[] ch = mTerminal.getScreen().mLines[0].mText;
            int used = mTerminal.getScreen().mLines[0].getSpaceUsed();
            expected0 = new String(ch, 0, used);
        }

        withTerminalSized(5, 2);
        enterStringSplit("\u4e2d");
        {
            char[] ch = mTerminal.getScreen().mLines[0].mText;
            int used = mTerminal.getScreen().mLines[0].getSpaceUsed();
            assertEquals(expected0, new String(ch, 0, used));
        }
    }

    // -----------------------------------------------------------------------
    // Erase sequences
    // -----------------------------------------------------------------------

    /** ESC [ 2 J  →  erase entire display. */
    public void testSplitEraseDisplay() {
        withTerminalSized(5, 3);
        enterString("ABCDE\r\n");
        enterString("\033[2J");
        assertLineIs(0, "     ");

        withTerminalSized(5, 3);
        enterString("ABCDE\r\n");
        enterStringSplit("\033[2J");
        assertLineIs(0, "     ");
    }

    // -----------------------------------------------------------------------
    // Regression: subsequent normal text must not be eaten by a stale parser
    // state left over from a split sequence.
    // -----------------------------------------------------------------------

    /**
     * After a split colour sequence the parser must return to ESC_NONE so that
     * subsequent plain text is emitted normally and not consumed as escape args.
     */
    public void testNoStaleEscapeStateAfterSplitSgr() {
        withTerminalSized(10, 3);
        // Apply colour, then immediately write plain ASCII — must appear on screen.
        enterStringSplit("\033[32m");   // SGR green, split byte by byte
        enterString("OK\033[0m");
        assertLineIs(0, "OK        ");
    }

    /**
     * After a split OSC sequence the parser must return to ESC_NONE so that
     * subsequent plain text is emitted normally.
     */
    public void testNoStaleEscapeStateAfterSplitOsc() {
        withTerminalSized(10, 3);
        enterStringSplit("\033]0;title\007");  // set title, split byte by byte
        enterString("Hi");
        assertLineIs(0, "Hi        ");
    }

    /**
     * ESC alone (no following byte yet) must not corrupt the screen — once the
     * sequence completes in a later append call it must work as normal.
     */
    public void testEscAloneFollowedByRest() {
        withTerminalSized(10, 3);
        // ESC in one call, then [A (cursor-up 1) in the next
        mTerminal.append(new byte[]{0x1b}, 1);               // ESC
        mTerminal.append(new byte[]{'[', 'A'}, 2);           // [A
        assertInvariants();
        // Cursor stayed at row 0 because cursor-up on row 0 is a no-op
        assertCursorAt(0, 0);
        assertLineIs(0, "          ");
    }

    /**
     * CSI introducer alone (ESC [) arriving in one read, digits + final byte in the next.
     */
    public void testCsiIntroducerAlone() {
        withTerminalSized(10, 5);
        enterString("line1\r\nline2\r\nline3\r\n");

        // ESC [ alone, then 2A (cursor up 2) in the next read
        mTerminal.append(new byte[]{0x1b, '['}, 2);
        mTerminal.append(new byte[]{'2', 'A'}, 2);
        assertInvariants();
        assertCursorAt(1, 0);  // was at row 3, moved up 2 → row 1
    }
}
