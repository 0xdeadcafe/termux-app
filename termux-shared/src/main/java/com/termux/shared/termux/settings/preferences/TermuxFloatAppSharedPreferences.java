package com.termux.shared.termux.settings.preferences;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.settings.preferences.AppSharedPreferences;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_FLOAT_APP;
import com.termux.shared.termux.TermuxConstants;

public class TermuxFloatAppSharedPreferences extends AppSharedPreferences {

    private int MIN_FONTSIZE;
    private int MAX_FONTSIZE;
    private int DEFAULT_FONTSIZE;

    private static final String LOG_TAG = "TermuxFloatAppSharedPreferences";

    private TermuxFloatAppSharedPreferences(@NonNull Context context) {
        super(context,
            context.getSharedPreferences(TermuxConstants.TERMUX_FLOAT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE),
            context.getSharedPreferences(TermuxConstants.TERMUX_FLOAT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS));

        setFontVariables(context);
    }

    @Nullable
    public static TermuxFloatAppSharedPreferences build(@NonNull final Context context) {
        Context termuxFloatPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_FLOAT_PACKAGE_NAME);
        if (termuxFloatPackageContext == null)
            return null;
        else
            return new TermuxFloatAppSharedPreferences(termuxFloatPackageContext);
    }

    public static TermuxFloatAppSharedPreferences build(@NonNull final Context context, final boolean exitAppOnError) {
        Context termuxFloatPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_FLOAT_PACKAGE_NAME, exitAppOnError);
        if (termuxFloatPackageContext == null)
            return null;
        else
            return new TermuxFloatAppSharedPreferences(termuxFloatPackageContext);
    }



    public int getWindowX() {
        return mSharedPreferences.getInt(TERMUX_FLOAT_APP.KEY_WINDOW_X, 200);
    }

    public void setWindowX(int value) {
        mSharedPreferences.edit().putInt(TERMUX_FLOAT_APP.KEY_WINDOW_X, value).apply();
    }

    public int getWindowY() {
        return mSharedPreferences.getInt(TERMUX_FLOAT_APP.KEY_WINDOW_Y, 200);
    }

    public void setWindowY(int value) {
        mSharedPreferences.edit().putInt(TERMUX_FLOAT_APP.KEY_WINDOW_Y, value).apply();
    }



    public int getWindowWidth() {
        return mSharedPreferences.getInt(TERMUX_FLOAT_APP.KEY_WINDOW_WIDTH, 500);
    }

    public void setWindowWidth(int value) {
        mSharedPreferences.edit().putInt(TERMUX_FLOAT_APP.KEY_WINDOW_WIDTH, value).apply();
    }

    public int getWindowHeight() {
        return mSharedPreferences.getInt(TERMUX_FLOAT_APP.KEY_WINDOW_HEIGHT, 500);
    }

    public void setWindowHeight(int value) {
        mSharedPreferences.edit().putInt(TERMUX_FLOAT_APP.KEY_WINDOW_HEIGHT, value).apply();
    }



    public void setFontVariables(Context context) {
        int[] sizes = TermuxAppSharedPreferences.getDefaultFontSizes(context);

        DEFAULT_FONTSIZE = sizes[0];
        MIN_FONTSIZE = sizes[1];
        MAX_FONTSIZE = sizes[2];
    }

    public int getFontSize() {
        int fontSize;
        try {
            String s = mSharedPreferences.getString(TERMUX_FLOAT_APP.KEY_FONTSIZE, Integer.toString(DEFAULT_FONTSIZE));
            fontSize = s != null ? Integer.parseInt(s) : DEFAULT_FONTSIZE;
        } catch (NumberFormatException | ClassCastException e) {
            fontSize = DEFAULT_FONTSIZE;
        }
        return Math.min(Math.max(fontSize, MIN_FONTSIZE), MAX_FONTSIZE);
    }

    public void setFontSize(int value) {
        mSharedPreferences.edit().putString(TERMUX_FLOAT_APP.KEY_FONTSIZE, Integer.toString(value)).apply();
    }

    public void changeFontSize(boolean increase) {
        int fontSize = getFontSize();

        fontSize += (increase ? 1 : -1) * 2;
        fontSize = Math.max(MIN_FONTSIZE, Math.min(fontSize, MAX_FONTSIZE));

        setFontSize(fontSize);
    }


    public int getLogLevel(boolean readFromFile) {
        if (readFromFile)
            return mMultiProcessSharedPreferences.getInt(TERMUX_FLOAT_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
        else
            return mSharedPreferences.getInt(TERMUX_FLOAT_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
    }

    @SuppressLint("ApplySharedPref")
    public void setLogLevel(Context context, int logLevel, boolean commitToFile) {
        logLevel = Logger.setLogLevel(context, logLevel);
        if (commitToFile)
            mSharedPreferences.edit().putInt(TERMUX_FLOAT_APP.KEY_LOG_LEVEL, logLevel).commit();
        else
            mSharedPreferences.edit().putInt(TERMUX_FLOAT_APP.KEY_LOG_LEVEL, logLevel).apply();
    }


    public boolean isTerminalViewKeyLoggingEnabled(boolean readFromFile) {
        if (readFromFile)
            return mMultiProcessSharedPreferences.getBoolean(TERMUX_FLOAT_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, TERMUX_FLOAT_APP.DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED);
        else
            return mSharedPreferences.getBoolean(TERMUX_FLOAT_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, TERMUX_FLOAT_APP.DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED);
    }

    @SuppressLint("ApplySharedPref")
    public void setTerminalViewKeyLoggingEnabled(boolean value, boolean commitToFile) {
        if (commitToFile)
            mSharedPreferences.edit().putBoolean(TERMUX_FLOAT_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, value).commit();
        else
            mSharedPreferences.edit().putBoolean(TERMUX_FLOAT_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, value).apply();
    }

}
