package com.termux.shared.termux.settings.preferences;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.settings.preferences.AppSharedPreferences;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_BOOT_APP;
import com.termux.shared.termux.TermuxConstants;

public class TermuxBootAppSharedPreferences extends AppSharedPreferences {

    private static final String LOG_TAG = "TermuxBootAppSharedPreferences";

    private TermuxBootAppSharedPreferences(@NonNull Context context) {
        super(context,
            context.getSharedPreferences(TermuxConstants.TERMUX_BOOT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE),
            context.getSharedPreferences(TermuxConstants.TERMUX_BOOT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS));
    }

    @Nullable
    public static TermuxBootAppSharedPreferences build(@NonNull final Context context) {
        Context termuxBootPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_BOOT_PACKAGE_NAME);
        if (termuxBootPackageContext == null)
            return null;
        else
            return new TermuxBootAppSharedPreferences(termuxBootPackageContext);
    }

    public static TermuxBootAppSharedPreferences build(@NonNull final Context context, final boolean exitAppOnError) {
        Context termuxBootPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_BOOT_PACKAGE_NAME, exitAppOnError);
        if (termuxBootPackageContext == null)
            return null;
        else
            return new TermuxBootAppSharedPreferences(termuxBootPackageContext);
    }



    public int getLogLevel(boolean readFromFile) {
        if (readFromFile)
            return mMultiProcessSharedPreferences.getInt(TERMUX_BOOT_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
        else
            return mSharedPreferences.getInt(TERMUX_BOOT_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
    }

    @SuppressLint("ApplySharedPref")
    public void setLogLevel(Context context, int logLevel, boolean commitToFile) {
        logLevel = Logger.setLogLevel(context, logLevel);
        if (commitToFile)
            mSharedPreferences.edit().putInt(TERMUX_BOOT_APP.KEY_LOG_LEVEL, logLevel).commit();
        else
            mSharedPreferences.edit().putInt(TERMUX_BOOT_APP.KEY_LOG_LEVEL, logLevel).apply();
    }

}
