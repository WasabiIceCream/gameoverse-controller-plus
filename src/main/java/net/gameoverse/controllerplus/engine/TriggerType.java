package net.gameoverse.controllerplus.engine;

/** How an advanced bind is triggered. */
public enum TriggerType {
    /** A short press of {@code button}, fired on release (or on press when the button has no hold or multi-tap bind). */
    TAP,
    /** {@code button} held for {@code ticks}. */
    HOLD,
    /** {@code button} pressed {@code count} times, each within {@code windowTicks} of the last release. */
    MULTI_TAP,
    /** {@code other} (the modifier) held, then {@code button} pressed. */
    LAYER,
    /** {@code button} and {@code other} held together, in either order. */
    CHORD,
    /**
     * {@code button} pressed while the player is scoped (spyglass or zoom). Fires on press, then
     * repeats every {@code windowTicks} after {@code ticks} while held (PRESS mode). While scoped the
     * button does nothing else: its other binds, layers and normal Controlify bindings are held back.
     */
    SCOPED
}
