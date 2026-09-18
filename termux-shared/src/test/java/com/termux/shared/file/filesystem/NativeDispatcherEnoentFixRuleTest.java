package com.termux.shared.file.filesystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

/**
 * Proof/regression tests for beads-94h: demonstrates that {@link NativeDispatcherEnoentFixRule}
 * fixes the Robolectric Os.lstat ENOENT gap for genuinely missing paths (regular files and
 * directories), and that the gap remains present by default (opt-in only, zero effect on
 * production).
 */
@RunWith(RobolectricTestRunner.class)
public class NativeDispatcherEnoentFixRuleTest {

    @Rule
    public NativeDispatcherEnoentFixRule enoentFix = new NativeDispatcherEnoentFixRule();

    @Test
    public void missingPathIsCorrectlyNoExist_withRuleApplied() {
        File missing = new File("/tmp/definitely-missing-" + System.nanoTime() + ".txt");
        assertEquals(FileType.NO_EXIST, FileTypes.getFileType(missing.getAbsolutePath(), false));
    }

    @Test
    public void existingRegularFileStillWorks_withRuleApplied() throws Exception {
        File file = File.createTempFile("exists", ".txt");
        assertEquals(FileType.REGULAR, FileTypes.getFileType(file.getAbsolutePath(), false));
    }

    @Test
    public void existingDirectoryStillWorks_withRuleApplied() {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        assertEquals(FileType.DIRECTORY, FileTypes.getFileType(dir.getAbsolutePath(), false));
    }

    // Deliberately no @Rule on this nested-style check within the same class: verify the gap is
    // opt-in only by clearing the checker within a single test to simulate "rule not applied".
    @Test
    public void gapIsStillPresentWhenCheckerNotInstalled() {
        NativeDispatcher.TEST_ONLY_FILE_EXISTENCE_CHECKER = null; // simulate no @Rule
        File missing = new File("/tmp/definitely-missing-" + System.nanoTime() + ".txt");
        // This documents the known Robolectric limitation (beads-94h) still reproduces by
        // default: without the checker installed, a missing path is misclassified as something
        // other than NO_EXIST (SOCKET, as of Robolectric 4.13/4.17). If a future Robolectric
        // version fixes this upstream, this assertion (not equals SOCKET) may start failing in a
        // *good* way -- i.e. FileType.NO_EXIST would then also satisfy assertNotEquals(SOCKET,...)
        // trivially, so this test is intentionally loose (checks "not correctly NO_EXIST", not a
        // specific wrong value) to avoid being surprised by an upstream fix.
        assertNotEquals(FileType.NO_EXIST, FileTypes.getFileType(missing.getAbsolutePath(), false));
    }
}
