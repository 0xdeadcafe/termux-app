package com.termux.terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;

import android.graphics.Bitmap;
import android.graphics.Rect;

import android.os.SystemClock;

/**
 * A circular buffer of {@link TerminalRow}:s which keeps notes about what is visible on a logical screen and the scroll
 * history.
 * <p>
 * See {@link #externalToInternalRow(int)} for how to map from logical screen rows to array indices.
 */
public final class TerminalBuffer {

    public static final String LOG_TAG = "TerminalBuffer";



    private TerminalSessionClient mClient;

    TerminalRow[] mLines;
    /** The length of {@link #mLines}. */
    int mTotalRows;
    /** The number of rows and columns visible on the screen. */
    int mScreenRows, mColumns;
    /** The number of rows kept in history. */
    private int mActiveTranscriptRows = 0;
    /** The index in the circular buffer where the visible screen starts. */
    private int mScreenFirstRow = 0;


    /**
     * The {@link TerminalSixel} if a sixel command is being processed, from which the final
     * {@link TerminalBitmap} is created.
     */
    private TerminalSixel mTerminalSixel;


    /** The map for bitmap number to the {@link TerminalBitmap} loaded in the terminal. */
    private final HashMap<Integer, TerminalBitmap> mTerminalBitmaps;

    /** The time since last garbage collection for all the {@link TerminalBitmap} that are loaded in the terminal. */
    private long mTerminalBitmapsLastGC;

    /**
     * The bitmap number start for {@link #mTerminalBitmaps} keys.
     *
     * The bitmap number and coordinates are encoded in the `long` {@link TerminalRow#mStyle} for
     * the `TerminalRow` character of a column by
     * {@link TerminalBitmap#buildOrThrow(TerminalBuffer, int, Bitmap, int, int, int, int)} by
     * getting encoded value from {@link TextStyle#encodeTerminalBitmap(int, int, int)}.
     * The `TerminalRenderer.render()` then checks during rendering terminal output whether a
     * character at a row/coloumn index is a bitmap instead of text by calling
     * `TextStyle.isTerminalBitmap()`.
     */
    public static final int TERMINAL_BITMAP__NUM_START = 0;

    /**
     * The bitmap number end for {@link #mTerminalBitmaps} keys.
     */
    public static final int TERMINAL_BITMAP__NUM_END = Integer.MAX_VALUE;




    public TerminalBuffer(int columns, int totalRows, int screenRows) {
        this(null, columns, totalRows, screenRows);
    }

    /**
     * Create a transcript screen.
     *
     * @param client    the {@link TerminalSessionClient}.
     * @param columns    the width of the screen in characters.
     * @param totalRows  the height of the entire text area, in rows of text.
     * @param screenRows the height of just the screen, not including the transcript that holds lines that have scrolled off
     *                   the top of the screen.
     */
    public TerminalBuffer(TerminalSessionClient client, int columns, int totalRows, int screenRows) {
        mClient = client;

        mColumns = columns;
        mTotalRows = totalRows;
        mScreenRows = screenRows;
        mLines = new TerminalRow[totalRows];

        blockSet(0, 0, columns, screenRows, ' ', TextStyle.NORMAL);
        mTerminalBitmaps = new HashMap<>();
        mTerminalBitmapsLastGC = SystemClock.uptimeMillis();
    }



    public TerminalSessionClient getClient() {
        return mClient;
    }


