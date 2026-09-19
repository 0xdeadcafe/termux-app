package com.termux.shared.termux.settings.properties;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit tests verifying the {@code Map}s in {@link TermuxPropertyConstants} converted from
 * Guava's {@code ImmutableBiMap} (beads-e6w) still contain the exact same key/value pairs.
 */
public class TermuxPropertyConstantsTest {

    @Test
    public void mapBellBehaviour_hasExpectedEntries() {
        assertEquals(3, TermuxPropertyConstants.MAP_BELL_BEHAVIOUR.size());
        assertEquals(Integer.valueOf(TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_VIBRATE),
            TermuxPropertyConstants.MAP_BELL_BEHAVIOUR.get(TermuxPropertyConstants.VALUE_BELL_BEHAVIOUR_VIBRATE));
        assertEquals(Integer.valueOf(TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_BEEP),
            TermuxPropertyConstants.MAP_BELL_BEHAVIOUR.get(TermuxPropertyConstants.VALUE_BELL_BEHAVIOUR_BEEP));
        assertEquals(Integer.valueOf(TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_IGNORE),
            TermuxPropertyConstants.MAP_BELL_BEHAVIOUR.get(TermuxPropertyConstants.VALUE_BELL_BEHAVIOUR_IGNORE));
    }

    @Test
    public void mapTerminalCursorStyle_hasExpectedEntries() {
        assertEquals(3, TermuxPropertyConstants.MAP_TERMINAL_CURSOR_STYLE.size());
        assertEquals(Integer.valueOf(TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_STYLE_BLOCK),
            TermuxPropertyConstants.MAP_TERMINAL_CURSOR_STYLE.get(TermuxPropertyConstants.VALUE_TERMINAL_CURSOR_STYLE_BLOCK));
        assertEquals(Integer.valueOf(TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_STYLE_UNDERLINE),
            TermuxPropertyConstants.MAP_TERMINAL_CURSOR_STYLE.get(TermuxPropertyConstants.VALUE_TERMINAL_CURSOR_STYLE_UNDERLINE));
        assertEquals(Integer.valueOf(TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_STYLE_BAR),
            TermuxPropertyConstants.MAP_TERMINAL_CURSOR_STYLE.get(TermuxPropertyConstants.VALUE_TERMINAL_CURSOR_STYLE_BAR));
    }

    @Test
    public void mapSessionShortcuts_hasExpectedEntries() {
        assertEquals(4, TermuxPropertyConstants.MAP_SESSION_SHORTCUTS.size());
        assertEquals(Integer.valueOf(TermuxPropertyConstants.ACTION_SHORTCUT_CREATE_SESSION),
            TermuxPropertyConstants.MAP_SESSION_SHORTCUTS.get(TermuxPropertyConstants.KEY_SHORTCUT_CREATE_SESSION));
        assertEquals(Integer.valueOf(TermuxPropertyConstants.ACTION_SHORTCUT_NEXT_SESSION),
            TermuxPropertyConstants.MAP_SESSION_SHORTCUTS.get(TermuxPropertyConstants.KEY_SHORTCUT_NEXT_SESSION));
        assertEquals(Integer.valueOf(TermuxPropertyConstants.ACTION_SHORTCUT_PREVIOUS_SESSION),
            TermuxPropertyConstants.MAP_SESSION_SHORTCUTS.get(TermuxPropertyConstants.KEY_SHORTCUT_PREVIOUS_SESSION));
        assertEquals(Integer.valueOf(TermuxPropertyConstants.ACTION_SHORTCUT_RENAME_SESSION),
            TermuxPropertyConstants.MAP_SESSION_SHORTCUTS.get(TermuxPropertyConstants.KEY_SHORTCUT_RENAME_SESSION));
    }

    @Test
    public void mapBackKeyBehaviour_hasExpectedEntries() {
        assertEquals(2, TermuxPropertyConstants.MAP_BACK_KEY_BEHAVIOUR.size());
        assertEquals(TermuxPropertyConstants.IVALUE_BACK_KEY_BEHAVIOUR_BACK,
            TermuxPropertyConstants.MAP_BACK_KEY_BEHAVIOUR.get(TermuxPropertyConstants.IVALUE_BACK_KEY_BEHAVIOUR_BACK));
        assertEquals(TermuxPropertyConstants.IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE,
            TermuxPropertyConstants.MAP_BACK_KEY_BEHAVIOUR.get(TermuxPropertyConstants.IVALUE_BACK_KEY_BEHAVIOUR_ESCAPE));
    }

    @Test
    public void mapNightMode_hasExpectedEntries() {
        assertEquals(3, TermuxPropertyConstants.MAP_NIGHT_MODE.size());
        assertEquals(TermuxPropertyConstants.IVALUE_NIGHT_MODE_TRUE,
            TermuxPropertyConstants.MAP_NIGHT_MODE.get(TermuxPropertyConstants.IVALUE_NIGHT_MODE_TRUE));
        assertEquals(TermuxPropertyConstants.IVALUE_NIGHT_MODE_FALSE,
            TermuxPropertyConstants.MAP_NIGHT_MODE.get(TermuxPropertyConstants.IVALUE_NIGHT_MODE_FALSE));
        assertEquals(TermuxPropertyConstants.IVALUE_NIGHT_MODE_SYSTEM,
            TermuxPropertyConstants.MAP_NIGHT_MODE.get(TermuxPropertyConstants.IVALUE_NIGHT_MODE_SYSTEM));
    }

    @Test
    public void mapSoftKeyboardToggleBehaviour_hasExpectedEntries() {
        assertEquals(2, TermuxPropertyConstants.MAP_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR.size());
        assertEquals(TermuxPropertyConstants.IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE,
            TermuxPropertyConstants.MAP_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR.get(TermuxPropertyConstants.IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_SHOW_HIDE));
        assertEquals(TermuxPropertyConstants.IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE,
            TermuxPropertyConstants.MAP_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR.get(TermuxPropertyConstants.IVALUE_SOFT_KEYBOARD_TOGGLE_BEHAVIOUR_ENABLE_DISABLE));
    }

    @Test
    public void mapVolumeKeysBehaviour_hasExpectedEntries() {
        assertEquals(2, TermuxPropertyConstants.MAP_VOLUME_KEYS_BEHAVIOUR.size());
        assertEquals(TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL,
            TermuxPropertyConstants.MAP_VOLUME_KEYS_BEHAVIOUR.get(TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL));
        assertEquals(TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME,
            TermuxPropertyConstants.MAP_VOLUME_KEYS_BEHAVIOUR.get(TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME));
    }

}
