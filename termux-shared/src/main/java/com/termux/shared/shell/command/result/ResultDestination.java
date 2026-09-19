package com.termux.shared.shell.command.result;

import android.app.PendingIntent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;

/**
 * Sealed type describing where a command result should be delivered.
 *
 * <p>Exactly one concrete subtype is constructed per delivery channel:
 * <ul>
 *   <li>{@link PendingIntentResult} — deliver result as bundle extras via a {@link PendingIntent}.</li>
 *   <li>{@link DirectoryResult} — deliver result by writing files to a directory.</li>
 * </ul>
 *
 * <p>Both channels may be active simultaneously (a {@link PendingIntentResult} and a
 * {@link DirectoryResult} can coexist on the same {@link com.termux.shared.shell.command.ExecutionCommand}).
 */
public sealed interface ResultDestination
        permits ResultDestination.PendingIntentResult, ResultDestination.DirectoryResult {

    // -----------------------------------------------------------------------
    // Concrete subtypes
    // -----------------------------------------------------------------------

    /** Deliver result as bundle extras via a {@link PendingIntent}. */
    final class PendingIntentResult implements ResultDestination {

        @NonNull  public final PendingIntent pendingIntent;
        @NonNull  public final String bundleKey;
        @NonNull  public final String stdoutKey;
        @NonNull  public final String stderrKey;
        @NonNull  public final String exitCodeKey;
        @NonNull  public final String errCodeKey;
        @NonNull  public final String errmsgKey;
        @NonNull  public final String stdoutOriginalLengthKey;
        @NonNull  public final String stderrOriginalLengthKey;

        public PendingIntentResult(
                @NonNull  PendingIntent pendingIntent,
                @NonNull  String bundleKey,
                @NonNull  String stdoutKey,
                @NonNull  String stderrKey,
                @NonNull  String exitCodeKey,
                @NonNull  String errCodeKey,
                @NonNull  String errmsgKey,
                @NonNull  String stdoutOriginalLengthKey,
                @NonNull  String stderrOriginalLengthKey) {
            this.pendingIntent = pendingIntent;
            this.bundleKey = bundleKey;
            this.stdoutKey = stdoutKey;
            this.stderrKey = stderrKey;
            this.exitCodeKey = exitCodeKey;
            this.errCodeKey = errCodeKey;
            this.errmsgKey = errmsgKey;
            this.stdoutOriginalLengthKey = stdoutOriginalLengthKey;
            this.stderrOriginalLengthKey = stderrOriginalLengthKey;
        }
    }

    /** Deliver result by writing files to a directory. */
    final class DirectoryResult implements ResultDestination {

        @NonNull  public final String directoryPath;
        @Nullable public final String directoryAllowedParentPath;
        public final boolean singleFile;
        /** {@code null} only when {@link #singleFile} is {@code false}. */
        @Nullable public final String fileBasename;
        @Nullable public final String fileOutputFormat;
        @Nullable public final String fileErrorFormat;
        /** Empty string if no suffix; never {@code null}. */
        @NonNull  public final String filesSuffix;

        public DirectoryResult(
                @NonNull  String directoryPath,
                @Nullable String directoryAllowedParentPath,
                boolean singleFile,
                @Nullable String fileBasename,
                @Nullable String fileOutputFormat,
                @Nullable String fileErrorFormat,
                @NonNull  String filesSuffix) {
            this.directoryPath = directoryPath;
            this.directoryAllowedParentPath = directoryAllowedParentPath;
            this.singleFile = singleFile;
            this.fileBasename = fileBasename;
            this.fileOutputFormat = fileOutputFormat;
            this.fileErrorFormat = fileErrorFormat;
            this.filesSuffix = filesSuffix;
        }
    }

    // -----------------------------------------------------------------------
    // Logging helpers (used by ExecutionCommand logging)
    // -----------------------------------------------------------------------

    /**
     * Returns a log-friendly string for the given result destinations.
     *
     * @param pi        The {@link PendingIntentResult}, or {@code null} if not active.
     * @param dir       The {@link DirectoryResult}, or {@code null} if not active.
     * @param ignoreNull If {@code true}, omit null/empty optional fields.
     */
    static String getLogString(
            @Nullable PendingIntentResult pi,
            @Nullable DirectoryResult dir,
            boolean ignoreNull) {

        StringBuilder sb = new StringBuilder();
        sb.append("Result Pending: `").append(pi != null || dir != null).append("`");

        if (pi != null) {
            sb.append("\nResult PendingIntent Creator: `").append(pi.pendingIntent.getCreatorPackage()).append("`");
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Bundle Key", pi.bundleKey, "-"));
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Stdout Key", pi.stdoutKey, "-"));
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Stderr Key", pi.stderrKey, "-"));
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Exit Code Key", pi.exitCodeKey, "-"));
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Err Code Key", pi.errCodeKey, "-"));
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Error Key", pi.errmsgKey, "-"));
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Stdout Original Length Key", pi.stdoutOriginalLengthKey, "-"));
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Stderr Original Length Key", pi.stderrOriginalLengthKey, "-"));
        }

        if (dir != null) {
            if (pi != null) sb.append("\n");
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Directory Path", dir.directoryPath, "-"));
            sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Single File", dir.singleFile, "-"));
            if (!ignoreNull || dir.fileBasename != null)
                sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result File Basename", dir.fileBasename, "-"));
            if (!ignoreNull || dir.fileOutputFormat != null)
                sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result File Output Format", dir.fileOutputFormat, "-"));
            if (!ignoreNull || dir.fileErrorFormat != null)
                sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result File Error Format", dir.fileErrorFormat, "-"));
            if (!ignoreNull || !dir.filesSuffix.isEmpty())
                sb.append("\n").append(Logger.getSingleLineLogStringEntry("Result Files Suffix", dir.filesSuffix, "-"));
        }

        return sb.toString();
    }

    /**
     * Returns a markdown string for the given result destinations.
     *
     * @param pi  The {@link PendingIntentResult}, or {@code null} if not active.
     * @param dir The {@link DirectoryResult}, or {@code null} if not active.
     */
    static String getMarkdownString(
            @Nullable PendingIntentResult pi,
            @Nullable DirectoryResult dir) {

        StringBuilder sb = new StringBuilder();

        if (pi != null)
            sb.append(MarkdownUtils.getSingleLineMarkdownStringEntry(
                    "Result PendingIntent Creator", pi.pendingIntent.getCreatorPackage(), "-"));
        else
            sb.append("**Result PendingIntent Creator:** -  ");

        if (dir != null) {
            sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Result Directory Path", dir.directoryPath, "-"));
            sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Result Single File", dir.singleFile, "-"));
            sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Result File Basename", dir.fileBasename, "-"));
            sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Result File Output Format", dir.fileOutputFormat, "-"));
            sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Result File Error Format", dir.fileErrorFormat, "-"));
            sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Result Files Suffix", dir.filesSuffix, "-"));
        }

        return sb.toString();
    }
}
