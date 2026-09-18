package com.termux.shared.errors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Exception-throwing equivalent of {@link FunctionErrno}'s parameter validation errors.
 *
 * <p>Part of the {@code Errno}/{@link Error} deprecate-first migration (see {@code beads-xs0},
 * {@code beads-9rw}). {@link FunctionErrno} is used cross-cutting from several domains
 * (FileUtils, ActivityUtils, ResultSender, PermissionUtils) to build a nullable {@link Error}
 * for common parameter-validation failures (null/empty, unset, invalid, wrong type). Each
 * domain's own {@code *OrThrow} migration (see {@code beads-km2}, {@code beads-hg1},
 * {@code beads-q2n}, {@code beads-1kf}, {@code beads-jw0}) should call these helpers directly
 * instead of constructing a {@link FunctionErrno} {@link Error} and converting it separately.
 *
 * <p>Naming convention used by this class:
 * <ul>
 *   <li>{@code throwIfX(...)} — conditional: performs the check itself and only throws on
 *   failure. Prefer these, they collapse the old {@code if (...) return errno.getError(...);}
 *   boilerplate into a single line.</li>
 *   <li>{@code throwX(...)} — unconditional: always throws. For call sites where the caller has
 *   already evaluated a more complex condition (e.g. "none of these 4 parameters are set") and
 *   just needs to report it with the right wording.</li>
 * </ul>
 */
public class FunctionException extends TermuxException {

    private FunctionException(@NonNull final Error error) {
        super(error);
    }

    /**
     * Throws if {@code value} is {@code null} or empty.
     *
     * @param value The parameter value to check.
     * @param paramNameLabel The name/label of the parameter, as it should appear in the message,
     *                        e.g. {@code "file path"} or {@code label + "file path"}.
     * @param methodName The name of the method the parameter was passed to.
     */
    public static void throwIfNullOrEmpty(@Nullable final String value, @NonNull final String paramNameLabel,
                                           @NonNull final String methodName) throws FunctionException {
        if (value == null || value.isEmpty())
            throw new FunctionException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(paramNameLabel, methodName));
    }

    /**
     * Unconditionally throws the "parameters are null or empty" (plural wording) error. Callers
     * should evaluate their own null/empty condition across the relevant parameters first.
     *
     * @param paramNamesLabel A label describing the affected parameters, e.g.
     *                         {@code "context, resultConfig or resultData"}.
     * @param methodName The name of the method the parameters were passed to.
     */
    public static void throwNullOrEmptyParameters(@NonNull final String paramNamesLabel,
                                                    @NonNull final String methodName) throws FunctionException {
        throw new FunctionException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETERS.getError(paramNamesLabel, methodName));
    }

    /**
     * Throws if {@code value} is {@code null}.
     *
     * @param value The parameter value to check.
     * @param paramNameLabel The name/label of the parameter, as it should appear in the message.
     * @param methodName The name of the method the parameter was passed to.
     */
    public static void throwIfUnset(@Nullable final Object value, @NonNull final String paramNameLabel,
                                     @NonNull final String methodName) throws FunctionException {
        if (value == null)
            throw new FunctionException(FunctionErrno.ERRNO_UNSET_PARAMETER.getError(paramNameLabel, methodName));
    }

    /**
     * Unconditionally throws the "parameters must be set" (plural wording) error. Callers should
     * evaluate their own unset condition across the relevant parameters first.
     *
     * @param paramNamesLabel A label describing the affected parameters, e.g.
     *                         {@code "resultConfig.resultPendingIntent or resultConfig.resultDirectoryPath"}.
     * @param methodName The name of the method the parameters were passed to.
     */
    public static void throwUnsetParameters(@NonNull final String paramNamesLabel,
                                             @NonNull final String methodName) throws FunctionException {
        throw new FunctionException(FunctionErrno.ERRNO_UNSET_PARAMETERS.getError(paramNamesLabel, methodName));
    }

    /**
     * Unconditionally throws the "parameter is invalid" error. Callers should evaluate their own
     * validity condition first.
     *
     * @param paramNameLabel The name/label of the parameter, as it should appear in the message.
     * @param methodName The name of the method the parameter was passed to.
     * @param detail Extra detail appended to the message, e.g. {@code " It must be >= 0."}.
     */
    public static void throwInvalidParameter(@NonNull final String paramNameLabel, @NonNull final String methodName,
                                              @NonNull final String detail) throws FunctionException {
        throw new FunctionException(FunctionErrno.ERRNO_INVALID_PARAMETER.getError(paramNameLabel, methodName, detail));
    }

    /**
     * Throws if {@code value} is not an instance of {@code expectedType}.
     *
     * @param value The parameter value to check.
     * @param expectedType The {@link Class} that {@code value} is expected to be an instance of.
     * @param paramNameLabel The name/label of the parameter, as it should appear in the message.
     * @param methodName The name of the method the parameter was passed to.
     */
    public static void throwIfNotInstanceOf(@Nullable final Object value, @NonNull final Class<?> expectedType,
                                             @NonNull final String paramNameLabel, @NonNull final String methodName) throws FunctionException {
        if (!expectedType.isInstance(value))
            throw new FunctionException(FunctionErrno.ERRNO_PARAMETER_NOT_INSTANCE_OF.getError(paramNameLabel, methodName, expectedType.getSimpleName()));
    }

    /**
     * Unconditionally throws the "parameter is not an instance of" error. For call sites that
     * check against multiple acceptable types by hand (e.g. {@code instanceof AppCompatActivity
     * || instanceof Activity}) and just need to report the failure with a free-text description
     * of what was expected.
     *
     * @param paramNameLabel The name/label of the parameter, as it should appear in the message.
     * @param methodName The name of the method the parameter was passed to.
     * @param expectedTypeDescription A free-text description of the expected type(s), e.g.
     *                                 {@code "Activity or AppCompatActivity"}.
     */
    public static void throwNotInstanceOf(@NonNull final String paramNameLabel, @NonNull final String methodName,
                                           @NonNull final String expectedTypeDescription) throws FunctionException {
        throw new FunctionException(FunctionErrno.ERRNO_PARAMETER_NOT_INSTANCE_OF.getError(paramNameLabel, methodName, expectedTypeDescription));
    }
}
