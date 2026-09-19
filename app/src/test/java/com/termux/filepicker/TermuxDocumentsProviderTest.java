package com.termux.filepicker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.provider.DocumentsContract.Document;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Unit tests for the security fixes made to {@link TermuxDocumentsProvider}:
 * beads-0zt (BASE_DIR confinement in getFileForDocId), beads-rhn (canonical comparison in
 * isChildDocument), beads-rsp (path traversal via displayName in createDocument), and
 * beads-dq0 (NPE when listFiles() returns null in queryChildDocuments).
 *
 * <p>{@code BASE_DIR} in the provider is a {@code static final File} derived from
 * {@code TermuxConstants.TERMUX_HOME_DIR}, a hardcoded path
 * ({@code /data/data/com.termux/files/home}) that a JVM test process has no permission to
 * create. Tests use {@link TermuxDocumentsProvider#TEST_ONLY_BASE_DIR_OVERRIDE} (a test-only
 * seam added alongside these tests, mirroring the existing
 * {@code NativeDispatcher.TEST_ONLY_FILE_EXISTENCE_CHECKER} pattern) to point the confinement
 * check at a real, writable {@link TemporaryFolder} instead.
 */
@RunWith(RobolectricTestRunner.class)
public class TermuxDocumentsProviderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private TermuxDocumentsProvider provider;
    private File baseDir;

    @Before
    public void setUp() throws IOException {
        provider = Robolectric.setupContentProvider(TermuxDocumentsProvider.class);
        baseDir = tempFolder.getRoot().getCanonicalFile();
        TermuxDocumentsProvider.TEST_ONLY_BASE_DIR_OVERRIDE = baseDir;
    }

    @After
    public void tearDown() {
        TermuxDocumentsProvider.TEST_ONLY_BASE_DIR_OVERRIDE = null;
    }

    // -----------------------------------------------------------------------
    // Group 1: getFileForDocId confinement (beads-0zt)
    // Exercised indirectly via queryDocument(), which calls getFileForDocId() internally.
    // -----------------------------------------------------------------------

    @Test
    public void queryDocument_resolvesFileInsideHome() throws Exception {
        File file = new File(baseDir, "inside.txt");
        assertTrue(file.createNewFile());

        Cursor cursor = provider.queryDocument(file.getPath(), null);

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());
        int nameIdx = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME);
        assertEquals("inside.txt", cursor.getString(nameIdx));
    }

    @Test
    public void queryDocument_throwsForPathTraversalOutsideHome() {
        // A crafted docId that starts under baseDir but uses enough "../" segments to escape it
        // and resolve to a real file outside it. /etc/passwd exists on every Linux test runner;
        // excess ".." segments above filesystem root are a no-op in canonicalization, so the
        // exact depth of baseDir under the system temp directory does not matter.
        assertTrue("Sanity check: /etc/passwd must exist for this traversal test",
            new File("/etc/passwd").exists());
        String traversalDocId = baseDir.getPath() + "/../../../../../../../../etc/passwd";

        assertThrows(FileNotFoundException.class, () -> provider.queryDocument(traversalDocId, null));
    }

    @Test
    public void queryDocument_throwsForPathEntirelyOutsideHome() {
        assertThrows(FileNotFoundException.class, () -> provider.queryDocument("/etc/passwd", null));
    }

    // -----------------------------------------------------------------------
    // Group 2: isChildDocument canonical comparison (beads-rhn)
    // -----------------------------------------------------------------------

    @Test
    public void isChildDocument_trueForLegitimateChild() throws Exception {
        File child = new File(baseDir, "child.txt");
        assertTrue(child.createNewFile());

        assertTrue(provider.isChildDocument(baseDir.getPath(), child.getPath()));
    }

    @Test
    public void isChildDocument_falseForStringPrefixSiblingNotActuallyNested() throws Exception {
        File parent = tempFolder.newFolder("foo");
        File sibling = new File(tempFolder.getRoot(), "foobar");
        assertTrue(sibling.mkdir());

        // "foobar" string-prefixes "foo" but is a sibling directory, not a descendant.
        assertFalse(provider.isChildDocument(parent.getPath(), sibling.getPath()));
    }

    @Test
    public void isChildDocument_falseForSymlinkEscapingParent() throws Exception {
        File parent = tempFolder.newFolder("parent-with-symlink");
        File outsideTarget = tempFolder.newFolder("outside-target");
        File linkInsideParent = new File(parent, "escape-link");
        Files.createSymbolicLink(linkInsideParent.toPath(), outsideTarget.toPath());

        // linkInsideParent's raw path string looks nested under parent, but it canonically
        // resolves to outsideTarget, a sibling of parent, not a descendant.
        assertFalse(provider.isChildDocument(parent.getPath(), linkInsideParent.getPath()));
    }

    // -----------------------------------------------------------------------
    // Group 3: createDocument path traversal via displayName (beads-rsp)
    // -----------------------------------------------------------------------

    @Test
    public void createDocument_sanitizesTraversalInDisplayName() throws Exception {
        String resultPath = provider.createDocument(baseDir.getPath(), "text/plain", "../../../evil");

        File expected = new File(baseDir, "evil");
        assertEquals(expected.getPath(), resultPath);
        assertTrue(expected.exists());
        // Must have been created inside baseDir, not escaped via the traversal.
        assertEquals(baseDir.getCanonicalPath(), expected.getParentFile().getCanonicalPath());
    }

    @Test
    public void createDocument_createsNormalFileNormally() throws Exception {
        String resultPath = provider.createDocument(baseDir.getPath(), "text/plain", "normalfile.txt");

        File expected = new File(baseDir, "normalfile.txt");
        assertEquals(expected.getPath(), resultPath);
        assertTrue(expected.isFile());
    }

    @Test
    public void createDocument_throwsForRootDisplayName() {
        assertThrows(FileNotFoundException.class,
            () -> provider.createDocument(baseDir.getPath(), "text/plain", "/"));
    }

    @Test
    public void createDocument_throwsForEmptyDisplayName() {
        assertThrows(FileNotFoundException.class,
            () -> provider.createDocument(baseDir.getPath(), "text/plain", ""));
    }

    // -----------------------------------------------------------------------
    // Group 4: queryChildDocuments NPE when listFiles() returns null (beads-dq0)
    // -----------------------------------------------------------------------

    @Test
    public void queryChildDocuments_returnsEmptyCursorWhenListFilesReturnsNull() throws Exception {
        // File#listFiles() returns null when called on something that isn't a directory (or on
        // I/O error). A regular file inside baseDir triggers this without needing to break
        // filesystem permissions to force a null return.
        File regularFile = new File(baseDir, "not-a-directory.txt");
        assertTrue(regularFile.createNewFile());

        Cursor cursor = provider.queryChildDocuments(regularFile.getPath(), null, (String) null);

        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
    }

    @Test
    public void queryChildDocuments_listsChildrenOfRealDirectory() throws Exception {
        assertTrue(new File(baseDir, "a.txt").createNewFile());
        assertTrue(new File(baseDir, "b.txt").createNewFile());

        Cursor cursor = provider.queryChildDocuments(baseDir.getPath(), null, (String) null);

        assertEquals(2, cursor.getCount());
    }

    // -----------------------------------------------------------------------
    // Group 5: includeFile() move/rename flags + null-safe getParentFile() (beads-1sv)
    // -----------------------------------------------------------------------

    @Test
    public void includeFile_setsDeleteMovedRenameWhenParentWritable() throws Exception {
        File file = new File(baseDir, "movable.txt");
        assertTrue(file.createNewFile());

        Cursor cursor = provider.queryDocument(file.getPath(), null);
        assertTrue(cursor.moveToFirst());
        int flags = cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS));

        assertTrue("FLAG_SUPPORTS_DELETE should be set",
            (flags & Document.FLAG_SUPPORTS_DELETE) != 0);
        assertTrue("FLAG_SUPPORTS_MOVE should be set",
            (flags & Document.FLAG_SUPPORTS_MOVE) != 0);
        assertTrue("FLAG_SUPPORTS_RENAME should be set",
            (flags & Document.FLAG_SUPPORTS_RENAME) != 0);
    }

    @Test
    public void includeFile_noMoveRenameDeleteForNonWritableParent() throws Exception {
        // Create a subdirectory with a file, then make the subdirectory read-only so that
        // getParentFile().canWrite() returns false for the child file.
        File subDir = tempFolder.newFolder("readonly-parent");
        File file = new File(subDir, "locked.txt");
        assertTrue(file.createNewFile());
        assertTrue(subDir.setWritable(false, false));

        try {
            Cursor cursor = provider.queryDocument(file.getPath(), null);
            assertTrue(cursor.moveToFirst());
            int flags = cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS));

            assertEquals("FLAG_SUPPORTS_DELETE should NOT be set",
                0, flags & Document.FLAG_SUPPORTS_DELETE);
            assertEquals("FLAG_SUPPORTS_MOVE should NOT be set",
                0, flags & Document.FLAG_SUPPORTS_MOVE);
            assertEquals("FLAG_SUPPORTS_RENAME should NOT be set",
                0, flags & Document.FLAG_SUPPORTS_RENAME);
        } finally {
            subDir.setWritable(true, false); // restore so TemporaryFolder cleanup succeeds
        }
    }

}

