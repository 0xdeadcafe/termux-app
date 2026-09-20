package com.termux.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

import com.termux.R;
import com.termux.shared.data.DataUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.termux.plugins.TermuxPluginUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.file.filesystem.FileType;
import com.termux.shared.errors.Errno;
import com.termux.shared.errors.Error;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;
import com.termux.shared.file.FileUtils;
import com.termux.shared.errors.TermuxException;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.shell.command.CommandMetadata;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.shell.command.ExecutionRequest;
import com.termux.shared.shell.command.result.ResultIntake;

/**
 * A service that receives {@link RUN_COMMAND_SERVICE#ACTION_RUN_COMMAND} intent from third party apps and
 * plugins that contains info on command execution and forwards the extras to {@link TermuxService}
 * for the actual execution.
 *
 * Check https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent for more info.
 */
public class RunCommandService extends Service {

    private static final String LOG_TAG = "RunCommandService";

    @Override
    public IBinder onBind(Intent intent) {
        return null; // This service is not designed to be bound
    }

    @Override
    public void onCreate() {
        Logger.logVerbose(LOG_TAG, "onCreate");
        runStartForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.logDebug(LOG_TAG, "onStartCommand");

        if (intent == null) return Service.START_NOT_STICKY;

        // Start foreground notification before spawning the background thread so the service
        // is not killed while the background work (SharedPreferences / file I/O) is running.
        runStartForeground();

        // Move all intent processing to a background thread to avoid blocking the main thread
        // with disk I/O (properties file, SharedPreferences, canonical path, file validation)
        // which risks an ANR after 5 seconds.
        final Intent intentToProcess = intent;
        new Thread(() -> processCommandIntentAsync(intentToProcess)).start();

        return Service.START_NOT_STICKY;
    }

