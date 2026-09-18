package com.termux.shared.errors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A checked exception that wraps an {@link Error}.
 *
 * <p>This is part of the migration away from the {@code Errno}/{@link Error} "error carrier"
 * pattern (see {@code beads-xs0}) and towards normal Java exceptions, while remaining fully
 * backwards compatible with existing {@link Error}-returning APIs during the transition.
 *
 * <p>Migration pattern ("deprecate-first"): for any existing method
 * {@code Error someMethod(args...)}, add a sibling method {@code someMethodOrThrow(args...)}
 * that calls the original and converts a non-null {@link Error} result into a thrown
 * {@link TermuxException} via {@link #throwIfFailed(Error)}. The original method is then
 * marked {@code @Deprecated} pointing callers at the new one. This lets old and new call sites
 * co-exist across releases without a breaking change to the published {@code termux-shared}
 * API, and lets the {@code Error}-returning overload be removed later once downstream plugin
 * consumers have had a release cycle to migrate.
 *
 * <p>Example:
 * <pre>{@code
 * @Deprecated
 * public static Error readStringFromFile(String label, String filePath, StringBuilder out) {
 *     ...
 * }
 *
 * public static String readStringFromFileOrThrow(String label, String filePath) throws TermuxException {
 *     StringBuilder out = new StringBuilder();
 *     TermuxException.throwIfFailed(readStringFromFile(label, filePath, out));
 *     return out.toString();
 * }
 * }</pre>
 */
public class TermuxException extends Exception {

    @NonNull
    private final Error error;

    public TermuxException(@NonNull final Error error) {
        super(error.getMessage(), firstCauseOrNull(error));
        this.error = error;
    }

    public TermuxException(final String type, final Integer code, final String message) {
        this(new Error(type, code, message));
    }

    public TermuxException(final String type, final Integer code, final String message, final Throwable cause) {
        this(new Error(type, code, message, cause));
    }

    public TermuxException(final String type, final Integer code, final String message, final List<Throwable> causes) {
        this(new Error(type, code, message, causes));
    }

    public TermuxException(final String message) {
        this(new Error(message));
    }

    public TermuxException(final String message, final Throwable cause) {
        this(new Error(message, cause));
    }

    /**
     * The underlying {@link Error} this exception was created from. Kept around so that existing
     * {@link Error}-based logging/markdown helpers ({@link Error#getErrorLogString()},
     * {@link Error#getErrorMarkdownString()}, etc) keep working unchanged for callers that catch
     * a {@link TermuxException} but still want the old formatted output.
     */
    @NonNull
    public Error getError() {
        return error;
    }

    /**
     * If {@code error} is non-null and represents a failure, throw it as a {@link TermuxException}.
     * Otherwise return normally. This is the core bridge used by {@code *OrThrow} sibling methods
     * to adapt a legacy {@code Error}-returning call into exception-throwing style.
     */
    public static void throwIfFailed(@Nullable final Error error) throws TermuxException {
        if (error != null && error.isStateFailed())
            throw new TermuxException(error);
    }

    @Nullable
    private static Throwable firstCauseOrNull(@NonNull final Error error) {
        List<Throwable> throwables = error.getThrowablesList();
        if (throwables == null || throwables.isEmpty())
            return null;
        return throwables.get(0);
    }

    @NonNull
    public List<Throwable> getCauses() {
        List<Throwable> throwables = error.getThrowablesList();
        return throwables == null ? Collections.emptyList() : throwables;
    }

    @NonNull
    @Override
    public String toString() {
        return error.getErrorLogString();
    }
}
