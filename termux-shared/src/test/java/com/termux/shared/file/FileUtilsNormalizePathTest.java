package com.termux.shared.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit tests for {@link FileUtils#normalizePath(String)}.
 *
 * <p>{@code normalizePath} is a string-only operation (no filesystem access) used as a
 * lightweight security barrier before path-containment checks.  These tests document its
 * exact contract — including the important limitation that it <em>strips</em> {@code ..}
 * components in place rather than performing full canonical resolution, so callers that
 * need true confinement must still use {@link java.io.File#getCanonicalPath()}.
 */
public class FileUtilsNormalizePathTest {

    // -----------------------------------------------------------------------
    // Null / empty / unchanged
    // -----------------------------------------------------------------------

    @Test
    public void nullInput_returnsNull() {
        assertNull(FileUtils.normalizePath(null));
    }

    @Test
    public void emptyString_returnsEmpty() {
        assertEquals("", FileUtils.normalizePath(""));
    }

    @Test
    public void normalAbsolutePath_unchanged() {
        assertEquals("/data/data/com.termux",
            FileUtils.normalizePath("/data/data/com.termux"));
    }

    // -----------------------------------------------------------------------
    // Double-slash collapsing
    // -----------------------------------------------------------------------

    @Test
    public void doubleSlash_collapsed() {
        assertEquals("/foo/bar", FileUtils.normalizePath("//foo/bar"));
    }

    @Test
    public void multipleDoubleSlashes_collapsed() {
        assertEquals("/foo/bar/baz", FileUtils.normalizePath("/foo//bar//baz"));
    }

    // -----------------------------------------------------------------------
    // Trailing slash stripping
    // -----------------------------------------------------------------------

    @Test
    public void trailingSlash_stripped() {
        assertEquals("foo", FileUtils.normalizePath("foo/"));
    }

    // -----------------------------------------------------------------------
    // Current-directory (./) stripping
    // -----------------------------------------------------------------------

    @Test
    public void leadingDotSlash_stripped() {
        assertEquals("foo", FileUtils.normalizePath("./foo"));
    }

    @Test
    public void embeddedDotSlash_stripped() {
        assertEquals("foo/bar", FileUtils.normalizePath("foo/./bar"));
    }

    // -----------------------------------------------------------------------
    // Parent-directory (../) stripping
    //
    // NOTE: normalizePath removes ".." tokens in place but does NOT perform
    // full canonical path resolution.  "a/b/../c" becomes "a/b/c" (not "a/c")
    // and "a/../../b" becomes "a/b" (not the expected two-levels-up result).
    // Callers that need hard confinement guarantees must use getCanonicalPath().
    // -----------------------------------------------------------------------

    @Test
    public void leadingDotDotSlash_stripped() {
        // "../foo" → "/foo"  (the ".." at the start is removed; the "/" that
        // followed it is preserved as the path root).
        assertEquals("/foo", FileUtils.normalizePath("../foo"));
    }

    @Test
    public void embeddedSlashDotDotSlash_stripped() {
        assertEquals("a/b", FileUtils.normalizePath("a/../b"));
    }

    @Test
    public void trailingSlashDotDot_stripped() {
        assertEquals("foo", FileUtils.normalizePath("foo/.."));
    }

    @Test
    public void multipleleadingDotDot_stripped() {
        // "../../b" — both ".." components are stripped, leaving "/b".
        assertEquals("/b", FileUtils.normalizePath("../../b"));
    }

    @Test
    public void mixedLeadingSegmentAndDotDot_strippedInPlace() {
        // "a/../../b" — both "/.." tokens are removed in a single pass,
        // giving "a/b".  The leading "a" segment is NOT consumed by the
        // second "..", which is the documented limitation above.
        assertEquals("a/b", FileUtils.normalizePath("a/../../b"));
    }

    @Test
    public void dotDotInMiddleWithoutBacktrack() {
        // "a/b/../c" → "a/b/c" (not "a/c") — strip-in-place, no backtrack.
        assertEquals("a/b/c", FileUtils.normalizePath("a/b/../c"));
    }
}