    /** Processes the RUN_COMMAND intent on a background thread. */
    private void processCommandIntentAsync(Intent intent) {
        Logger.logVerboseExtended(LOG_TAG, "Intent Received:\n" + IntentUtils.getIntentString(intent));

        // Minimal early request: only pluginAPIHelp + pendingIntent for error notifications
        // before full validation is complete.
        String pluginAPIHelp = this.getString(R.string.error_run_command_service_api_help, RUN_COMMAND_SERVICE.RUN_COMMAND_API_HELP_URL);
        ExecutionCommand executionCommand = new ExecutionCommand();
        executionCommand.request = new ExecutionRequest.Builder()
            .metadata(new CommandMetadata("Execution Command", null, null, pluginAPIHelp, null, false))
            .build();

        Error error;
        String errmsg;

        // If invalid action passed, then just return
        if (!RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND.equals(intent.getAction())) {
            errmsg = this.getString(R.string.error_run_command_service_invalid_intent_action, intent.getAction());
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg);
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false);
            stopService(); return;
        }

        // --- Stage 1: parse all intent extras into local variables ---

        String executableExtra = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, null);
        String executable = executableExtra;
        String[] arguments = IntentUtils.getStringArrayExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, null);

        /*
        * If intent was sent with `am` command, then normal comma characters may have been replaced
        * with alternate characters if a normal comma existed in an argument itself to prevent it
        * splitting into multiple arguments by `am` command.
        */
        boolean replaceCommaAlternativeCharsInArguments = intent.getBooleanExtra(RUN_COMMAND_SERVICE.EXTRA_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS, false);
        if (replaceCommaAlternativeCharsInArguments) {
            String commaAlternativeCharsInArguments = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS, null);
            if (commaAlternativeCharsInArguments == null)
                commaAlternativeCharsInArguments = TermuxConstants.COMMA_ALTERNATIVE;
            DataUtils.replaceSubStringsInStringArrayItems(arguments, commaAlternativeCharsInArguments, TermuxConstants.COMMA_NORMAL);
        }

        String stdin = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_STDIN, null);
        String workingDirectory = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_WORKDIR, null);

        String runnerStr = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RUNNER,
            intent.getBooleanExtra(RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, false) ? Runner.APP_SHELL.getName() : Runner.TERMINAL_SESSION.getName());
        Runner runner = Runner.runnerOf(runnerStr);
        if (runner == null) {
            errmsg = this.getString(R.string.error_run_command_service_invalid_execution_command_runner, runnerStr);
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg);
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false);
            stopService(); return;
        }

        Integer backgroundCustomLogLevel = IntentUtils.getIntegerExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, null);
        String sessionAction = intent.getStringExtra(RUN_COMMAND_SERVICE.EXTRA_SESSION_ACTION);
        String shellName = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_SHELL_NAME, null);
        String shellCreateMode = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_SHELL_CREATE_MODE, null);
        CommandMetadata metadata = new CommandMetadata(
            IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, "RUN_COMMAND Execution Intent Command"),
            IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMAND_DESCRIPTION, null),
            IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_COMMAND_HELP, null),
            pluginAPIHelp, null, true);

        // Set pendingIntent early so that the allow-external-apps error notification can show
        // the creator package. Directory fields are withheld until the check passes (see below).
        PendingIntent earlyPi = intent.getParcelableExtra(RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT);
        if (earlyPi != null)
            executionCommand.request = executionCommand.request.toBuilder()
                .resultIntake(new ResultIntake(earlyPi, null, false, null, null, null, "")).build();

        // --- Stage 2: allow-external-apps security check ---

        // If "allow-external-apps" property to not set to "true", then just return
        errmsg = TermuxPluginUtils.checkIfAllowExternalAppsPolicyIsViolated(this, LOG_TAG);
        if (errmsg != null) {
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg);
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, true);
            stopService(); return;
        }

        // Do not send result back to any file based result config before "allow-external-app"
        // property has been ensured to be "true", otherwise clients can overwrite files inside
        // Termux home or prefix, or external storage (if Termux has been granted permission) using
        // `DirectoryResult.fileErrorFormat` as `ResultSender.sendCommandResultDataToDirectoryOrThrow()`
        // uses client controlled format passed to `String.format()` that is used to set the error
        // file content, which can even be used to overwrite `termux.properties` file to set
        // `allow-external-app=true` in it or any other shell rc file. The client would still need
        // to have the `RUN_COMMAND` permission before it could do this.
        // Note that clients waiting for file based result will hang if "allow-external-app" property
        // is not "true" if we do not send the result, but a notification will still be shown for
        // clients to know. A result is still sent if pending intent is used as that does not have
        // this security issue.
        PendingIntent resultPi = intent.getParcelableExtra(RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT);
        String resultDir = IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_DIRECTORY, null);
        ResultIntake resultIntake = (resultPi != null || resultDir != null) ? new ResultIntake(
            resultPi, resultDir,
            intent.getBooleanExtra(RUN_COMMAND_SERVICE.EXTRA_RESULT_SINGLE_FILE, false),
            IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_FILE_BASENAME, null),
            IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_FILE_OUTPUT_FORMAT, null),
            IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_FILE_ERROR_FORMAT, null),
            IntentUtils.getStringExtraIfSet(intent, RUN_COMMAND_SERVICE.EXTRA_RESULT_FILES_SUFFIX, "")) : null;
        // Now that we have the full resultIntake, update the command for any remaining error paths
        executionCommand.request = executionCommand.request.toBuilder()
            .metadata(metadata).resultIntake(resultIntake).build();

        // --- Stage 3: validate executable ---

        if (executable == null || executable.isEmpty()) {
            errmsg = this.getString(R.string.error_run_command_service_mandatory_extra_missing, RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH);
            executionCommand.setStateFailed(Errno.ERRNO_FAILED.getCode(), errmsg);
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false);
            stopService(); return;
        }

        executable = TermuxFileUtils.getCanonicalPath(executable, null, true);

        try {
            FileUtils.validateRegularFileExistenceAndPermissionsOrThrow("executable", executable, null,
                FileUtils.APP_EXECUTABLE_FILE_PERMISSIONS, true, true, false);
        } catch (TermuxException e) {
            executionCommand.setStateFailed(e.getError());
            TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false);
            stopService(); return;
        }

        // --- Stage 4: validate workingDirectory ---

        if (workingDirectory != null && !workingDirectory.isEmpty()) {
            workingDirectory = TermuxFileUtils.getCanonicalPath(workingDirectory, null, true);
            error = TermuxFileUtils.validateDirectoryFileExistenceAndPermissions("working", workingDirectory,
                true, true, true, false, true);
            if (error != null) {
                executionCommand.setStateFailed(error);
                TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false);
                stopService(); return;
            }
        }

        // If the executable passed as the extra was an applet for coreutils/busybox, use it
        // instead of the canonical path since otherwise arguments would be passed to coreutils/busybox.
        executableExtra = TermuxFileUtils.getExpandedTermuxPath(executableExtra);
        if (FileUtils.getFileType(executableExtra, false) == FileType.SYMLINK) {
            Logger.logVerbose(LOG_TAG, "The executableExtra path \"" + executableExtra + "\" is a symlink so using it instead of the canonical path \"" + executable + "\"");
            executable = executableExtra;
        }

        Uri executableUri = new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(executable).build();

        // --- Stage 5: build final request and forward to TermuxService ---

        executionCommand.request = new ExecutionRequest.Builder()
            .executable(executable)
            .executableUri(executableUri)
            .arguments(arguments)
            .stdin(stdin)
            .workingDirectory(workingDirectory)
            .runner(runner)
            .backgroundCustomLogLevel(backgroundCustomLogLevel)
            .sessionAction(sessionAction)
            .shellName(shellName)
            .shellCreateMode(shellCreateMode)
            .metadata(metadata)
            .resultIntake(resultIntake)
            .build();

        Logger.logVerboseExtended(LOG_TAG, executionCommand.toString());

        Intent execIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executionCommand.request.executableUri);
        execIntent.setClass(this, TermuxService.class);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, executionCommand.request.arguments);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_STDIN, executionCommand.request.stdin);
        if (executionCommand.request.workingDirectory != null && !executionCommand.request.workingDirectory.isEmpty())
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, executionCommand.request.workingDirectory);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, executionCommand.request.runner != null ? executionCommand.request.runner.getName() : null);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, executionCommand.request.backgroundCustomLogLevel == null ? null : String.valueOf((int) executionCommand.request.backgroundCustomLogLevel));
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION, executionCommand.request.sessionAction);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, executionCommand.request.shellName);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, executionCommand.request.shellCreateMode);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, executionCommand.request.metadata.label);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_DESCRIPTION, executionCommand.request.metadata.description);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_HELP, executionCommand.request.metadata.help);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PLUGIN_API_HELP, executionCommand.request.metadata.pluginAPIHelp);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, executionCommand.request.resultIntake != null ? executionCommand.request.resultIntake.pendingIntent : null);
        execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_DIRECTORY, executionCommand.request.resultIntake != null ? executionCommand.request.resultIntake.directoryPath : null);
        if (executionCommand.request.resultIntake != null && executionCommand.request.resultIntake.directoryPath != null) {
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_SINGLE_FILE, executionCommand.request.resultIntake.singleFile);
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_FILE_BASENAME, executionCommand.request.resultIntake.fileBasename);
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_FILE_OUTPUT_FORMAT, executionCommand.request.resultIntake.fileOutputFormat);
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_FILE_ERROR_FORMAT, executionCommand.request.resultIntake.fileErrorFormat);
            execIntent.putExtra(TERMUX_SERVICE.EXTRA_RESULT_FILES_SUFFIX, executionCommand.request.resultIntake.filesSuffix);
        }

        // Start TERMUX_SERVICE and pass it execution intent
        this.startForegroundService(execIntent);

        stopService();
    }

    private void stopService() {
        runStopForeground();
        stopSelf();
    }

    private void runStartForeground() {
        setupNotificationChannel();
        startForeground(TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_ID, buildNotification());
    }

    private void runStopForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }

    private Notification buildNotification() {
        // Build the notification
        NotificationCompat.Builder builder =  NotificationUtils.getNotificationBuilder(this,
            TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_ID, NotificationCompat.PRIORITY_LOW,
            TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_NAME, null, null,
            null, null);
        if (builder == null)  return null;

        // No need to show a timestamp:
        builder.setShowWhen(false);

        // Set notification icon
        builder.setSmallIcon(R.drawable.ic_service_notification);

        // Set background color for small notification icon
        builder.setColor(0xFF607D8B);

        return builder.build();
    }

    private void setupNotificationChannel() {
        NotificationUtils.setupNotificationChannel(this, TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_ID,
            TermuxConstants.TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
    }

}
