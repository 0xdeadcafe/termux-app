package com.termux.shared.file.filesystem;

import org.junit.rules.ExternalResource;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;

/**
 * JUnit {@link org.junit.rules.TestRule} that installs a correct, real-filesystem-backed
 * {@link NativeDispatcher.TestOnlyFileExistenceChecker} for the duration of a test, working
 * around a Robolectric limitation where {@code android.system.Os.lstat()}/{@code stat()} never
 * throw {@code ENOENT} for a genuinely missing path (see beads-94h). Guarantees the checker is
 * reset to {@code null} after the test, even if the test throws.
 *
 * <p>Usage:
 * <pre>{@code
 * @Rule
 * public NativeDispatcherEnoentFixRule enoentFix = new NativeDispatcherEnoentFixRule();
 * }</pre>
 *
 * <p>Uses {@code NOFOLLOW_LINKS} (lstat-like) semantics, which is deliberately more correct than
 * plain {@code java.io.File#exists()}: a dangling symlink (one that exists but whose target does
 * not) is still correctly reported as *existing* here, since the symlink file itself is present
 * on disk -- matching what real Android's {@code Os.lstat()} does for the existence check.
 * {@code File#exists()} follows symlinks and would incorrectly treat a dangling symlink as
 * missing entirely (ENOENT), which is what this rule avoids.
 *
 * <p>Note this rule only fixes the ENOENT/existence-detection gap (beads-94h). It does NOT fix a
 * separate, narrower Robolectric quirk where {@code ShadowLinux}'s mode-computation logic
 * ({@code OsConstantsValues.getMode()}) can still misreport the exact {@link FileType} of a
 * dangling symlink specifically (as opposed to whether something exists at all) -- that quirk
 * lives one layer deeper (in how the mode bits are derived, not whether ENOENT is thrown) and is
 * out of scope here.
 *
 * <p>This class lives in {@code src/test}, not {@code src/main}, and is safe to use
 * {@code java.nio.file.*} APIs unconditionally here (unlike in production FileUtils code, which
 * must stay compatible with {@code minSdkVersion=21} without full NIO desugaring) since tests run
 * directly on the build/CI JVM, not on an Android device.
 */
public class NativeDispatcherEnoentFixRule extends ExternalResource {

    @Override
    protected void before() {
        NativeDispatcher.TEST_ONLY_FILE_EXISTENCE_CHECKER = filePath ->
            Files.exists(Paths.get(filePath), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    protected void after() {
        NativeDispatcher.TEST_ONLY_FILE_EXISTENCE_CHECKER = null;
    }
}
