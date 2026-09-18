package com.termux.shared.shell.command.result;

import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.Intent;

import com.termux.shared.errors.TermuxException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Unit tests for the sendCommandResultData*OrThrow sibling methods added for beads-1kf
 * (ResultSenderErrno migration, part of the beads-xs0 Errno/Error deprecate-first migration
 * family).
 *
 * <p>As with beads-km2, tests that would need to write a brand-new result file to a directory
 * are avoided (see beads-94h): sendCommandResultDataToDirectory's temp-file-then-move pattern
 * hits the same Robolectric Os.lstat ENOENT gap for the initial write. Tests here focus on
 * validation branches and the PendingIntent-based success path, which doesn't touch the
 * filesystem at all.
 */
@RunWith(RobolectricTestRunner.class)
public class ResultSenderOrThrowTest {

    private ResultData newResultData(String stdout, int exitCode) {
        ResultData data = new ResultData();
        data.appendStdout(stdout);
        data.exitCode = exitCode;
        return data;
    }

    private PendingIntent newPendingIntent() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), android.app.Activity.class);
        return PendingIntent.getActivity(RuntimeEnvironment.getApplication(), 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Test
    public void sendCommandResultDataOrThrow_throwsForNullParams() {
        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataOrThrow(null, "test", "label", null, null, false));
    }

    @Test
    public void sendCommandResultDataOrThrow_throwsWhenNeitherPendingIntentNorDirectorySet() {
        ResultConfig resultConfig = new ResultConfig();
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataOrThrow(RuntimeEnvironment.getApplication(), "test", "label", resultConfig, resultData, false));
    }

    @Test
    public void sendCommandResultDataWithPendingIntentOrThrow_throwsForMissingRequiredFields() {
        ResultConfig resultConfig = new ResultConfig();
        resultConfig.resultPendingIntent = newPendingIntent();
        // resultBundleKey deliberately left null.
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataWithPendingIntentOrThrow(RuntimeEnvironment.getApplication(), "test", "label", resultConfig, resultData, false));
    }

    @Test
    public void sendCommandResultDataWithPendingIntentOrThrow_doesNotThrowForValidSend() throws TermuxException {
        ResultConfig resultConfig = new ResultConfig();
        resultConfig.resultPendingIntent = newPendingIntent();
        resultConfig.resultBundleKey = "result";
        resultConfig.resultStdoutKey = "stdout";
        resultConfig.resultStdoutOriginalLengthKey = "stdout_original_length";
        resultConfig.resultStderrKey = "stderr";
        resultConfig.resultStderrOriginalLengthKey = "stderr_original_length";
        resultConfig.resultExitCodeKey = "exit_code";
        resultConfig.resultErrCodeKey = "err_code";
        resultConfig.resultErrmsgKey = "errmsg";

        ResultData resultData = newResultData("hello", 0);

        ResultSender.sendCommandResultDataWithPendingIntentOrThrow(RuntimeEnvironment.getApplication(), "test", "label", resultConfig, resultData, false);
    }

    @Test
    public void sendCommandResultDataToDirectoryOrThrow_throwsForNullDirectoryPath() {
        ResultConfig resultConfig = new ResultConfig();
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataToDirectoryOrThrow(RuntimeEnvironment.getApplication(), "test", "label", resultConfig, resultData, false));
    }

    @Test
    public void sendCommandResultDataToDirectoryOrThrow_throwsForInvalidSingleFileBasename() {
        ResultConfig resultConfig = new ResultConfig();
        resultConfig.resultDirectoryPath = "/tmp";
        resultConfig.resultSingleFile = true;
        resultConfig.resultFileBasename = "contains/a/slash";
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataToDirectoryOrThrow(RuntimeEnvironment.getApplication(), "test", "label", resultConfig, resultData, false));
    }

    @Test
    public void sendCommandResultDataToDirectoryOrThrow_throwsForInvalidFilesSuffix() {
        ResultConfig resultConfig = new ResultConfig();
        resultConfig.resultDirectoryPath = "/tmp";
        resultConfig.resultSingleFile = false;
        resultConfig.resultFilesSuffix = "contains/a/slash";
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataToDirectoryOrThrow(RuntimeEnvironment.getApplication(), "test", "label", resultConfig, resultData, false));
    }
}
