package com.termux.shared.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.termux.shared.errors.TermuxException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Unit tests for {@link FileUtils#readTextFromFileOrThrow(String, String, Charset, boolean)}.
 *
 * <p>Note: cases that depend on {@code lstat(2)} correctly reporting ENOENT for a genuinely
 * missing path are intentionally not covered here — as of Robolectric 4.13 its {@code Os.lstat}
 * shadow returns a zeroed {@code struct stat} instead of throwing for non-existent files (and
 * {@code OsConstants.S_IFSOCK} shadows to {@code 0}, which combined make a missing file
 * misclassify as {@link com.termux.shared.file.filesystem.FileType#SOCKET} instead of
 * {@code NO_EXIST} under this test runner). That is a test-environment limitation, not a
 * behavior of the production code, which relies on the real Android {@code Os.lstat}.
 */
@RunWith(RobolectricTestRunner.class)
public class FileUtilsReadTextFromFileOrThrowTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void readsExistingFileContent() throws IOException, TermuxException {
        File file = tempFolder.newFile("existing.txt");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("hello world");
        }

        String content = FileUtils.readTextFromFileOrThrow(
            "test", file.getAbsolutePath(), Charset.defaultCharset(), false);

        assertEquals("hello world", content);
    }

    @Test
    public void throwsTermuxException_forNullFilePath() {
        assertThrows(TermuxException.class, () ->
            FileUtils.readTextFromFileOrThrow("test", null, Charset.defaultCharset(), false));
    }

    @Test
    public void throwsTermuxException_forEmptyFilePath() {
        assertThrows(TermuxException.class, () ->
            FileUtils.readTextFromFileOrThrow("test", "", Charset.defaultCharset(), false));
    }
}
