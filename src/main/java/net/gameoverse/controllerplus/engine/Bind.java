package net.gameoverse.controllerplus.engine;

/**
 * One compiled advanced bind. Button ids are plain strings (Controlify input ids such as
 * {@code controlify:button/south}) so the engine has no Minecraft or Controlify dependency.
 *
 * @param id          unique within one engine; used to track toggles
 * @param type        trigger type
 * @param button      the trigger button (for LAYER, the button pressed while the modifier is held)
 * @param other       LAYER: the modifier; CHORD: the second button; otherwise unused
 * @param ticks       HOLD: ticks the button must be held; SCOPED (REPEAT mode): ticks before the first repeat
 * @param count       MULTI_TAP: number of presses
 * @param windowTicks MULTI_TAP: ticks allowed between a release and the next press; SCOPED, and any
 *                    REPEAT-mode bind: ticks between repeats
 * @param action      what to run (a Controlify binding id or one of this mod's action ids)
 * @param mode        how to run it
 * @param context     where the bind works (in game, or in inventory/recipe screens)
 */
public record Bind(int id, TriggerType type, String button, String other, int ticks, int count, int windowTicks,
                   String action, ActionMode mode, InputContext context) {

    public Bind {
        if (context == null) context = InputContext.GAME;
    }

    public static Bind tap(int id, String button, String action, ActionMode mode) {
        return new Bind(id, TriggerType.TAP, button, null, 0, 0, 0, action, mode, InputContext.GAME);
    }

    public static Bind hold(int id, String button, int ticks, String action, ActionMode mode) {
        return new Bind(id, TriggerType.HOLD, button, null, Math.max(1, ticks), 0, 0, action, mode, InputContext.GAME);
    }

    /** Same bind with another repeat interval (REPEAT mode). */
    public Bind withWindowTicks(int newWindowTicks) {
        return new Bind(id, type, button, other, ticks, count, Math.max(1, newWindowTicks), action, mode, context);
    }

    /** Same bind in another context. */
    public Bind in(InputContext newContext) {
        return new Bind(id, type, button, other, ticks, count, windowTicks, action, mode, newContext);
    }

    public static Bind multiTap(int id, String button, int count, int windowTicks, String action, ActionMode mode) {
        return new Bind(id, TriggerType.MULTI_TAP, button, null, 0, Math.max(2, count), Math.max(1, windowTicks), action, mode, InputContext.GAME);
    }

    public static Bind layer(int id, String modifier, String button, String action, ActionMode mode) {
        return new Bind(id, TriggerType.LAYER, button, modifier, 0, 0, 0, action, mode, InputContext.GAME);
    }

    /**
     * @param delayTicks    REPEAT mode: ticks before the first repeat
     * @param intervalTicks REPEAT mode: ticks between repeats
     */
    public static Bind scoped(int id, String button, int delayTicks, int intervalTicks, String action, ActionMode mode) {
        return new Bind(id, TriggerType.SCOPED, button, null, Math.max(1, delayTicks), 0, Math.max(1, intervalTicks), action, mode, InputContext.GAME);
    }

    public static Bind chord(int id, String a, String b, String action, ActionMode mode) {
        return new Bind(id, TriggerType.CHORD, a, b, 0, 0, 0, action, mode, InputContext.GAME);
    }
}
