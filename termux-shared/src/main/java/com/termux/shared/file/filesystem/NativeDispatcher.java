package com.termux.shared.file.filesystem;

import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

public class NativeDispatcher {

    /**
     * Test-only seam for {@link #validateFileExistence(String)}, used to work around a
     * Robolectric test-runner limitation (see {@code beads-94h}) where {@link Os#lstat(String)}/
     * {@link Os#stat(String)} never throw {@code ENOENT} for a genuinely non-existent path under
     * that runner, which otherwise makes it impossible to reliably unit test the "file does not
     * exist yet" branch of any {@link FileTypes#getFileType(String, boolean)} caller.
     *
     * <p>Must remain {@code null} in production -- this has zero effect on real Android, where
     * the real syscalls behind {@link Os#stat}/{@link Os#lstat} already correctly report ENOENT
     * on their own. When a test installs a non-null checker (typically via a {@code @Rule} or
     * {@code @Before}/{@code @After} pair to guarantee it's reset), it takes priority and can
     * short-circuit with a clear "no such file" {@link IOException} before ever reaching the
     * broken Robolectric shadow.
     *
     * <p>Deliberately not typed with {@code java.util.function.Predicate} (API 24+) or
     * {@code java.nio.file.*} (API 26+ without desugaring) since this class is loaded on
     * {@code minSdkVersion=21}; this tiny interface avoids any class-availability risk on old
     * Android versions even though it is only ever assigned from test code.
     */
    public interface TestOnlyFileExistenceChecker {
        boolean exists(String filePath);
    }

    @Nullable
    @VisibleForTesting
    public static TestOnlyFileExistenceChecker TEST_ONLY_FILE_EXISTENCE_CHECKER = null;

    public static void stat(String filePath, FileAttributes fileAttributes) throws IOException {
        validateFileExistence(filePath);

        try {
            fileAttributes.loadFromStructStat(Os.stat(filePath));
        } catch (ErrnoException e) {
            throw new IOException("Failed to run Os.stat() on file at path \"" + filePath + "\": " + e.getMessage());
        }
    }

    public static void lstat(String filePath, FileAttributes fileAttributes) throws IOException {
        validateFileExistence(filePath);

        try {
            fileAttributes.loadFromStructStat(Os.lstat(filePath));
        } catch (ErrnoException e) {
            throw new IOException("Failed to run Os.lstat() on file at path \"" + filePath + "\": " + e.getMessage());
        }
    }

    public static void fstat(FileDescriptor fileDescriptor, FileAttributes fileAttributes) throws IOException {
        validateFileDescriptor(fileDescriptor);

        try {
            fileAttributes.loadFromStructStat(Os.fstat(fileDescriptor));
        } catch (ErrnoException e) {
            throw new IOException("Failed to run Os.fstat() on file descriptor \"" + fileDescriptor.toString() + "\": " + e.getMessage());
        }
    }

    public static void validateFileExistence(String filePath) throws IOException {
        if (filePath == null || filePath.isEmpty()) throw new IOException("The path is null or empty");

        TestOnlyFileExistenceChecker checker = TEST_ONLY_FILE_EXISTENCE_CHECKER;
        if (checker != null && !checker.exists(filePath)) {
            // The "(ENOENT)" substring is intentional: FileTypes#getFileType() only suppresses its
            // error log for exceptions whose message contains it, matching what the real
            // ErrnoException message would contain on real Android for a missing path.
            throw new IOException("No such file or directory (ENOENT): \"" + filePath + "\"");
        }

        File file = new File(filePath);

        //if (!file.exists())
        //    throw new IOException("No such file or directory: \"" + filePath + "\"");
    }

    public static void validateFileDescriptor(FileDescriptor fileDescriptor) throws IOException {
        if (fileDescriptor == null) throw new IOException("The file descriptor is null");

        if (!fileDescriptor.valid())
            throw new IOException("No such file descriptor: \"" + fileDescriptor.toString() + "\"");
    }

}
