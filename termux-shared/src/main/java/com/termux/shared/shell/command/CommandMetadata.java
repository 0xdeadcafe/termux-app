package com.termux.shared.shell.command;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Diagnostic and display metadata for an {@link ExecutionCommand}.
 *
 * <p>These fields describe the command's origin and provide human-readable context for logs,
 * error reports, and notifications. They have no effect on how the command executes.
 *
 * <p>An instance is always present on an {@link ExecutionCommand} — the default label is
 * {@code "Execution Command"} and all other fields default to {@code null} / {@code false}.
 */
public final class CommandMetadata {

    /** Display label for this command. Never {@code null}. */
    @NonNull  public final String label;
    /** Markdown description of the command, or {@code null} if not provided. */
    @Nullable public final String description;
    /** Markdown help text for the command, or {@code null} if not provided. */
    @Nullable public final String help;
    /** Help text for the plugin API that launched this command, or {@code null} if not provided. */
    @Nullable public final String pluginAPIHelp;
    /** The originating {@link Intent}, retained for diagnostic logging. Usually {@code null}. */
    @Nullable public final Intent commandIntent;
    /** {@code true} if this command was launched by an external plugin. */
    public final boolean isPlugin;

    public CommandMetadata(
            @NonNull  String label,
            @Nullable String description,
            @Nullable String help,
            @Nullable String pluginAPIHelp,
            @Nullable Intent commandIntent,
            boolean isPlugin) {
        this.label = label;
        this.description = description;
        this.help = help;
        this.pluginAPIHelp = pluginAPIHelp;
        this.commandIntent = commandIntent;
        this.isPlugin = isPlugin;
    }

    /** Return a copy of this metadata with a different label. */
    @NonNull
    public CommandMetadata withLabel(@NonNull String newLabel) {
        return new CommandMetadata(newLabel, description, help, pluginAPIHelp, commandIntent, isPlugin);
    }
}
