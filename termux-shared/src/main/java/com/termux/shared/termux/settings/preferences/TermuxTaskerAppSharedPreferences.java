package com.termux.shared.termux.settings.preferences;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.android.PackageUtils;
import com.termux.shared.settings.preferences.AppSharedPreferences;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_TASKER_APP;
import com.termux.shared.logger.Logger;

public class TermuxTaskerAppSharedPreferences extends AppSharedPreferences {

    private static final String LOG_TAG = "TermuxTaskerAppSharedPreferences";

    private TermuxTaskerAppSharedPreferences(@NonNull Context context) {
        super(context,
            context.getSharedPreferences(TermuxConstants.TERMUX_TASKER_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE),
            context.getSharedPreferences(TermuxConstants.TERMUX_TASKER_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS));
    }

    @Nullable
    public static TermuxTaskerAppSharedPreferences build(@NonNull final Context context) {
        Context termuxTaskerPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_TASKER_PACKAGE_NAME);
        if (termuxTaskerPackageContext == null)
            return null;
        else
            return new TermuxTaskerAppSharedPreferences(termuxTaskerPackageContext);
    }

    public static TermuxTaskerAppSharedPreferences build(@NonNull final Context context, final boolean exitAppOnError) {
        Context termuxTaskerPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_TASKER_PACKAGE_NAME, exitAppOnError);
        if (termuxTaskerPackageContext == null)
            return null;
        else
            return new TermuxTaskerAppSharedPreferences(termuxTaskerPackageContext);
    }



    public int getLogLevel(boolean readFromFile) {
        if (readFromFile)
            return mMultiProcessSharedPreferences.getInt(TERMUX_TASKER_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
        else
            return mSharedPreferences.getInt(TERMUX_TASKER_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
    }

    @SuppressLint("ApplySharedPref")
    public void setLogLevel(Context context, int logLevel, boolean commitToFile) {
        logLevel = Logger.setLogLevel(context, logLevel);
        if (commitToFile)
            mSharedPreferences.edit().putInt(TERMUX_TASKER_APP.KEY_LOG_LEVEL, logLevel).commit();
        else
            mSharedPreferences.edit().putInt(TERMUX_TASKER_APP.KEY_LOG_LEVEL, logLevel).apply();
    }



    public int getLastPendingIntentRequestCode() {
        return mSharedPreferences.getInt(TERMUX_TASKER_APP.KEY_LAST_PENDING_INTENT_REQUEST_CODE, TERMUX_TASKER_APP.DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE);
    }

    public void setLastPendingIntentRequestCode(int lastPendingIntentRequestCode) {
        mSharedPreferences.edit().putInt(TERMUX_TASKER_APP.KEY_LAST_PENDING_INTENT_REQUEST_CODE, lastPendingIntentRequestCode).apply();
    }

}
