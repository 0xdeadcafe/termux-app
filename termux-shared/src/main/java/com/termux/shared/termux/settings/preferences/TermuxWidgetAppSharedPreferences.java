package com.termux.shared.termux.settings.preferences;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.settings.preferences.AppSharedPreferences;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_WIDGET_APP;
import com.termux.shared.termux.TermuxConstants;

import java.util.UUID;

public class TermuxWidgetAppSharedPreferences extends AppSharedPreferences {

    private static final String LOG_TAG = "TermuxWidgetAppSharedPreferences";

    private TermuxWidgetAppSharedPreferences(@NonNull Context context) {
        super(context,
            context.getSharedPreferences(TermuxConstants.TERMUX_WIDGET_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE),
            context.getSharedPreferences(TermuxConstants.TERMUX_WIDGET_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS));
    }

    @Nullable
    public static TermuxWidgetAppSharedPreferences build(@NonNull final Context context) {
        Context termuxWidgetPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_WIDGET_PACKAGE_NAME);
        if (termuxWidgetPackageContext == null)
            return null;
        else
            return new TermuxWidgetAppSharedPreferences(termuxWidgetPackageContext);
    }

    public static TermuxWidgetAppSharedPreferences build(@NonNull final Context context, final boolean exitAppOnError) {
        Context termuxWidgetPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_WIDGET_PACKAGE_NAME, exitAppOnError);
        if (termuxWidgetPackageContext == null)
            return null;
        else
            return new TermuxWidgetAppSharedPreferences(termuxWidgetPackageContext);
    }



    public static String getGeneratedToken(@NonNull Context context) {
        TermuxWidgetAppSharedPreferences preferences = TermuxWidgetAppSharedPreferences.build(context, true);
        if (preferences == null) return null;
        return preferences.getGeneratedToken();
    }

    @SuppressLint("ApplySharedPref")
    public String getGeneratedToken() {
        String token = mSharedPreferences.getString(TERMUX_WIDGET_APP.KEY_TOKEN, null);
        if (token == null || token.isEmpty()) token = null;
        if (token == null) {
            token = UUID.randomUUID().toString();
            mSharedPreferences.edit().putString(TERMUX_WIDGET_APP.KEY_TOKEN, token).commit();
        }
        return token;
    }



    public int getLogLevel(boolean readFromFile) {
        if (readFromFile)
            return mMultiProcessSharedPreferences.getInt(TERMUX_WIDGET_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
        else
            return mSharedPreferences.getInt(TERMUX_WIDGET_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
    }

    @SuppressLint("ApplySharedPref")
    public void setLogLevel(Context context, int logLevel, boolean commitToFile) {
        logLevel = Logger.setLogLevel(context, logLevel);
        if (commitToFile)
            mSharedPreferences.edit().putInt(TERMUX_WIDGET_APP.KEY_LOG_LEVEL, logLevel).commit();
        else
            mSharedPreferences.edit().putInt(TERMUX_WIDGET_APP.KEY_LOG_LEVEL, logLevel).apply();
    }

}
