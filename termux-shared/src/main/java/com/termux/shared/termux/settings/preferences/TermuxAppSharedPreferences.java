package com.termux.shared.termux.settings.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.android.PackageUtils;
import com.termux.shared.settings.preferences.AppSharedPreferences;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

public class TermuxAppSharedPreferences extends AppSharedPreferences {

    private int MIN_FONTSIZE;
    private int MAX_FONTSIZE;
    private int DEFAULT_FONTSIZE;

    private static final String LOG_TAG = "TermuxAppSharedPreferences";

    private TermuxAppSharedPreferences(@NonNull Context context) {
        super(context,
            context.getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE),
            context.getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS));

        setFontVariables(context);
    }

    @Nullable
    public static TermuxAppSharedPreferences build(@NonNull final Context context) {
        Context termuxPackageContext = PackageUtils.getContextForPackage(context, TermuxConstants.TERMUX_PACKAGE_NAME);
        if (termuxPackageContext == null)
            return null;
        else
            return new TermuxAppSharedPreferences(termuxPackageContext);
    }

    public static TermuxAppSharedPreferences build(@NonNull final Context context, final boolean exitAppOnError) {
        Context termuxPackageContext = TermuxUtils.getContextForPackageOrExitApp(context, TermuxConstants.TERMUX_PACKAGE_NAME, exitAppOnError);
        if (termuxPackageContext == null)
            return null;
        else
            return new TermuxAppSharedPreferences(termuxPackageContext);
    }



    public boolean shouldShowTerminalToolbar() {
        return mSharedPreferences.getBoolean(TERMUX_APP.KEY_SHOW_TERMINAL_TOOLBAR, TERMUX_APP.DEFAULT_VALUE_SHOW_TERMINAL_TOOLBAR);
    }

    public void setShowTerminalToolbar(boolean value) {
        mSharedPreferences.edit().putBoolean(TERMUX_APP.KEY_SHOW_TERMINAL_TOOLBAR, value).apply();
    }

    public boolean toogleShowTerminalToolbar() {
        boolean currentValue = shouldShowTerminalToolbar();
        setShowTerminalToolbar(!currentValue);
        return !currentValue;
    }


    public boolean isSoftKeyboardEnabled() {
        return mSharedPreferences.getBoolean(TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED, TERMUX_APP.DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED);
    }

    public void setSoftKeyboardEnabled(boolean value) {
        mSharedPreferences.edit().putBoolean(TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED, value).apply();
    }

    public boolean isSoftKeyboardEnabledOnlyIfNoHardware() {
        return mSharedPreferences.getBoolean(TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE, TERMUX_APP.DEFAULT_VALUE_KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE);
    }

    public void setSoftKeyboardEnabledOnlyIfNoHardware(boolean value) {
        mSharedPreferences.edit().putBoolean(TERMUX_APP.KEY_SOFT_KEYBOARD_ENABLED_ONLY_IF_NO_HARDWARE, value).apply();
    }



    public boolean shouldKeepScreenOn() {
        return mSharedPreferences.getBoolean(TERMUX_APP.KEY_KEEP_SCREEN_ON, TERMUX_APP.DEFAULT_VALUE_KEEP_SCREEN_ON);
    }

    public void setKeepScreenOn(boolean value) {
        mSharedPreferences.edit().putBoolean(TERMUX_APP.KEY_KEEP_SCREEN_ON, value).apply();
    }



    public static int[] getDefaultFontSizes(Context context) {
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());

        int[] sizes = new int[3];

        sizes[1] = (int) (4f * dipInPixels); // min

        int defaultFontSize = Math.round(12 * dipInPixels);
        if (defaultFontSize % 2 == 1) defaultFontSize--;

        sizes[0] = defaultFontSize; // default
        sizes[2] = 256; // max

        return sizes;
    }

    public void setFontVariables(Context context) {
        int[] sizes = getDefaultFontSizes(context);

        DEFAULT_FONTSIZE = sizes[0];
        MIN_FONTSIZE = sizes[1];
        MAX_FONTSIZE = sizes[2];
    }

    public int getFontSize() {
        int fontSize;
        try {
            String s = mSharedPreferences.getString(TERMUX_APP.KEY_FONTSIZE, Integer.toString(DEFAULT_FONTSIZE));
            fontSize = s != null ? Integer.parseInt(s) : DEFAULT_FONTSIZE;
        } catch (NumberFormatException | ClassCastException e) {
            fontSize = DEFAULT_FONTSIZE;
        }
        return Math.min(Math.max(fontSize, MIN_FONTSIZE), MAX_FONTSIZE);
    }

    public void setFontSize(int value) {
        mSharedPreferences.edit().putString(TERMUX_APP.KEY_FONTSIZE, Integer.toString(value)).apply();
    }

    public void changeFontSize(boolean increase) {
        int fontSize = getFontSize();

        fontSize += (increase ? 1 : -1) * 2;
        fontSize = Math.max(MIN_FONTSIZE, Math.min(fontSize, MAX_FONTSIZE));

        setFontSize(fontSize);
    }



    public String getCurrentSession() {
        String value = mSharedPreferences.getString(TERMUX_APP.KEY_CURRENT_SESSION, null);
        return (value == null || value.isEmpty()) ? null : value;
    }

    public void setCurrentSession(String value) {
        mSharedPreferences.edit().putString(TERMUX_APP.KEY_CURRENT_SESSION, value).apply();
    }



    public int getLogLevel() {
        return mSharedPreferences.getInt(TERMUX_APP.KEY_LOG_LEVEL, Logger.DEFAULT_LOG_LEVEL);
    }

    public void setLogLevel(Context context, int logLevel) {
        logLevel = Logger.setLogLevel(context, logLevel);
        mSharedPreferences.edit().putInt(TERMUX_APP.KEY_LOG_LEVEL, logLevel).apply();
    }



    public int getLastNotificationId() {
        return mSharedPreferences.getInt(TERMUX_APP.KEY_LAST_NOTIFICATION_ID, TERMUX_APP.DEFAULT_VALUE_KEY_LAST_NOTIFICATION_ID);
    }

    public void setLastNotificationId(int notificationId) {
        mSharedPreferences.edit().putInt(TERMUX_APP.KEY_LAST_NOTIFICATION_ID, notificationId).apply();
    }


    @SuppressLint("ApplySharedPref")
    public synchronized int getAndIncrementAppShellNumberSinceBoot() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        int cur = mSharedPreferences.getInt(TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT, TERMUX_APP.DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT);
        if (cur < 0) cur = Integer.MAX_VALUE;
        int next = cur + 1;
        if (next < 0) next = Integer.MAX_VALUE;
        mSharedPreferences.edit().putInt(TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT, next).commit();
        return cur;
    }

    @SuppressLint("ApplySharedPref")
    public synchronized void resetAppShellNumberSinceBoot() {
        mSharedPreferences.edit().putInt(TERMUX_APP.KEY_APP_SHELL_NUMBER_SINCE_BOOT, TERMUX_APP.DEFAULT_VALUE_APP_SHELL_NUMBER_SINCE_BOOT).commit();
    }

    @SuppressLint("ApplySharedPref")
    public synchronized int getAndIncrementTerminalSessionNumberSinceBoot() {
        // Keep value at MAX_VALUE on integer overflow and not 0, since not first shell
        int cur = mSharedPreferences.getInt(TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT, TERMUX_APP.DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT);
        if (cur < 0) cur = Integer.MAX_VALUE;
        int next = cur + 1;
        if (next < 0) next = Integer.MAX_VALUE;
        mSharedPreferences.edit().putInt(TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT, next).commit();
        return cur;
    }

    @SuppressLint("ApplySharedPref")
    public synchronized void resetTerminalSessionNumberSinceBoot() {
        mSharedPreferences.edit().putInt(TERMUX_APP.KEY_TERMINAL_SESSION_NUMBER_SINCE_BOOT, TERMUX_APP.DEFAULT_VALUE_TERMINAL_SESSION_NUMBER_SINCE_BOOT).commit();
    }


    public boolean isTerminalViewKeyLoggingEnabled() {
        return mSharedPreferences.getBoolean(TERMUX_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, TERMUX_APP.DEFAULT_VALUE_TERMINAL_VIEW_KEY_LOGGING_ENABLED);
    }

    public void setTerminalViewKeyLoggingEnabled(boolean value) {
        mSharedPreferences.edit().putBoolean(TERMUX_APP.KEY_TERMINAL_VIEW_KEY_LOGGING_ENABLED, value).apply();
    }



    public boolean arePluginErrorNotificationsEnabled(boolean readFromFile) {
        if (readFromFile)
            return mMultiProcessSharedPreferences.getBoolean(TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_PLUGIN_ERROR_NOTIFICATIONS_ENABLED);
        else
            return mSharedPreferences.getBoolean(TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_PLUGIN_ERROR_NOTIFICATIONS_ENABLED);
    }

    public void setPluginErrorNotificationsEnabled(boolean value) {
        mSharedPreferences.edit().putBoolean(TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED, value).apply();
    }



    public boolean areCrashReportNotificationsEnabled(boolean readFromFile) {
        if (readFromFile)
            return mMultiProcessSharedPreferences.getBoolean(TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_CRASH_REPORT_NOTIFICATIONS_ENABLED);
       else
            return mSharedPreferences.getBoolean(TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED, TERMUX_APP.DEFAULT_VALUE_CRASH_REPORT_NOTIFICATIONS_ENABLED);
    }

    public void setCrashReportNotificationsEnabled(boolean value) {
        mSharedPreferences.edit().putBoolean(TERMUX_APP.KEY_CRASH_REPORT_NOTIFICATIONS_ENABLED, value).apply();
    }

}
