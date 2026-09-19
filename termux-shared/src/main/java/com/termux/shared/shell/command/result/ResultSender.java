package com.termux.shared.shell.command.result;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.termux.shared.R;
import com.termux.shared.data.DataUtils;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.errors.TermuxException;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.errors.FunctionErrno;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.shell.command.ShellCommandConstants.RESULT_SENDER;

public class ResultSender {

    private static final String LOG_TAG = "ResultSender";

    /**
     * Send {@link ResultData} to the caller via the supplied destinations.
     *
     * <p>If both {@code pi} and {@code dir} are non-null the result is delivered via both channels
     * in order: PendingIntent first, then directory. If the PendingIntent delivery fails the
     * directory delivery is skipped.
     *
     * @param context              The {@link Context} for operations.
     * @param logTag               The log tag to use for logging.
     * @param label                The label for the command.
     * @param pi                   The {@link ResultDestination.PendingIntentResult}, or {@code null}.
     * @param dir                  The {@link ResultDestination.DirectoryResult}, or {@code null}.
     * @param resultData           The {@link ResultData} object containing result data.
     * @param logStdoutAndStderr   Set to {@code true} to log stdout/stderr.
     * @throws TermuxException If a delivery step fails.
     */
    public static void sendCommandResultDataOrThrow(Context context, String logTag, String label,
            ResultDestination.PendingIntentResult pi,
            ResultDestination.DirectoryResult dir,
            ResultData resultData, boolean logStdoutAndStderr) throws TermuxException {
        if (context == null || (pi == null && dir == null) || resultData == null)
            throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETERS.getError(
                    "context, resultData, or both pi and dir", "sendCommandResultDataOrThrow"));

        if (pi != null) {
            sendCommandResultDataWithPendingIntentOrThrow(context, logTag, label, pi, resultData, logStdoutAndStderr);
            if (dir == null) return;
        }

