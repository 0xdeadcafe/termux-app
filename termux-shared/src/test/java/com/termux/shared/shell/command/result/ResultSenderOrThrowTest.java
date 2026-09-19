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
 * Unit tests for {@link ResultSender}.
 *
 * <p>Tests cover the typed {@link ResultDestination} variants introduced when {@code ResultConfig}
 * was replaced with a sealed interface (beads-sl6).
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

    private ResultDestination.PendingIntentResult newFullPendingIntentResult() {
        return new ResultDestination.PendingIntentResult(
            newPendingIntent(),
            "result", "stdout", "stderr", "exit_code", "err_code", "errmsg",
            "stdout_original_length", "stderr_original_length");
    }

    // -----------------------------------------------------------------------
    // Success paths
    // -----------------------------------------------------------------------

    @Test
    public void sendCommandResultDataToDirectoryOrThrow_singleFile_writesResultFile() throws Exception {
        File outputDir = tempFolder.newFolder("result-output");

        ResultDestination.DirectoryResult dir = new ResultDestination.DirectoryResult(
            outputDir.getAbsolutePath(), null, true, "result", null, null, "");

        ResultData resultData = newResultData("hello from result", 0);

        ResultSender.sendCommandResultDataToDirectoryOrThrow(
            RuntimeEnvironment.getApplication(), "test", "label", dir, resultData, false);

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
            ResultSender.sendCommandResultDataOrThrow(null, "test", "label", null, null, null, false));
    }

    @Test
    public void sendCommandResultDataOrThrow_throwsWhenNeitherDestinationSet() {
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataOrThrow(
                RuntimeEnvironment.getApplication(), "test", "label", null, null, resultData, false));
    }

    @Test
    public void sendCommandResultDataWithPendingIntentOrThrow_throwsForNullPendingIntent() {
        // PendingIntentResult cannot be constructed with null pendingIntent by callers (it's @NonNull),
        // so we test the null-destination guard instead.
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataWithPendingIntentOrThrow(
                RuntimeEnvironment.getApplication(), "test", "label", null, resultData, false));
    }

    @Test
    public void sendCommandResultDataWithPendingIntentOrThrow_doesNotThrowForValidSend() throws TermuxException {
        ResultDestination.PendingIntentResult pi = newFullPendingIntentResult();
        ResultData resultData = newResultData("hello", 0);

        ResultSender.sendCommandResultDataWithPendingIntentOrThrow(
            RuntimeEnvironment.getApplication(), "test", "label", pi, resultData, false);
    }

    @Test
    public void sendCommandResultDataToDirectoryOrThrow_throwsForNullDir() {
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataToDirectoryOrThrow(
                RuntimeEnvironment.getApplication(), "test", "label", null, resultData, false));
    }

    @Test
    public void sendCommandResultDataToDirectoryOrThrow_throwsForInvalidSingleFileBasename() {
        ResultDestination.DirectoryResult dir = new ResultDestination.DirectoryResult(
            "/tmp", null, true, "contains/a/slash", null, null, "");
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataToDirectoryOrThrow(
                RuntimeEnvironment.getApplication(), "test", "label", dir, resultData, false));
    }

    @Test
    public void sendCommandResultDataToDirectoryOrThrow_throwsForInvalidFilesSuffix() {
        ResultDestination.DirectoryResult dir = new ResultDestination.DirectoryResult(
            "/tmp", null, false, null, null, null, "contains/a/slash");
        ResultData resultData = newResultData("hello", 0);

        assertThrows(TermuxException.class, () ->
            ResultSender.sendCommandResultDataToDirectoryOrThrow(
                RuntimeEnvironment.getApplication(), "test", "label", dir, resultData, false));
    }
}
