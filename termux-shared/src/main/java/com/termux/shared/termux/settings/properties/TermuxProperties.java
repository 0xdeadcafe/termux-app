package com.termux.shared.termux.settings.properties;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.file.filesystem.FileType;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Single-class replacement for the former four-layer property stack
 * (SharedPropertiesParser + SharedProperties + TermuxSharedProperties + TermuxAppSharedProperties).
 *
 * <p>Reads {@code termux.properties} from the first readable path in
 * {@link TermuxConstants#TERMUX_PROPERTIES_FILE_PATHS_LIST}, parses each property into its typed
 * internal value, and stores it in a single in-memory {@link HashMap} cache protected by a lock.
 *
 * <p>Call {@link #init(Context)} once at application startup (e.g. in
 * {@link android.app.Application#onCreate()}) and {@link #getProperties()} everywhere else.
 * Call {@link #loadFromDisk()} to reload after the file changes.
 */
public class TermuxProperties {

    private static TermuxProperties instance;

    private final Context mContext;
    /** Single in-memory cache: property key → typed internal value. */
    private Map<String, Object> mCache = new HashMap<>();
    private final Object mLock = new Object();

    public static final String LOG_TAG = "TermuxProperties";

    // -----------------------------------------------------------------------
    // Generic boolean maps (public so callers / tests can reference them)
    // -----------------------------------------------------------------------

    /** Maps {@code "true"} / {@code "false"} strings to {@link Boolean}. */
    public static final Map<String, Boolean> MAP_GENERIC_BOOLEAN = TermuxPropertyConstants.mapOf(
        TermuxPropertyConstants.entry("true", true),
        TermuxPropertyConstants.entry("false", false));

    /** Maps {@code "true"} / {@code "false"} strings to inverted {@link Boolean}. */
    public static final Map<String, Boolean> MAP_GENERIC_INVERTED_BOOLEAN = TermuxPropertyConstants.mapOf(
        TermuxPropertyConstants.entry("true", false),
        TermuxPropertyConstants.entry("false", true));

    // -----------------------------------------------------------------------
    // Singleton lifecycle
    // -----------------------------------------------------------------------

    private TermuxProperties(@NonNull Context context) {
        mContext = context.getApplicationContext();
        loadFromDisk();
    }

    /**
     * Initialise the singleton and load properties from disk.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    public static synchronized TermuxProperties init(@NonNull Context context) {
        if (instance == null)
            instance = new TermuxProperties(context);
        return instance;
    }

    /** Returns the singleton, or {@code null} if {@link #init(Context)} has not been called yet. */
    @Nullable
    public static TermuxProperties getProperties() {
        return instance;
    }

    // -----------------------------------------------------------------------
    // Loading
    // -----------------------------------------------------------------------

    /**
     * (Re)load all properties from disk into the in-memory cache.
     * Thread-safe; safe to call at any time.
     */
    public synchronized void loadFromDisk() {
        Properties raw = new Properties();
        File file = findPropertiesFile();

        if (file != null) {
            try (FileInputStream in = new FileInputStream(file)) {
                Logger.logVerbose(LOG_TAG, "Loading properties from \"" + file.getAbsolutePath() + "\"");
                raw.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception e) {
                Toast.makeText(mContext,
                    "Could not open properties file \"" + file.getAbsolutePath() + "\": " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
                Logger.logStackTraceWithMessage(LOG_TAG,
                    "Error loading properties file \"" + file.getAbsolutePath() + "\"", e);
            }
        }

        raw = replaceUseBlackUIProperty(raw);

        Map<String, Object> cache = new HashMap<>();
        for (String key : TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST)
            cache.put(key, parseValue(key, raw.getProperty(key)));

        synchronized (mLock) { mCache = cache; }
        dumpToLog();
    }

    /**
     * Returns the first readable, regular-file properties path from
     * {@link TermuxConstants#TERMUX_PROPERTIES_FILE_PATHS_LIST}, or {@code null}.
     */
    @Nullable
    private static File findPropertiesFile() {
        for (String path : TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST) {
            File f = new File(path);
            FileType ft = FileUtils.getFileType(path, false); // symlinks not followed
            if (ft == FileType.REGULAR) {
                if (f.canRead()) return f;
                Logger.logWarn(LOG_TAG, "Ignoring properties file at \"" + path + "\": not readable");
            } else if (ft != FileType.NO_EXIST) {
                Logger.logWarn(LOG_TAG, "Ignoring properties file at \"" + path + "\" of type: " + ft.getName());
            }
        }
        Logger.logDebug(LOG_TAG, "No readable properties file found at: " + TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST);
        return null;
    }

    // -----------------------------------------------------------------------
    // Cache access
    // -----------------------------------------------------------------------

    private Object getFromCache(String key) {
        synchronized (mLock) {
            if (mCache.containsKey(key))
                return mCache.get(key);
        }
        // Key absent from cache (should not happen after loadFromDisk) – return default.
        Object def = parseValue(key, null);
        Logger.logWarn(LOG_TAG, "Key \"" + key + "\" not in cache, returning default: " + def);
        return def;
    }

    // -----------------------------------------------------------------------
    // Static: read a single property directly from disk (no instance required)
    // -----------------------------------------------------------------------

    /**
     * Read the {@code night-mode} property value directly from disk.
     * Used by {@link com.termux.shared.termux.theme.TermuxThemeUtils} before the singleton is set up.
     */
    @Nullable
    public static String getNightMode(@NonNull Context context) {
        return (String) parseValue(TermuxPropertyConstants.KEY_NIGHT_MODE,
            readRawProperty(context, TermuxPropertyConstants.KEY_NIGHT_MODE));
    }

    /** Read a single raw {@link String} value from the properties file on disk. */
    @Nullable
    private static String readRawProperty(@NonNull Context context, String key) {
        File file = findPropertiesFile();
        if (file == null) return null;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error reading property \"" + key + "\"", e);
        }
        p = replaceUseBlackUIProperty(p);
        return p.getProperty(key);
    }

    /**
     * Return the typed cached value for {@code key}, or the default if absent.
     * The {@code cached} parameter is ignored (there is only one in-memory cache);
     * it exists for compatibility with callers from the old three-layer API.
     */
    public Object getInternalPropertyValue(String key, @SuppressWarnings("unused") boolean cached) {
        return getFromCache(key);
    }

    // -----------------------------------------------------------------------
    // Typed getters (public instance API)
    // -----------------------------------------------------------------------

    public boolean shouldAllowExternalApps() {
        return (boolean) getFromCache(TermuxConstants.PROP_ALLOW_EXTERNAL_APPS);
    }

    public boolean isFileShareReceiverDisabled() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_DISABLE_FILE_SHARE_RECEIVER);
    }

    public boolean isFileViewReceiverDisabled() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_DISABLE_FILE_VIEW_RECEIVER);
    }

    public boolean areHardwareKeyboardShortcutsDisabled() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_DISABLE_HARDWARE_KEYBOARD_SHORTCUTS);
    }

    public boolean areTerminalSessionChangeToastsDisabled() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST);
    }

    public boolean isEnforcingCharBasedInput() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_ENFORCE_CHAR_BASED_INPUT);
    }

    public boolean shouldExtraKeysTextBeAllCaps() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS);
    }

    public boolean shouldSoftKeyboardBeHiddenOnStartup() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP);
    }

    public boolean shouldRunTermuxAmSocketServer() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_RUN_TERMUX_AM_SOCKET_SERVER);
    }

    public boolean shouldOpenTerminalTranscriptURLOnClick() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_TERMINAL_ONCLICK_URL_OPEN);
    }

    public boolean isUsingCtrlSpaceWorkaround() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_USE_CTRL_SPACE_WORKAROUND);
    }

    public boolean isUsingFullScreen() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_USE_FULLSCREEN);
    }

    public boolean isUsingFullScreenWorkAround() {
        return (boolean) getFromCache(TermuxPropertyConstants.KEY_USE_FULLSCREEN_WORKAROUND);
    }

    public int getBellBehaviour() {
        return (int) getFromCache(TermuxPropertyConstants.KEY_BELL_BEHAVIOUR);
    }

    public int getDeleteTMPDIRFilesOlderThanXDaysOnExit() {
        return (int) getFromCache(TermuxPropertyConstants.KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT);
    }

    public int getTerminalCursorBlinkRate() {
        return (int) getFromCache(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_BLINK_RATE);
    }

    public int getTerminalCursorStyle() {
        return (int) getFromCache(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_STYLE);
    }

    public int getTerminalMarginHorizontal() {
        return (int) getFromCache(TermuxPropertyConstants.KEY_TERMINAL_MARGIN_HORIZONTAL);
    }

    public int getTerminalMarginVertical() {
        return (int) getFromCache(TermuxPropertyConstants.KEY_TERMINAL_MARGIN_VERTICAL);
    }

    public int getTerminalTranscriptRows() {
        return (int) getFromCache(TermuxPropertyConstants.KEY_TERMINAL_TRANSCRIPT_ROWS);
    }

    public float getTerminalToolbarHeightScaleFactor() {
        return (float) getFromCache(TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR);
    }

    public boolean isBackKeyTheEscapeKey() {
        return TermuxPropertyConstants.IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE.equals(
            getFromCache(TermuxPropertyConstants.KEY_BACK_KEY_BEHAVIOUR));
    }

    public String getDefaultWorkingDirectory() {
        return (String) getFromCache(TermuxPropertyConstants.KEY_DEFAULT_WORKING_DIRECTORY);
    }

    public String getNightMode() {
        return (String) getFromCache(TermuxPropertyConstants.KEY_NIGHT_MODE);
    }

    public boolean shouldEnableDisableSoftKeyboardOnToggle() {
        return TermuxPropertyConstants.IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE.equals(
            getFromCache(TermuxPropertyConstants.KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR));
    }

    public boolean areVirtualVolumeKeysDisabled() {
        return TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME.equals(
            getFromCache(TermuxPropertyConstants.KEY_VOLUME_KEYS_BEHAVIOUR));
    }

    // -----------------------------------------------------------------------
    // Parsing (private — converts raw String → typed Object)
    // -----------------------------------------------------------------------

    /**
     * Convert a raw property string value to its typed internal representation.
     * Returns a sensible default when {@code value} is {@code null} or invalid.
     */
    @Nullable
    private static Object parseValue(String key, String value) {
        if (key == null) return null;
        switch (key) {
            case TermuxPropertyConstants.KEY_BELL_BEHAVIOUR:
                return (int) getDefaultIfNotInMap(key, TermuxPropertyConstants.MAP_BELL_BEHAVIOUR,
                    toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_BELL_BEHAVIOUR, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT:
                return getDefaultIfNotInRange(key,
                    DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT),
                    TermuxPropertyConstants.DEFAULT_IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT,
                    TermuxPropertyConstants.IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MIN,
                    TermuxPropertyConstants.IVALUE_DELETE_TMPDIR_FILES_OLDER_THAN_X_DAYS_ON_EXIT_MAX,
                    true, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_TERMINAL_CURSOR_BLINK_RATE:
                return getDefaultIfNotInRange(key,
                    DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_BLINK_RATE),
                    TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_BLINK_RATE,
                    TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN,
                    TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX,
                    true, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_TERMINAL_CURSOR_STYLE:
                return (int) getDefaultIfNotInMap(key, TermuxPropertyConstants.MAP_TERMINAL_CURSOR_STYLE,
                    toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_STYLE, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_TERMINAL_MARGIN_HORIZONTAL:
                return getDefaultIfNotInRange(key,
                    DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_HORIZONTAL),
                    TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_HORIZONTAL,
                    TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_HORIZONTAL_MIN,
                    TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_HORIZONTAL_MAX,
                    true, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_TERMINAL_MARGIN_VERTICAL:
                return getDefaultIfNotInRange(key,
                    DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_VERTICAL),
                    TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_VERTICAL,
                    TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_VERTICAL_MIN,
                    TermuxPropertyConstants.IVALUE_TERMINAL_MARGIN_VERTICAL_MAX,
                    true, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_TERMINAL_TRANSCRIPT_ROWS:
                return getDefaultIfNotInRange(key,
                    DataUtils.getIntFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS),
                    TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS,
                    TermuxPropertyConstants.IVALUE_TERMINAL_TRANSCRIPT_ROWS_MIN,
                    TermuxPropertyConstants.IVALUE_TERMINAL_TRANSCRIPT_ROWS_MAX,
                    true, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR:
                return getDefaultIfNotInRange(key,
                    DataUtils.getFloatFromString(value, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR),
                    TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR,
                    TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MIN,
                    TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MAX,
                    true, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_SHORTCUT_CREATE_SESSION:
            case TermuxPropertyConstants.KEY_SHORTCUT_NEXT_SESSION:
            case TermuxPropertyConstants.KEY_SHORTCUT_PREVIOUS_SESSION:
            case TermuxPropertyConstants.KEY_SHORTCUT_RENAME_SESSION:
                return getCodePointForSessionShortcut(key, value);
            case TermuxPropertyConstants.KEY_BACK_KEY_BEHAVIOUR:
                return getDefaultIfNotInMap(key, TermuxPropertyConstants.MAP_BACK_KEY_BEHAVIOUR,
                    toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_BACK_KEY_BEHAVIOUR, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_DEFAULT_WORKING_DIRECTORY:
                return getDefaultWorkingDirectory(value);
            case TermuxPropertyConstants.KEY_EXTRA_KEYS:
                return getDefaultIfNullOrEmpty(value, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS);
            case TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE:
                return getDefaultIfNullOrEmpty(value, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE);
            case TermuxPropertyConstants.KEY_NIGHT_MODE:
                return getDefaultIfNotInMap(key, TermuxPropertyConstants.MAP_NIGHT_MODE,
                    toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_NIGHT_MODE, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR:
                return getDefaultIfNotInMap(key, TermuxPropertyConstants.MAP_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR,
                    toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR, true, LOG_TAG);
            case TermuxPropertyConstants.KEY_VOLUME_KEYS_BEHAVIOUR:
                return getDefaultIfNotInMap(key, TermuxPropertyConstants.MAP_VOLUME_KEYS_BEHAVIOUR,
                    toLowerCase(value), TermuxPropertyConstants.DEFAULT_IVALUE_VOLUME_KEYS_BEHAVIOUR, true, LOG_TAG);
            default:
                if (TermuxPropertyConstants.TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST.contains(key))
                    return getBooleanValueForStringValue(key, value, false, true, LOG_TAG);
                if (TermuxPropertyConstants.TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST.contains(key))
                    return getBooleanValueForStringValue(key, value, true, true, LOG_TAG);
                return value; // raw String (may be null)
        }
    }

    private static String getDefaultWorkingDirectory(String path) {
        if (path == null || path.isEmpty()) return TermuxPropertyConstants.DEFAULT_IVALUE_DEFAULT_WORKING_DIRECTORY;
        File workDir = new File(path);
        if (!workDir.exists() || !workDir.isDirectory() || !workDir.canRead()) {
            Logger.logError(LOG_TAG, "The path \"" + path + "\" for \"" + TermuxPropertyConstants.KEY_DEFAULT_WORKING_DIRECTORY + "\" does not exist, is not a directory or is not readable. Using default.");
            return TermuxPropertyConstants.DEFAULT_IVALUE_DEFAULT_WORKING_DIRECTORY;
        }
        return path;
    }

    @Nullable
    private static Integer getCodePointForSessionShortcut(String key, String value) {
        if (value == null) return null;
        String[] parts = value.toLowerCase().trim().split("\\+");
        String input = parts.length == 2 ? parts[1].trim() : null;
        if (!(parts.length == 2 && parts[0].trim().equals("ctrl")) || input == null || input.isEmpty() || input.length() > 2) {
            Logger.logError(LOG_TAG, "Keyboard shortcut '" + key + "' is not Ctrl+<something>");
            return null;
        }
        char c = input.charAt(0);
        if (Character.isLowSurrogate(c)) {
            if (input.length() != 2 || Character.isHighSurrogate(input.charAt(1))) {
                Logger.logError(LOG_TAG, "Keyboard shortcut '" + key + "' is not Ctrl+<something>");
                return null;
            }
            return Character.toCodePoint(input.charAt(1), c);
        }
        return (int) c;
    }

    /**
     * Migrate the deprecated {@code use-black-ui} property to {@code night-mode} on read.
     */
    @NonNull
    private static Properties replaceUseBlackUIProperty(@NonNull Properties properties) {
        String useBlackUI = properties.getProperty(TermuxPropertyConstants.KEY_USE_BLACK_UI);
        if (useBlackUI == null) return properties;

        Logger.logWarn(LOG_TAG, "Removing deprecated property " + TermuxPropertyConstants.KEY_USE_BLACK_UI + "=" + useBlackUI);
        properties.remove(TermuxPropertyConstants.KEY_USE_BLACK_UI);

        if (properties.getProperty(TermuxPropertyConstants.KEY_NIGHT_MODE) == null) {
            Boolean boolValue = getBooleanValueForStringValue(useBlackUI);
            if (boolValue != null) {
                String nightMode = boolValue ? TermuxPropertyConstants.IVALUE_NIGHT_MODE_TRUE
                                             : TermuxPropertyConstants.IVALUE_NIGHT_MODE_FALSE;
                Logger.logWarn(LOG_TAG, "Replacing deprecated " + TermuxPropertyConstants.KEY_USE_BLACK_UI + "=" + boolValue + " with " + TermuxPropertyConstants.KEY_NIGHT_MODE + "=" + nightMode);
                properties.put(TermuxPropertyConstants.KEY_NIGHT_MODE, nightMode);
            }
        }
        return properties;
    }

    // -----------------------------------------------------------------------
    // Debug logging
    // -----------------------------------------------------------------------

    private void dumpToLog() {
        Map<String, Object> snapshot;
        synchronized (mLock) { snapshot = new HashMap<>(mCache); }

        StringBuilder sb = new StringBuilder("Termux Properties:");
        for (Map.Entry<String, Object> e : snapshot.entrySet())
            sb.append("\n").append(e.getKey()).append(": `").append(e.getValue()).append("`");
        Logger.logVerbose(LOG_TAG, sb.toString());
    }

    // -----------------------------------------------------------------------
    // Static validation helpers (public — used by TermuxPropertyConstants maps
    // and tested directly)
    // -----------------------------------------------------------------------

    /** {@code "true"/"false"} → {@link Boolean}, or {@code null} for any other string. */
    @Nullable
    public static Boolean getBooleanValueForStringValue(String value) {
        return MAP_GENERIC_BOOLEAN.get(toLowerCase(value));
    }

    /** {@code "true"/"false"} → boolean with default and optional error logging. */
    public static boolean getBooleanValueForStringValue(String key, String value, boolean def,
                                                        boolean logErrorOnInvalidValue, String logTag) {
        return (boolean) getDefaultIfNotInMap(key, MAP_GENERIC_BOOLEAN, toLowerCase(value), def,
            logErrorOnInvalidValue, logTag);
    }

    /** Inverted variant: {@code "true"} → {@code false}, {@code "false"} → {@code true}. */
    public static boolean getInvertedBooleanValueForStringValue(String key, String value, boolean def,
                                                                boolean logErrorOnInvalidValue, String logTag) {
        return (boolean) getDefaultIfNotInMap(key, MAP_GENERIC_INVERTED_BOOLEAN, toLowerCase(value), def,
            logErrorOnInvalidValue, logTag);
    }

    /**
     * Return {@code map.get(inputValue)} if present, otherwise {@code defaultOutputValue}.
     * Logs an error if {@code inputValue} is non-null and not in the map and
     * {@code logErrorOnInvalidValue} is {@code true}.
     */
    public static Object getDefaultIfNotInMap(String key, @NonNull Map<?, ?> map, Object inputValue,
                                              Object defaultOutputValue, boolean logErrorOnInvalidValue,
                                              String logTag) {
        Object outputValue = map.get(inputValue);
        if (outputValue == null) {
            // Reverse lookup for the error message (linear scan is fine: maps are tiny and this
            // path only runs on validation errors).
            Object defaultInputValue = null;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (java.util.Objects.equals(e.getValue(), defaultOutputValue)) {
                    defaultInputValue = e.getKey();
                    break;
                }
            }
            if (defaultInputValue == null)
                Logger.logError(LOG_TAG, "Default output \"" + defaultOutputValue + "\" for key \"" + key + "\" not found in map: " + map.values());

            if (logErrorOnInvalidValue && inputValue != null) {
                if (key != null)
                    Logger.logError(logTag, "The value \"" + inputValue + "\" for the key \"" + key + "\" is invalid. Using default \"" + defaultInputValue + "\".");
                else
                    Logger.logError(logTag, "The value \"" + inputValue + "\" is invalid. Using default \"" + defaultInputValue + "\".");
            }
            return defaultOutputValue;
        }
        return outputValue;
    }

    /**
     * Return {@code value} if within [{@code min}, {@code max}], otherwise {@code def}.
     * Logs an error on out-of-range if {@code logErrorOnInvalidValue} is {@code true}
     * (suppressed when {@code value == 0} and {@code ignoreErrorIfValueZero} is {@code true}).
     */
    public static int getDefaultIfNotInRange(String key, int value, int def, int min, int max,
                                             boolean logErrorOnInvalidValue, boolean ignoreErrorIfValueZero,
                                             String logTag) {
        if (value >= min && value <= max) return value;
        if (logErrorOnInvalidValue && (!ignoreErrorIfValueZero || value != 0)) {
            String msg = "The value \"" + value + "\" for the key \"" + key + "\" is not within "
                + min + "-" + max + " (inclusive). Using default \"" + def + "\".";
            if (key != null) Logger.logError(logTag, msg);
            else Logger.logError(logTag, "The value \"" + value + "\" is not within " + min + "-" + max + ". Using default \"" + def + "\".");
        }
        return def;
    }

    /** Float overload of {@link #getDefaultIfNotInRange(String, int, int, int, int, boolean, boolean, String)}. */
    public static float getDefaultIfNotInRange(String key, float value, float def, float min, float max,
                                               boolean logErrorOnInvalidValue, boolean ignoreErrorIfValueZero,
                                               String logTag) {
        if (value >= min && value <= max) return value;
        if (logErrorOnInvalidValue && (!ignoreErrorIfValueZero || value != 0)) {
            if (key != null)
                Logger.logError(logTag, "The value \"" + value + "\" for the key \"" + key + "\" is not within " + min + "-" + max + ". Using default \"" + def + "\".");
            else
                Logger.logError(logTag, "The value \"" + value + "\" is not within " + min + "-" + max + ". Using default \"" + def + "\".");
        }
        return def;
    }

    /** Return {@code object} if non-null, otherwise {@code def}. */
    @Nullable
    public static <T> T getDefaultIfNull(@Nullable T object, @Nullable T def) {
        return object != null ? object : def;
    }

    /** Return {@code object} if non-null and non-empty, otherwise {@code def}. */
    @Nullable
    public static String getDefaultIfNullOrEmpty(@Nullable String object, @Nullable String def) {
        return (object == null || object.isEmpty()) ? def : object;
    }

    /** Null-safe lowercase. */
    @Nullable
    public static String toLowerCase(@Nullable String value) {
        return value == null ? null : value.toLowerCase();
    }

}
