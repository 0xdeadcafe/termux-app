package com.termux.shared.shell.command;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.shell.command.result.ResultIntake;

/**
 * Immutable snapshot of all execution inputs for a shell command.
 *
 * <p>Constructed once (via {@link Builder}) from an Intent or a direct call site and then
 * attached to an {@link ExecutionCommand}. All fields are {@code final}; no setters exist.
 * Use {@link #toBuilder()} to derive a modified copy.
 */
public final class ExecutionRequest {

    /** Optional unique id; {@code null} if not managed by a shell manager. */
    @Nullable public final Integer id;

    /** The {@link ExecutionCommand.Runner} for this command. May be {@code null} until validated. */
    @Nullable public final ExecutionCommand.Runner runner;

    /** Executable path, or {@code null} for the default login shell. */
    @Nullable public final String executable;

    /** Executable {@link Uri}, or {@code null} if not applicable. */
    @Nullable public final Uri executableUri;

    /** Argument array, or {@code null} / empty for no arguments. */
    @Nullable public final String[] arguments;

    /** Stdin string for {@link ExecutionCommand.Runner#APP_SHELL} commands; {@code null} otherwise. */
    @Nullable public final String stdin;

    /** Working directory, or {@code null} to use the runner default. */
    @Nullable public final String workingDirectory;

    /** Terminal transcript rows for {@link ExecutionCommand.Runner#TERMINAL_SESSION}; {@code null} for default. */
    @Nullable public final Integer terminalTranscriptRows;

    /** Custom log level for {@link ExecutionCommand.Runner#APP_SHELL} background commands; {@code null} for default. */
    @Nullable public final Integer backgroundCustomLogLevel;

    /** Session action string for {@link ExecutionCommand.Runner#TERMINAL_SESSION}; {@code null} if unset. */
    @Nullable public final String sessionAction;

    /** Shell name used to identify the session/task; {@code null} to derive from executable. */
    @Nullable public final String shellName;

    /** {@link ExecutionCommand.ShellCreateMode} string; {@code null} defaults to ALWAYS. */
    @Nullable public final String shellCreateMode;

    /** {@code true} if this command should start a failsafe terminal session. */
    public final boolean isFailsafe;

    /** {@code true} if the shell command environment variables should be set for this command. */
    public final boolean setShellCommandShellEnvironment;

    /** Display and diagnostic metadata. Never {@code null}. */
    @NonNull public final CommandMetadata metadata;

    /**
     * Raw result-delivery parameters from a plugin Intent, or {@code null} if no result is
     * expected back from the caller.
     */
    @Nullable public final ResultIntake resultIntake;

    private ExecutionRequest(Builder b) {
        this.id                            = b.id;
        this.runner                        = b.runner;
        this.executable                    = b.executable;
        this.executableUri                 = b.executableUri;
        this.arguments                     = b.arguments;
        this.stdin                         = b.stdin;
        this.workingDirectory              = b.workingDirectory;
        this.terminalTranscriptRows        = b.terminalTranscriptRows;
        this.backgroundCustomLogLevel      = b.backgroundCustomLogLevel;
        this.sessionAction                 = b.sessionAction;
        this.shellName                     = b.shellName;
        this.shellCreateMode               = b.shellCreateMode;
        this.isFailsafe                    = b.isFailsafe;
        this.setShellCommandShellEnvironment = b.setShellCommandShellEnvironment;
        this.metadata                      = b.metadata != null ? b.metadata
                : new CommandMetadata("Execution Command", null, null, null, null, false);
        this.resultIntake                  = b.resultIntake;
    }

    /** Return a {@link Builder} pre-populated with this request's values. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static final class Builder {

        private Integer id;
        private ExecutionCommand.Runner runner;
        private String executable;
        private Uri executableUri;
        private String[] arguments;
        private String stdin;
        private String workingDirectory;
        private Integer terminalTranscriptRows;
        private Integer backgroundCustomLogLevel;
        private String sessionAction;
        private String shellName;
        private String shellCreateMode;
        private boolean isFailsafe;
        private boolean setShellCommandShellEnvironment;
        private CommandMetadata metadata;
        private ResultIntake resultIntake;

        public Builder() {}

        /** Copy constructor — pre-populates all fields from {@code src}. */
        public Builder(@NonNull ExecutionRequest src) {
            this.id                              = src.id;
            this.runner                          = src.runner;
            this.executable                      = src.executable;
            this.executableUri                   = src.executableUri;
            this.arguments                       = src.arguments;
            this.stdin                           = src.stdin;
            this.workingDirectory                = src.workingDirectory;
            this.terminalTranscriptRows          = src.terminalTranscriptRows;
            this.backgroundCustomLogLevel        = src.backgroundCustomLogLevel;
            this.sessionAction                   = src.sessionAction;
            this.shellName                       = src.shellName;
            this.shellCreateMode                 = src.shellCreateMode;
            this.isFailsafe                      = src.isFailsafe;
            this.setShellCommandShellEnvironment = src.setShellCommandShellEnvironment;
            this.metadata                        = src.metadata;
            this.resultIntake                    = src.resultIntake;
        }

        public Builder id(Integer v)                            { id = v; return this; }
        public Builder runner(ExecutionCommand.Runner v)        { runner = v; return this; }
        public Builder executable(String v)                     { executable = v; return this; }
        public Builder executableUri(Uri v)                     { executableUri = v; return this; }
        public Builder arguments(String[] v)                    { arguments = v; return this; }
        public Builder stdin(String v)                          { stdin = v; return this; }
        public Builder workingDirectory(String v)               { workingDirectory = v; return this; }
        public Builder terminalTranscriptRows(Integer v)        { terminalTranscriptRows = v; return this; }
        public Builder backgroundCustomLogLevel(Integer v)      { backgroundCustomLogLevel = v; return this; }
        public Builder sessionAction(String v)                  { sessionAction = v; return this; }
        public Builder shellName(String v)                      { shellName = v; return this; }
        public Builder shellCreateMode(String v)                { shellCreateMode = v; return this; }
        public Builder isFailsafe(boolean v)                    { isFailsafe = v; return this; }
        public Builder setShellCommandShellEnvironment(boolean v) { setShellCommandShellEnvironment = v; return this; }
        public Builder metadata(CommandMetadata v)              { metadata = v; return this; }
        public Builder resultIntake(ResultIntake v)             { resultIntake = v; return this; }

        public ExecutionRequest build() {
            return new ExecutionRequest(this);
        }
    }
}
