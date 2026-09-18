# Termux-App — Code Review TODO

> Generated from in-depth static analysis of all 201 Java source files across
> `app/`, `terminal-emulator/`, `terminal-view/`, and `termux-shared/`.
> SDK: `minSdkVersion=21`, `targetSdkVersion=28`, `compileSdkVersion=36`

---

## 🔴 Security Issues

### 1. Zip Slip — Path Traversal During Bootstrap Extraction
**File:** `app/src/main/java/com/termux/app/TermuxInstaller.java:182`
**Severity: Critical**

No canonical-path validation when extracting zip entries into the staging directory.
```java
String zipEntryName = zipEntry.getName();
File targetFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntryName);
// ← no check that targetFile.getCanonicalPath().startsWith(TERMUX_STAGING_PREFIX_DIR_PATH)
```
A crafted entry like `../../lib/libevil.so` writes outside the staging dir. The SYMLINKS.txt
`oldPath` (symlink target) is also accepted verbatim with no path constraints.

**Fix:**
```java
String canonical = targetFile.getCanonicalPath();
if (!canonical.startsWith(TERMUX_STAGING_PREFIX_DIR_PATH + "/")) {
    throw new RuntimeException("Zip Slip: entry escapes staging dir: " + zipEntryName);
}
```

---

### 2. Path Traversal in `FileReceiverActivity.saveStreamWithName()`
**File:** `app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java:215`
**Severity: High**

`attachmentFileName` comes from an external content provider's `DISPLAY_NAME` column or Intent
subject and is used directly in a `File` constructor with no sanitization:
```java
final File outFile = new File(receiveDir, attachmentFileName);
```
A value like `../../.bashrc` or `../../.ssh/authorized_keys` escapes `~/downloads` and can
overwrite arbitrary files under `$HOME`.

**Fix:** Strip all directory components before use:
```java
String safeName = new File(attachmentFileName).getName();
if (DataUtils.isNullOrEmpty(safeName)) { showErrorDialogAndQuit(...); return null; }
final File outFile = new File(receiveDir, safeName);
```

---

### 3. Missing `FLAG_IMMUTABLE` on All `PendingIntent` Calls
**Severity: High**

All `PendingIntent` creations in the project are missing `FLAG_IMMUTABLE`. On Android 12+
(API 31+) this causes an `IllegalArgumentException` crash for apps targeting ≥ 31, and on
devices running Android 12+ the implicit intents can be intercepted/modified by other apps
regardless of `targetSdkVersion`.

| File | Line | Call |
|------|------|------|
| `app/.../TermuxService.java` | 787 | `PendingIntent.getActivity(…, 0)` |
| `app/.../TermuxService.java` | 830 | `PendingIntent.getService(…, 0)` — exit action |
| `app/.../TermuxService.java` | 838 | `PendingIntent.getService(…, 0)` — wake lock action |
| `termux-shared/.../TermuxPluginUtils.java` | 383 | `PendingIntent.getActivity(…, FLAG_UPDATE_CURRENT)` |
| `termux-shared/.../TermuxPluginUtils.java` | 387 | `PendingIntent.getBroadcast(…, FLAG_UPDATE_CURRENT)` |
| `termux-shared/.../TermuxCrashUtils.java` | 345 | `PendingIntent.getActivity(…, FLAG_UPDATE_CURRENT)` |
| `termux-shared/.../TermuxCrashUtils.java` | 349 | `PendingIntent.getBroadcast(…, FLAG_UPDATE_CURRENT)` |

**Fix:** Use `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT` everywhere. Use `FLAG_MUTABLE` only
where the receiver must fill in extras (e.g., result intents back to plugin callers).

---

### 4. `TermuxShellManager.init()` Singleton Not Thread-Safe
**File:** `termux-shared/src/main/java/com/termux/shared/termux/shell/TermuxShellManager.java:65`
**Severity: Medium**

