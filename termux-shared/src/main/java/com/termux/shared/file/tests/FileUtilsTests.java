package com.termux.shared.file.tests;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.errors.Errno;
import com.termux.shared.file.FileUtils;
import com.termux.shared.file.FileUtilsErrno;
import com.termux.shared.logger.Logger;
import com.termux.shared.errors.Error;
import com.termux.shared.errors.TermuxException;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

public class FileUtilsTests {

    private static final String LOG_TAG = "FileUtilsTests";

    /**
     * Run basic tests for {@link FileUtils} class.
     *
     * Move tests need to be written, specially for failures.
     *
     * The log level must be set to verbose.
     *
     * Run at app startup like in an activity
     * FileUtilsTests.runTests(this, TermuxConstants.TERMUX_HOME_DIR_PATH + "/FileUtilsTests");
     *
     * @param context The {@link Context} for operations.
     */
    public static void runTests(@NonNull final Context context, @NonNull final String testRootDirectoryPath) {
        try {
            Logger.logInfo(LOG_TAG, "Running tests");
            Logger.logInfo(LOG_TAG, "testRootDirectoryPath: \"" + testRootDirectoryPath + "\"");

            String fileUtilsTestsDirectoryCanonicalPath = FileUtils.getCanonicalPath(testRootDirectoryPath, null);
            assertEqual("FileUtilsTests directory path is not a canonical path", testRootDirectoryPath, fileUtilsTestsDirectoryCanonicalPath);

            runTestsInner(testRootDirectoryPath);
            Logger.logInfo(LOG_TAG, "All tests successful");
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, e.getMessage());
            Logger.showToast(context, e.getMessage() != null ? e.getMessage().replaceAll("(?s)\nFull Error:\n.*", "") : null, true);
        }
    }

    private static void runTestsInner(@NonNull final String testRootDirectoryPath) throws Exception {
        String label;
        String path;

        /*
         * - dir1
         *  - sub_dir1
         *  - sub_reg1
         *  - sub_sym1 (absolute symlink to dir2)
         *  - sub_sym2 (copy of sub_sym1 for symlink to dir2)
         *  - sub_sym3 (relative symlink to dir4)
         * - dir2
         *  - sub_reg1
         *  - sub_reg2 (copy of dir2/sub_reg1)
         * - dir3 (copy of dir1)
         * - dir4 (moved from dir3)
         */

        String dir1_label = "dir1";
        String dir1_path = testRootDirectoryPath + "/dir1";

        String dir1__sub_dir1_label = "dir1/sub_dir1";
        String dir1__sub_dir1_path = dir1_path + "/sub_dir1";

        String dir1__sub_dir2_label = "dir1/sub_dir2";
        String dir1__sub_dir2_path = dir1_path + "/sub_dir2";

        String dir1__sub_dir3_label = "dir1/sub_dir3";
        String dir1__sub_dir3_path = dir1_path + "/sub_dir3";

        String dir1__sub_dir3__sub_reg1_label = "dir1/sub_dir3/sub_reg1";
        String dir1__sub_dir3__sub_reg1_path = dir1__sub_dir3_path + "/sub_reg1";

        String dir1__sub_reg1_label = "dir1/sub_reg1";
        String dir1__sub_reg1_path = dir1_path + "/sub_reg1";

        String dir1__sub_sym1_label = "dir1/sub_sym1";
        String dir1__sub_sym1_path = dir1_path + "/sub_sym1";

        String dir1__sub_sym2_label = "dir1/sub_sym2";
        String dir1__sub_sym2_path = dir1_path + "/sub_sym2";

        String dir1__sub_sym3_label = "dir1/sub_sym3";
        String dir1__sub_sym3_path = dir1_path + "/sub_sym3";


        String dir2_label = "dir2";
        String dir2_path = testRootDirectoryPath + "/dir2";

        String dir2__sub_reg1_label = "dir2/sub_reg1";
        String dir2__sub_reg1_path = dir2_path + "/sub_reg1";

        String dir2__sub_reg2_label = "dir2/sub_reg2";
        String dir2__sub_reg2_path = dir2_path + "/sub_reg2";


        String dir3_label = "dir3";
        String dir3_path = testRootDirectoryPath + "/dir3";

        String dir4_label = "dir4";
        String dir4_path = testRootDirectoryPath + "/dir4";





        // Create or clear test root directory file
        label = "testRootDirectoryPath";
        FileUtils.clearDirectoryOrThrow(label, testRootDirectoryPath);

        if (!FileUtils.directoryFileExists(testRootDirectoryPath, false))
            throwException("The " + label + " directory file does not exist as expected after creation");


        // Create dir1 directory file
        FileUtils.createDirectoryFileOrThrow(dir1_label, dir1_path);

        // Create dir2 directory file
        FileUtils.createDirectoryFileOrThrow(dir2_label, dir2_path);





        // Create dir1/sub_dir1 directory file
        label = dir1__sub_dir1_label; path = dir1__sub_dir1_path;
        FileUtils.createDirectoryFileOrThrow(label, path);
        if (!FileUtils.directoryFileExists(path, false))
            throwException("The " + label + " directory file does not exist as expected after creation");

        // Create dir1/sub_reg1 regular file
        label = dir1__sub_reg1_label; path = dir1__sub_reg1_path;
        FileUtils.createRegularFileOrThrow(label, path);
        if (!FileUtils.regularFileExists(path, false))
            throwException("The " + label + " regular file does not exist as expected after creation");

        // Create dir1/sub_sym1 -> dir2 absolute symlink file
        label = dir1__sub_sym1_label; path = dir1__sub_sym1_path;
        FileUtils.createSymlinkFileOrThrow(label, dir2_path, path);
        if (!FileUtils.symlinkFileExists(path))
            throwException("The " + label + " symlink file does not exist as expected after creation");

        // Copy dir1/sub_sym1 symlink file to dir1/sub_sym2
        label = dir1__sub_sym2_label; path = dir1__sub_sym2_path;
        FileUtils.copySymlinkFileOrThrow(label, dir1__sub_sym1_path, path, false);;
        if (!FileUtils.symlinkFileExists(path))
            throwException("The " + label + " symlink file does not exist as expected after copying it from " + dir1__sub_sym1_label);
        if (!new File(path).getCanonicalPath().equals(dir2_path))
            throwException("The " + label + " symlink file does not point to " + dir2_label);





        // Write "line1" to dir2/sub_reg1 regular file
        label = dir2__sub_reg1_label; path = dir2__sub_reg1_path;
        FileUtils.writeTextToFileOrThrow(label, path, Charset.defaultCharset(), "line1", false);
        if (!FileUtils.regularFileExists(path, false))
            throwException("The " + label + " file does not exist as expected after writing to it with append mode false");

        // Write "line2" to dir2/sub_reg1 regular file
        FileUtils.writeTextToFileOrThrow(label, path, Charset.defaultCharset(), "\nline2", true);

        // Read dir2/sub_reg1 regular file
        String fileContent = FileUtils.readTextFromFileOrThrow(label, path, Charset.defaultCharset(), false);
        assertEqual("The data read from " + label + " file in not as expected", "line1\nline2", fileContent);

        // Copy dir2/sub_reg1 regular file to dir2/sub_reg2 file
        label = dir2__sub_reg2_label; path = dir2__sub_reg2_path;
        FileUtils.copyRegularFileOrThrow(label, dir2__sub_reg1_path, path, false);
        if (!FileUtils.regularFileExists(path, false))
            throwException("The " + label + " regular file does not exist as expected after copying it from " + dir2__sub_reg1_label);





        // Copy dir1 directory file to dir3
        label = dir3_label; path = dir3_path;
        FileUtils.copyDirectoryFileOrThrow(label, dir2_path, path, false);
        if (!FileUtils.directoryFileExists(path, false))
            throwException("The " + label + " directory file does not exist as expected after copying it from " + dir2_label);

        // Copy dir1 directory file to dir3 again to test overwrite
        label = dir3_label; path = dir3_path;
        FileUtils.copyDirectoryFileOrThrow(label, dir2_path, path, false);
        if (!FileUtils.directoryFileExists(path, false))
            throwException("The " + label + " directory file does not exist as expected after copying it from " + dir2_label);

        // Move dir3 directory file to dir4
        label = dir4_label; path = dir4_path;
        FileUtils.moveDirectoryFileOrThrow(label, dir3_path, path, false);
        if (!FileUtils.directoryFileExists(path, false))
            throwException("The " + label + " directory file does not exist as expected after copying it from " + dir3_label);





        // Create dir1/sub_sym3 -> dir4 relative symlink file
        label = dir1__sub_sym3_label; path = dir1__sub_sym3_path;
        FileUtils.createSymlinkFileOrThrow(label, "../dir4", path);
        if (!FileUtils.symlinkFileExists(path))
            throwException("The " + label + " symlink file does not exist as expected after creation");

        // Create dir1/sub_sym3 -> dirX relative dangling symlink file
        // This is to ensure that symlinkFileExists returns true if a symlink file exists but is dangling
        label = dir1__sub_sym3_label; path = dir1__sub_sym3_path;
        FileUtils.createSymlinkFileOrThrow(label, "../dirX", path);
        if (!FileUtils.symlinkFileExists(path))
            throwException("The " + label + " dangling symlink file does not exist as expected after creation");





        // Delete dir1/sub_sym2 symlink file
        label = dir1__sub_sym2_label; path = dir1__sub_sym2_path;
        FileUtils.deleteSymlinkFileOrThrow(label, path, false);
        if (FileUtils.fileExists(path, false))
            throwException("The " + label + " symlink file still exist after deletion");

        // Check if dir2 directory file still exists after deletion of dir1/sub_sym2 since it was a symlink to dir2
        // When deleting a symlink file, its target must not be deleted
        label = dir2_label; path = dir2_path;
        if (!FileUtils.directoryFileExists(path, false))
            throwException("The " + label + " directory file has unexpectedly been deleted after deletion of " + dir1__sub_sym2_label);





        // Delete dir1 directory file
        label = dir1_label; path = dir1_path;
        FileUtils.deleteDirectoryFileOrThrow(label, path, false);
        if (FileUtils.fileExists(path, false))
            throwException("The " + label + " directory file still exist after deletion");


        // Check if dir2 directory file and dir2/sub_reg1 regular file still exist after deletion of
        // dir1 since there was a dir1/sub_sym1 symlink to dir2 in it
        // When deleting a directory, any targets of symlinks must not be deleted when deleting symlink files
        label = dir2_label; path = dir2_path;
        if (!FileUtils.directoryFileExists(path, false))
            throwException("The " + label + " directory file has unexpectedly been deleted after deletion of " + dir1_label);
        label = dir2__sub_reg1_label; path = dir2__sub_reg1_path;
        if (!FileUtils.fileExists(path, false))
            throwException("The " + label + " regular file has unexpectedly been deleted after deletion of " + dir1_label);





        // Delete dir2/sub_reg1 regular file
        label = dir2__sub_reg1_label; path = dir2__sub_reg1_path;
        FileUtils.deleteRegularFileOrThrow(label, path, false);
        if (FileUtils.fileExists(path, false))
            throwException("The " + label + " regular file still exist after deletion");


        List<String> ignoredSubFilePaths = Arrays.asList(dir1__sub_dir2_path, dir1__sub_dir3__sub_reg1_path);

        // Create dir1 directory file
        FileUtils.createDirectoryFileOrThrow(dir1_label, dir1_path);

        // Test empty dir
        FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFilesOrThrow(dir1_label, dir1_path, ignoredSubFilePaths, false);


        // Create dir1/sub_dir3 directory file
        label = dir1__sub_dir3_label; path = dir1__sub_dir3_path;
        FileUtils.createDirectoryFileOrThrow(label, path);
        if (!FileUtils.directoryFileExists(path, false))
            throwException("The " + label + " directory file does not exist as expected after creation");

        // Test parent dir existing of non existing ignored regular file (should throw ERRNO_NON_EMPTY_DIRECTORY_FILE)
        try {
            FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFilesOrThrow(dir1_label, dir1_path, ignoredSubFilePaths, false);
            throwException("Expected ERRNO_NON_EMPTY_DIRECTORY_FILE but validation succeeded");
        } catch (TermuxException e) {
            assertErrnoEqual("Failed to validate if " + dir1_label + " directory file is empty with parent dir existing of non existing ignored regular file", FileUtilsErrno.ERRNO_NON_EMPTY_DIRECTORY_FILE, e.getError());
        }


        // Write "line1" to dir1/sub_dir3/sub_reg1 regular file
        label = dir1__sub_dir3__sub_reg1_label; path = dir1__sub_dir3__sub_reg1_path;
        FileUtils.writeTextToFileOrThrow(label, path, Charset.defaultCharset(), "line1", false);
        if (!FileUtils.regularFileExists(path, false))
            throwException("The " + label + " file does not exist as expected after writing to it with append mode false");

        // Test ignored regular file existing
        FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFilesOrThrow(dir1_label, dir1_path, ignoredSubFilePaths, false);


        // Create dir1/sub_dir2 directory file
        label = dir1__sub_dir2_label; path = dir1__sub_dir2_path;
        FileUtils.createDirectoryFileOrThrow(label, path);
        if (!FileUtils.directoryFileExists(path, false))
            throwException("The " + label + " directory file does not exist as expected after creation");

        // Test ignored dir file existing
        FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFilesOrThrow(dir1_label, dir1_path, ignoredSubFilePaths, false);


        // Create dir1/sub_dir1 directory file
        label = dir1__sub_dir1_label; path = dir1__sub_dir1_path;
        FileUtils.createDirectoryFileOrThrow(label, path);
        if (!FileUtils.directoryFileExists(path, false))
            throwException("The " + label + " directory file does not exist as expected after creation");

        // Test non ignored dir file existing (should throw ERRNO_NON_EMPTY_DIRECTORY_FILE)
        try {
            FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFilesOrThrow(dir1_label, dir1_path, ignoredSubFilePaths, false);
            throwException("Expected ERRNO_NON_EMPTY_DIRECTORY_FILE but validation succeeded");
        } catch (TermuxException e) {
            assertErrnoEqual("Failed to validate if " + dir1_label + " directory file is empty with non ignored dir file existing", FileUtilsErrno.ERRNO_NON_EMPTY_DIRECTORY_FILE, e.getError());
        }


        // Delete dir1 directory file
        label = dir1_label; path = dir1_path;
        FileUtils.deleteDirectoryFileOrThrow(label, path, false);


        FileUtils.getFileType("/dev/ptmx", false);
        FileUtils.getFileType("/dev/null", false);
    }



    public static void assertEqual(@NonNull final String message, final String expected, final Error actual) throws Exception {
        String actualString = actual != null ? actual.getMessage() : null;
        if (!equalsRegardingNull(expected, actualString))
            throwException(message + "\nexpected: \"" + expected + "\"\nactual: \"" + actualString + "\"\nFull Error:\n" + (actual != null ? actual.toString() : ""));
    }

    public static void assertEqual(@NonNull final String message, final String expected, final String actual) throws Exception {
        if (!equalsRegardingNull(expected, actual))
            throwException(message + "\nexpected: \"" + expected + "\"\nactual: \"" + actual + "\"");
    }

    private static boolean equalsRegardingNull(final String expected, final String actual) {
        if (expected == null) {
            return actual == null;
        }

        return isEquals(expected, actual);
    }

    public static void assertErrnoEqual(@NonNull final String message, final Errno expected, final Error actual) throws Exception {
        if ((expected == null && actual != null) || (expected != null && !expected.equalsErrorTypeAndCode(actual)))
            throwException(message + "\nexpected: \"" + expected + "\"\nactual: \"" + actual + "\"\nFull Error:\n" + (actual != null ? actual.toString() : ""));
    }



    private static boolean isEquals(String expected, String actual) {
        return expected.equals(actual);
    }

    public static void throwException(@NonNull final String message) throws Exception {
            throw new Exception(message);
    }

}
