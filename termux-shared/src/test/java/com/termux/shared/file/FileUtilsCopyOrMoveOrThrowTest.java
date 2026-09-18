package com.termux.shared.file;

import static org.junit.Assert.assertThrows;

import com.termux.shared.errors.TermuxException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileWriter;

/**
 * Unit tests for the copy-style, move-style, and copyOrMoveFileOrThrow sibling methods added for
 * beads-km2.
 *
 * <p>As documented in FileUtilsValidateAndCreateOrThrowTest and beads-94h, Robolectric's
 * {@code Os.lstat} never throws {@code ENOENT} for a genuinely non-existent path. This turns out
 * to block more than just "create a new file": every successful copy/move ultimately deletes an
 * old path (the pre-existing destination being overwritten, or the source after a move) and then
 * re-{@code stat}s it to confirm the delete actually took effect (see
 * {@code FileUtils.deleteFile}'s "file still exists after deleting" check) -- that re-stat of a
 * now-genuinely-deleted path hits the exact same Robolectric limitation. So "successful
 * copy/move" end states cannot be reliably asserted here either. These tests instead focus on
 * the validation/error branches, which only ever stat paths that genuinely exist throughout.
 */
@RunWith(RobolectricTestRunner.class)
public class FileUtilsCopyOrMoveOrThrowTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File writeFile(String name, String content) throws Exception {
        File file = tempFolder.newFile(name);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    @Test
    public void copyRegularFileOrThrow_throwsWhenSourceMissingAndNotIgnored() {
        File src = new File(tempFolder.getRoot(), "does-not-exist.txt");
        File dest = new File(tempFolder.getRoot(), "dest.txt");

        assertThrows(TermuxException.class, () ->
            FileUtils.copyRegularFileOrThrow("test", src.getAbsolutePath(), dest.getAbsolutePath(), false));
    }

    @Test
    public void copyRegularFileOrThrow_throwsWhenSourceAndDestAreSamePath() throws Exception {
        File src = writeFile("same.txt", "hello");

        assertThrows(TermuxException.class, () ->
            FileUtils.copyRegularFileOrThrow("test", src.getAbsolutePath(), src.getAbsolutePath(), false));
    }

    @Test
    public void moveDirectoryFileOrThrow_throwsWhenSourceAndDestAreSamePath() throws Exception {
        File dir = tempFolder.newFolder("samedir");

        assertThrows(TermuxException.class, () ->
            FileUtils.moveDirectoryFileOrThrow("test", dir.getAbsolutePath(), dir.getAbsolutePath(), false));
    }

    @Test
    public void copySymlinkFileOrThrow_throwsWhenSourceIsNotASymlink() throws Exception {
        // copySymlinkFile requires the source to actually be a symlink; a plain regular file
        // should be rejected via ERRNO_FILE_NOT_AN_ALLOWED_FILE_TYPE.
        File src = writeFile("regular-not-symlink.txt", "hi");
        File dest = writeFile("dest-symlink.txt", "placeholder");

        assertThrows(TermuxException.class, () ->
            FileUtils.copySymlinkFileOrThrow("test", src.getAbsolutePath(), dest.getAbsolutePath(), false));
    }

    @Test
    public void copyOrMoveFileOrThrow_throwsForNullSourcePath() {
        assertThrows(TermuxException.class, () ->
            FileUtils.copyOrMoveFileOrThrow("test", null, "/tmp/dest", false, false,
                com.termux.shared.file.filesystem.FileTypes.FILE_TYPE_ANY_FLAGS, true, true));
    }

    @Test
    public void copyOrMoveFileOrThrow_throwsForNullDestPath() throws Exception {
        File src = writeFile("src.txt", "hi");
        assertThrows(TermuxException.class, () ->
            FileUtils.copyOrMoveFileOrThrow("test", src.getAbsolutePath(), null, false, false,
                com.termux.shared.file.filesystem.FileTypes.FILE_TYPE_ANY_FLAGS, true, true));
    }

    @Test
    public void copyDirectoryFileOrThrow_throwsWhenSourceIsNotADirectory() throws Exception {
        File src = writeFile("not-a-dir.txt", "hi");
        File dest = new File(tempFolder.getRoot(), "dest-dir");

        assertThrows(TermuxException.class, () ->
            FileUtils.copyDirectoryFileOrThrow("test", src.getAbsolutePath(), dest.getAbsolutePath(), false));
    }
}
