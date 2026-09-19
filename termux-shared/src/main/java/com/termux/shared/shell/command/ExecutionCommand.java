package com.termux.shared.shell.command;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.IntentUtils;
import com.termux.shared.shell.command.result.ResultDestination;
import com.termux.shared.shell.command.result.ResultData;
import com.termux.shared.shell.command.result.ResultIntake;
import com.termux.shared.errors.Error;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.terminal.TerminalSession;

import java.util.Collections;
import java.util.List;

public class ExecutionCommand {

    /*
    The {@link ExecutionState#SUCCESS} and {@link ExecutionState#FAILED} is defined based on
    successful execution of command without any internal errors or exceptions being raised.
    The shell command {@link #exitCode} being non-zero **does not** mean that execution command failed.
    Only the {@link #errCode} being non-zero means that execution command failed from the Termux app
    perspective.
    */

    /** The {@link Enum} that defines {@link ExecutionCommand} state. */
    public enum ExecutionState {

        PRE_EXECUTION("Pre-Execution", 0),
        EXECUTING("Executing", 1),
        EXECUTED("Executed", 2),
        SUCCESS("Success", 3),
        FAILED("Failed", 4);

        private final String name;
        private final int value;

        ExecutionState(final String name, final int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Runner {

        /** Run command in {@link TerminalSession}. */
        TERMINAL_SESSION("terminal-session"),

        /** Run command in {@link AppShell}. */
        APP_SHELL("app-shell");

        private final String name;

        Runner(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        /** Get {@link Runner} for {@code name} if found, otherwise {@code null}. */
        @Nullable
        public static Runner runnerOf(String name) {
            for (Runner v : Runner.values()) {
                if (v.name.equals(name)) {
                    return v;
                }
            }
            return null;
        }

        /** Get {@link Runner} for {@code name} if found, otherwise {@code def}. */
        @NonNull
        public static Runner runnerOf(@Nullable String name, @NonNull Runner def) {
            Runner runner = runnerOf(name);
            return runner != null ? runner : def;
        }
    }

    public enum ShellCreateMode {

        /** Always create {@link TerminalSession}. */
        ALWAYS("always"),

        /** Create shell only if no shell with {@link #shellName} found. */
        NO_SHELL_WITH_NAME("no-shell-with-name");

        private final String mode;

        ShellCreateMode(final String mode) {
            this.mode = mode;
        }

        public String getMode() {
            return mode;
        }

        public boolean equalsMode(String sessionCreateMode) {
            return sessionCreateMode != null && sessionCreateMode.equals(this.mode);
        }

        /** Get {@link ShellCreateMode} for {@code mode} if found, otherwise {@code null}. */
        @Nullable
        public static ShellCreateMode modeOf(String mode) {
            for (ShellCreateMode v : ShellCreateMode.values()) {
                if (v.mode.equals(mode)) {
                    return v;
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Execution inputs — all live in ExecutionRequest.
    // ------------------------------------------------------------------

    /**
     * All execution inputs. Mutable so that entry points (e.g. {@code RunCommandService})
     * can build the request incrementally across a staged validation flow; treat as
     * "written once" in practice.
     */
    public ExecutionRequest request;

    // ------------------------------------------------------------------
    // Runtime state — not part of the immutable request.
    // ------------------------------------------------------------------

    /** The process id of command. Set by the runner at launch time. */
    public int mPid = -1;

    /** Lifecycle state machine for this command. */
    public final ExecutionLifecycle lifecycle = new ExecutionLifecycle();

    /** Typed PendingIntent destination, or {@code null} if not active. */
    @Nullable public ResultDestination.PendingIntentResult resultPendingIntentDestination;
    /** Typed directory destination, or {@code null} if not active. */
    @Nullable public ResultDestination.DirectoryResult resultDirectoryDestination;

    /** Result data accumulated during execution. */
    public final ResultData resultData = new ResultData();

    private static final String LOG_TAG = "ExecutionCommand";

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /** Construct with a fully-built {@link ExecutionRequest}. */
    public ExecutionCommand(@NonNull ExecutionRequest request) {
        this.request = request;
    }

    /**
     * No-arg constructor for early error-reporting paths where the full request is not yet
     * known. Callers must assign {@link #request} before executing.
     */
    public ExecutionCommand() {
        this.request = new ExecutionRequest.Builder().build();
    }

    /**
     * Convenience constructor — only the id is known at construction time.
     * Callers must assign {@link #request} before executing.
     */
    public ExecutionCommand(Integer id) {
        this.request = new ExecutionRequest.Builder().id(id).build();
    }

    /**
     * Legacy convenience constructor for callers that supply the core execution inputs directly.
     * Prefer {@link ExecutionRequest.Builder} for new call sites.
     */
    public ExecutionCommand(Integer id, String executable, String[] arguments, String stdin,
                            String workingDirectory, Runner runner, boolean isFailsafe) {
        this.request = new ExecutionRequest.Builder()
                .id(id)
                .executable(executable)
                .arguments(arguments)
                .stdin(stdin)
                .workingDirectory(workingDirectory)
                .runner(runner)
                .isFailsafe(isFailsafe)
                .build();
    }

    // ------------------------------------------------------------------
    // Business logic
    // ------------------------------------------------------------------

    public boolean isPluginExecutionCommandWithPendingResult() {
        return request.metadata.isPlugin && request.resultIntake != null;
    }

    // ------------------------------------------------------------------
    // Lifecycle FSM delegates (forward to ExecutionLifecycle)
    // ------------------------------------------------------------------

    /** Delegate: advance state via {@link ExecutionLifecycle#setState}. */
    public boolean setState(ExecutionState newState) {
        return lifecycle.setState(newState, getCommandIdAndLabelLogString());
    }

    /** Delegate: {@link ExecutionLifecycle#hasExecuted()}. */
    public boolean hasExecuted() { return lifecycle.hasExecuted(); }

    /** Delegate: {@link ExecutionLifecycle#isExecuting()}. */
    public boolean isExecuting() { return lifecycle.isExecuting(); }

    /** Delegate: {@link ExecutionLifecycle#isSuccessful()}. */
    public boolean isSuccessful() { return lifecycle.isSuccessful(); }

    /** Delegate: {@link ExecutionLifecycle#shouldNotProcessResults()}. */
    public boolean shouldNotProcessResults() { return lifecycle.shouldNotProcessResults(); }

    // ------------------------------------------------------------------
    // setStateFailed — compound op touching both lifecycle and resultData
    // ------------------------------------------------------------------

    public synchronized boolean setStateFailed(@NonNull Error error) {
        return setStateFailed(error.getType(), error.getCode(), error.getMessage(), null);
    }

    public synchronized boolean setStateFailed(@NonNull Error error, Throwable throwable) {
        return setStateFailed(error.getType(), error.getCode(), error.getMessage(), Collections.singletonList(throwable));
    }

    public synchronized boolean setStateFailed(@NonNull Error error, List<Throwable> throwablesList) {
        return setStateFailed(error.getType(), error.getCode(), error.getMessage(), throwablesList);
    }

    public synchronized boolean setStateFailed(int code, String message) {
        return setStateFailed(null, code, message, null);
    }

    public synchronized boolean setStateFailed(int code, String message, Throwable throwable) {
        return setStateFailed(null, code, message, Collections.singletonList(throwable));
    }

    public synchronized boolean setStateFailed(int code, String message, List<Throwable> throwablesList) {
        return setStateFailed(null, code, message, throwablesList);
    }

    public synchronized boolean setStateFailed(String type, int code, String message, List<Throwable> throwablesList) {
        if (!this.resultData.setStateFailed(type, code, message, throwablesList)) {
            Logger.logWarn(LOG_TAG, "setStateFailed for " + getCommandIdAndLabelLogString() + " resultData encountered an error.");
        }
        return setState(ExecutionState.FAILED);
    }

    /** Returns {@code true} if state is FAILED AND {@link ResultData} contains at least one error. */
    public synchronized boolean isStateFailed() {
        if (lifecycle.getCurrentState() != ExecutionState.FAILED)
            return false;
        if (!resultData.isStateFailed()) {
            Logger.logWarn(LOG_TAG, "The " + getCommandIdAndLabelLogString()
                    + " has an invalid errCode value set in errors list while having ExecutionState.FAILED state.\n" + resultData.errorsList);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // toString / logging helpers
    // ------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() {
        if (!hasExecuted())
            return getExecutionInputLogString(this, true, true);
        else
            return getExecutionOutputLogString(this, true, true, true);
    }

    public static String getExecutionInputLogString(final ExecutionCommand ec, boolean ignoreNull, boolean logStdin) {
        if (ec == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(ec.getCommandIdAndLabelLogString()).append(":");
        if (ec.mPid != -1)
            sb.append("\n").append(ec.getPidLogString());
        if (ec.lifecycle.getPreviousState() != ExecutionState.PRE_EXECUTION)
            sb.append("\n").append(ec.getPreviousStateLogString());
        sb.append("\n").append(ec.getCurrentStateLogString());
        sb.append("\n").append(ec.getExecutableLogString());
        sb.append("\n").append(ec.getArgumentsLogString());
        sb.append("\n").append(ec.getWorkingDirectoryLogString());
        sb.append("\n").append(ec.getRunnerLogString());
        sb.append("\n").append(ec.getIsFailsafeLogString());
        if (ec.request.runner == Runner.APP_SHELL) {
            if (logStdin && (!ignoreNull || !DataUtils.isNullOrEmpty(ec.request.stdin)))
                sb.append("\n").append(ec.getStdinLogString());
            if (!ignoreNull || ec.request.backgroundCustomLogLevel != null)
                sb.append("\n").append(ec.getBackgroundCustomLogLevelLogString());
        }
        if (!ignoreNull || ec.request.sessionAction != null)
            sb.append("\n").append(ec.getSessionActionLogString());
        if (!ignoreNull || ec.request.shellName != null)
            sb.append("\n").append(ec.getShellNameLogString());
        if (!ignoreNull || ec.request.shellCreateMode != null)
            sb.append("\n").append(ec.getShellCreateModeLogString());
        sb.append("\n").append(ec.getSetRunnerShellEnvironmentLogString());
        if (!ignoreNull || ec.request.metadata.commandIntent != null)
            sb.append("\n").append(ec.getCommandIntentLogString());
        sb.append("\n").append(ec.getIsPluginExecutionCommandLogString());
        if (ec.request.metadata.isPlugin)
            sb.append("\n").append(ResultDestination.getLogString(ec.resultPendingIntentDestination, ec.resultDirectoryDestination, ignoreNull));
        return sb.toString();
    }

    public static String getExecutionOutputLogString(final ExecutionCommand ec, boolean ignoreNull, boolean logResultData, boolean logStdoutAndStderr) {
        if (ec == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(ec.getCommandIdAndLabelLogString()).append(":");
        sb.append("\n").append(ec.getPreviousStateLogString());
        sb.append("\n").append(ec.getCurrentStateLogString());
        if (logResultData)
            sb.append("\n").append(ResultData.getResultDataLogString(ec.resultData, logStdoutAndStderr));
        return sb.toString();
    }

    public static String getDetailedLogString(final ExecutionCommand ec) {
        if (ec == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(getExecutionInputLogString(ec, false, true));
        sb.append(getExecutionOutputLogString(ec, false, true, true));
        sb.append("\n").append(ec.getCommandDescriptionLogString());
        sb.append("\n").append(ec.getCommandHelpLogString());
        sb.append("\n").append(ec.getPluginAPIHelpLogString());
        return sb.toString();
    }

    public static String getExecutionCommandMarkdownString(final ExecutionCommand ec) {
        if (ec == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(ec.request.metadata.label).append("\n");
        if (ec.mPid != -1)
            sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Pid", ec.mPid, "-"));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Previous State", ec.lifecycle.getPreviousState().getName(), "-"));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Current State", ec.lifecycle.getCurrentState().getName(), "-"));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Executable", ec.request.executable, "-"));
        sb.append("\n").append(getArgumentsMarkdownString("Arguments", ec.request.arguments));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Working Directory", ec.request.workingDirectory, "-"));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Runner", ec.request.runner != null ? ec.request.runner.getName() : null, "-"));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("isFailsafe", ec.request.isFailsafe, "-"));
        if (ec.request.runner == Runner.APP_SHELL) {
            if (!DataUtils.isNullOrEmpty(ec.request.stdin))
                sb.append("\n").append(MarkdownUtils.getMultiLineMarkdownStringEntry("Stdin", ec.request.stdin, "-"));
            if (ec.request.backgroundCustomLogLevel != null)
                sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Background Custom Log Level", ec.request.backgroundCustomLogLevel, "-"));
        }
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Session Action", ec.request.sessionAction, "-"));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Shell Name", ec.request.shellName, "-"));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Shell Create Mode", ec.request.shellCreateMode, "-"));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("Set Shell Command Shell Environment", ec.request.setShellCommandShellEnvironment, "-"));
        sb.append("\n").append(MarkdownUtils.getSingleLineMarkdownStringEntry("isPluginExecutionCommand", ec.request.metadata.isPlugin, "-"));
        sb.append("\n\n").append(ResultDestination.getMarkdownString(ec.resultPendingIntentDestination, ec.resultDirectoryDestination));
        sb.append("\n\n").append(ResultData.getResultDataMarkdownString(ec.resultData));
        if (ec.request.metadata.description != null || ec.request.metadata.help != null) {
            if (ec.request.metadata.description != null)
                sb.append("\n\n### Command Description\n\n").append(ec.request.metadata.description).append("\n");
            if (ec.request.metadata.help != null)
                sb.append("\n\n### Command Help\n\n").append(ec.request.metadata.help).append("\n");
            sb.append("\n##\n");
        }
        if (ec.request.metadata.pluginAPIHelp != null) {
            sb.append("\n\n### Plugin API Help\n\n").append(ec.request.metadata.pluginAPIHelp);
            sb.append("\n##\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Per-field log string helpers (all read from request.*)
    // ------------------------------------------------------------------

    public String getIdLogString() {
        return request.id != null ? "(" + request.id + ") " : "";
    }

    public String getPidLogString() {
        return "Pid: `" + mPid + "`";
    }

    public String getCurrentStateLogString() {
        return "Current State: `" + lifecycle.getCurrentState().getName() + "`";
    }

    public String getPreviousStateLogString() {
        return "Previous State: `" + lifecycle.getPreviousState().getName() + "`";
    }

    public String getCommandLabelLogString() {
        return request.metadata.label;
    }

    public String getCommandIdAndLabelLogString() {
        return getIdLogString() + getCommandLabelLogString();
    }

    public String getExecutableLogString() {
        return "Executable: `" + request.executable + "`";
    }

    public String getArgumentsLogString() {
        return getArgumentsLogString("Arguments", request.arguments);
    }

    public String getWorkingDirectoryLogString() {
        return "Working Directory: `" + request.workingDirectory + "`";
    }

    public String getRunnerLogString() {
        return Logger.getSingleLineLogStringEntry("Runner", request.runner != null ? request.runner.getName() : null, "-");
    }

    public String getIsFailsafeLogString() {
        return "isFailsafe: `" + request.isFailsafe + "`";
    }

    public String getStdinLogString() {
        if (DataUtils.isNullOrEmpty(request.stdin))
            return "Stdin: -";
        else
            return Logger.getMultiLineLogStringEntry("Stdin", request.stdin, "-");
    }

    public String getBackgroundCustomLogLevelLogString() {
        return "Background Custom Log Level: `" + request.backgroundCustomLogLevel + "`";
    }

    public String getSessionActionLogString() {
        return Logger.getSingleLineLogStringEntry("Session Action", request.sessionAction, "-");
    }

    public String getShellNameLogString() {
        return Logger.getSingleLineLogStringEntry("Shell Name", request.shellName, "-");
    }

    public String getShellCreateModeLogString() {
        return Logger.getSingleLineLogStringEntry("Shell Create Mode", request.shellCreateMode, "-");
    }

    public String getSetRunnerShellEnvironmentLogString() {
        return "Set Shell Command Shell Environment: `" + request.setShellCommandShellEnvironment + "`";
    }

    public String getCommandDescriptionLogString() {
        return Logger.getSingleLineLogStringEntry("Command Description", request.metadata.description, "-");
    }

    public String getCommandHelpLogString() {
        return Logger.getSingleLineLogStringEntry("Command Help", request.metadata.help, "-");
    }

    public String getPluginAPIHelpLogString() {
        return Logger.getSingleLineLogStringEntry("Plugin API Help", request.metadata.pluginAPIHelp, "-");
    }

    public String getCommandIntentLogString() {
        if (request.metadata.commandIntent == null)
            return "Command Intent: -";
        else
            return Logger.getMultiLineLogStringEntry("Command Intent", IntentUtils.getIntentString(request.metadata.commandIntent), "-");
    }

    public String getIsPluginExecutionCommandLogString() {
        return "isPluginExecutionCommand: `" + request.metadata.isPlugin + "`";
    }

    // ------------------------------------------------------------------
    // Static argument formatting helpers (unchanged)
    // ------------------------------------------------------------------

    public static String getArgumentsLogString(String label, final String[] argumentsArray) {
        StringBuilder argumentsString = new StringBuilder(label + ":");
        if (argumentsArray != null && argumentsArray.length != 0) {
            argumentsString.append("\n```\n");
            for (int i = 0; i != argumentsArray.length; i++) {
                argumentsString.append(Logger.getSingleLineLogStringEntry("Arg " + (i + 1),
                    DataUtils.getTruncatedCommandOutput(argumentsArray[i], Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD / 5, true, false, true),
                    "-")).append("\n");
            }
            argumentsString.append("```");
        } else {
            argumentsString.append(" -");
        }
        return argumentsString.toString();
    }

    public static String getArgumentsMarkdownString(String label, final String[] argumentsArray) {
        StringBuilder argumentsString = new StringBuilder("**" + label + ":**");
        if (argumentsArray != null && argumentsArray.length != 0) {
            argumentsString.append("\n");
            for (int i = 0; i != argumentsArray.length; i++) {
                argumentsString.append(MarkdownUtils.getMultiLineMarkdownStringEntry("Arg " + (i + 1), argumentsArray[i], "-")).append("\n");
            }
        } else {
            argumentsString.append(" -  ");
        }
        return argumentsString.toString();
    }
}