        if (dir != null)
            sendCommandResultDataToDirectoryOrThrow(context, logTag, label, dir, resultData, logStdoutAndStderr);
    }

    /**
     * Send {@link ResultData} to the command caller via {@link ResultDestination.PendingIntentResult}.
     *
     * @param context            The {@link Context} for operations.
     * @param logTag             The log tag to use for logging.
     * @param label              The label for the command.
     * @param pi                 The {@link ResultDestination.PendingIntentResult} describing the delivery.
     * @param resultData         The {@link ResultData} object containing result data.
     * @param logStdoutAndStderr Set to {@code true} to log stdout/stderr.
     * @throws TermuxException If delivery fails.
     */
    public static void sendCommandResultDataWithPendingIntentOrThrow(Context context, String logTag, String label,
            ResultDestination.PendingIntentResult pi,
            ResultData resultData, boolean logStdoutAndStderr) throws TermuxException {
        if (context == null || pi == null || resultData == null)
            throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(
                    "context, pi or resultData", "sendCommandResultDataWithPendingIntentOrThrow"));

        logTag = DataUtils.getDefaultIfNull(logTag, LOG_TAG);

        Logger.logDebugExtended(logTag, "Sending result for command \"" + label + "\":\n" + pi + "\n" + ResultData.getResultDataLogString(resultData, logStdoutAndStderr));

        String resultDataStdout = resultData.stdout.toString();
        String resultDataStderr = resultData.stderr.toString();

        String stdoutOriginalLength = String.valueOf(resultDataStdout.length());
        String stderrOriginalLength = String.valueOf(resultDataStderr.length());

        // Truncate stdout and stderr to TRANSACTION_SIZE_LIMIT_IN_BYTES
        String truncatedStdout = null;
        String truncatedStderr = null;

        if (resultDataStderr.isEmpty()) {
            truncatedStdout = DataUtils.getTruncatedCommandOutput(resultDataStdout, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, false, false);
        } else if (resultDataStdout.isEmpty()) {
            truncatedStderr = DataUtils.getTruncatedCommandOutput(resultDataStderr, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, false, false);
        } else {
            truncatedStdout = DataUtils.getTruncatedCommandOutput(resultDataStdout, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES / 2, false, false, false);
            truncatedStderr = DataUtils.getTruncatedCommandOutput(resultDataStderr, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES / 2, false, false, false);
        }

        if (truncatedStdout != null && truncatedStdout.length() < resultDataStdout.length()) {
            Logger.logWarn(logTag, "The result for command \"" + label + "\" stdout length truncated from " + stdoutOriginalLength + " to " + truncatedStdout.length());
            resultDataStdout = truncatedStdout;
        }

        if (truncatedStderr != null && truncatedStderr.length() < resultDataStderr.length()) {
            Logger.logWarn(logTag, "The result for command \"" + label + "\" stderr length truncated from " + stderrOriginalLength + " to " + truncatedStderr.length());
            resultDataStderr = truncatedStderr;
        }

        String resultDataErrmsg = null;
        if (resultData.isStateFailed()) {
            resultDataErrmsg = ResultData.getErrorsListLogString(resultData);
            if (resultDataErrmsg.isEmpty()) resultDataErrmsg = null;
        }

        String errmsgOriginalLength = (resultDataErrmsg == null) ? null : String.valueOf(resultDataErrmsg.length());

        // Truncate error to TRANSACTION_SIZE_LIMIT_IN_BYTES / 4 (trim from end to preserve stacktrace start)
        String truncatedErrmsg = DataUtils.getTruncatedCommandOutput(resultDataErrmsg, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES / 4, true, false, false);
        if (truncatedErrmsg != null && truncatedErrmsg.length() < resultDataErrmsg.length()) {
            Logger.logWarn(logTag, "The result for command \"" + label + "\" error length truncated from " + errmsgOriginalLength + " to " + truncatedErrmsg.length());
            resultDataErrmsg = truncatedErrmsg;
        }

        final Bundle resultBundle = new Bundle();
        resultBundle.putString(pi.stdoutKey, resultDataStdout);
        resultBundle.putString(pi.stdoutOriginalLengthKey, stdoutOriginalLength);
        resultBundle.putString(pi.stderrKey, resultDataStderr);
        resultBundle.putString(pi.stderrOriginalLengthKey, stderrOriginalLength);
        if (resultData.exitCode != null)
            resultBundle.putInt(pi.exitCodeKey, resultData.exitCode);
        resultBundle.putInt(pi.errCodeKey, resultData.getErrCode());
        resultBundle.putString(pi.errmsgKey, resultDataErrmsg);

        Intent resultIntent = new Intent();
        resultIntent.putExtra(pi.bundleKey, resultBundle);

        try {
            pi.pendingIntent.send(context, Activity.RESULT_OK, resultIntent);
        } catch (PendingIntent.CanceledException e) {
            // The caller doesn't want the result anymore — ignore
            Logger.logDebug(logTag, "The command \"" + label + "\" creator " + pi.pendingIntent.getCreatorPackage() + " does not want the results anymore");
        }
    }

    /**
     * Send {@link ResultData} to the command caller by writing it to files in
     * {@link ResultDestination.DirectoryResult#directoryPath}.
     *
     * @param context            The {@link Context} for operations.
     * @param logTag             The log tag to use for logging.
     * @param label              The label for the command.
     * @param dir                The {@link ResultDestination.DirectoryResult} describing the delivery.
     * @param resultData         The {@link ResultData} object containing result data.
     * @param logStdoutAndStderr Set to {@code true} to log stdout/stderr.
     * @throws TermuxException If delivery fails.
     */
    public static void sendCommandResultDataToDirectoryOrThrow(Context context, String logTag, String label,
            ResultDestination.DirectoryResult dir,
            ResultData resultData, boolean logStdoutAndStderr) throws TermuxException {
        if (context == null || dir == null || resultData == null)
            throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(
                    "context, dir or resultData", "sendCommandResultDataToDirectoryOrThrow"));

        logTag = DataUtils.getDefaultIfNull(logTag, LOG_TAG);

        String resultDataStdout = resultData.stdout.toString();
        String resultDataStderr = resultData.stderr.toString();

        String resultDataExitCode = "";
        if (resultData.exitCode != null)
            resultDataExitCode = String.valueOf(resultData.exitCode);

        String resultDataErrmsg = DataUtils.getDefaultIfNull(
                resultData.isStateFailed() ? ResultData.getErrorsListLogString(resultData) : null, "");

        Logger.logDebugExtended(logTag, "Writing result for command \"" + label + "\":\n" + dir + "\n" + ResultData.getResultDataLogString(resultData, logStdoutAndStderr));

        try {
            FileUtils.validateDirectoryFileExistenceAndPermissionsOrThrow("result", dir.directoryPath,
                dir.directoryAllowedParentPath, true,
                FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS, true, true,
                true, true);
        } catch (TermuxException e) {
            e.getError().appendMessage("\n" + context.getString(R.string.msg_directory_absolute_path, "Result", dir.directoryPath));
            throw e;
        }

        if (dir.singleFile) {
            if (DataUtils.isNullOrEmpty(dir.fileBasename) || dir.fileBasename.contains("/"))
                throw new TermuxException(ResultSenderErrno.ERROR_RESULT_FILE_BASENAME_NULL_OR_INVALID.getError(dir.fileBasename));

            String error_or_output;

            if (resultData.isStateFailed()) {
                try {
                    if (DataUtils.isNullOrEmpty(dir.fileErrorFormat)) {
                        error_or_output = String.format(RESULT_SENDER.FORMAT_FAILED_ERR__ERRMSG__STDOUT__STDERR__EXIT_CODE,
                            MarkdownUtils.getMarkdownCodeForString(String.valueOf(resultData.getErrCode()), false),
                            MarkdownUtils.getMarkdownCodeForString(resultDataErrmsg, true),
                            MarkdownUtils.getMarkdownCodeForString(resultDataStdout, true),
                            MarkdownUtils.getMarkdownCodeForString(resultDataStderr, true),
                            MarkdownUtils.getMarkdownCodeForString(resultDataExitCode, false));
                    } else {
                        error_or_output = String.format(dir.fileErrorFormat,
                            resultData.getErrCode(), resultDataErrmsg, resultDataStdout, resultDataStderr, resultDataExitCode);
                    }
                } catch (Exception e) {
                    throw new TermuxException(ResultSenderErrno.ERROR_FORMAT_RESULT_ERROR_FAILED_WITH_EXCEPTION.getError(e.getMessage()));
                }
            } else {
                try {
                    if (DataUtils.isNullOrEmpty(dir.fileOutputFormat)) {
                        if (resultDataStderr.isEmpty() && resultDataExitCode.equals("0"))
                            error_or_output = String.format(RESULT_SENDER.FORMAT_SUCCESS_STDOUT, resultDataStdout);
                        else if (resultDataStderr.isEmpty())
                            error_or_output = String.format(RESULT_SENDER.FORMAT_SUCCESS_STDOUT__EXIT_CODE,
                                resultDataStdout,
                                MarkdownUtils.getMarkdownCodeForString(resultDataExitCode, false));
                        else
                            error_or_output = String.format(RESULT_SENDER.FORMAT_SUCCESS_STDOUT__STDERR__EXIT_CODE,
                                MarkdownUtils.getMarkdownCodeForString(resultDataStdout, true),
                                MarkdownUtils.getMarkdownCodeForString(resultDataStderr, true),
                                MarkdownUtils.getMarkdownCodeForString(resultDataExitCode, false));
                    } else {
                        error_or_output = String.format(dir.fileOutputFormat,
                            resultDataStdout, resultDataStderr, resultDataExitCode);
                    }
                } catch (Exception e) {
                    throw new TermuxException(ResultSenderErrno.ERROR_FORMAT_RESULT_OUTPUT_FAILED_WITH_EXCEPTION.getError(e.getMessage()));
                }
            }

            String temp_filename = dir.fileBasename + "-" + AndroidUtils.getCurrentMilliSecondLocalTimeStamp();
            FileUtils.writeTextToFileOrThrow(temp_filename, dir.directoryPath + "/" + temp_filename,
                null, error_or_output, false);
            FileUtils.moveRegularFileOrThrow("error or output temp file", dir.directoryPath + "/" + temp_filename,
                dir.directoryPath + "/" + dir.fileBasename, false);
        } else {
            String filename;

            if (dir.filesSuffix.contains("/"))
                throw new TermuxException(ResultSenderErrno.ERROR_RESULT_FILES_SUFFIX_INVALID.getError(dir.filesSuffix));

            if (!resultDataStdout.isEmpty()) {
                filename = RESULT_SENDER.RESULT_FILE_STDOUT_PREFIX + dir.filesSuffix;
                FileUtils.writeTextToFileOrThrow(filename, dir.directoryPath + "/" + filename,
                    null, resultDataStdout, false);
            }

            if (!resultDataStderr.isEmpty()) {
                filename = RESULT_SENDER.RESULT_FILE_STDERR_PREFIX + dir.filesSuffix;
                FileUtils.writeTextToFileOrThrow(filename, dir.directoryPath + "/" + filename,
                    null, resultDataStderr, false);
            }

            if (!resultDataExitCode.isEmpty()) {
                filename = RESULT_SENDER.RESULT_FILE_EXIT_CODE_PREFIX + dir.filesSuffix;
                FileUtils.writeTextToFileOrThrow(filename, dir.directoryPath + "/" + filename,
                    null, resultDataExitCode, false);
            }

            if (resultData.isStateFailed() && !resultDataErrmsg.isEmpty()) {
                filename = RESULT_SENDER.RESULT_FILE_ERRMSG_PREFIX + dir.filesSuffix;
                FileUtils.writeTextToFileOrThrow(filename, dir.directoryPath + "/" + filename,
                    null, resultDataErrmsg, false);
            }

            // Write errCode to temp file first (atomic rename ensures caller sees complete file)
            String temp_filename = RESULT_SENDER.RESULT_FILE_ERR_PREFIX + "-" + AndroidUtils.getCurrentMilliSecondLocalTimeStamp();
            if (!dir.filesSuffix.isEmpty()) temp_filename = temp_filename + "-" + dir.filesSuffix;
            FileUtils.writeTextToFileOrThrow(temp_filename, dir.directoryPath + "/" + temp_filename,
                null, String.valueOf(resultData.getErrCode()), false);

            filename = RESULT_SENDER.RESULT_FILE_ERR_PREFIX + dir.filesSuffix;
            FileUtils.moveRegularFileOrThrow(RESULT_SENDER.RESULT_FILE_ERR_PREFIX + " temp file",
                dir.directoryPath + "/" + temp_filename,
                dir.directoryPath + "/" + filename, false);
        }
    }

}
