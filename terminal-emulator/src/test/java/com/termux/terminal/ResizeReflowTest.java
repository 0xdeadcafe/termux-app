package com.termux.terminal;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the terminal reflow system, verifying that content, cursor, and
 * scrollback history are correctly preserved when the terminal is resized.
 *
 * <p>Nine of the ten tests are currently {@link Ignore @Ignore}d because
 * {@code TerminalBuffer.resize()} does not yet implement reflow — it wipes
 * scrollback history and misplaces the cursor on column changes.
 * The implementation is tracked in <b>termux-app-1qb</b>.
 * Remove the {@code @Ignore} annotations when that task is merged.
 */
@RunWith(JUnit4.class)
public class ResizeReflowTest extends TerminalTestCase {

    /** Bridge JUnit-3 setUp() into the JUnit-4 lifecycle. */
    @Before
    public void initTerminalTestCase() throws Exception {
        setUp();
    }

    /**
     * Narrow → Wide → Narrow round-trip should preserve all content.
     * Also verifies scrollback history is retained after column changes.
     */
    @Ignore("termux-app-1qb: reflowResize() not yet implemented")
    @Test
    public void testReflowRoundTripPreservesContent() {
        final int cols = 5, rows = 3;
        withTerminalSized(cols, rows);
        // Fill screen and scroll a couple lines into history
        enterString("AAAAA\r\nBBBBB\r\nCCCCC\r\nDDDDD\r\nEEEEE");
        assertLinesAre("CCCCC", "DDDDD", "EEEEE");
        assertHistoryStartsWith("AAAAA", "BBBBB");

        // Widen then narrow
        resize(8, rows).assertLinesAre("CCCCC   ", "DDDDD   ", "EEEEE   ");
        assertHistoryStartsWith("AAAAA   ", "BBBBB   ");
        resize(cols, rows).assertLinesAre("CCCCC", "DDDDD", "EEEEE");
        assertHistoryStartsWith("AAAAA", "BBBBB");
    }

    /**
     * Width changes should preserve scrollback history access.
     */
    @Ignore("termux-app-1qb: reflowResize() not yet implemented")
    @Test
    public void testReflowPreservesHistory() {
        final int cols = 10, rows = 3;
        withTerminalSized(cols, rows);
        // Generate enough content to have history
        for (int i = 0; i < 10; i++) {
            enterString("Line" + i + "\r\n");
        }
        // Last 3 rows are on screen
        assertLinesAre("Line7     ", "Line8     ", "Line9     ");

        // Widen - history should still be accessible
        resize(15, rows);
        String transcript = mTerminal.getScreen().getTranscriptText();
        assertTrue("History should contain Line0 after widen", transcript.contains("Line0"));
        assertTrue("History should contain Line6 after widen", transcript.contains("Line6"));

        // Back to original
        resize(cols, rows);
        transcript = mTerminal.getScreen().getTranscriptText();
        assertTrue("History should contain Line0 after round-trip", transcript.contains("Line0"));
    }

    /**
     * Content should remain correct after narrowing.
     * Each 5-column row becomes 2 rows at 3 columns.
     */
    @Ignore("termux-app-1qb: reflowResize() not yet implemented")
    @Test
    public void testReflowNarrowing() {
        withTerminalSized(5, 4);
        enterString("ABCDE\r\nFGHIJ");
        assertLinesAre("ABCDE", "FGHIJ", "     ", "     ");
        assertLineWraps(false, false, false, false);

        resize(3, 4);
        // Each row in old width becomes 2 rows at new width
        // ABCDE -> ABC, DE
        // FGHIJ -> FGH, IJ
        // Total: 4 rows, cursor at FGH line
        assertLinesAre("ABC", "DE ", "FGH", "IJ ");
        assertLineWraps(true, false, true, false);
    }

    /**
     * Content should remain correct after widening.
     */
    @Test
    public void testReflowWidening() {
        withTerminalSized(3, 3);
        enterString("ABC\r\nDEF");
        resize(5, 3);
        assertLinesAre("ABC  ", "DEF  ", "     ");
    }

