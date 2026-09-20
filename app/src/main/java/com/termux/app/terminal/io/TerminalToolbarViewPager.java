package com.termux.app.terminal.io;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.terminal.TerminalSession;

public class TerminalToolbarViewPager {

    public static class PageAdapter extends RecyclerView.Adapter<PageAdapter.ViewHolder> {

        final TermuxActivity mActivity;
        String mSavedTextInput;

        public PageAdapter(TermuxActivity activity, String savedTextInput) {
            this.mActivity = activity;
            this.mSavedTextInput = savedTextInput;
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }

        @Override
        public int getItemViewType(int position) {
            // Each page has a unique type so RecyclerView never recycles across pages.
            return position;
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(mActivity);
            View layout;
            if (viewType == 0) {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_extra_keys, parent, false);
                ExtraKeysView extraKeysView = (ExtraKeysView) layout;
                extraKeysView.setExtraKeysViewClient(mActivity.getTermuxTerminalExtraKeys());
                extraKeysView.setButtonTextAllCaps(mActivity.getProperties().shouldExtraKeysTextBeAllCaps());
                mActivity.setExtraKeysView(extraKeysView);
                extraKeysView.reload(mActivity.getTermuxTerminalExtraKeys().getExtraKeysInfo(),
                    mActivity.getTerminalToolbarDefaultHeight());
            } else {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_text_input, parent, false);
                final EditText editText = layout.findViewById(R.id.terminal_toolbar_text_input);

                if (mSavedTextInput != null) {
                    editText.setText(mSavedTextInput);
                    mSavedTextInput = null;
                }

                editText.setOnEditorActionListener((v, actionId, event) -> {
                    TerminalSession session = mActivity.getCurrentSession();
                    if (session != null) {
                        if (session.isRunning()) {
                            String textToSend = editText.getText().toString();
                            if (textToSend.length() == 0) {
                                session.write("\r"); // send bare Enter
                            } else {
                                // Route through paste() so bracketed paste mode (DECSET 2004)
                                // wrapping is applied when the terminal app requests it
                                mActivity.getTerminalView().mEmulator.paste(textToSend);
                            }
                        } else {
                            mActivity.getTermuxTerminalSessionClient().removeFinishedSession(session);
                        }
                        editText.setText("");
                    }
                    return true;
                });
            }
            return new ViewHolder(layout);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            // All view setup is performed eagerly in onCreateViewHolder.
        }
    }



    public static class OnPageChangeCallback extends ViewPager2.OnPageChangeCallback {

        final TermuxActivity mActivity;
        final ViewPager2 mTerminalToolbarViewPager;

        public OnPageChangeCallback(TermuxActivity activity, ViewPager2 viewPager) {
            this.mActivity = activity;
            this.mTerminalToolbarViewPager = viewPager;
        }

        @Override
        public void onPageSelected(int position) {
            if (position == 0) {
                mActivity.getTerminalView().requestFocus();
            } else {
                final EditText editText = mTerminalToolbarViewPager.findViewById(R.id.terminal_toolbar_text_input);
                if (editText != null) editText.requestFocus();
            }
        }

    }

}
