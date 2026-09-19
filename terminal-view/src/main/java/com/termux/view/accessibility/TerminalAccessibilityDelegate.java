package com.termux.view.accessibility;

import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import com.termux.view.TerminalView;

/**
 * Accessibility delegate for {@link TerminalView} that exposes the terminal cursor position
 * (row and column) to assistive technologies such as brltty.
 *
 * <p>Registered via {@link TerminalView#setAccessibilityDelegate} during view construction so
 * that any {@link android.accessibilityservice.AccessibilityService} can read the extras from
 * the accessibility node info.</p>
 */
public class TerminalAccessibilityDelegate extends View.AccessibilityDelegate {

    private static final String LOG_TAG = "TerminalAccessibilityDelegate";

    /** Extra key identifying the node as a terminal view. */
    public static final String EXTRA_ACCESSIBILITY_NODE_TYPE = "accessibility-node-type";
    /** Extra key for the terminal cursor column. */
    public static final String EXTRA_TERMINAL_CURSOR_COL = "terminal-cursor-col";
    /** Extra key for the terminal cursor row. */
    public static final String EXTRA_TERMINAL_CURSOR_ROW = "terminal-cursor-row";

    private final TerminalView mTerminalView;

    public TerminalAccessibilityDelegate(@NonNull TerminalView terminalView) {
        mTerminalView = terminalView;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        Bundle extra = info.getExtras();
        if (mTerminalView.mEmulator != null) {
            extra.putString(EXTRA_ACCESSIBILITY_NODE_TYPE, "terminal");
            extra.putInt(EXTRA_TERMINAL_CURSOR_COL, mTerminalView.mEmulator.getCursorCol());
            extra.putInt(EXTRA_TERMINAL_CURSOR_ROW, mTerminalView.mEmulator.getCursorRow());
        }
        info.setContentDescription(mTerminalView.getText());
    }

}