`shellManager` is not `volatile`, so the null check and assignment are not atomic:
```java
private static TermuxShellManager shellManager; // ← not volatile

public static TermuxShellManager init(@NonNull Context context) {
    if (shellManager == null)            // unsynchronized read
        shellManager = new TermuxShellManager(context); // unsynchronized write
    return shellManager;
}
```
Two threads can race, resulting in two instances or a partially-constructed instance being
returned.

**Fix:** Declare `private static volatile TermuxShellManager shellManager;`
or synchronize `init()`.

---

### 5. `TermuxOpenReceiver.ContentProvider.openFile()` — Null `callingPackageName`
**File:** `app/src/main/java/com/termux/app/TermuxOpenReceiver.java`
**Severity: Low**

`getCallingPackage()` is documented to return `null` in some contexts (system-process callers,
content resolver with no package info). The result is used in logging without a null guard:
```java
String callingPackageName = getCallingPackage(); // can be null
Logger.logDebug(LOG_TAG, "Open file request received from " + callingPackageName + …);
```
Non-crashing but produces misleading `"from null"` in logs.

**Fix:** `String callingPackageName = DataUtils.getDefaultIfNull(getCallingPackage(), "unknown");`

---

### 6. `SettingsActivity` Exported Without Permission Protection
**File:** `app/src/main/AndroidManifest.xml`
**Severity: Low**

```xml
<activity android:name=".app.activities.SettingsActivity" android:exported="true" …/>
```
No `android:permission` attribute. Any installed app can launch the settings screen directly,
potentially confusing users or triggering unintended configuration changes.

**Fix:** Add `android:permission="${TERMUX_PACKAGE_NAME}.permission.RUN_COMMAND"` or set
`android:exported="false"` if external launch is not needed.

---

### 7. `targetSdkVersion=28` Is 6 Years Stale
**File:** `gradle.properties`
**Severity: Medium** (security regression + Play Store compliance)

