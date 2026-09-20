package com.termux.shared.termux.settings.preferences;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.settings.preferences.AppSharedPreferences;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_STYLING_APP;
import com.termux.shared.termux.TermuxConstants;

public class TermuxStylingAppSharedPreferences extends AppSharedPreferences {

    private static final String LOG_TAG = "TermuxStylingAppSharedPreferences";

    private TermuxStylingAppSharedPreferences(@NonNull Context context) {
        super(context,
            context.getSharedPreferences(TermuxConstants.TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE),
            context.getSharedPreferences(TermuxConstants.TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS));
    }

    @Nullable
    public static TermuxStylingAppSharedPreferences build(@NonNull final Context context) {
        Context termuxStylingPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_STYLING_PACKAGE_NAME);
        if (termuxStylingPackageContext == null)
            return null;
        else
            return new TermuxStylingAppSharedPreferences(termuxStylingPackageContext);
    }

    public static TermuxStylingAppSharedPreferences build(@NonNull final Context context, final boolean exitAppOnError) {
        Context termuxStylingPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, exitAppOnError);
        if (termuxStylingPackageContext == null)
            return null;
        else
            return new TermuxStylingAppSharedPreferences(termuxStylingPackageContext);
    }



    public int getLogLevel(boolean readFromFile) {
        if (readFromFile)
            return mMultiProcessSharedPreferences.getInt(TERMUX_STYLING_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
        else
            return mSharedPreferences.getInt(TERMUX_STYLING_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
    }

    @SuppressLint("ApplySharedPref")
    public void setLogLevel(Context context, int logLevel, boolean commitToFile) {
        logLevel = Logger.setLogLevel(context, logLevel);
        if (commitToFile)
            mSharedPreferences.edit().putInt(TERMUX_STYLING_APP.KEY_LOG_LEVEL, logLevel).commit();
        else
            mSharedPreferences.edit().putInt(TERMUX_STYLING_APP.KEY_LOG_LEVEL, logLevel).apply();
    }

}
