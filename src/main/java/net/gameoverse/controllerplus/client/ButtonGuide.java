package net.gameoverse.controllerplus.client;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.contextual.Context;
import dev.isxander.controlify.contextual.GuideRule;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.InputComponent;
import dev.isxander.controlify.font.BindingFontHelper;
import dev.isxander.controlify.font.InputFontMapper;
import dev.isxander.controlify.gui.guide.PrecomputedLines;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.gameoverse.controllerplus.compat.SpellSlots;
import net.gameoverse.controllerplus.config.BindEntry;
import net.gameoverse.controllerplus.config.Defaults;
import net.gameoverse.controllerplus.engine.GuidePlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Our part of Controlify's in-game button guide ("Show in-game button guide"). Called from
 * {@code GuideInstanceImplMixin} on each guide update (once a tick while the guide is on):
 * <ol>
 *   <li>{@link #filter}: drops Controlify's own entries for buttons whose meaning our binds change
 *       right now (D-down's Drop, whose tap now crawls; A/X/Y/B while LB is held; LB/RB while
 *       scoped).</li>
 *   <li>{@link #extend}: adds our lines, laid out the way Controlify lays out its own: glyph then text
 *       on the left, text then glyph on the right. Chords get two glyphs joined by "+".</li>
 * </ol>
 * What to show is decided by {@link GuidePlanner}; this class supplies the game facts, the labels
 * (spell names from Spell Engine) and the glyphs (Controlify's per-controller input font).
 */
public final class ButtonGuide {
    private static final Logger LOG = LoggerFactory.getLogger(ControllerPlus.MOD_ID);
    private static final Identifier IN_GAME = Identifier.fromNamespaceAndPath("controlify", "in_game");
    private static final String K = "gameoverse_controller_plus.guide.";
    private static final int GLYPH_HEIGHT = 15;

    private static final boolean SPELL_ENGINE = FabricLoader.getInstance().isModLoaded("spell_engine");
    private static boolean failed;
    private static boolean announced;
    /** The plan made by the last {@link #filter} call, used by the {@link #extend} call of the same update. */
    private static GuidePlanner.Plan lastPlan;
    private static ControllerEntity lastController;
    /** Guide updates so far, and when each modifier was first seen held (so a quick LB tap doesn't flash the layer list). */
    private static long updates;
    private static final Map<String, Long> heldSince = new HashMap<>();
    /** Updates (ticks) a modifier must be held before the guide switches to its layer. */
    private static final int LAYER_DELAY = 4;

    private ButtonGuide() {
    }

    static void invalidate() {
        lastPlan = null;
        lastController = null;
    }

    /** Controlify's winning guide rules for this update, minus the ones our binds make wrong. */
    public static List<GuideRule> filter(List<GuideRule> rules, Identifier domainId, Context context) {
        lastPlan = null;
        if (failed || rules == null || !IN_GAME.equals(domainId)) return rules;
        updates++;
        try {
            GuidePlanner planner = ControllerPlus.get().guidePlanner();
            ControllerEntity controller = context.controller();
            if (planner == null || controller == null) return rules;
            GuidePlanner.Plan plan = planner.plan(new Facts(controller, context.verbosity().getLevel()));
            lastPlan = plan;
            lastController = controller;
            if (!announced) {
                announced = true;
                LOG.info("Controlify in-game button guide: showing advanced binds");
            }
            if (plan.overridden().isEmpty()) return rules;
            List<GuideRule> kept = new ArrayList<>(rules.size());
            for (GuideRule rule : rules) {
                InputBinding binding = rule.binding().onOrNull(controller);
                if (binding != null && isOverridden(binding, plan)) continue;
                kept.add(rule);
            }
            return kept;
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return rules;
        }
    }

    /**
     * Controlify's lines with ours appended, as {@code [left, right]}, or null to keep Controlify's
     * lines unchanged.
     */
    public static PrecomputedLines[] extend(PrecomputedLines left, PrecomputedLines right, Font font, Identifier domainId) {
        GuidePlanner.Plan plan = lastPlan;
        ControllerEntity controller = lastController;
        lastPlan = null;
        if (failed || plan == null || controller == null || !IN_GAME.equals(domainId) || plan.entries().isEmpty()) return null;
        try {
            PrecomputedLines.Builder l = copy(left);
            PrecomputedLines.Builder r = copy(right);
            Identifier ns = controller.info().type().namespace();
            InputFontMapper fonts = Controlify.instance().inputFontMapper();
            for (GuidePlanner.Entry e : plan.entries()) {
                Component label = label(e, controller);
                if (label == null) continue;
                MutableComponent glyph = Component.empty();
                for (int i = 0; i < e.buttons().size(); i++) {
                    if (i > 0) glyph.append("+");
                    Identifier button = Identifier.tryParse(e.buttons().get(i));
                    if (button != null) glyph.append(fonts.getComponentFromInputs(ns, List.of(button)));
                }
                boolean onRight = e.side() == GuidePlanner.Side.RIGHT;
                addLine(onRight ? r : l, font, glyph, label, onRight);
            }
            return new PrecomputedLines[]{l.build(), r.build()};
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return null;
        }
    }

    private static boolean isOverridden(InputBinding binding, GuidePlanner.Plan plan) {
        List<Identifier> inputs = binding.boundInput().getRelevantInputs();
        return inputs.size() == 1 && plan.overridden().contains(inputs.getFirst().toString());
    }

    private static PrecomputedLines.Builder copy(PrecomputedLines lines) {
        PrecomputedLines.Builder b = new PrecomputedLines.Builder();
        if (lines == null) return b;
        for (PrecomputedLines.PrecomputedLine line : lines.lines()) {
            b.addLine(line.text(), line.width(), line.height(), line.backgroundLeft(), line.backgroundRight());
        }
        return b;
    }

    /** Same layout as Controlify's own lines: the text box sits next to the glyph, on the screen-inner side. */
    private static void addLine(PrecomputedLines.Builder builder, Font font, Component glyph, Component label, boolean right) {
        boolean glyphAfter = font.isBidirectional() ^ right;
        Component text = Component.empty()
                .append(glyphAfter ? label : glyph)
                .append(" ")
                .append(glyphAfter ? glyph : label);
        int labelWidth = font.width(label);
        int width = font.width(text);
        int backgroundLeft = glyphAfter ? 0 : width - labelWidth;
        int height = Math.max(GLYPH_HEIGHT, BindingFontHelper.getComponentHeight(font, glyph));
        builder.addLine(text, width, height, backgroundLeft, backgroundLeft + labelWidth);
    }

    private static Component label(GuidePlanner.Entry e, ControllerEntity controller) {
        int spell = ActionDriver.spellSlot(e.action());
        Component name;
        if (spell > 0) {
            if (e.form() == GuidePlanner.Form.LAYER_SUMMARY) {
                name = Component.translatable(K + (spell <= 4 ? "spells" : "more_spells"));
            } else {
                name = SPELL_ENGINE ? SpellSlots.name(spell) : null;
                if (name == null) return null;
            }
        } else {
            name = actionName(e.action(), controller);
            Identifier id = ControllerPlus.get().id(e.action());
            if (id != null && Hooks.isForced(id)) name = Component.translatable(K + "stop", name);
        }
        return switch (e.form()) {
            case HOLD, LAYER_SUMMARY -> Component.translatable(K + "hold", name);
            case MULTI_TAP -> Component.translatable(K + "multi_tap", e.count(), name);
            default -> name;
        };
    }

    /** Our short name if we have one, else the Controlify binding's name, else the id. */
    private static Component actionName(String action, ControllerEntity controller) {
        String key = K + "action." + action.replace(':', '.');
        if (Language.getInstance().has(key)) return Component.translatable(key);
        InputBinding binding = binding(action, controller);
        if (binding != null) return binding.name();
        return Component.literal(BindEntry.shortName(action));
    }

    private static InputBinding binding(String action, ControllerEntity controller) {
        Identifier id = ControllerPlus.get().id(action);
        if (id == null) return null;
        InputComponent input = controller.input().orElse(null);
        return input == null ? null : input.getBinding(id);
    }

    private static void fail(Throwable e) {
        failed = true;
        LOG.error("Controlify in-game button guide integration failed; the guide shows Controlify's own entries only", e);
    }

    /** Game facts for the planner. */
    private record Facts(ControllerEntity controller, int verbosity) implements GuidePlanner.Context {
        @Override
        public boolean scoped() {
            Minecraft mc = Minecraft.getInstance();
            return mc.screen == null && Scoping.isScoped(mc);
        }

        @Override
        public boolean isDown(String button) {
            Identifier id = ControllerPlus.get().id(button);
            InputComponent input = controller.input().orElse(null);
            boolean down = id != null && input != null && input.stateNow().isButtonDown(id);
            if (!down) {
                heldSince.remove(button);
                return false;
            }
            return updates - heldSince.computeIfAbsent(button, b -> updates) >= LAYER_DELAY;
        }

        @Override
        public int level(String action) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return 0;
            int spell = ActionDriver.spellSlot(action);
            if (spell > 0) return SPELL_ENGINE && SpellSlots.name(spell) != null ? 2 : 0;
            if (Defaults.SCROLL_UP.equals(action) || Defaults.SCROLL_DOWN.equals(action)) return 1;
            if (Defaults.DROP_ONE.equals(action) || action.equals("controlify:drop") || action.equals("controlify:drop_stack")) {
                return player.getMainHandItem().isEmpty() ? 0 : 2;
            }
            if (action.startsWith("gameoverse_controller_plus:")) return 3;
            Identifier id = ControllerPlus.get().id(action);
            if (id == null || binding(action, controller) == null) return 0; // mod not installed
            if (Hooks.isForced(id)) return 1; // toggled on: show how to turn it off
            return switch (action) {
                case "controlify_modded:key.spyglass-improvements.use" ->
                        player.getInventory().contains(s -> s.is(Items.SPYGLASS)) ? 2 : 3;
                case "controlify_modded:key.puffish_skills.open",
                     "controlify_modded:key.oracle_index.open",
                     "controlify_modded:key.apotheosis.open_world_tier_select" -> 2;
                default -> 3;
            };
        }
    }
}
