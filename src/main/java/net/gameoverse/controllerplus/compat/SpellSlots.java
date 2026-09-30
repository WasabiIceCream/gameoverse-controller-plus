package net.gameoverse.controllerplus.compat;

import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.spell_engine.client.input.SpellHotbar;

/**
 * Spell Engine bridge. Only loaded when spell_engine is present.
 *
 * <p>Spell Engine decides per slot which KeyMapping casts it: the slot's own
 * {@code keybindings.spell_engine.spell_hotbar_N} key if it is bound to a keyboard key, otherwise
 * the key it defers to (here {@code key.hotbar.N}), with the first spell on the use key. It then
 * polls that KeyMapping's {@code isDown()}. Pressing Controlify's
 * {@code controlify_modded:keybindings.spell_engine.spell_hotbar_N} only helps when that spell key
 * is bound, so "spell slot N" here means the N-th spell on the bar that isn't on the use key (LT
 * already casts that one), and we hold down whichever KeyMapping Spell Engine resolved for it.
 * Only {@code isDown} is set, never a click, so a deferred {@code key.hotbar.N} never switches the
 * held item.
 */
public final class SpellSlots {
    private SpellSlots() {
    }

    /** The KeyMapping that casts spell slot {@code n} (1-based) right now, or null. */
    public static KeyMapping resolve(int n) {
        SpellHotbar bar = SpellHotbar.INSTANCE;
        if (bar == null || bar.structuredSlots == null || n < 1) return null;
        List<SpellHotbar.Slot> other = bar.structuredSlots.other();
        if (other == null || n > other.size()) return null;
        return other.get(n - 1).getKeyBinding(Minecraft.getInstance().options);
    }
}
