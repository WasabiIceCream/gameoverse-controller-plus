package net.gameoverse.controllerplus.config;

import java.util.Objects;
import net.gameoverse.controllerplus.engine.ActionMode;
import net.gameoverse.controllerplus.engine.Bind;
import net.gameoverse.controllerplus.engine.TriggerType;

/**
 * One advanced bind as stored in the config file and edited on the config screen. Mutable on
 * purpose (the screen edits it in place); {@link #compile(int)} turns it into an engine {@link Bind}.
 */
public final class BindEntry {
    public boolean enabled = true;
    public TriggerType type = TriggerType.HOLD;
    /** Trigger button (for LAYER: the button pressed while the modifier is held; CHORD: first button). */
    public String button = "controlify:button/back";
    /** LAYER: the modifier button. CHORD: the second button. Ignored otherwise. */
    public String modifier = "controlify:button/left_shoulder";
    /** HOLD: how long to hold. */
    public int ms = 400;
    /** MULTI_TAP: number of presses. */
    public int count = 2;
    /** MULTI_TAP: longest gap between a release and the next press. */
    public int windowMs = 250;
    /** Controlify binding id, or one of this mod's action ids. */
    public String action = "controlify:jump";
    public ActionMode mode = ActionMode.PRESS;

    public BindEntry() {
    }

    public static BindEntry of(TriggerType type, String button, String modifier, int ms, int count, int windowMs,
                               String action, ActionMode mode) {
        BindEntry e = new BindEntry();
        e.type = type;
        e.button = button;
        e.modifier = modifier;
        e.ms = ms;
        e.count = count;
        e.windowMs = windowMs;
        e.action = action;
        e.mode = mode;
        return e;
    }

    public BindEntry copy() {
        BindEntry e = of(type, button, modifier, ms, count, windowMs, action, mode);
        e.enabled = enabled;
        return e;
    }

    public static int msToTicks(int ms) {
        return Math.max(1, Math.round(ms / 50f));
    }

    /** Null when the entry is disabled or incomplete. */
    public Bind compile(int id) {
        if (!enabled || type == null || mode == null || isBlank(button) || isBlank(action)) return null;
        return switch (type) {
            case TAP -> Bind.tap(id, button, action, mode);
            case HOLD -> Bind.hold(id, button, msToTicks(ms), action, mode);
            case MULTI_TAP -> Bind.multiTap(id, button, count, msToTicks(windowMs), action, mode);
            case LAYER -> isBlank(modifier) || modifier.equals(button) ? null : Bind.layer(id, modifier, button, action, mode);
            case CHORD -> isBlank(modifier) || modifier.equals(button) ? null : Bind.chord(id, button, modifier, action, mode);
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Short human-readable summary for the config screen, e.g. "hold back 450 ms". */
    public String summary() {
        String b = shortName(button);
        String m = shortName(modifier);
        String trigger = switch (type == null ? TriggerType.TAP : type) {
            case TAP -> "tap " + b;
            case HOLD -> "hold " + b + " " + ms + " ms";
            case MULTI_TAP -> count + "x tap " + b;
            case LAYER -> m + " + " + b;
            case CHORD -> b + " & " + m;
        };
        return trigger + " -> " + shortName(action);
    }

    public static String shortName(String id) {
        if (id == null) return "?";
        int slash = id.lastIndexOf('/');
        if (slash >= 0) return id.substring(slash + 1);
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BindEntry e)) return false;
        return enabled == e.enabled && type == e.type && Objects.equals(button, e.button)
                && (type != TriggerType.LAYER && type != TriggerType.CHORD || Objects.equals(modifier, e.modifier))
                && (type != TriggerType.HOLD || ms == e.ms)
                && (type != TriggerType.MULTI_TAP || count == e.count && windowMs == e.windowMs)
                && Objects.equals(action, e.action) && mode == e.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, type, button, action, mode);
    }
}
