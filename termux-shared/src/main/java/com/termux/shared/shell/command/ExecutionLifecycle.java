package com.termux.shared.shell.command;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;

/**
 * The lifecycle state machine for an {@link ExecutionCommand}.
 *
 * <p>Owns the three mutable FSM fields and all pure state-transition / query methods.
 * Compound operations that also touch {@link com.termux.shared.shell.command.result.ResultData}
 * (i.e. {@code setStateFailed} and {@code isStateFailed}) remain on {@link ExecutionCommand}.
 */
public class ExecutionLifecycle {

    private ExecutionCommand.ExecutionState currentState  = ExecutionCommand.ExecutionState.PRE_EXECUTION;
    private ExecutionCommand.ExecutionState previousState = ExecutionCommand.ExecutionState.PRE_EXECUTION;

    /** Set to {@code true} once result processing has been initiated; prevents duplicate dispatch. */
    public boolean processingResultsAlreadyCalled;

    private static final String LOG_TAG = "ExecutionLifecycle";

    // -----------------------------------------------------------------------
    // Accessors (used by logging helpers in ExecutionCommand)
    // -----------------------------------------------------------------------

    public synchronized ExecutionCommand.ExecutionState getCurrentState()  { return currentState;  }
    public synchronized ExecutionCommand.ExecutionState getPreviousState() { return previousState; }

    // -----------------------------------------------------------------------
    // State-transition
    // -----------------------------------------------------------------------

    /**
     * Attempt to advance to {@code newState}.
     *
     * @param newState         The target state.
     * @param commandIdAndLabel Human-readable command identifier used in error log messages.
     * @return {@code true} if the transition was accepted; {@code false} if it was invalid.
     */
    public synchronized boolean setState(
            @NonNull ExecutionCommand.ExecutionState newState,
            @NonNull String commandIdAndLabel) {
        // Transitions cannot go backwards, and SUCCESS is terminal.
        if (newState.getValue() < currentState.getValue() || currentState == ExecutionCommand.ExecutionState.SUCCESS) {
            Logger.logError(LOG_TAG, "Invalid " + commandIdAndLabel + " state transition from \""
                    + currentState.getName() + "\" to \"" + newState.getName() + "\"");
            return false;
        }
        // FAILED may be re-entered (to accumulate errors), but we preserve the last valid previousState.
        if (currentState != ExecutionCommand.ExecutionState.FAILED)
            previousState = currentState;
        currentState = newState;
        return true;
    }

    // -----------------------------------------------------------------------
    // Pure state queries
    // -----------------------------------------------------------------------

    public synchronized boolean hasExecuted() {
        return currentState.getValue() >= ExecutionCommand.ExecutionState.EXECUTED.getValue();
    }

    public synchronized boolean isExecuting() {
        return currentState == ExecutionCommand.ExecutionState.EXECUTING;
    }

    public synchronized boolean isSuccessful() {
        return currentState == ExecutionCommand.ExecutionState.SUCCESS;
    }

    /**
     * Returns {@code true} if result processing has already been initiated, otherwise marks it
     * as initiated and returns {@code false}. Used to guard against duplicate result dispatch.
     */
    public synchronized boolean shouldNotProcessResults() {
        if (processingResultsAlreadyCalled) return true;
        processingResultsAlreadyCalled = true;
        return false;
    }
}
