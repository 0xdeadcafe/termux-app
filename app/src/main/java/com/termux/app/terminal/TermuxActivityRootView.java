package com.termux.app.terminal;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.app.TermuxActivity;

import java.util.List;

/**
 * Root view of {@link TermuxActivity}. Adjusts its own padding so the terminal content
 * and extra keys are never obscured by the status bar, navigation bar, or soft keyboard.
 *
 * <p>Padding is driven by window insets:
 * <ul>
 *   <li>Top: status bar height</li>
 *   <li>Bottom: whichever is taller — the navigation bar or the soft keyboard</li>
 * </ul>
 *
 * <p>{@link ViewCompat#setWindowInsetsAnimationCallback} animates the bottom padding in
 * sync with the keyboard show/hide transition. {@link ViewCompat#setOnApplyWindowInsetsListener}
 * locks in the final padding value once the animation settles.
 */
public class TermuxActivityRootView extends LinearLayout {

    public TermuxActivity mActivity;

    public TermuxActivityRootView(Context context) {
        super(context);
    }

    public TermuxActivityRootView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TermuxActivityRootView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setActivity(TermuxActivity activity) {
        mActivity = activity;
        registerInsetsCallbacks();
    }

    private void registerInsetsCallbacks() {
        // Handles final padding when insets settle (initial layout or after animation).
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            applyInsets(insets);
            return WindowInsetsCompat.CONSUMED;
        });

        // Animates padding in sync with the IME show/hide transition.
        ViewCompat.setWindowInsetsAnimationCallback(this,
            new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                @Override
                @NonNull
                public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets,
                                                     @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
                    applyInsets(insets);
                    return insets;
                }
            });
    }

    private void applyInsets(@NonNull WindowInsetsCompat insets) {
        int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        setPadding(0, top, 0, Math.max(ime, nav));
    }

}
