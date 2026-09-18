package com.termux.shared.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.termux.shared.errors.TermuxException;
import com.termux.shared.file.filesystem.NativeDispatcherEnoentFixRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Unit tests for the copy-style, move-style, and copyOrMoveFileOrThrow sibling methods added for
 * beads-km2.
 *
 * <p>Originally, successful copy/move end-states could not be tested here because every move
 * (and every overwriting copy) calls {@code FileUtils.deleteFile()} on the consumed path, which
 * re-stats it to confirm deletion -- and Robolectric's {@code Os.lstat} never throws
 * {@code ENOENT} for a genuinely missing path (beads-94h). Success paths are now unlocked by
 * {@link NativeDispatcherEnoentFixRule} (beads-94h), which wires a real-filesystem
 * existence check for the duration of each test.
 */
@RunWith(RobolectricTestRunner.class)
public class FileUtilsCopyOrMoveOrThrowTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public NativeDispatcherEnoentFixRule enoentFix = new NativeDispatcherEnoentFixRule();

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

    // -----------------------------------------------------------------------
    // Success paths (previously blocked by beads-94h; now unlocked via enoentFix)
    // -----------------------------------------------------------------------

    @Test
    public void copyRegularFileOrThrow_copiesFileToNewDestination() throws Exception {
        File src = writeFile("source.txt", "hello copy");
        File dest = new File(tempFolder.getRoot(), "dest-copy.txt");

        FileUtils.copyRegularFileOrThrow("test", src.getAbsolutePath(), dest.getAbsolutePath(), false);

        assertTrue("destination must exist after copy", dest.exists());
        assertEquals("hello copy", new String(Files.readAllBytes(dest.toPath()), StandardCharsets.UTF_8));
        assertTrue("source must still exist after copy", src.exists());
    }

    @Test
    public void moveRegularFileOrThrow_movesFileAndRemovesSource() throws Exception {
        File src = writeFile("source-to-move.txt", "hello move");
        File dest = new File(tempFolder.getRoot(), "dest-moved.txt");

        FileUtils.moveRegularFileOrThrow("test", src.getAbsolutePath(), dest.getAbsolutePath(), false);

        assertTrue("destination must exist after move", dest.exists());
        assertEquals("hello move", new String(Files.readAllBytes(dest.toPath()), StandardCharsets.UTF_8));
        assertTrue("source must be gone after move", !src.exists());
    }

    @Test
    public void copyRegularFileOrThrow_overwritesExistingDestination() throws Exception {
        File src = writeFile("src-overwrite.txt", "new content");
        File dest = writeFile("dest-overwrite.txt", "old content");

        FileUtils.copyRegularFileOrThrow("test", src.getAbsolutePath(), dest.getAbsolutePath(), false);

        assertEquals("new content", new String(Files.readAllBytes(dest.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void copyDirectoryFileOrThrow_throwsWhenSourceIsNotADirectory() throws Exception {
        File src = writeFile("not-a-dir.txt", "hi");
        File dest = new File(tempFolder.getRoot(), "dest-dir");

        assertThrows(TermuxException.class, () ->
            FileUtils.copyDirectoryFileOrThrow("test", src.getAbsolutePath(), dest.getAbsolutePath(), false));
    }
}
