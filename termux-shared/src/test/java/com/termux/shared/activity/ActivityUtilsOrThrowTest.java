package com.termux.shared.activity;

import static org.junit.Assert.assertThrows;

import android.app.Activity;
import android.content.Intent;

import com.termux.shared.errors.TermuxException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Unit tests for the startActivity*OrThrow sibling methods added for beads-hg1 (ActivityErrno
 * migration, part of the beads-xs0 Errno/Error deprecate-first migration family).
 */
@RunWith(RobolectricTestRunner.class)
public class ActivityUtilsOrThrowTest {

    @Test
    public void startActivityOrThrow_doesNotThrowForValidActivityIntent() throws TermuxException {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Activity.class);
        // Starting an activity from a non-Activity (Application) context requires this flag on
        // real Android too, not just Robolectric.
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityUtils.startActivityOrThrow(RuntimeEnvironment.getApplication(), intent);
    }

    @Test
    public void startActivityOrThrow_throwsWhenContextIsNull() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Activity.class);
        assertThrows(TermuxException.class, () ->
            ActivityUtils.startActivityOrThrow(null, intent));
    }

    @Test
    public void startActivityForResultOrThrow_throwsWhenContextIsNull() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Activity.class);
        assertThrows(TermuxException.class, () ->
            ActivityUtils.startActivityForResultOrThrow(null, 1, intent));
    }

    @Test
    public void startActivityForResultOrThrow_throwsWhenContextIsNotAnActivity() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Activity.class);
        // The Application context is a valid Context but not an Activity/AppCompatActivity.
        assertThrows(TermuxException.class, () ->
            ActivityUtils.startActivityForResultOrThrow(RuntimeEnvironment.getApplication(), 1, intent));
    }

    @Test
    public void startActivityForResultOrThrow_doesNotThrowWhenContextIsAnActivity() throws TermuxException {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        Intent intent = new Intent(activity, Activity.class);

        ActivityUtils.startActivityForResultOrThrow(activity, 1, intent);
    }
}
