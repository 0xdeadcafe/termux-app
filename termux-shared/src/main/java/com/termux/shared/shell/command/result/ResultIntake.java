package com.termux.shared.shell.command.result;

import android.app.PendingIntent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Raw result-delivery parameters extracted from a plugin Intent, before validation and
 * conversion into typed {@link ResultDestination} objects.
 *
 * <p>An instance is only created when at least one of {@link #pendingIntent} or
 * {@link #directoryPath} is non-null, so a non-null {@code ResultIntake} on an
 * {@link com.termux.shared.shell.command.ExecutionCommand} means the caller expects a result back.
 */
public final class ResultIntake {

    /** The {@link PendingIntent} to fire with the result, or {@code null} if not requested. */
    @Nullable public final PendingIntent pendingIntent;
    /** Directory path to write result files into, or {@code null} if not requested. */
    @Nullable public final String directoryPath;
    /** Whether to write a single combined result file instead of per-stream files. */
    public final boolean singleFile;
    /** Basename for the result file when {@link #singleFile} is {@code true}. */
    @Nullable public final String fileBasename;
    /** {@link java.util.Formatter} format string for successful result file output. */
    @Nullable public final String fileOutputFormat;
    /** {@link java.util.Formatter} format string for error result file output. */
    @Nullable public final String fileErrorFormat;
    /** Suffix appended to result filenames. Never {@code null}; empty string if no suffix. */
    @NonNull public final String filesSuffix;

    public ResultIntake(
            @Nullable PendingIntent pendingIntent,
            @Nullable String directoryPath,
            boolean singleFile,
            @Nullable String fileBasename,
            @Nullable String fileOutputFormat,
            @Nullable String fileErrorFormat,
            @NonNull String filesSuffix) {
        this.pendingIntent = pendingIntent;
        this.directoryPath = directoryPath;
        this.singleFile = singleFile;
        this.fileBasename = fileBasename;
        this.fileOutputFormat = fileOutputFormat;
        this.fileErrorFormat = fileErrorFormat;
        this.filesSuffix = filesSuffix;
    }
}