    /**
     * Wrapped lines should be rejoined and re-split correctly.
     */
    @Ignore("termux-app-1qb: reflowResize() not yet implemented")
    @Test
    public void testReflowWrappedLines() {
        // Create a long line that wraps at 5 columns
        withTerminalSized(5, 4);
        enterString("1234567890");
        // "12345", "67890" with linewrap
        assertLinesAre("12345", "67890", "     ", "     ");
        assertLineWraps(true, false, false, false);

        // Widen to 8 columns - the two wrapped rows should rejoin to 1 row
        resize(8, 4);
        assertLinesAre("12345678", "90      ", "        ", "        ");
        assertLineWraps(true, false, false, false);

        // Narrow to 3 columns
        resize(3, 4);
        assertLinesAre("123", "456", "789", "0  ");
        assertLineWraps(true, true, true, false);
    }

    /**
     * Cursor position should be accurate after reflow.
     */
    @Ignore("termux-app-1qb: reflowResize() not yet implemented")
    @Test
    public void testReflowPreservesCursor() {
        withTerminalSized(8, 3);
        enterString("Hello");
        assertCursorAt(0, 5);

        // Widen - cursor should stay at same column
        resize(12, 3);
        assertCursorAt(0, 5);

        // Type more
        enterString(" World");
        assertCursorAt(0, 11);

        // Narrow - cursor should be at correct new position
        resize(6, 3);
        // Text wraps: "Hello " fits on row 0 with wrap, "World" on row 1
        assertCursorAt(1, 0);
    }

    /**
     * Combining characters should survive reflow.
     */
    @Ignore("termux-app-1qb: reflowResize() not yet implemented")
    @Test
    public void testReflowWithCombiningChars() {
        withTerminalSized(3, 3);
        enterString("A\u0302BC\u0308DEF");
        assertLinesAre("A\u0302B", "C\u0308D", "EF ");
        resize(4, 3);
        assertLinesAre("A\u0302BC\u0308", "DEF ", "    ");
    }

    /**
     * CJK wide characters should be correctly reflowed.
     */
    @Ignore("termux-app-1qb: reflowResize() not yet implemented")
    @Test
    public void testReflowWithWideChars() {
        final int cols = 4, rows = 3;
        withTerminalSized(cols, rows);
        enterString("\uFF21\uFF22");  // 2 wide chars (each 2 cols)
        assertLinesAre("\uFF21\uFF22 ", "    ", "    ");
        resize(2, rows);
        assertLinesAre("\uFF21", "\uFF22", "  ");
        resize(cols, rows);
        assertLinesAre("\uFF21\uFF22 ", "    ", "    ");
    }

    /**
     * Large scrollback reflow should complete quickly.
     */
    @Ignore("termux-app-1qb: reflowResize() not yet implemented")
    @Test
    public void testReflowLargeScrollback() {
        final int cols = 80, rows = 24;
        withTerminalSized(cols, rows);
        // Generate ~2000 lines of content
        for (int i = 0; i < 2100; i++) {
            enterString("Line " + String.format("%04d", i) + "\r\n");
        }
        assertLinesAre("Line 2097", "Line 2098", "Line 2099",
            "        ", "        ", "        ",
            "        ", "        ", "        ",
            "        ", "        ", "        ",
            "        ", "        ", "        ",
            "        ", "        ", "        ",
            "        ", "        ", "        ",
            "        ", "        ", "        ");

        long startTime = System.currentTimeMillis();
        resize(40, 24);
        long elapsed = System.currentTimeMillis() - startTime;
        assertTrue("Reflow of 2000+ rows should complete in < 500ms, took " + elapsed + "ms",
            elapsed < 500);

        // Content should still be valid
        String transcript = mTerminal.getScreen().getTranscriptText();
        assertTrue("History preserved after reflow", transcript.contains("Line 0000"));
        assertTrue("Recent history preserved", transcript.contains("Line 2096"));
    }

    /**
     * Alt buffer should not lose content on resize.
     */
    @Ignore("termux-app-1qb: reflowResize() not yet implemented")
    @Test
    public void testReflowAltBuffer() {
        final int rows = 3, cols = 3;
        withTerminalSized(cols, rows);
        enterString("a\r\ndef$").assertLinesAre("a  ", "def", "$  ");

        // Switch to alt buffer
        enterString("\033[?1049h").assertLinesAre("   ", "   ", "   ");
        enterString("h").assertLinesAre("   ", "   ", "h  ");

        // Resize and back
        resize(cols, 5).resize(cols, rows);
        assertLinesAre("   ", "   ", "h  ");

        // Switch back from alt buffer
        enterString("\033[?1049l").assertLinesAre("a  ", "def", "$  ");
    }

}
