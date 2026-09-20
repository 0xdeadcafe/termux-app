package com.termux.shared.termux.settings.preferences;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.settings.preferences.AppSharedPreferences;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_API_APP;
import com.termux.shared.termux.TermuxConstants;

public class TermuxAPIAppSharedPreferences extends AppSharedPreferences {

    private static final String LOG_TAG = "TermuxAPIAppSharedPreferences";

    private TermuxAPIAppSharedPreferences(@NonNull Context context) {
        super(context,
            context.getSharedPreferences(TermuxConstants.TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE),
            context.getSharedPreferences(TermuxConstants.TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS));
    }

    @Nullable
    public static TermuxAPIAppSharedPreferences build(@NonNull final Context context) {
        Context termuxAPIPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_API_PACKAGE_NAME);
        if (termuxAPIPackageContext == null)
            return null;
        else
            return new TermuxAPIAppSharedPreferences(termuxAPIPackageContext);
    }

    public static TermuxAPIAppSharedPreferences build(@NonNull final Context context, final boolean exitAppOnError) {
        Context termuxAPIPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_API_PACKAGE_NAME, exitAppOnError);
        if (termuxAPIPackageContext == null)
            return null;
        else
            return new TermuxAPIAppSharedPreferences(termuxAPIPackageContext);
    }



    public int getLogLevel(boolean readFromFile) {
        if (readFromFile)
            return mMultiProcessSharedPreferences.getInt(TERMUX_API_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
        else
            return mSharedPreferences.getInt(TERMUX_API_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
    }

    @SuppressLint("ApplySharedPref")
    public void setLogLevel(Context context, int logLevel, boolean commitToFile) {
        logLevel = Logger.setLogLevel(context, logLevel);
        if (commitToFile)
            mSharedPreferences.edit().putInt(TERMUX_API_APP.KEY_LOG_LEVEL, logLevel).commit();
        else
            mSharedPreferences.edit().putInt(TERMUX_API_APP.KEY_LOG_LEVEL, logLevel).apply();
    }


    public int getLastPendingIntentRequestCode() {
        return mSharedPreferences.getInt(TERMUX_API_APP.KEY_LAST_PENDING_INTENT_REQUEST_CODE, TERMUX_API_APP.DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE);
    }

    @SuppressLint("ApplySharedPref")
    public void setLastPendingIntentRequestCode(int lastPendingIntentRequestCode) {
        mSharedPreferences.edit().putInt(TERMUX_API_APP.KEY_LAST_PENDING_INTENT_REQUEST_CODE, lastPendingIntentRequestCode).commit();
    }

}
