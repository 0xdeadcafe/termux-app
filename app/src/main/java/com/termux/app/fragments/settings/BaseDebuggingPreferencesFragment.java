package com.termux.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.shared.logger.Logger;

/**
 * Base class for all plugin debugging-preference screens.
 *
 * <p>Subclasses supply three things:
 * <ol>
 *   <li>the XML preference resource ({@link #getPreferencesXmlResId()})</li>
 *   <li>the plugin-specific {@link PreferenceDataStore} ({@link #createDataStore})</li>
 *   <li>the current log level, or {@code null} when preferences are unavailable
 *       ({@link #getLogLevel})</li>
 * </ol>
 * Everything else — wiring the data store, inflating the XML, and populating the log-level
 * {@link ListPreference} — is handled here.
 */
public abstract class BaseDebuggingPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        getPreferenceManager().setPreferenceDataStore(createDataStore(context));
        setPreferencesFromResource(getPreferencesXmlResId(), rootKey);
        configureLoggingPreferences(context);
    }

    /** XML preference resource to inflate, e.g. {@code R.xml.termux_debugging_preferences}. */
    @XmlRes
    protected abstract int getPreferencesXmlResId();

    /** Create (or retrieve) the {@link PreferenceDataStore} for this plugin. */
    protected abstract PreferenceDataStore createDataStore(@NonNull Context context);

    /**
     * Return the plugin's current log level, or {@code null} if the shared-preferences store
     * is unavailable (in which case the log-level list preference will be omitted).
     */
    @Nullable
    protected abstract Integer getLogLevel(@NonNull Context context);

    private void configureLoggingPreferences(@NonNull Context context) {
        PreferenceCategory loggingCategory = findPreference("logging");
        if (loggingCategory == null) return;

        ListPreference logLevelListPreference = findPreference("log_level");
        if (logLevelListPreference == null) return;

        Integer logLevel = getLogLevel(context);
        if (logLevel == null) return;

        setLogLevelListPreferenceData(logLevelListPreference, context, logLevel);
        loggingCategory.addPreference(logLevelListPreference);
    }

    /**
     * Populate {@code logLevelListPreference} with the available log levels and select
     * {@code logLevel}. Creates a new {@link ListPreference} if {@code logLevelListPreference}
     * is {@code null}.
     */
    public static ListPreference setLogLevelListPreferenceData(
            ListPreference logLevelListPreference, Context context, int logLevel) {
        if (logLevelListPreference == null)
            logLevelListPreference = new ListPreference(context);

        CharSequence[] logLevels = Logger.getLogLevelsArray();
        CharSequence[] logLevelLabels = Logger.getLogLevelLabelsArray(context, logLevels, true);

        logLevelListPreference.setEntryValues(logLevels);
        logLevelListPreference.setEntries(logLevelLabels);
        logLevelListPreference.setValue(String.valueOf(logLevel));
        logLevelListPreference.setDefaultValue(Logger.DEFAULT_LOG_LEVEL);

        return logLevelListPreference;
    }
}
