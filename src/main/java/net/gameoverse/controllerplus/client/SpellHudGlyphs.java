package net.gameoverse.controllerplus.client;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.InputComponent;
import dev.isxander.controlify.font.BindingFontHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.gameoverse.controllerplus.compat.SpellSlots;
import net.gameoverse.controllerplus.config.Defaults;
import net.gameoverse.controllerplus.engine.Bind;
import net.gameoverse.controllerplus.engine.TriggerType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller glyphs on Spell Engine's spell hotbar. Spell Engine labels each slot with its keyboard key
 * ({@code HudRenderHelper.SpellHotBarWidget.KeyBindingViewModel.from}: a texture for mouse buttons, else the
 * key's name as text). With a controller in use, a slot's label becomes the buttons that cast it:
 * <ul>
 *   <li>the slot on the use key (an item's own use, e.g. a shield, or a spell on use): Controlify's use binding;</li>
 *   <li>spell slot n: our {@code spell_slot_n} bind (LB/RB + a face button by default);</li>
 *   <li>any other key: the Controlify binding that emulates it, if bound.</li>
 * </ul>
 * The label is a marker string; {@link #draw} renders the glyph component in Spell Engine's
 * {@code drawKeybinding}, which only knows plain text.
 */
public final class SpellHudGlyphs {
    private static final Logger LOG = LoggerFactory.getLogger("gameoverse_controller_plus");
    public static final boolean SPELL_ENGINE = FabricLoader.getInstance().isModLoaded("spell_engine");
    private static final String MARK = "gcp:";
    private static final int SPELL_SLOTS = 8;
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static boolean failed;
    /** Centre x of the first slot of the layer group being drawn (slots are drawn left to right). */
    private static int groupStartX;

    /**
     * One slot's label. A spell slot on a layer bind shows only its face button; the layer's modifier
     * (LB/RB) is drawn once above its group of consecutive slots, by the group's last slot.
     */
    private record Entry(Component glyph, Component header, boolean groupStart, boolean groupEnd) {
        static Entry single(Component glyph) {
            return new Entry(glyph, null, false, false);
        }
    }

    private SpellHudGlyphs() {
    }

    /** A marker label for this key's controller glyph, or null to keep Spell Engine's own label. */
    public static String label(KeyMapping key) {
        if (failed || key == null || !SPELL_ENGINE) return null;
        try {
            ControlifyApi api = ControlifyApi.get();
            if (!api.currentInputMode().isController()) return null;
            ControllerEntity controller = api.getCurrentController().orElse(null);
            if (controller == null) return null;
            Entry entry = entry(key, controller);
            if (entry == null) return null;
            String label = MARK + key.getName();
            ENTRIES.put(label, entry);
            return label;
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOG.error("Controller glyphs on Spell Engine's spell hotbar failed; it shows keyboard keys", e);
            return null;
        }
    }

    private static Entry entry(KeyMapping key, ControllerEntity controller) {
        if (key == Minecraft.getInstance().options.keyUse) return single(bindingGlyph("controlify:use", controller));
        int count = SpellSlots.count();
        for (int n = 1; n <= Math.min(count, SPELL_SLOTS); n++) {
            if (SpellSlots.resolve(n) != key) continue;
            Bind bind = slotBind(n);
            if (bind == null) return null;
            if (bind.type() != TriggerType.LAYER) return Entry.single(buttons(bind, controller));
            Bind prev = n > 1 ? slotBind(n - 1) : null;
            Bind next = n < Math.min(count, SPELL_SLOTS) ? slotBind(n + 1) : null;
            return new Entry(glyph(bind.button(), controller), glyph(bind.other(), controller),
                    !sameLayer(prev, bind), !sameLayer(next, bind));
        }
        return single(bindingGlyph("controlify_modded:" + key.getName(), controller));
    }

    private static Entry single(Component glyph) {
        return glyph == null ? null : Entry.single(glyph);
    }

    private static Bind slotBind(int n) {
        return ControllerPlus.get().bindFor(Defaults.SPELL_SLOT + n);
    }

    private static boolean sameLayer(Bind other, Bind bind) {
        return other != null && other.type() == TriggerType.LAYER && bind.other().equals(other.other());
    }

    private static Component glyph(String buttonId, ControllerEntity controller) {
        Identifier button = Identifier.tryParse(buttonId);
        if (button == null) return Component.empty();
        return Controlify.instance().inputFontMapper().getComponentFromInputs(controller.info().type().namespace(), List.of(button));
    }

    private static Component bindingGlyph(String id, ControllerEntity controller) {
        Identifier bid = Identifier.tryParse(id);
        InputComponent input = controller.input().orElse(null);
        InputBinding binding = bid == null || input == null ? null : input.getBinding(bid);
        return binding == null || binding.isUnbound() ? null : binding.inputGlyph();
    }

    /** The bind's buttons as glyphs: modifier + button for a layer, both buttons for a chord. */
    private static Component buttons(Bind bind, ControllerEntity controller) {
        List<String> ids = switch (bind.type()) {
            case LAYER -> List.of(bind.other(), bind.button());
            case CHORD -> List.of(bind.button(), bind.other());
            default -> List.of(bind.button());
        };
        Identifier ns = controller.info().type().namespace();
        MutableComponent out = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) out.append("+");
            Identifier button = Identifier.tryParse(ids.get(i));
            if (button != null) out.append(Controlify.instance().inputFontMapper().getComponentFromInputs(ns, List.of(button)));
        }
        return out;
    }

    /**
     * Draws a marker label's glyph where Spell Engine would draw that label: {@code x} is the left edge
     * (LEADING), right edge (TRAILING) or centre; {@code vTrailing} puts the bottom at {@code y}. A layer
     * group's last slot also draws the modifier centred above the group. False if the label isn't ours.
     */
    public static boolean draw(GuiGraphicsExtractor context, Font font, String label, int x, int y,
                               boolean hLeading, boolean hTrailing, boolean vTrailing) {
        if (label == null || !label.startsWith(MARK)) return false;
        Entry entry = ENTRIES.get(label);
        if (entry == null) return true;
        int width = font.width(entry.glyph());
        int height = BindingFontHelper.getComponentHeight(font, entry.glyph());
        int left = hLeading ? x : hTrailing ? x - width : x - width / 2;
        int top = vTrailing ? y - height : y - height / 2;
        context.text(font, entry.glyph(), left, top, -1, false);
        int centre = left + width / 2;
        if (entry.groupStart()) groupStartX = centre;
        if (entry.groupEnd() && entry.header() != null) {
            int headerWidth = font.width(entry.header());
            int headerHeight = BindingFontHelper.getComponentHeight(font, entry.header());
            int middle = (groupStartX + centre) / 2;
            context.text(font, entry.header(), middle - headerWidth / 2, top - headerHeight - 1, -1, false);
        }
        return true;
    }
}
