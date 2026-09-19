package com.termux.app.terminal.io;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.TermuxActivity;

/**
 * Work around for fullscreen mode in Termux to fix ExtraKeysView not being visible.
 * This class is derived from:
 * https://stackoverflow.com/questions/7417123/android-how-to-adjust-layout-in-full-screen-mode-when-softkeyboard-is-visible
 * and has some additional tweaks
 * ---
 * For more information, see https://issuetracker.google.com/issues/36911528
 *
 * <h3>Why this still exists (audit: beads-pbv)</h3>
 *
 * <p>This workaround is default-OFF ({@code use-fullscreen-workaround=true} in
 * {@code termux.properties} required). It is only applied when both
 * {@link com.termux.shared.termux.settings.properties.TermuxProperties#isUsingFullScreen()} and
 * {@link com.termux.shared.termux.settings.properties.TermuxProperties#isUsingFullScreenWorkAround()}
 * are true.
 *
 * <p>{@link TermuxActivityRootView#onGlobalLayout()} handles keyboard overlap for the normal
 * (non-fullscreen) case via a bottom-margin approach. However, when
 * {@link android.view.WindowManager.LayoutParams#FLAG_FULLSCREEN} is set,
 * {@link android.view.WindowManager.LayoutParams#SOFT_INPUT_ADJUST_RESIZE} is broken (Android
 * bug: https://issuetracker.google.com/issues/36911528). In that mode this workaround directly
 * resizes the content view's height to exclude the keyboard, which is a different mechanism from
 * the margin approach in {@link TermuxActivityRootView}.
 *
 * <p>Both mechanisms run simultaneously in fullscreen mode, operating on different layout
 * properties of the same view (height vs bottomMargin). They are NOT interchangeable.
 *
 * <p>The proper long-term fix is to replace {@code FLAG_FULLSCREEN} +
 * {@code SOFT_INPUT_ADJUST_RESIZE} with {@code WindowInsetsController.hide(statusBars())} +
 * {@code WindowInsetsAnimationCallback} / {@code WindowInsets.Type.ime()}, which would let this
 * class be deleted. See the existing TODO in
 * {@link com.termux.shared.view.KeyboardUtils#setSoftInputModeAdjustResize}.
 * Tracked as beads-r3k.
 */
public class FullScreenWorkAround {
    private final View mChildOfContent;
    private int mUsableHeightPrevious;
    private final ViewGroup.LayoutParams mViewGroupLayoutParams;

    private final int mNavBarHeight;


    public static void apply(TermuxActivity activity) {
        new FullScreenWorkAround(activity);
    }

    private FullScreenWorkAround(TermuxActivity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        mChildOfContent = content.getChildAt(0);
        mViewGroupLayoutParams = mChildOfContent.getLayoutParams();
        mNavBarHeight = activity.getNavBarHeight();
        mChildOfContent.getViewTreeObserver().addOnGlobalLayoutListener(this::possiblyResizeChildOfContent);
    }

    private void possiblyResizeChildOfContent() {
        int usableHeightNow = computeUsableHeight();
        if (usableHeightNow != mUsableHeightPrevious) {
            int usableHeightSansKeyboard = mChildOfContent.getRootView().getHeight();
            int heightDifference = usableHeightSansKeyboard - usableHeightNow;
            if (heightDifference > (usableHeightSansKeyboard / 4)) {
                // keyboard probably just became visible

                // ensures that usable layout space does not extend behind the
                // soft keyboard, causing the extra keys to not be visible
                mViewGroupLayoutParams.height = (usableHeightSansKeyboard - heightDifference) + getNavBarHeight();
            } else {
                // keyboard probably just became hidden
                mViewGroupLayoutParams.height = usableHeightSansKeyboard;
            }
            mChildOfContent.requestLayout();
            mUsableHeightPrevious = usableHeightNow;
        }
    }

    private int getNavBarHeight() {
        return mNavBarHeight;
    }

    private int computeUsableHeight() {
        Rect r = new Rect();
        mChildOfContent.getWindowVisibleDisplayFrame(r);
        return (r.bottom - r.top);
    }

}

