package com.termux.shared.file;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.termux.shared.errors.TermuxException;
import com.termux.shared.file.filesystem.FileType;
import com.termux.shared.file.filesystem.FileTypes;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

/**
 * Unit tests for the delete*OrThrow/clearDirectoryOrThrow/deleteFilesOlderThanXDaysOrThrow
 * sibling methods added for beads-km2.
 *
 * <p>As documented in beads-94h, deleteFile()'s own post-delete "does it still exist" check hits
 * the Robolectric Os.lstat ENOENT gap, so a genuinely successful delete cannot be reliably
 * asserted not to throw here. Tests instead focus on validation/wrong-type/missing-and-not-ignored
 * branches, which only ever stat paths that exist (or never call stat on the target file at all).
 */
@RunWith(RobolectricTestRunner.class)
public class FileUtilsDeleteOrThrowTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void deleteFileOrThrow_throwsForNullPath() {
        assertThrows(TermuxException.class, () ->
            FileUtils.deleteFileOrThrow("test", null, false));
    }

    @Test
    public void deleteRegularFileOrThrow_throwsWhenMissingAndNotIgnored() {
        File missing = new File(tempFolder.getRoot(), "does-not-exist.txt");
        assertThrows(TermuxException.class, () ->
            FileUtils.deleteRegularFileOrThrow("test", missing.getAbsolutePath(), false));
    }

    @Test
    public void deleteRegularFileOrThrow_throwsWhenPathIsActuallyADirectory() throws Exception {
        File dir = tempFolder.newFolder("a-directory");

        assertThrows(TermuxException.class, () ->
            FileUtils.deleteRegularFileOrThrow("test", dir.getAbsolutePath(), false));

        assertTrue("Directory should not have been touched", dir.exists());
    }

    @Test
    public void deleteDirectoryFileOrThrow_throwsWhenPathIsActuallyARegularFile() throws Exception {
        File file = tempFolder.newFile("a-file.txt");

        assertThrows(TermuxException.class, () ->
            FileUtils.deleteDirectoryFileOrThrow("test", file.getAbsolutePath(), false));

        assertTrue("File should not have been touched", file.exists());
    }

    @Test
    public void deleteFileOrThrow_doesNotThrowWhenWrongTypeIsIgnored() throws Exception {
        File dir = tempFolder.newFolder("ignored-wrong-type-dir");

        // allowedFileTypeFlags = REGULAR only, but ignoreWrongFileType = true, so this should be
        // a silent no-op rather than a thrown exception, and the directory must survive.
        FileUtils.deleteFileOrThrow("test", dir.getAbsolutePath(), false, true, FileType.REGULAR.getValue());

        assertTrue(dir.exists());
    }

    @Test
    public void clearDirectoryOrThrow_throwsWhenPathIsARegularFile() throws Exception {
        File file = tempFolder.newFile("not-a-directory.txt");

        assertThrows(TermuxException.class, () ->
            FileUtils.clearDirectoryOrThrow("test", file.getAbsolutePath()));
    }

    @Test
    public void clearDirectoryOrThrow_doesNotThrowForAlreadyEmptyDirectory() throws Exception {
        File dir = tempFolder.newFolder("already-empty");

        FileUtils.clearDirectoryOrThrow("test", dir.getAbsolutePath());

        assertTrue(dir.exists());
    }

    @Test
    public void deleteFilesOlderThanXDaysOrThrow_throwsForNegativeDays() {
        File dir = tempFolder.getRoot();
        assertThrows(TermuxException.class, () ->
            FileUtils.deleteFilesOlderThanXDaysOrThrow("test", dir.getAbsolutePath(), null, -1, false, FileTypes.FILE_TYPE_ANY_FLAGS));
    }

    @Test
    public void deleteFilesOlderThanXDaysOrThrow_throwsWhenPathIsNotADirectory() throws Exception {
        File file = tempFolder.newFile("not-a-dir-for-cleanup.txt");
        assertThrows(TermuxException.class, () ->
            FileUtils.deleteFilesOlderThanXDaysOrThrow("test", file.getAbsolutePath(), null, 1, false, FileTypes.FILE_TYPE_ANY_FLAGS));
    }

    @Test
    public void deleteFilesOlderThanXDaysOrThrow_doesNotThrowWhenNoFilesAreOldEnough() throws Exception {
        File dir = tempFolder.newFolder("recent-files-dir");
        tempFolder.newFile("recent-files-dir/recent.txt");

        // Files just created are not older than 1 day, so nothing should be deleted and no error
        // should be raised.
        FileUtils.deleteFilesOlderThanXDaysOrThrow("test", dir.getAbsolutePath(), null, 1, false, FileTypes.FILE_TYPE_ANY_FLAGS);
    }
}