`targetSdkVersion=28` (Android 9, October 2018) opts the app out of every security
improvement introduced since then:
- No `FLAG_IMMUTABLE` enforcement on `PendingIntent` (see item #3)
- `requestLegacyExternalStorage=true` still applies, bypassing scoped storage
- Background activity launch restrictions (Android 10+) are relaxed
- No foreground service type enforcement (Android 14+)
- Google Play will reject apps targeting below API 33

**Fix:** Incrementally raise `targetSdkVersion` toward 34+, addressing each behavioral
change (scoped storage, exact alarms, foreground service types, etc.) as it is enabled.

---

## 🐛 Bugs

### 8. `buildNotification()` Can Return `null` — Passed Directly to `startForeground()` / `notify()`
**File:** `app/src/main/java/com/termux/app/TermuxService.java:206, 857`
**Severity: High**

`buildNotification()` returns `null` if `NotificationUtils.geNotificationBuilder()` returns
`null` (which it explicitly does for `NOTIFICATION_MODE_NONE`). Both call sites pass the
return value directly without a null guard:
```java
startForeground(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification()); // NPE risk
…
notificationManager.notify(id, buildNotification()); // NPE risk
```

**Fix:**
```java
Notification n = buildNotification();
if (n != null) startForeground(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, n);
```

---

### 9. `System.exit(1)` in `TerminalSession.wrapFileDescriptor()`
**File:** `terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java:331`
**Severity: High**

Calling `System.exit()` in an Android app bypasses all Activity/Service lifecycle callbacks,
foreground service cleanup, open file descriptor teardown, and the standard crash reporter.
Running shells lose their PTY without `SIGHUP`, pending plugin `PendingIntent` results are
never delivered, and no crash log is generated.
```java
} catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
    Logger.logStackTraceWithMessage(…);
    System.exit(1);  // ← kills the entire Android process abruptly
}
```

**Fix:** `throw new RuntimeException("Error wrapping file descriptor", e);`

---

### 10. Race Condition: Shell List Mutation in Callbacks Not Synchronized
**File:** `app/src/main/java/com/termux/app/TermuxService.java:508-522, 640-658`
**Severity: Medium**

`onAppShellExited()` is posted to `mHandler` (main thread) and modifies
`mShellManager.mTermuxTasks` without any lock. `onTermuxSessionExited()` is not `synchronized`
at all and directly modifies `mTermuxSessions`. Other methods (`createTermuxTask`,
`createTermuxSession`, `killAllTermuxExecutionCommands`) are `synchronized(this)` and also
touch those same `ArrayList` instances — causing data races on unsynchronized `ArrayList`.

**Fix:** Synchronize all access to shell lists on a single monitor (`TermuxService.this`) or
use `Collections.synchronizedList`, and ensure the callbacks acquire the same lock.

---

### 11. `TerminalEmulator` Cursor Field Race (Self-Acknowledged TODO)
**File:** `terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java:3426`
**Severity: Medium**

```java
// TODO: Check if there are thread synchronization issues with mCursorCol and mCursorRow,
// possibly causing others bugs too.
if (column < 0) column = 0;
```
This guard was added after a production `ArrayIndexOutOfBoundsException(index=-1)` crash.
`mCursorCol` is written by the main thread and read from the render thread without any
`volatile` or synchronization. The guard itself is insufficient because the field can be
mutated between the read and the subsequent `setChar()` call.

**Fix:** Access `mCursorCol`/`mCursorRow` only on the main thread, or protect them with
`volatile` / a dedicated lock.

---

### 12. Javadoc Says `SIGILL`, Code Sends `SIGKILL` (3 Places)
**Files:** `termux-shared/.../AppShell.java:249, 278` and `termux-shared/.../TermuxSession.java:205`
**Severity: Low**

`SIGILL` (signal 4) is "Illegal Instruction". `SIGKILL` (signal 9) is the intended termination
signal. The behavior is correct, but the Javadoc is wrong in three separate places, creating
a persistent documentation lie that will confuse future contributors.

**Fix:** Change all three `{@link OsConstants#SIGILL}` references in Javadoc to
`{@link OsConstants#SIGKILL}`.

---

### 13. `InterruptedException` Caught and Swallowed — Interrupt Flag Cleared
**File:** `termux-shared/src/main/java/com/termux/shared/shell/command/runner/app/AppShell.java:143, 152`
**Severity: Low**

```java
} catch (IllegalThreadStateException | InterruptedException e) {
    // TODO: Should either of these be handled or returned?
}
```
Catching `InterruptedException` without calling `Thread.currentThread().interrupt()` clears
the thread's interrupt status, violating the Java interrupt contract and making the thread
permanently unresponsive to future interrupts.

**Fix:** Add `Thread.currentThread().interrupt();` inside the catch block, or re-throw.

---

### 14. Null Check After Guaranteed Non-Null Dereference in `onTermuxSessionExited()`
**File:** `app/src/main/java/com/termux/app/TermuxService.java:643-648`
**Severity: Low**

```java
ExecutionCommand executionCommand = termuxSession.getExecutionCommand(); // always non-null
…
if (executionCommand != null && executionCommand.isPluginExecutionCommand) // check is unreachable
```
`getExecutionCommand()` returns the field set in the constructor — it is never null.
The outer `if (termuxSession != null)` already guards against the only null case.
The same pattern exists in `onAppShellExited()`.

**Fix:** Remove the redundant `executionCommand != null` guard.

---

### 15. `FileUtils.normalizePath()` Does Not Sanitize `..` Traversal
**File:** `termux-shared/src/main/java/com/termux/shared/file/FileUtils.java:107-114`
**Severity: Low**

```java
path = path.replaceAll("/+", "/");
path = path.replaceAll("\\./", "");  // removes "./" but NOT "../"
```
Any caller relying on this function as a security normalization step will be bypassed by
`../` components. The function appears alongside many security-sensitive path helpers and
its name implies full normalization.

**Fix:** Either add `../` stripping, or rename the function to `cleanupRedundantSlashes()`
to make clear it is not a security normalization. Always prefer `File.getCanonicalPath()` for
security-sensitive path confinement checks.

---

### 16. Terminal Toolbar Renders as Zero-Height Ghost When Extra Keys Are Null
**File:** `app/src/main/java/com/termux/app/TermuxActivity.java`
**Severity: Low**

```java
layoutParams.height = Math.round(
    mTerminalToolbarDefaultHeight *
    (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : …getMatrix().length) *
    mProperties.getTerminalToolbarHeightScaleFactor());
```
When `getExtraKeysInfo()` is `null`, the multiplier is `0`, producing `height = 0`. The
toolbar is still set to `View.VISIBLE` (when `shouldShowTerminalToolbar()` is `true`), so it
occupies zero pixels but is technically visible — a layout ghost that can cause subtle
measurement/drawing issues in parent views.

**Fix:** When `getExtraKeysInfo() == null`, set `visibility = View.GONE` instead of a
zero height.

---

## 💀 Dead Code & Bloat

### 17. `PopupWindowCompatGingerbread.java` — Entire Class Is Obsolete
**File:** `terminal-view/src/main/java/com/termux/view/support/PopupWindowCompatGingerbread.java`

This class uses reflection to call `PopupWindow.setWindowLayoutType()` because it wasn't
public API on Android 2.3 (Gingerbread, API 9). `minSdkVersion=21`. The method has been
public API since API 23. The entire reflection shim can be deleted and the two call sites in
`TextSelectionHandleView.java` changed to direct method calls.

---

### 18. Commented-Out `Runner` Enum Values in `ExecutionCommand.java`
**File:** `termux-shared/src/main/java/com/termux/shared/shell/command/ExecutionCommand.java`

```java
///** Run command in {@link AdbShell}. */
//ADB_SHELL("adb-shell"),
///** Run command in {@link RootShell}. */
//ROOT_SHELL("root-shell");
```
Abandoned future feature stubs. Remove them.

---

### 19. Commented-Out Dead Code in `AppShell.executeInner()`
**File:** `termux-shared/src/main/java/com/termux/shared/shell/command/runner/app/AppShell.java:191`

```java
//STDIN.write("exit\n".getBytes(StandardCharsets.UTF_8));
//STDIN.flush();
```
Remove.

---

### 20. `LOG_ESCAPE_SEQUENCES = false` — Permanently-False Compile-Time Constant
**File:** `terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java`

```java
private static final boolean LOG_ESCAPE_SEQUENCES = false;
```
This `static final` constant controls numerous debug-logging blocks throughout the file. Since
it is always `false`, the compiler eliminates those blocks entirely — they are dead code in
source. Either remove the blocks and the constant, or replace with a proper build-variant
debug flag.

---

### 21. `ProgressDialog` Is a Deprecated API
**File:** `app/src/main/java/com/termux/app/TermuxInstaller.java:117`

```java
final ProgressDialog progress = ProgressDialog.show(activity, null, …, true, false);
```
`ProgressDialog` was deprecated in Android API 26. Replace with a non-deprecated modal dialog
or `CircularProgressIndicator` from Material Components.

---

### 22. `Notification.Builder.setDefaults()` Is Deprecated (Existing TODO, Never Fixed)
**File:** `termux-shared/src/main/java/com/termux/shared/notification/NotificationUtils.java:112`

```java
// TODO: setDefaults() is deprecated and should also implement setting notification
// mode via notification channel
```
`setDefaults()` was deprecated in API 26. Sound/vibrate/lights should be configured on the
`NotificationChannel` at creation time instead.

---

### 23. `RunCommandService.LocalBinder` and `onBind()` Are Dead Code
**File:** `app/src/main/java/com/termux/app/RunCommandService.java`

```java
class LocalBinder extends Binder {
    public final RunCommandService service = RunCommandService.this;
}
private final IBinder mBinder = new LocalBinder();

@Override
public IBinder onBind(Intent intent) { return mBinder; }
```
`RunCommandService` is a "fire and forward" service — it validates and forwards an intent to
`TermuxService`, then stops itself. It is never bound to from any component. The entire
`LocalBinder`/`onBind` infrastructure is unreachable.

**Fix:** Remove `LocalBinder`, `mBinder`, and override `onBind()` to return `null`.

---

### 24. Duplicate `UserAction` Enum Types
**Files:**
- `app/src/main/java/com/termux/app/models/UserAction.java`
- `termux-shared/src/main/java/com/termux/shared/termux/models/UserAction.java`

Two separate `UserAction` enums exist for similar purposes (labeling user actions in
crash/report notifications) with completely different values. Consolidate into one.

---

### 25. Unused `ArrayAdapter` Import in `TermuxShellManager.java`
**File:** `termux-shared/src/main/java/com/termux/shared/termux/shell/TermuxShellManager.java`

```java
import android.widget.ArrayAdapter; // only appears in a Javadoc comment, not in code
```
Remove the import.

---

## Summary

| # | Category | Severity | File | Issue |
|---|---|---|---|---|
| 1 | Security | 🔴 Critical | `TermuxInstaller.java:182` | Zip Slip — no canonical path validation on extracted entries |
| 2 | Security | 🔴 High | `FileReceiverActivity.java:215` | Path traversal via unsanitized `attachmentFileName` |
| 3 | Security | 🔴 High | `TermuxService.java` + 4 others | `PendingIntent` missing `FLAG_IMMUTABLE` — 7 call sites |
| 4 | Security | 🟠 Medium | `TermuxShellManager.java:65` | Singleton `init()` not thread-safe — missing `volatile` |
| 5 | Security | 🟡 Low | `TermuxOpenReceiver.java` | Null `callingPackageName` not guarded before use |
| 6 | Security | 🟡 Low | `AndroidManifest.xml` | `SettingsActivity` exported without any permission |
| 7 | Security | 🟠 Medium | `gradle.properties` | `targetSdkVersion=28` stale — 6 years of security mitigations skipped |
| 8 | Bug | 🔴 High | `TermuxService.java:206,857` | Null return from `buildNotification()` passed to `startForeground()`/`notify()` |
| 9 | Bug | 🔴 High | `TerminalSession.java:331` | `System.exit(1)` on reflection failure — bypasses all lifecycle cleanup |
| 10 | Bug | 🟠 Medium | `TermuxService.java:508,640` | Shell list mutation in async callbacks not synchronized |
| 11 | Bug | 🟠 Medium | `TerminalEmulator.java:3426` | Acknowledged cursor field race between main and render threads |
| 12 | Bug | 🟡 Low | `AppShell.java:249,278`, `TermuxSession.java:205` | Javadoc says `SIGILL`; code sends `SIGKILL` |
| 13 | Bug | 🟡 Low | `AppShell.java:143,152` | `InterruptedException` swallowed — interrupt flag cleared |
| 14 | Bug | 🟡 Low | `TermuxService.java:643` | Null check after guaranteed non-null dereference |
| 15 | Bug | 🟡 Low | `FileUtils.java:107` | `normalizePath()` strips `./` but not `../` |
| 16 | Bug | 🟡 Low | `TermuxActivity.java` | Toolbar height = 0 (visible ghost) when `extraKeysInfo == null` |
| 17 | Dead Code | — | `PopupWindowCompatGingerbread.java` | Gingerbread (API 9) reflection shim — entire class unnecessary |
| 18 | Dead Code | — | `ExecutionCommand.java` | Commented-out `ADB_SHELL`, `ROOT_SHELL` enum stubs |
| 19 | Dead Code | — | `AppShell.java:191` | Commented-out `STDIN.write("exit\n")` block |
| 20 | Dead Code | — | `TerminalEmulator.java` | `LOG_ESCAPE_SEQUENCES = false` permanent dead-code constant |
| 21 | Bloat | — | `TermuxInstaller.java:117` | `ProgressDialog` — deprecated since API 26 |
| 22 | Bloat | — | `NotificationUtils.java:112` | `setDefaults()` deprecated since API 26 — existing TODO never resolved |
| 23 | Dead Code | — | `RunCommandService.java` | `LocalBinder`/`onBind()` unreachable — service never bound |
| 24 | Bloat | — | `app/models/`, `shared/termux/models/` | Duplicate `UserAction` enum types |
| 25 | Bloat | — | `TermuxShellManager.java` | `ArrayAdapter` import unused in code |
