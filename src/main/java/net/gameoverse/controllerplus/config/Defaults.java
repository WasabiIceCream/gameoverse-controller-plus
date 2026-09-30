package net.gameoverse.controllerplus.config;

import static net.gameoverse.controllerplus.engine.ActionMode.HOLD_WHILE;
import static net.gameoverse.controllerplus.engine.ActionMode.PRESS;
import static net.gameoverse.controllerplus.engine.ActionMode.REPEAT;
import static net.gameoverse.controllerplus.engine.ActionMode.TOGGLE;

import java.util.ArrayList;
import java.util.List;
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
        list.add(BindEntry.of(TriggerType.SCOPED, BTN + "right_shoulder", null, 300, 2, 100, SCROLL_UP, PRESS));
        list.add(BindEntry.of(TriggerType.SCOPED, BTN + "left_shoulder", null, 300, 2, 100, SCROLL_DOWN, PRESS));
        return list;
    }
}
