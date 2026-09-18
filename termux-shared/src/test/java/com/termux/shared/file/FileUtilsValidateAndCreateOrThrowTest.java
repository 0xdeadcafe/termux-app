package com.termux.shared.file;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.termux.shared.errors.TermuxException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Collections;

/**
 * Unit tests for the validate*OrThrow and create*OrThrow sibling methods added for beads-km2.
 *
 * <p>As documented in FileUtilsReadTextFromFileOrThrowTest and filed as beads-94h, Robolectric's
 * {@code Os.lstat} never throws {@code ENOENT} for a genuinely non-existent path (it returns a
 * zeroed struct that {@code FileTypes.getFileType()} misclassifies as {@code FileType.SOCKET}).
 * This means the "path does not exist yet, go create it" branch of the create-style and
 * validate-style methods cannot be reliably unit-tested here. These tests instead exercise the branches that operate on
 * already-existing paths (both the correct-type no-op path and the wrong-type error path), which
 * stat correctly under Robolectric, to still get real coverage of the Error-to-TermuxException
 * conversion this migration is about.
 */
@RunWith(RobolectricTestRunner.class)
public class FileUtilsValidateAndCreateOrThrowTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void validateDirectoryFileEmptyOrThrow_doesNotThrowForEmptyDirectory() throws TermuxException {
        FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFilesOrThrow(
            "test", tempFolder.getRoot().getAbsolutePath(), Collections.emptyList(), false);
    }

    @Test
    public void validateDirectoryFileEmptyOrThrow_throwsForNonEmptyDirectoryWithNoIgnoredFiles() throws Exception {
        File child = tempFolder.newFile("child.txt");

        assertThrows(TermuxException.class, () ->
            FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFilesOrThrow(
                "test", tempFolder.getRoot().getAbsolutePath(), Collections.emptyList(), false));

        assertTrue(child.exists());
    }

    @Test
    public void validateDirectoryFileEmptyOrThrow_doesNotThrowWhenOnlyIgnoredFilePresent() throws Exception {
        File child = tempFolder.newFile("ignored.txt");

        FileUtils.validateDirectoryFileEmptyOrOnlyContainsSpecificFilesOrThrow(
            "test", tempFolder.getRoot().getAbsolutePath(), Collections.singletonList(child.getAbsolutePath()), false);
    }

    @Test
    public void createRegularFileOrThrow_doesNotThrowIfAlreadyExistsAsRegularFile() throws Exception {
        File file = tempFolder.newFile("already-exists.txt");

        FileUtils.createRegularFileOrThrow("test", file.getAbsolutePath(), null, false, false);

        assertTrue(file.exists());
    }

    @Test
    public void createRegularFileOrThrow_throwsWhenPathIsAnExistingDirectory() throws Exception {
        File dir = tempFolder.newFolder("existing-dir");

        assertThrows(TermuxException.class, () ->
            FileUtils.createRegularFileOrThrow("test", dir.getAbsolutePath(), null, false, false));
    }

    @Test
    public void createDirectoryFileOrThrow_doesNotThrowIfAlreadyExistsAsDirectory() throws Exception {
        File dir = tempFolder.newFolder("already-a-dir");

        FileUtils.createDirectoryFileOrThrow("test", dir.getAbsolutePath(), null, false, false);

        assertTrue(dir.isDirectory());
    }

    @Test
    public void createDirectoryFileOrThrow_throwsWhenPathIsAnExistingRegularFile() throws Exception {
        File file = tempFolder.newFile("already-a-file.txt");

        assertThrows(TermuxException.class, () ->
            FileUtils.createDirectoryFileOrThrow("test", file.getAbsolutePath(), null, false, false));
    }

    @Test
    public void createSymlinkFileOrThrow_throwsForNullTargetPath() {
        File link = new File(tempFolder.getRoot(), "link.txt");
        assertThrows(TermuxException.class, () ->
            FileUtils.createSymlinkFileOrThrow("test", null, link.getAbsolutePath(), true, true, true));
    }

    @Test
    public void createSymlinkFileOrThrow_doesNotThrowWhenDestAlreadyExistsAndOverwriteDisabled() throws Exception {
        File target = tempFolder.newFile("target.txt");
        File dest = tempFolder.newFile("dest-already-exists.txt");

        // overwrite=false, so this should be a silent no-op success, not a symlink creation.
        FileUtils.createSymlinkFileOrThrow("test", target.getAbsolutePath(), dest.getAbsolutePath(),
            true, false, true);

        assertTrue(dest.isFile());
        assertTrue(!FileUtils.symlinkFileExists(dest.getAbsolutePath()));
    }

    @Test
    public void validateRegularFileExistenceAndPermissionsOrThrow_throwsForNullPath() {
        assertThrows(TermuxException.class, () ->
            FileUtils.validateRegularFileExistenceAndPermissionsOrThrow(
                "test", null, null, null, false, false, false));
    }

    @Test
    public void validateRegularFileExistenceAndPermissionsOrThrow_doesNotThrowForExistingRegularFileWithNoPermissionCheck() throws Exception {
        File file = tempFolder.newFile("regular.txt");

        FileUtils.validateRegularFileExistenceAndPermissionsOrThrow(
            "test", file.getAbsolutePath(), null, null, false, false, false);
    }

    @Test
    public void validateDirectoryFileExistenceAndPermissionsOrThrow_doesNotThrowForExistingDirectory() throws Exception {
        File dir = tempFolder.newFolder("existing-validated-dir");

        FileUtils.validateDirectoryFileExistenceAndPermissionsOrThrow(
            "test", dir.getAbsolutePath(), null, false, null, false, false, false, false);
    }

    @Test
    public void validateDirectoryFileExistenceAndPermissionsOrThrow_throwsWhenPathIsRegularFileNotDirectory() throws Exception {
        File file = tempFolder.newFile("not-a-dir.txt");

        assertThrows(TermuxException.class, () ->
            FileUtils.validateDirectoryFileExistenceAndPermissionsOrThrow(
                "test", file.getAbsolutePath(), null, false, null, false, false, false, false));
    }
}
