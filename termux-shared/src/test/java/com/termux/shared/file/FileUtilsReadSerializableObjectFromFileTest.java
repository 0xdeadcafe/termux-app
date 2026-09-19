package com.termux.shared.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.errors.TermuxException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Unit tests for {@link FileUtils#readSerializableObjectFromFileOrThrow(String, String, Class, boolean)},
 * specifically the {@code AllowListingObjectInputStream} hardening added for beads-h94.
 */
@RunWith(RobolectricTestRunner.class)
public class FileUtilsReadSerializableObjectFromFileTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Stand-in for a legitimate, expected payload type (analogous to ReportInfo). */
    public static class AllowedPayload implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String value;
        public AllowedPayload(String value) { this.value = value; }
    }

    /** Stand-in for an unexpected/attacker-controlled class (analogous to a gadget-chain class). */
    public static class DisallowedPayload implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String value;
        public DisallowedPayload(String value) { this.value = value; }
    }

    @Test
    public void readsBackAllowedType() throws Exception {
        File file = new File(tempFolder.getRoot(), "allowed.ser");
        // Pre-create the file as a real regular file first: as of Robolectric 4.13, Os.lstat on a
        // genuinely non-existent path does not throw ENOENT (see FileUtilsReadTextFromFileOrThrowTest
        // javadoc for details), which would otherwise make preWriteToFile() misclassify this brand
        // new path and reject the write. Not an issue on real Android, only under this test runner.
        assertTrue(file.createNewFile());

        FileUtils.writeSerializableObjectToFileOrThrow(
            "test", file.getAbsolutePath(), new AllowedPayload("hello"));

        AllowedPayload result = FileUtils.readSerializableObjectFromFileOrThrow(
            "test", file.getAbsolutePath(), AllowedPayload.class, false);

        assertNotNull(result);
        assertEquals("hello", result.value);
    }

    @Test
    public void rejectsDisallowedType() throws Exception {
        // Simulate an attacker (or a bug elsewhere) having replaced the file with a serialized
        // instance of a class the caller never asked for and does not expect.
        File file = new File(tempFolder.getRoot(), "disallowed.ser");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(new DisallowedPayload("malicious"));
        }

        try {
            FileUtils.readSerializableObjectFromFileOrThrow(
                "test", file.getAbsolutePath(), AllowedPayload.class, false);
            throw new AssertionError("Expected TermuxException when deserializing a non-allow-listed class");
        } catch (TermuxException e) {
            assertNotNull("Expected a non-null error", e.getError());
        }
    }

    @Test
    public void rejectsDisallowedType_evenWhenAssignmentCompatible() throws Exception {
        // A subclass of the requested type that was not itself requested should still be
        // rejected: resolveClass() checks the exact serialized class name, not assignability.
        File file = new File(tempFolder.getRoot(), "subclass.ser");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(new AllowedPayloadSubclass("hi"));
        }

        try {
            FileUtils.readSerializableObjectFromFileOrThrow(
                "test", file.getAbsolutePath(), AllowedPayload.class, false);
            throw new AssertionError("Expected TermuxException for subclass of requested type");
        } catch (TermuxException e) {
            assertNotNull(e.getError());
        }
    }

    public static class AllowedPayloadSubclass extends AllowedPayload {
        private static final long serialVersionUID = 1L;
        public AllowedPayloadSubclass(String value) { super(value); }
    }
}
