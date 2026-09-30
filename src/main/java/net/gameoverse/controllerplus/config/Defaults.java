package net.gameoverse.controllerplus.config;

import static net.gameoverse.controllerplus.engine.ActionMode.HOLD_WHILE;
import static net.gameoverse.controllerplus.engine.ActionMode.PRESS;
import static net.gameoverse.controllerplus.engine.ActionMode.REPEAT;
import static net.gameoverse.controllerplus.engine.ActionMode.TOGGLE;

import java.util.ArrayList;
import java.util.List;
import net.gameoverse.controllerplus.engine.InputContext;
import net.gameoverse.controllerplus.engine.TriggerType;

/**
 * The shipped layout. Lives in code so every player picks up changes with a mod update, until
 * they customise their own binds (see {@link ControllerPlusConfig#customized}).
 */
public final class Defaults {
    public static final String BTN = "controlify:button/";
    public static final String SPELL_SLOT = "gameoverse_controller_plus:spell_slot_";
    /** One mouse-wheel notch up / down, through Minecraft's own scroll handler (spyglass and zoom mods listen there). */
    public static final String SCROLL_UP = "gameoverse_controller_plus:scroll_up";
    public static final String SCROLL_DOWN = "gameoverse_controller_plus:scroll_down";
    /** One click of vanilla's Drop key ({@code key.drop}): one item from the held stack. */
    public static final String DROP_ONE = "gameoverse_controller_plus:drop_one";
    /**
     * Spyglass Astronomy (1.0.3), while scoped: cycle its mode (normal, draw constellations, select
     * stars), hold to draw or select (its "attack" input; not in the defaults since 1.0.4, RT does it),
     * and show info on the selection ({@code /sga:info}). See {@code compat.SpyglassAstronomy}.
     */
    public static final String ASTRONOMY_MODE = "gameoverse_controller_plus:astronomy_mode";
    public static final String ASTRONOMY_USE = "gameoverse_controller_plus:astronomy_use";
    public static final String ASTRONOMY_INFO = "gameoverse_controller_plus:astronomy_info";
    /**
     * Prefix of "press the key bound to KeyMapping NAME", delivered to the open screen the way a real
     * key press is (Minecraft's KeyboardHandler, so JEI and other mods' screen key handlers see it).
     * NAME is the KeyMapping name, e.g. {@code key.jei.showRecipe}.
     */
    public static final String KEY_PRESS = "gameoverse_controller_plus:key_press/";
    public static final String JEI_SHOW_RECIPE = KEY_PRESS + "key.jei.showRecipe";
    public static final String JEI_SHOW_USES = KEY_PRESS + "key.jei.showUses";
    public static final String JEI_RECIPE_BACK = KEY_PRESS + "key.jei.recipeBack";

    /**
     * 1.0.3's default "B held while scoped = draw/select". Dropped in 1.0.4 (B is needed with the right
     * stick; RT already draws); config version 2 files that still hold it exactly get it removed.
     */
    static BindEntry legacyScopedB() {
        return BindEntry.of(TriggerType.SCOPED, BTN + "east", null, 300, 2, 100, ASTRONOMY_USE, HOLD_WHILE);
    }

    private Defaults() {
    }

