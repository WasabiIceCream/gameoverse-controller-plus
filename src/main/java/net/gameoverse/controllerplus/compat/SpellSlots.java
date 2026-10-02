package net.gameoverse.controllerplus.compat;

import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.internals.casting.SpellCast;

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
        SpellHotbar.Slot slot = slot(n);
        return slot == null ? null : slot.getKeyBinding(Minecraft.getInstance().options);
    }

    /** The name of what spell slot {@code n} (1-based) casts right now, or null if the slot is empty. */
    public static Component name(int n) {
        SpellHotbar.Slot slot = slot(n);
        if (slot == null) return null;
        SpellCast.Option option = slot.option();
        if (option != null && option.spell() != null && option.spell().unwrapKey().isPresent()) {
            return Component.translatable(SpellTooltip.spellTranslationKey(option.id()));
        }
        ItemStack stack = slot.itemStack();
        return stack != null && !stack.isEmpty() ? stack.getHoverName() : null;
    }

    /** How many spell slots (besides the use key's) the spell hotbar has right now. */
    public static int count() {
        SpellHotbar bar = SpellHotbar.INSTANCE;
        if (bar == null || bar.structuredSlots == null || bar.structuredSlots.other() == null) return 0;
        return bar.structuredSlots.other().size();
    }

    private static SpellHotbar.Slot slot(int n) {
        SpellHotbar bar = SpellHotbar.INSTANCE;
        if (bar == null || bar.structuredSlots == null || n < 1) return null;
        List<SpellHotbar.Slot> other = bar.structuredSlots.other();
        if (other == null || n > other.size()) return null;
        return other.get(n - 1);
    }
}
