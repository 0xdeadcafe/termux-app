package com.termux.shared.errors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.termux.shared.activity.ActivityErrno;
import com.termux.shared.file.FileUtilsErrno;
import com.termux.shared.net.socket.local.LocalSocketErrno;
import com.termux.shared.shell.am.AmSocketServerErrno;
import com.termux.shared.shell.command.result.ResultSenderErrno;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regression test for beads-jb0: two {@code Errno} subclasses each defined two constants that
 * accidentally shared the same (type, code) pair, which silently collide in {@link Errno}'s
 * global static registry (the second one declared overwrites the first for
 * {@link Errno#valueOf(String, Integer)} lookups, even though both {@link Error} instances still
 * carry their own correct message text). This scans every {@code public static final Errno}
 * constant declared directly on {@link Errno} and each of its known subclasses and asserts:
 * <ol>
 *   <li>No two distinct constants share the same (type, code) pair.</li>
 *   <li>{@link Errno#valueOf(String, Integer)} round-trips back to the exact same instance for
 *   every constant.</li>
 * </ol>
 */
public class ErrnoRegistryTest {

    /** Every class that declares its own {@code public static final Errno} constants. */
    private static final Class<?>[] ERRNO_CLASSES = {
        Errno.class,
        FunctionErrno.class,
        FileUtilsErrno.class,
        ActivityErrno.class,
        AmSocketServerErrno.class,
        ResultSenderErrno.class,
        LocalSocketErrno.class,
    };

    private static final class NamedErrno {
        final String declaringClassSimpleName;
        final String fieldName;
        final Errno errno;

        NamedErrno(String declaringClassSimpleName, String fieldName, Errno errno) {
            this.declaringClassSimpleName = declaringClassSimpleName;
            this.fieldName = fieldName;
            this.errno = errno;
        }

        String qualifiedName() {
            return declaringClassSimpleName + "." + fieldName;
        }
    }

    private static List<NamedErrno> collectAllDeclaredErrnoConstants() throws ReflectiveOperationException {
        List<NamedErrno> all = new ArrayList<>();
        for (Class<?> clazz : ERRNO_CLASSES) {
            for (Field field : clazz.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (field.getType() != Errno.class) continue;
                if (!Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers) || !Modifier.isFinal(modifiers)) continue;

                Errno errno = (Errno) field.get(null);
                all.add(new NamedErrno(clazz.getSimpleName(), field.getName(), errno));
            }
        }
        return all;
    }

    @Test
    public void noTwoConstantsShareTheSameTypeAndCode() throws ReflectiveOperationException {
        List<NamedErrno> all = collectAllDeclaredErrnoConstants();
        assertTrue("Expected to find Errno constants to check, something is wrong with the reflection scan", all.size() > 10);

        Map<String, NamedErrno> seenByKey = new HashMap<>();
        List<String> collisions = new ArrayList<>();

        for (NamedErrno current : all) {
            String key = current.errno.getType() + ":" + current.errno.getCode();
            NamedErrno previous = seenByKey.get(key);
            if (previous != null) {
                collisions.add(previous.qualifiedName() + " and " + current.qualifiedName()
                    + " both use (type=\"" + current.errno.getType() + "\", code=" + current.errno.getCode() + ")");
            } else {
                seenByKey.put(key, current);
            }
        }

        assertTrue("Found Errno (type, code) collisions, which silently break Errno.valueOf() lookups:\n"
            + String.join("\n", collisions), collisions.isEmpty());
    }

    @Test
    public void valueOfRoundTripsForEveryDeclaredConstant() throws ReflectiveOperationException {
        List<NamedErrno> all = collectAllDeclaredErrnoConstants();

        for (NamedErrno named : all) {
            Errno resolved = Errno.valueOf(named.errno.getType(), named.errno.getCode());
            assertSame("Errno.valueOf() did not round-trip for " + named.qualifiedName()
                    + " (type=\"" + named.errno.getType() + "\", code=" + named.errno.getCode() + ")."
                    + " This usually means another constant was registered with the same (type, code) pair"
                    + " and silently overwrote it in Errno's global registry.",
                named.errno, resolved);
        }
    }

    @Test
    public void everyErrnoSubclassDeclaresAUniqueTypeString() throws ReflectiveOperationException {
        // Sanity check: if two domain classes ever accidentally reused the same TYPE string, codes
        // that look distinct per-class could still collide globally.
        Map<String, String> typeToClass = new HashMap<>();
        for (Class<?> clazz : ERRNO_CLASSES) {
            Field typeField = clazz.getDeclaredField("TYPE");
            String type = (String) typeField.get(null);
            String existing = typeToClass.put(type, clazz.getSimpleName());
            if (existing != null) {
                fail("TYPE \"" + type + "\" is used by both " + existing + " and " + clazz.getSimpleName());
            }
        }
        assertEquals(ERRNO_CLASSES.length, typeToClass.size());
    }

    @Test
    public void regression_functionErrnoInvalidParameterAndNotInstanceOfHaveDistinctCodes() {
        // The specific pair this issue was originally filed about (beads-jb0).
        assertFalse(FunctionErrno.ERRNO_INVALID_PARAMETER.getCode() == FunctionErrno.ERRNO_PARAMETER_NOT_INSTANCE_OF.getCode());
    }

    @Test
    public void regression_localSocketErrnoPeerUidCodesHaveDistinctCodes() {
        // The second pair found by the same reflection scan while fixing beads-jb0.
        assertFalse(LocalSocketErrno.ERRNO_GET_CLIENT_SOCKET_PEER_UID_FAILED.getCode() == LocalSocketErrno.ERRNO_CLIENT_SOCKET_PEER_UID_INVALID.getCode());
    }
}
