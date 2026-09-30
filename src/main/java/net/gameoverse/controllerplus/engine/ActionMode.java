package net.gameoverse.controllerplus.engine;

/** What the action does when its trigger fires. */
public enum ActionMode {
    /** A short press, like tapping the bound key once. */
    PRESS,
    /** Held down for as long as the trigger's button stays held (charged spells, zoom). */
    HOLD_WHILE,
    /** Flips between held and released each time the trigger fires. */
    TOGGLE
}
