package com.termux.shared.settings.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A class that holds {@link SharedPreferences} objects for apps. */
public class AppSharedPreferences {

    /** The {@link Context} for operations. */
    protected final Context mContext;

    /** The {@link SharedPreferences} for single-process reads/writes. */
    protected final SharedPreferences mSharedPreferences;

    /** The {@link SharedPreferences} for multi-process reads/writes. */
    protected final SharedPreferences mMultiProcessSharedPreferences;

    protected AppSharedPreferences(@NonNull Context context, @Nullable SharedPreferences sharedPreferences) {
        this(context, sharedPreferences, null);
    }

    protected AppSharedPreferences(@NonNull Context context, @Nullable SharedPreferences sharedPreferences,
                                   @Nullable SharedPreferences multiProcessSharedPreferences) {
        mContext = context;
        mSharedPreferences = sharedPreferences;
        mMultiProcessSharedPreferences = multiProcessSharedPreferences;
    }



    /** Get {@link #mContext}. */
    public Context getContext() {
        return mContext;
    }

    /** Get {@link #mSharedPreferences}. */
    public SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }

    /** Get {@link #mMultiProcessSharedPreferences}. */
    public SharedPreferences getMultiProcessSharedPreferences() {
        return mMultiProcessSharedPreferences;
    }

}
