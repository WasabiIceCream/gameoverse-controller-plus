package net.gameoverse.controllerplus.engine;

/**
 * One compiled advanced bind. Button ids are plain strings (Controlify input ids such as
 * {@code controlify:button/south}) so the engine has no Minecraft or Controlify dependency.
 *
 * @param id          unique within one engine; used to track toggles
 * @param type        trigger type
 * @param button      the trigger button (for LAYER, the button pressed while the modifier is held)
 * @param other       LAYER: the modifier; CHORD: the second button; otherwise unused
 * @param ticks       HOLD: ticks the button must be held; SCOPED: ticks before the first repeat
 * @param count       MULTI_TAP: number of presses
 * @param windowTicks MULTI_TAP: ticks allowed between a release and the next press; SCOPED: ticks
 *                    between repeats
 * @param action      what to run (a Controlify binding id or one of this mod's action ids)
 * @param mode        how to run it
 */
public record Bind(int id, TriggerType type, String button, String other, int ticks, int count, int windowTicks,
                   String action, ActionMode mode) {

    public static Bind tap(int id, String button, String action, ActionMode mode) {
        return new Bind(id, TriggerType.TAP, button, null, 0, 0, 0, action, mode);
    }

    public static Bind hold(int id, String button, int ticks, String action, ActionMode mode) {
        return new Bind(id, TriggerType.HOLD, button, null, Math.max(1, ticks), 0, 0, action, mode);
    }

    public static Bind multiTap(int id, String button, int count, int windowTicks, String action, ActionMode mode) {
        return new Bind(id, TriggerType.MULTI_TAP, button, null, 0, Math.max(2, count), Math.max(1, windowTicks), action, mode);
    }

    public static Bind layer(int id, String modifier, String button, String action, ActionMode mode) {
        return new Bind(id, TriggerType.LAYER, button, modifier, 0, 0, 0, action, mode);
    }

    public static Bind scoped(int id, String button, int delayTicks, int intervalTicks, String action, ActionMode mode) {
        return new Bind(id, TriggerType.SCOPED, button, null, Math.max(1, delayTicks), 0, Math.max(1, intervalTicks), action, mode);
    }

    public static Bind chord(int id, String a, String b, String action, ActionMode mode) {
        return new Bind(id, TriggerType.CHORD, a, b, 0, 0, 0, action, mode);
    }
}
