package com.termux.shared.file.filesystem;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.logger.Logger;

public class FileTypes {

    /** Flags to represent regular, directory and symlink file types defined by {@link FileType}. */
    public static final int FILE_TYPE_NORMAL_FLAGS = FileType.REGULAR.getValue() | FileType.DIRECTORY.getValue() | FileType.SYMLINK.getValue();

    /** Flags to represent any file type defined by {@link FileType}. */
    public static final int FILE_TYPE_ANY_FLAGS = Integer.MAX_VALUE;

    /**
     * Test-only seam that works around a Robolectric limitation where {@code Os.lstat()}/
     * {@code Os.stat()} never throw {@code ENOENT} for genuinely missing paths (beads-94h).
     * Must be {@code null} in production. Install via {@link NativeDispatcherEnoentFixRule}.
     */
    @Nullable
    @VisibleForTesting
    public static TestOnlyFileExistenceChecker TEST_ONLY_FILE_EXISTENCE_CHECKER = null;

    /** Checker interface for the test-only ENOENT seam. */
    public interface TestOnlyFileExistenceChecker {
        boolean exists(String filePath);
    }

    public static String convertFileTypeFlagsToNamesString(int fileTypeFlags) {
        StringBuilder sb = new StringBuilder();
        for (FileType fileType : new FileType[]{FileType.REGULAR, FileType.DIRECTORY, FileType.SYMLINK, FileType.CHARACTER, FileType.FIFO, FileType.BLOCK, FileType.UNKNOWN}) {
            if ((fileTypeFlags & fileType.getValue()) > 0)
                sb.append(fileType.getName()).append(",");
        }
        String result = sb.toString();
        return result.endsWith(",") ? result.substring(0, result.length() - 1) : result;
    }

    /**
     * Returns the {@link FileType} of the file at {@code filePath}.
     *
     * <p>Uses {@link Os#lstat(String)} when {@code followLinks} is {@code false} so symlinks are
     * reported as {@link FileType#SYMLINK}; uses {@link Os#stat(String)} when {@code true} to
     * follow symlinks and return the target's type.
     *
     * <p>Returns {@link FileType#NO_EXIST} for null/empty paths, non-existent files, or on error.
     *
     * @param filePath    The path of the file to check.
     * @param followLinks Whether to follow symlinks.
     * @return The {@link FileType} of the file.
     */
    @NonNull
    public static FileType getFileType(final String filePath, final boolean followLinks) {
        if (filePath == null || filePath.isEmpty()) return FileType.NO_EXIST;

        TestOnlyFileExistenceChecker checker = TEST_ONLY_FILE_EXISTENCE_CHECKER;
        if (checker != null && !checker.exists(filePath)) {
            return FileType.NO_EXIST;
        }

        try {
            int mode = followLinks ? Os.stat(filePath).st_mode : Os.lstat(filePath).st_mode;
            return modeToFileType(mode);
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.ENOENT)
                Logger.logError("Failed to get file type for file at path \"" + filePath + "\": " + e.getMessage());
            return FileType.NO_EXIST;
        }
    }

    private static FileType modeToFileType(int mode) {
        int type = mode & OsConstants.S_IFMT;
        if (type == OsConstants.S_IFREG)  return FileType.REGULAR;
        if (type == OsConstants.S_IFDIR)  return FileType.DIRECTORY;
        if (type == OsConstants.S_IFLNK)  return FileType.SYMLINK;
        if (type == OsConstants.S_IFSOCK) return FileType.SOCKET;
        if (type == OsConstants.S_IFCHR)  return FileType.CHARACTER;
        if (type == OsConstants.S_IFIFO)  return FileType.FIFO;
        if (type == OsConstants.S_IFBLK)  return FileType.BLOCK;
        return FileType.UNKNOWN;
    }

}
