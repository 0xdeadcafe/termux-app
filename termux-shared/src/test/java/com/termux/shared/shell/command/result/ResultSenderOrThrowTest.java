package com.termux.shared.shell.command.result;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.content.Intent;

import com.termux.shared.errors.TermuxException;
import com.termux.shared.file.filesystem.NativeDispatcherEnoentFixRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public NativeDispatcherEnoentFixRule enoentFix = new NativeDispatcherEnoentFixRule();

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

    // -----------------------------------------------------------------------
    // Success paths (previously blocked by beads-94h; now unlocked via enoentFix)
    // -----------------------------------------------------------------------

    @Test
    public void sendCommandResultDataToDirectoryOrThrow_singleFile_writesResultFile() throws Exception {
        File outputDir = tempFolder.newFolder("result-output");

        ResultConfig resultConfig = new ResultConfig();
        resultConfig.resultDirectoryPath = outputDir.getAbsolutePath();
        resultConfig.resultSingleFile = true;
        resultConfig.resultFileBasename = "result";

        ResultData resultData = newResultData("hello from result", 0);

        ResultSender.sendCommandResultDataToDirectoryOrThrow(
            RuntimeEnvironment.getApplication(), "test", "label", resultConfig, resultData, false);

        File resultFile = new File(outputDir, "result");
        assertTrue("result file must exist after send", resultFile.exists());
        String content = new String(Files.readAllBytes(resultFile.toPath()), StandardCharsets.UTF_8);
        assertTrue("result file must contain stdout", content.contains("hello from result"));
    }

    // -----------------------------------------------------------------------
    // Validation / error branches
    // -----------------------------------------------------------------------

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