    public String getTranscriptText() {
        return getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows).trim();
    }

    public String getTranscriptTextWithoutJoinedLines() {
        return getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows, false).trim();
    }

    public String getTranscriptTextWithFullLinesJoined() {
        return getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows, true, true).trim();
    }

    public String getSelectedText(int selX1, int selY1, int selX2, int selY2) {
        return getSelectedText(selX1, selY1, selX2, selY2, true);
    }

    public String getSelectedText(int selX1, int selY1, int selX2, int selY2, boolean joinBackLines) {
        return getSelectedText(selX1, selY1, selX2, selY2, joinBackLines, false);
    }

    public String getSelectedText(int selX1, int selY1, int selX2, int selY2, boolean joinBackLines, boolean joinFullLines) {
        final StringBuilder builder = new StringBuilder();
        final int columns = mColumns;

        if (selY1 < -getActiveTranscriptRows()) selY1 = -getActiveTranscriptRows();
        if (selY2 >= mScreenRows) selY2 = mScreenRows - 1;

        for (int row = selY1; row <= selY2; row++) {
            int x1 = (row == selY1) ? selX1 : 0;
            int x2;
            if (row == selY2) {
                x2 = selX2 + 1;
                if (x2 > columns) x2 = columns;
            } else {
                x2 = columns;
            }
            TerminalRow lineObject = mLines[externalToInternalRow(row)];
            int x1Index = lineObject.findStartOfColumn(x1);
            int x2Index = (x2 < mColumns) ? lineObject.findStartOfColumn(x2) : lineObject.getSpaceUsed();
            if (x2Index == x1Index) {
                // Selected the start of a wide character.
                x2Index = lineObject.findStartOfColumn(x2 + 1);
            }
            char[] line = lineObject.mText;
            int lastPrintingCharIndex = -1;
            int i;
            boolean rowLineWrap = getLineWrap(row);
            if (rowLineWrap && x2 == columns) {
                // If the line was wrapped, we shouldn't lose trailing space:
                lastPrintingCharIndex = x2Index - 1;
            } else {
                for (i = x1Index; i < x2Index; ++i) {
                    char c = line[i];
                    if (c != ' ') lastPrintingCharIndex = i;
                }
            }

            int len = lastPrintingCharIndex - x1Index + 1;
            if (lastPrintingCharIndex != -1 && len > 0)
                builder.append(line, x1Index, len);

            boolean lineFillsWidth = lastPrintingCharIndex == x2Index - 1;
            if ((!joinBackLines || !rowLineWrap) && (!joinFullLines || !lineFillsWidth)
                && row < selY2 && row < mScreenRows - 1) builder.append('\n');
        }
        return builder.toString();
    }

    public String getWordAtLocation(int x, int y) {
        // Set y1 and y2 to the lines where the wrapped line starts and ends.
        // I.e. if a line that is wrapped to 3 lines starts at line 4, and this
        // is called with y=5, then y1 would be set to 4 and y2 would be set to 6.
        int y1 = y;
        int y2 = y;
        while (y1 > 0 && !getSelectedText(0, y1 - 1, mColumns, y, true, true).contains("\n")) {
            y1--;
        }
        while (y2 < mScreenRows && !getSelectedText(0, y, mColumns, y2 + 1, true, true).contains("\n")) {
            y2++;
        }

        // Get the text for the whole wrapped line
        String text = getSelectedText(0, y1, mColumns, y2, true, true);
        // The index of x in text
        int textOffset = (y - y1) * mColumns + x;

        if (textOffset >= text.length()) {
          // The click was to the right of the last word on the line, so
          // there's no word to return
          return "";
        }

        // Set x1 and x2 to the indices of the last space before x and the
        // first space after x in text respectively
        int x1 = text.lastIndexOf(' ', textOffset);
        int x2 = text.indexOf(' ', textOffset);
        if (x2 == -1) {
            x2 = text.length();
        }

        if (x1 == x2) {
          // The click was on a space, so there's no word to return
          return "";
        }
        return text.substring(x1 + 1, x2);
    }

    public int getActiveTranscriptRows() {
        return mActiveTranscriptRows;
    }

    public int getActiveRows() {
        return mActiveTranscriptRows + mScreenRows;
    }

    /**
     * Convert a row value from the public external coordinate system to our internal private coordinate system.
     *
     * <pre>
     * - External coordinate system: -mActiveTranscriptRows to mScreenRows-1, with the screen being 0..mScreenRows-1.
     * - Internal coordinate system: the mScreenRows lines starting at mScreenFirstRow comprise the screen, while the
     *   mActiveTranscriptRows lines ending at mScreenFirstRow-1 form the transcript (as a circular buffer).
     *
     * External ↔ Internal:
     *
     * [ ...                            ]     [ ...                                     ]
     * [ -mActiveTranscriptRows         ]     [ mScreenFirstRow - mActiveTranscriptRows ]
     * [ ...                            ]     [ ...                                     ]
     * [ 0 (visible screen starts here) ]  ↔  [ mScreenFirstRow                         ]
     * [ ...                            ]     [ ...                                     ]
     * [ mScreenRows-1                  ]     [ mScreenFirstRow + mScreenRows-1         ]
     * </pre>
     *
     * @param externalRow a row in the external coordinate system.
     * @return The row corresponding to the input argument in the private coordinate system.
     */
    public int externalToInternalRow(int externalRow) {
        if (externalRow < -mActiveTranscriptRows || externalRow > mScreenRows)
            throw new IllegalArgumentException("extRow=" + externalRow + ", mScreenRows=" + mScreenRows + ", mActiveTranscriptRows=" + mActiveTranscriptRows);
        final int internalRow = mScreenFirstRow + externalRow;
        return (internalRow < 0) ? (mTotalRows + internalRow) : (internalRow % mTotalRows);
    }

    public void setLineWrap(int row) {
        mLines[externalToInternalRow(row)].mLineWrap = true;
    }

    public boolean getLineWrap(int row) {
        return mLines[externalToInternalRow(row)].mLineWrap;
    }

    public void clearLineWrap(int row) {
        mLines[externalToInternalRow(row)].mLineWrap = false;
    }

    /**
     * Resize the screen which this transcript backs. Currently, this only works if the number of columns does not
     * change or the rows expand (that is, it only works when shrinking the number of rows).
     *
     * @param newColumns The number of columns the screen should have.
     * @param newRows    The number of rows the screen should have.
     * @param cursor     An int[2] containing the (column, row) cursor location.
     */
    public void resize(int newColumns, int newRows, int newTotalRows, int[] cursor, long currentStyle, boolean altScreen) {
        // newRows > mTotalRows should not normally happen since mTotalRows is TRANSCRIPT_ROWS (10000):
        if (newColumns == mColumns && newRows <= mTotalRows) {
            // Fast resize where just the rows changed.
            int shiftDownOfTopRow = mScreenRows - newRows;
            if (shiftDownOfTopRow > 0 && shiftDownOfTopRow < mScreenRows) {
                // Shrinking. Check if we can skip blank rows at bottom below cursor.
                for (int i = mScreenRows - 1; i > 0; i--) {
                    if (cursor[1] >= i) break;
                    int r = externalToInternalRow(i);
                    if (mLines[r] == null || mLines[r].isBlank()) {
                        if (--shiftDownOfTopRow == 0) break;
                    }
                }
            } else if (shiftDownOfTopRow < 0) {
                // Negative shift down = expanding. Only move screen up if there is transcript to show:
                int actualShift = Math.max(shiftDownOfTopRow, -mActiveTranscriptRows);
                if (shiftDownOfTopRow != actualShift) {
                    // The new lines revealed by the resizing are not all from the transcript. Blank the below ones.
                    for (int i = 0; i < actualShift - shiftDownOfTopRow; i++)
                        allocateFullLineIfNecessary((mScreenFirstRow + mScreenRows + i) % mTotalRows).clear(currentStyle);
                    shiftDownOfTopRow = actualShift;
                }
            }
            mScreenFirstRow += shiftDownOfTopRow;
            mScreenFirstRow = (mScreenFirstRow < 0) ? (mScreenFirstRow + mTotalRows) : (mScreenFirstRow % mTotalRows);
            mTotalRows = newTotalRows;
            mActiveTranscriptRows = altScreen ? 0 : Math.max(0, mActiveTranscriptRows + shiftDownOfTopRow);
            cursor[1] -= shiftDownOfTopRow;
            mScreenRows = newRows;
        } else {
            // Reflow: repaginate logical lines, preserving scrollback history
            reflowResize(newColumns, newRows, newTotalRows, cursor, currentStyle, altScreen);
        }

        // Handle cursor scrolling off screen:
        if (cursor[0] < 0 || cursor[1] < 0) cursor[0] = cursor[1] = 0;
    }

    /**
     * Block copy lines and associated metadata from one location to another in the circular buffer, taking wraparound
     * into account.
     *
     * @param srcInternal The first line to be copied.
     * @param len         The number of lines to be copied.
     */
    private void blockCopyLinesDown(int srcInternal, int len) {
        if (len == 0) return;
        int totalRows = mTotalRows;

        int start = len - 1;
        // Save away line to be overwritten:
        TerminalRow lineToBeOverWritten = mLines[(srcInternal + start + 1) % totalRows];
        // Do the copy from bottom to top.
        for (int i = start; i >= 0; --i)
            mLines[(srcInternal + i + 1) % totalRows] = mLines[(srcInternal + i) % totalRows];
        // Put back overwritten line, now above the block:
        mLines[(srcInternal) % totalRows] = lineToBeOverWritten;
    }

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line screen, the arguments would be (0, 24).
     *
     * @param topMargin    First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style        the style for the newly exposed line.
     */
    public void scrollDownOneLine(int topMargin, int bottomMargin, long style) {
        if (topMargin > bottomMargin - 1 || topMargin < 0 || bottomMargin > mScreenRows)
            throw new IllegalArgumentException("topMargin=" + topMargin + ", bottomMargin=" + bottomMargin + ", mScreenRows=" + mScreenRows);

        // Copy the fixed topMargin lines one line down so that they remain on screen in same position:
        blockCopyLinesDown(mScreenFirstRow, topMargin);
        // Copy the fixed mScreenRows-bottomMargin lines one line down so that they remain on screen in same
        // position:
        blockCopyLinesDown(externalToInternalRow(bottomMargin), mScreenRows - bottomMargin);

        // Update the screen location in the ring buffer:
        mScreenFirstRow = (mScreenFirstRow + 1) % mTotalRows;
        // Note that the history has grown if not already full:
        if (mActiveTranscriptRows < mTotalRows - mScreenRows) mActiveTranscriptRows++;

        // Blank the newly revealed line above the bottom margin:
        int blankRow = externalToInternalRow(bottomMargin - 1);
        if (mLines[blankRow] == null) {
            mLines[blankRow] = new TerminalRow(mColumns, style);
        } else {
            // Remove bitmaps that are completely scrolled out.
            if(mLines[blankRow].mHasTerminalBitmap) {
                removeScrolledOutTerminalBitmaps(blankRow);
            }
            mLines[blankRow].clear(style);
        }
    }

    /**
     * Block copy characters from one position in the screen to another. The two positions can overlap. All characters
     * of the source and destination must be within the bounds of the screen, or else an InvalidParameterException will
     * be thrown.
     *
     * @param sx source X coordinate
     * @param sy source Y coordinate
     * @param w  width
     * @param h  height
     * @param dx destination X coordinate
     * @param dy destination Y coordinate
     */
    public void blockCopy(int sx, int sy, int w, int h, int dx, int dy) {
        if (w == 0) return;
        if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows || dx < 0 || dx + w > mColumns || dy < 0 || dy + h > mScreenRows)
            throw new IllegalArgumentException();
        boolean copyingUp = sy > dy;
        for (int y = 0; y < h; y++) {
            int y2 = copyingUp ? y : (h - (y + 1));
            TerminalRow sourceRow = allocateFullLineIfNecessary(externalToInternalRow(sy + y2));
            allocateFullLineIfNecessary(externalToInternalRow(dy + y2)).copyInterval(sourceRow, sx, sx + w, dx);
        }
    }

    /**
     * Block set characters. All characters must be within the bounds of the screen, or else and
     * InvalidParemeterException will be thrown. Typically this is called with a "val" argument of 32 to clear a block
     * of characters.
     */
    public void blockSet(int sx, int sy, int w, int h, int val, long style) {
        if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows) {
            throw new IllegalArgumentException(
                "Illegal arguments! blockSet(" + sx + ", " + sy + ", " + w + ", " + h + ", " + val + ", " + mColumns + ", " + mScreenRows + ")");
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++)
                setChar(sx + x, sy + y, val, style);
            if (sx + w == mColumns && val == ' ') {
                clearLineWrap(sy + y);
            }
        }
    }

    public TerminalRow allocateFullLineIfNecessary(int row) {
        return (mLines[row] == null) ? (mLines[row] = new TerminalRow(mColumns, 0)) : mLines[row];
    }

    public void setChar(int column, int row, int codePoint, long style) {
        if (row  < 0 || row >= mScreenRows || column < 0 || column >= mColumns)
            throw new IllegalArgumentException("TerminalBuffer.setChar(): row=" + row + ", column=" + column + ", mScreenRows=" + mScreenRows + ", mColumns=" + mColumns);
        row = externalToInternalRow(row);
        allocateFullLineIfNecessary(row).setChar(column, codePoint, style);
    }

    public long getStyleAt(int externalRow, int column) {
        return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).getStyle(column);
    }

    /** Support for http://vt100.net/docs/vt510-rm/DECCARA and http://vt100.net/docs/vt510-rm/DECCARA */
    public void setOrClearEffect(int bits, boolean setOrClear, boolean reverse, boolean rectangular, int leftMargin, int rightMargin, int top, int left,
                                 int bottom, int right) {
        for (int y = top; y < bottom; y++) {
            TerminalRow line = mLines[externalToInternalRow(y)];
            int startOfLine = (rectangular || y == top) ? left : leftMargin;
            int endOfLine = (rectangular || y + 1 == bottom) ? right : rightMargin;
            for (int x = startOfLine; x < endOfLine; x++) {
                long currentStyle = line.getStyle(x);
                int foreColor = TextStyle.decodeForeColor(currentStyle);
                int backColor = TextStyle.decodeBackColor(currentStyle);
                int effect = TextStyle.decodeEffect(currentStyle);
                if (reverse) {
                    // Clear out the bits to reverse and add them back in reversed:
                    effect = (effect & ~bits) | (bits & ~effect);
                } else if (setOrClear) {
                    effect |= bits;
                } else {
                    effect &= ~bits;
                }
                line.mStyle[x] = TextStyle.encode(foreColor, backColor, effect);
            }
        }
    }

    public synchronized void clearTranscript() {
        if (mScreenFirstRow < mActiveTranscriptRows) {
            Arrays.fill(mLines, mTotalRows + mScreenFirstRow - mActiveTranscriptRows, mTotalRows, null);
            Arrays.fill(mLines, 0, mScreenFirstRow, null);
        } else {
            Arrays.fill(mLines, mScreenFirstRow - mActiveTranscriptRows, mScreenFirstRow, null);
        }
        mActiveTranscriptRows = 0;
        clearTerminalBitmaps();
    }



    public synchronized TerminalBitmap getTerminalBitmap(long style) {
        int bitmapNum = TextStyle.getTerminalBitmapNum(style);
        return bitmapNum >= TERMINAL_BITMAP__NUM_START ? mTerminalBitmaps.get(bitmapNum): null;
    }

    public synchronized void clearTerminalBitmaps() {
        mTerminalBitmaps.clear();
    }

    public synchronized Bitmap getSixelBitmap(long style) {
        TerminalBitmap terminalBitmap = getTerminalBitmap(style);
        return terminalBitmap != null ? terminalBitmap.mBitmap : null;
    }


    public synchronized Rect getSixelRect(long style) {
        TerminalBitmap terminalBitmap = getTerminalBitmap(style);
        if (terminalBitmap == null) {
            return null;
        }

        int x = TextStyle.getTerminalBitmapX(style);
        int y = TextStyle.getTerminalBitmapY(style);
        return new Rect(
            x * terminalBitmap.mCellWidth,
            y * terminalBitmap.mCellHeight,
            (x + 1) * terminalBitmap.mCellWidth,
            (y + 1) * terminalBitmap.mCellHeight);
    }


    public synchronized void sixelStart(int width, int height) {
        mTerminalSixel = TerminalSixel.build(getClient(), width, height);
    }

    public synchronized int sixelEnd(int x, int y, int cellW, int cellH) {
        if (mTerminalSixel == null) return 0;

        int bitmapNum = getFreeTerminalBitmapNum();
        if (bitmapNum < TERMINAL_BITMAP__NUM_START) {
            Logger.logError(mClient, LOG_TAG, "Cannot create more than " + TERMINAL_BITMAP__NUM_END + " bitmaps");
            return 0;
        }

        TerminalBitmap terminalBitmap = TerminalBitmap.build(this, bitmapNum, mTerminalSixel, x, y, cellW, cellH);
        mTerminalSixel = null;
        if (terminalBitmap == null || terminalBitmap.getBitmap() == null) {
            return 0;
        }
        mTerminalBitmaps.put(bitmapNum, terminalBitmap);

        doTerminalBitmapsGC(30000);
        return terminalBitmap.mScrollLines;
    }

    /** Clears the {@link #mTerminalSixel} by setting it to `null`. */
    public synchronized void sixelClear() {
        mTerminalSixel = null;
    }

    /**
     * Clears the {@link #mTerminalSixel} by setting it to `null` and logs error.
     * Call this on error if further sixel commands/data should be parsed to prevent them from
     * printing on terminal, but sixel rendering should be ignored.
     */
    public synchronized void sixelIgnore() {
        Logger.logError(mClient, LOG_TAG, "Ignoring sixel rendering");
        mTerminalSixel = null;
    }

    public synchronized boolean sixelReadData(int codePoint, int repeat) {
        //  If an error occurred during processing (like OOM), then remaining sixel command is
        //  completely read, but is ignored.
        if (mTerminalSixel != null) {
            if (!mTerminalSixel.readData(codePoint, repeat)) {
                sixelIgnore();
                return false;
            }
        }
        return true;
    }

    public synchronized boolean sixelResize(int sixelWidth, int sixelHeight) {
        //  If an error occurred during processing (like OOM), then remaining sixel command is
        //  completely read, but is ignored.
        if (mTerminalSixel != null) {
            if (!mTerminalSixel.resize(sixelWidth, sixelHeight)) {
                sixelIgnore();
                return false;
            }
        }
        return true;
    }

    public synchronized void sixelSetColor(int color) {
        if (mTerminalSixel != null)
            mTerminalSixel.setColor(color);
    }

    public synchronized void sixelSetRGBColor(int color, int r, int g, int b) {
        if (mTerminalSixel != null)
            mTerminalSixel.setRGBColor(color, r, g, b);
    }



    private synchronized int getFreeTerminalBitmapNum() {
        int bitmapNum = TERMINAL_BITMAP__NUM_START;
        while (mTerminalBitmaps.containsKey(bitmapNum)) {
            bitmapNum++;
            if (bitmapNum == TERMINAL_BITMAP__NUM_END) {
                return -1;
            }
        }
        return bitmapNum;
    }


    public synchronized int[] addTerminalBitmapForImage(byte[] image, int x, int y, int cellW, int cellH, int width, int height, boolean shouldPreserveAspectRatio) {
        int bitmapNum = getFreeTerminalBitmapNum();
        if (bitmapNum < TERMINAL_BITMAP__NUM_START) {
            Logger.logError(mClient, LOG_TAG, "Cannot create more than " + TERMINAL_BITMAP__NUM_END + " bitmaps");
            return new int[] {0, 0};
        }

        TerminalBitmap terminalBitmap = TerminalBitmap.build(this, bitmapNum, image, x, y,
            cellW, cellH, width, height, shouldPreserveAspectRatio);
        if (terminalBitmap == null || terminalBitmap.getBitmap() == null) {
            return new int[] {0, 0};
        }
        mTerminalBitmaps.put(bitmapNum, terminalBitmap);

        doTerminalBitmapsGC(30000);
        return terminalBitmap.mCursorDelta;
    }


    /** Remove bitmaps that are completely scrolled out. */
    public synchronized void removeScrolledOutTerminalBitmaps(int row) {
        Set<Integer> bitmapsToRemove = new HashSet<>();

        for (int column = 0; column < mColumns; column++) {
            long columnStyle = mLines[row].getStyle(column);
            int bitmapNum = TextStyle.getTerminalBitmapNum(columnStyle);
            if (bitmapNum >= TERMINAL_BITMAP__NUM_START) {
                bitmapsToRemove.add(bitmapNum);
            }
        }

        if (row + 1 < mTotalRows) {
            TerminalRow nextLine = mLines[row + 1];
            if (nextLine.mHasTerminalBitmap) {
                for (int column = 0; column < mColumns; column++) {
                    long columnStyle = nextLine.getStyle(column);
                    int bitmapNum = TextStyle.getTerminalBitmapNum(columnStyle);
                    if (bitmapNum >= TERMINAL_BITMAP__NUM_START) {
                        bitmapsToRemove.add(bitmapNum);
                    }
                }
            }
        }

        for(Integer bitmapStyle : bitmapsToRemove) {
            mTerminalBitmaps.remove(bitmapStyle);
        }
    }

    public synchronized void doTerminalBitmapsGC(int timeDelta) {
        if (mTerminalBitmaps.isEmpty() || mTerminalBitmapsLastGC + timeDelta > SystemClock.uptimeMillis()) {
            return;
        }

        Set<Integer> bitmapsToKeep = new HashSet<>();

        for (int line = 0; line < mLines.length; line++) {
            if(mLines[line] != null && mLines[line].mHasTerminalBitmap) {
                for (int column = 0; column < mColumns; column++) {
                    long style = mLines[line].getStyle(column);
                    int bitmapNum = TextStyle.getTerminalBitmapNum(style);
                    if (bitmapNum >= TERMINAL_BITMAP__NUM_START) {
                        bitmapsToKeep.add(bitmapNum);
                    }
                }
            }
        }

        Set<Integer> bitmapNums = new HashSet<>(mTerminalBitmaps.keySet());
        for (Integer bitmapNum: bitmapNums) {
            if (!bitmapsToKeep.contains(bitmapNum)) {
                mTerminalBitmaps.remove(bitmapNum);
            }
        }

        mTerminalBitmapsLastGC = SystemClock.uptimeMillis();
    }


    // -------------------------------------------------------------------------
    // Terminal reflow — preserve scrollback history on column-width change
    // -------------------------------------------------------------------------

    private void reflowResize(int newColumns, int newRows, int newTotalRows, int[] cursor, long currentStyle, boolean altScreen) {
        TerminalRow[] oldLines = mLines;
        final int oldActiveTranscriptRows = mActiveTranscriptRows;
        final int oldScreenFirstRow = mScreenFirstRow;
        final int oldScreenRows = mScreenRows;
        final int oldTotalRows = mTotalRows;
        final int oldColumns = mColumns;
        final int oldCursorRow = cursor[1];
        final int oldCursorCol = cursor[0];

        // Merge mLineWrap-connected rows into logical lines
        java.util.ArrayList<LogicalLine> logicalLines = new java.util.ArrayList<>();
        java.util.ArrayList<LogicalLine> blankLines = new java.util.ArrayList<>();
        int cursorLineIdx = -1, cursorColOffset = -1;
        boolean inLogicalLine = false;

        for (int externalOldRow = -oldActiveTranscriptRows; externalOldRow < oldScreenRows; externalOldRow++) {
            int internalOldRow = oldScreenFirstRow + externalOldRow;
            internalOldRow = (internalOldRow < 0) ? (oldTotalRows + internalOldRow) : (internalOldRow % oldTotalRows);

            TerminalRow oldLine = oldLines[internalOldRow];
            if (oldLine == null) {
                if (inLogicalLine) {
                    logicalLines.get(logicalLines.size()-1).hardBreak = true;
                    inLogicalLine = false;
                }
                LogicalLine blank = new LogicalLine();
                blank.isBlank = true;
                blank.blankLineHeight = 1;
                blank.hardBreak = true;
                blank.startExternalRow = externalOldRow;
                blank.endExternalRow = externalOldRow;
                blankLines.add(blank);
                continue;
            }

            boolean cursorAtThisRow = (externalOldRow == oldCursorRow);
            int displayWidth = (oldLine.mLineWrap || cursorAtThisRow) ? oldColumns : countDisplayWidth(oldLine, oldColumns, false);

            if (!inLogicalLine) {
                LogicalLine ll = new LogicalLine();
                ll.startExternalRow = externalOldRow;
                ll.endExternalRow = externalOldRow;
                ll.totalVisualCols = displayWidth;
                logicalLines.add(ll);
                inLogicalLine = true;
            } else {
                LogicalLine current = logicalLines.get(logicalLines.size()-1);
                current.totalVisualCols += displayWidth;
                current.endExternalRow = externalOldRow;
            }

            if (cursorAtThisRow) {
                cursorLineIdx = logicalLines.size() - 1;
                cursorColOffset = oldCursorCol;
                for (int r = logicalLines.get(cursorLineIdx).startExternalRow; r < externalOldRow; r++) {
                    int ir = oldScreenFirstRow + r;
                    ir = (ir < 0) ? (oldTotalRows + ir) : (ir % oldTotalRows);
                    if (oldLines[ir] != null) cursorColOffset += oldColumns;
                }
            }

            if (!oldLine.mLineWrap) {
                if (inLogicalLine) {
                    logicalLines.get(logicalLines.size()-1).hardBreak = true;
                    inLogicalLine = false;
                }
            }
        }

        if (inLogicalLine && logicalLines.size() > 0)
            logicalLines.get(logicalLines.size()-1).hardBreak = true;

        // Merge blank lines in position order
        java.util.ArrayList<LogicalLine> allLines = new java.util.ArrayList<>();
        int blankIdx = 0;
        for (int li = 0; li < logicalLines.size(); li++) {
            LogicalLine ll = logicalLines.get(li);
            while (blankIdx < blankLines.size() && blankLines.get(blankIdx).startExternalRow < ll.startExternalRow)
                allLines.add(blankLines.get(blankIdx++));
            allLines.add(ll);
        }
        while (blankIdx < blankLines.size()) allLines.add(blankLines.get(blankIdx++));

        // Count how many rows we need at the new width
        int flatRowCount = 0;
        for (int i = 0; i < allLines.size(); i++) {
            LogicalLine ll = allLines.get(i);
            if (ll.isBlank) {
                flatRowCount += ll.blankLineHeight;
            } else {
                int rowsNeeded = Math.max(1, (ll.totalVisualCols + newColumns - 1) / newColumns);
                flatRowCount += rowsNeeded;
            }
        }

        int flatSize = Math.max(flatRowCount, newRows + 10);
        TerminalRow[] flatRows = new TerminalRow[flatSize];
        for (int i = 0; i < flatSize; i++)
            flatRows[i] = new TerminalRow(newColumns, currentStyle);

        // Walk old chars and write into new-width flat rows
        int flatIdx = 0;
        int cursorNewRow = -1, cursorNewCol = -1;
        boolean cursorPlaced = false;

        for (int lineIdx = 0; lineIdx < allLines.size(); lineIdx++) {
            LogicalLine ll = allLines.get(lineIdx);
            if (ll.isBlank) {
                flatIdx += ll.blankLineHeight;
                continue;
            }

            int writeCol = 0;
            int rowEndExternal = ll.endExternalRow;

            for (int externalOldRow = ll.startExternalRow; externalOldRow <= rowEndExternal; externalOldRow++) {
                int internalOldRow = oldScreenFirstRow + externalOldRow;
                internalOldRow = (internalOldRow < 0) ? (oldTotalRows + internalOldRow) : (internalOldRow % oldTotalRows);

                TerminalRow oldLine = oldLines[internalOldRow];
                if (oldLine == null) continue;

                boolean cursorAtThisRow = (externalOldRow == oldCursorRow);
                boolean rowLineWrap = (externalOldRow < rowEndExternal);
                int widthToProcess = (rowLineWrap || cursorAtThisRow) ? oldColumns : countDisplayWidth(oldLine, oldColumns, false);

                int oldCol = 0, charIdx = 0, spaceUsed = oldLine.getSpaceUsed(), colsWritten = 0;

                while (charIdx < spaceUsed && colsWritten < widthToProcess && oldCol < oldColumns) {
                    char c = oldLine.mText[charIdx];
                    int codePoint, charsConsumed;
                    if (Character.isHighSurrogate(c) && charIdx + 1 < spaceUsed) {
                        codePoint = Character.toCodePoint(c, oldLine.mText[charIdx + 1]);
                        charsConsumed = 2;
                    } else {
                        codePoint = c;
                        charsConsumed = 1;
                    }
                    int w = WcWidth.width(codePoint);
                    long style = (w > 0) ? oldLine.getStyle(oldCol) : 0;

                    if (w <= 0) {
                        if (writeCol > 0) flatRows[flatIdx].setChar(writeCol - 1, codePoint, style);
                        charIdx += charsConsumed;
                        continue;
                    }

                    if (writeCol + w > newColumns && writeCol > 0) {
                        if (rowLineWrap || colsWritten + w < widthToProcess)
                            flatRows[flatIdx].mLineWrap = true;
                        flatIdx++;
                        writeCol = 0;
                    }

                    if (writeCol + w > newColumns) {
                        charIdx += charsConsumed;
                        if (w > 0) oldCol += w;
                        continue;
                    }

                    flatRows[flatIdx].setChar(writeCol, codePoint, style);
                    colsWritten += w;

                    if (!cursorPlaced && cursorAtThisRow && oldCol <= oldCursorCol && oldCursorCol < oldCol + w) {
                        cursorNewRow = flatIdx;
                        cursorNewCol = writeCol;
                        cursorPlaced = true;
                    }

                    writeCol += w;
                    oldCol += w;
                    charIdx += charsConsumed;
                }
            }

            if (flatIdx < flatRows.length) flatRows[flatIdx].mLineWrap = false;
            flatIdx++;
        }

        // Reinitialise the buffer fields before writing output
        mLines = new TerminalRow[newTotalRows];
        for (int i = 0; i < newTotalRows; i++)
            mLines[i] = new TerminalRow(newColumns, currentStyle);
        mTotalRows = newTotalRows;
        mScreenRows = newRows;
        mColumns = newColumns;

        // Split into history + screen keeping cursor visible
        int effectiveContentRows = flatIdx;
        if (effectiveContentRows <= newRows) {
            mActiveTranscriptRows = 0;
            mScreenFirstRow = 0;
            for (int i = 0; i < effectiveContentRows && i < newTotalRows; i++) mLines[i] = flatRows[i];
            for (int i = effectiveContentRows; i < newRows; i++)
                if (mLines[i] == null) mLines[i] = new TerminalRow(newColumns, currentStyle);
        } else {
            int cursorRow = cursorPlaced ? cursorNewRow : effectiveContentRows - 1;
            int historyRows = Math.max(0, Math.min(cursorRow - Math.min(cursorRow, newRows - 1), effectiveContentRows - newRows));
            historyRows = Math.min(historyRows, newTotalRows - newRows);
            if (altScreen) historyRows = 0;

            mActiveTranscriptRows = historyRows;
            mScreenFirstRow = historyRows;

            for (int i = 0; i < historyRows; i++) mLines[i % newTotalRows] = flatRows[i];
            for (int i = 0; i < newRows && (historyRows + i) < flatRows.length; i++)
                mLines[(historyRows + i) % newTotalRows] = flatRows[historyRows + i];

            if (cursorPlaced) cursorNewRow -= historyRows;
        }

        if (cursorPlaced) { cursor[0] = cursorNewCol; cursor[1] = cursorNewRow; }
        if (cursor[0] < 0 || cursor[1] < 0) cursor[0] = cursor[1] = 0;
        if (cursor[1] >= mScreenRows) {
            cursor[1] = mScreenRows - 1;
            if (cursor[0] >= newColumns) cursor[0] = newColumns - 1;
        }
    }

    private static int countDisplayWidth(TerminalRow row, int columns, boolean includeTrailingSpaces) {
        if (row == null) return 0;
        if (includeTrailingSpaces) return columns;
        int cols = 0, charIdx = 0, spaceUsed = row.getSpaceUsed(), lastNonSpaceCol = -1;
        while (charIdx < spaceUsed && cols < columns) {
            char c = row.mText[charIdx];
            int codePoint;
            if (Character.isHighSurrogate(c) && charIdx + 1 < spaceUsed) {
                codePoint = Character.toCodePoint(c, row.mText[charIdx + 1]);
                charIdx += 2;
            } else {
                codePoint = c;
                charIdx++;
            }
            int w = WcWidth.width(codePoint);
            if (w > 0) {
                cols += w;
                if (codePoint != ' ') lastNonSpaceCol = cols;
            }
        }
        return (lastNonSpaceCol >= 0) ? lastNonSpaceCol : 0;
    }

    private static final class LogicalLine {
        int startExternalRow, endExternalRow;
        int totalVisualCols;
        boolean hardBreak, isBlank;
        int blankLineHeight = 1;

        LogicalLine() {}
    }

}
