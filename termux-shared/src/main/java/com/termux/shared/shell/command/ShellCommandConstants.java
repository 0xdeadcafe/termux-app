package com.termux.shared.shell.command;

import com.termux.shared.errors.Errno;

import java.util.Formatter;
import java.util.IllegalFormatException;

public class ShellCommandConstants {

    /**
     * Class to send back results of commands to their callers like plugin or 3rd party apps.
     */
    public static final class RESULT_SENDER {

        /*
         * The default `Formatter` format strings to use for
         * {@link com.termux.shared.shell.command.result.ResultDestination.DirectoryResult#fileBasename}
         * if {@link com.termux.shared.shell.command.result.ResultDestination.DirectoryResult#singleFile}
         * is {@code true}.
         */

        /** The {@link Formatter} format string for success if only `stdout` needs to be written to
         * the result file basename where `stdout` maps to `%1$s`.
         * Used when `err` equals {@link Errno#ERRNO_SUCCESS} (-1), `stderr` is empty,
         * `exit_code` equals `0`, and no custom output format is supplied. */
        public static final String FORMAT_SUCCESS_STDOUT = "%1$s%n";
        /** The {@link Formatter} format string for success if `stdout` and `exit_code` need to be
         * written to the result file basename where `stdout` maps to `%1$s` and `exit_code` to `%2$s`.
         * Used when `err` equals {@link Errno#ERRNO_SUCCESS} (-1), `stderr` is empty,
         * `exit_code` does not equal `0`, and no custom output format is supplied.
         * The exit code will be placed in a markdown inline code. */
        public static final String FORMAT_SUCCESS_STDOUT__EXIT_CODE = "%1$s%n%n%n%nexit_code=%2$s%n";
        /** The {@link Formatter} format string for success if `stdout`, `stderr` and `exit_code` need
         * to be written to the result file basename where `stdout` maps to `%1$s`, `stderr`
         * maps to `%2$s` and `exit_code` to `%3$s`.
         * Used when `err` equals {@link Errno#ERRNO_SUCCESS} (-1), `stderr` is not empty,
         * and no custom output format is supplied.
         * The stdout and stderr will be placed in a markdown code block. The exit code will be placed
         * in a markdown inline code. The surrounding backticks will be 3 more than the consecutive
         * backticks in any parameter itself for code blocks. */
        public static final String FORMAT_SUCCESS_STDOUT__STDERR__EXIT_CODE = "stdout=%n%1$s%n%n%n%nstderr=%n%2$s%n%n%n%nexit_code=%3$s%n";
        /** The {@link Formatter} format string for failure if `err`, `errmsg`(`error`), `stdout`,
         * `stderr` and `exit_code` need to be written to the result file basename where
         * `err` maps to `%1$s`, `errmsg` maps to `%2$s`, `stdout` maps
         * to `%3$s`, `stderr` to `%4$s` and `exit_code` maps to `%5$s`.
         * Do not define an argument greater than `5`, like `%6$s` if you change this value since it will
         * raise {@link java.util.IllegalFormatException}.
         * Used when `err` does not equal {@link Errno#ERRNO_SUCCESS} (-1) and no custom error format
         * is supplied.
         * The errmsg, stdout and stderr will be placed in a markdown code block. The err and exit code
         * will be placed in a markdown inline code. The surrounding backticks will be 3 more than
         * the consecutive backticks in any parameter itself for code blocks. The stdout, stderr
         * and exit code may be empty without any surrounding backticks if not set. */
        public static final String FORMAT_FAILED_ERR__ERRMSG__STDOUT__STDERR__EXIT_CODE = "err=%1$s%n%n%n%nerrmsg=%n%2$s%n%n%n%nstdout=%n%3$s%n%n%n%nstderr=%n%4$s%n%n%n%nexit_code=%5$s%n";



        /*
         * The default prefixes to use for result files under
         * {@link com.termux.shared.shell.command.result.ResultDestination.DirectoryResult#directoryPath}
         * if {@link com.termux.shared.shell.command.result.ResultDestination.DirectoryResult#singleFile}
         * is {@code false}.
         */

        /** The prefix for the err result file. */
        public static final String RESULT_FILE_ERR_PREFIX = "err";
        /** The prefix for the errmsg result file. */
        public static final String RESULT_FILE_ERRMSG_PREFIX = "errmsg";
        /** The prefix for the stdout result file. */
        public static final String RESULT_FILE_STDOUT_PREFIX = "stdout";
        /** The prefix for the stderr result file. */
        public static final String RESULT_FILE_STDERR_PREFIX = "stderr";
        /** The prefix for the exitCode result file. */
        public static final String RESULT_FILE_EXIT_CODE_PREFIX = "exit_code";

    }

}