    public static List<BindEntry> binds() {
        List<BindEntry> list = new ArrayList<>();
        String[] face = {"south", "west", "north", "east"}; // A, X, Y, B
        // Spell layers: LB + A/X/Y/B = spells 1-4, RB + A/X/Y/B = spells 5-8. Held for as long as
        // the face button is held, so charged and channelled spells work.
        for (int i = 0; i < 4; i++) {
            list.add(BindEntry.of(TriggerType.LAYER, BTN + face[i], BTN + "left_shoulder", 0, 2, 250,
                    SPELL_SLOT + (i + 1), HOLD_WHILE));
        }
        for (int i = 0; i < 4; i++) {
            list.add(BindEntry.of(TriggerType.LAYER, BTN + face[i], BTN + "right_shoulder", 0, 2, 250,
                    SPELL_SLOT + (i + 5), HOLD_WHILE));
        }
        // LB + RB together: Skill Forest.
        list.add(BindEntry.of(TriggerType.CHORD, BTN + "left_shoulder", BTN + "right_shoulder", 0, 2, 250,
                "controlify_modded:key.puffish_skills.open", PRESS));
        // Hold Back: Gameoverse Guide (tap keeps perspective).
        list.add(BindEntry.of(TriggerType.HOLD, BTN + "back", null, 450, 2, 250,
                "controlify_modded:key.oracle_index.open", PRESS));
        // Hold Y: World Tier (tap keeps inventory).
        list.add(BindEntry.of(TriggerType.HOLD, BTN + "north", null, 400, 2, 250,
                "controlify_modded:key.apotheosis.open_world_tier_select", PRESS));
        // D-up: tap opens the world map, hold toggles the spyglass (hold again to stop).
        list.add(BindEntry.of(TriggerType.TAP, BTN + "dpad_up", null, 0, 2, 250,
                "controlify_modded:mapstitch.key.open_world_map", PRESS));
        list.add(BindEntry.of(TriggerType.HOLD, BTN + "dpad_up", null, 250, 2, 250,
                "controlify_modded:key.spyglass-improvements.use", TOGGLE));
        // D-down: tap toggles crawling (Crawl's key is a hold key under Controlify, so the toggle
        // holds it down), hold drops one item, then one more every 150 ms while still held.
        list.add(BindEntry.of(TriggerType.TAP, BTN + "dpad_down", null, 0, 2, 250,
                "controlify_modded:key.crawl", TOGGLE));
        list.add(BindEntry.of(TriggerType.HOLD, BTN + "dpad_down", null, 250, 2, 150, DROP_ONE, REPEAT));
        // D-left: tap cycles the hotbar row, hold picks the block.
        list.add(BindEntry.of(TriggerType.TAP, BTN + "dpad_left", null, 0, 2, 250,
                "controlify_modded:key.hotbarslotcycling.cycle_left", PRESS));
        list.add(BindEntry.of(TriggerType.HOLD, BTN + "dpad_left", null, 250, 2, 250,
                "controlify:pick_block", PRESS));
        // While scoped (spyglass, Spyglass Improvements, Ok Zoomer): RB zooms in, LB zooms out, one
        // wheel notch per press, repeating after 300 ms every 100 ms. LB/RB do nothing else then.
        // (Repeat mode: until 1.0.2 a Scoped bind in Press mode repeated; now Press fires once.)
        list.add(BindEntry.of(TriggerType.SCOPED, BTN + "right_shoulder", null, 300, 2, 100, SCROLL_UP, REPEAT));
        list.add(BindEntry.of(TriggerType.SCOPED, BTN + "left_shoulder", null, 300, 2, 100, SCROLL_DOWN, REPEAT));
        // While scoped, Y and X drive Spyglass Astronomy: Y cycles its mode, X shows the selection's
        // info (and stops swapping hands while scoped: Controlify's swap_hands is on X). Drawing and
        // selecting is RT (Controlify's attack, which Spyglass Astronomy reads); A jumps and B rolls.
        // (1.0.3 also had B held = draw/select; 1.0.4 dropped it, see legacyScopedB().)
        list.add(BindEntry.of(TriggerType.SCOPED, BTN + "north", null, 300, 2, 100, ASTRONOMY_MODE, PRESS));
        list.add(BindEntry.of(TriggerType.SCOPED, BTN + "west", null, 300, 2, 100, ASTRONOMY_INFO, PRESS));
        // Inventory/container screens and JEI's recipe screen: RS shows the recipes for the item under
        // the cursor, held RS its uses (JEI's R / U keys). RS does nothing in Controlify's screens.
        list.add(BindEntry.of(TriggerType.TAP, BTN + "right_stick", null, 0, 2, 250, JEI_SHOW_RECIPE, PRESS)
                .in(InputContext.SCREEN));
        list.add(BindEntry.of(TriggerType.HOLD, BTN + "right_stick", null, 300, 2, 250, JEI_SHOW_USES, PRESS)
                .in(InputContext.SCREEN));
        // JEI's recipe screen: Y goes back to the previously shown recipes (B still closes it).
        list.add(BindEntry.of(TriggerType.TAP, BTN + "north", null, 0, 2, 250, JEI_RECIPE_BACK, PRESS)
                .in(InputContext.RECIPE_SCREEN));
        return list;
    }
}
