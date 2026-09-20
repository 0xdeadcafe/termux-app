package com.termux.shared.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.termux.shared.errors.TermuxException;
import com.termux.shared.file.filesystem.FileType;
import com.termux.shared.file.filesystem.FileTypes;
import com.termux.shared.file.filesystem.NativeDispatcherEnoentFixRule;

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
 * <p>Originally, successful deletes could not be verified here: {@code deleteFile()}'s own
 * post-delete existence check ("does it still exist?") calls {@code Os.lstat} which Robolectric
 * never throws {@code ENOENT} for a missing path (beads-94h), so the check would always
 * report the file as still present and return an error. Success paths are now unlocked by
 * {@link NativeDispatcherEnoentFixRule} (beads-94h), which installs a real-filesystem
 * existence check for the duration of each test.
 */
@RunWith(RobolectricTestRunner.class)
public class FileUtilsDeleteOrThrowTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public NativeDispatcherEnoentFixRule enoentFix = new NativeDispatcherEnoentFixRule();

    // -----------------------------------------------------------------------
    // Success paths (previously blocked by beads-94h; now unlocked via enoentFix)
    // -----------------------------------------------------------------------

    @Test
    public void deleteRegularFileOrThrow_deletesExistingFile() throws Exception {
        File file = tempFolder.newFile("to-delete.txt");
        assertTrue(file.exists());

        FileUtils.deleteRegularFileOrThrow("test", file.getAbsolutePath(), false);

        assertTrue("file must be gone after delete", !file.exists());
    }

    @Test
    public void deleteDirectoryFileOrThrow_deletesExistingDirectory() throws Exception {
        File dir = tempFolder.newFolder("to-delete-dir");
        assertTrue(dir.exists());

        FileUtils.deleteDirectoryFileOrThrow("test", dir.getAbsolutePath(), false);

        assertTrue("directory must be gone after delete", !dir.exists());
    }

    @Test
    public void clearDirectoryOrThrow_removesContentsOfNonEmptyDirectory() throws Exception {
        File dir = tempFolder.newFolder("to-clear");
        new java.io.File(dir, "child.txt").createNewFile();
        new java.io.File(dir, "subdir").mkdir();
        assertTrue("dir must be non-empty before clear", dir.list().length > 0);

        FileUtils.clearDirectoryOrThrow("test", dir.getAbsolutePath());

        assertTrue("dir must still exist after clear", dir.exists());
        assertEquals("dir must be empty after clear", 0, dir.list().length);
    }

    // -----------------------------------------------------------------------
    // Validation / error branches
    // -----------------------------------------------------------------------

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
            FileUtils.deleteFilesOlderThanXDaysOrThrow("test", dir.getAbsolutePath(), -1, false, FileTypes.FILE_TYPE_ANY_FLAGS));
    }

    @Test
    public void deleteFilesOlderThanXDaysOrThrow_throwsWhenPathIsNotADirectory() throws Exception {
        File file = tempFolder.newFile("not-a-dir-for-cleanup.txt");
        assertThrows(TermuxException.class, () ->
            FileUtils.deleteFilesOlderThanXDaysOrThrow("test", file.getAbsolutePath(), 1, false, FileTypes.FILE_TYPE_ANY_FLAGS));
    }

    @Test
    public void deleteFilesOlderThanXDaysOrThrow_doesNotThrowWhenNoFilesAreOldEnough() throws Exception {
        File dir = tempFolder.newFolder("recent-files-dir");
        tempFolder.newFile("recent-files-dir/recent.txt");

        // Files just created are not older than 1 day, so nothing should be deleted and no error
        // should be raised.
        FileUtils.deleteFilesOlderThanXDaysOrThrow("test", dir.getAbsolutePath(), 1, false, FileTypes.FILE_TYPE_ANY_FLAGS);
    }
}
