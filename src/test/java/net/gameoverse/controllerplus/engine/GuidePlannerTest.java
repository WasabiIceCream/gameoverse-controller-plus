package net.gameoverse.controllerplus.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GuidePlannerTest {
    static final String A = "a", X = "x", Y = "y", LB = "lb", RB = "rb", BACK = "back", UP = "dpad_up", DOWN = "dpad_down";

    /** A cut-down shipped layout. */
    static final List<Bind> LAYOUT = List.of(
            Bind.layer(0, LB, A, "spell_1", ActionMode.HOLD_WHILE),
            Bind.layer(1, LB, X, "spell_2", ActionMode.HOLD_WHILE),
            Bind.layer(2, RB, A, "spell_5", ActionMode.HOLD_WHILE),
            Bind.chord(3, LB, RB, "skills", ActionMode.PRESS),
            Bind.hold(4, BACK, 9, "guide", ActionMode.PRESS),
            Bind.hold(5, Y, 8, "tier", ActionMode.PRESS),
            Bind.tap(6, UP, "map", ActionMode.PRESS),
            Bind.hold(7, UP, 5, "spyglass", ActionMode.TOGGLE),
            Bind.tap(8, DOWN, "crawl", ActionMode.TOGGLE),
            Bind.hold(9, DOWN, 5, "drop", ActionMode.REPEAT),
            Bind.scoped(10, RB, 6, 2, "zoom_in", ActionMode.PRESS),
            Bind.scoped(11, LB, 6, 2, "zoom_out", ActionMode.PRESS));

    static final class Ctx implements GuidePlanner.Context {
        int verbosity = 2;
        boolean scoped;
        final Set<String> down = new HashSet<>();
        final Map<String, Integer> levels = new HashMap<>(Map.of(
                "spell_1", 2, "spell_2", 2, "spell_5", 2, "skills", 2, "guide", 2, "tier", 2,
                "map", 3, "spyglass", 2, "crawl", 3, "drop", 2));

        @Override public int verbosity() { return verbosity; }
        @Override public boolean scoped() { return scoped; }
        @Override public boolean isDown(String button) { return down.contains(button); }
        @Override public int level(String action) { return levels.getOrDefault(action, action.startsWith("zoom") ? 1 : 0); }
    }

    static List<String> actions(GuidePlanner.Plan plan) {
        return plan.entries().stream().map(GuidePlanner.Entry::action).toList();
    }

    @Test
    void reducedShowsSpellSummariesMenusAndContextualDpad() {
        Ctx ctx = new Ctx();
        GuidePlanner.Plan plan = new GuidePlanner(LAYOUT).plan(ctx);
        assertEquals(List.of("spell_1", "spell_5", "skills", "guide", "tier", "spyglass", "drop"), actions(plan));
        GuidePlanner.Entry lb = plan.entries().get(0);
        assertEquals(GuidePlanner.Form.LAYER_SUMMARY, lb.form());
        assertEquals(List.of(LB), lb.buttons());
        GuidePlanner.Entry chord = plan.entries().get(2);
        assertEquals(GuidePlanner.Form.CHORD, chord.form());
        assertEquals(List.of(LB, RB), chord.buttons());
        GuidePlanner.Entry drop = plan.entries().get(6);
        assertEquals(GuidePlanner.Form.HOLD, drop.form());
        assertEquals(GuidePlanner.Side.RIGHT, drop.side());
        assertEquals(Set.of(UP, DOWN), plan.overridden(), "tap-replaced D-pad buttons hide Controlify's own entries");
    }

    @Test
    void fullAddsEverythingMinimalAlmostNothing() {
        Ctx ctx = new Ctx();
        ctx.verbosity = 3;
        assertTrue(actions(new GuidePlanner(LAYOUT).plan(ctx)).containsAll(List.of("map", "crawl")));
        ctx.verbosity = 1;
        assertTrue(new GuidePlanner(LAYOUT).plan(ctx).entries().isEmpty());
    }

    @Test
    void noSpellsNoSummary() {
        Ctx ctx = new Ctx();
        ctx.levels.put("spell_1", 0);
        ctx.levels.put("spell_2", 0);
        List<String> a = actions(new GuidePlanner(LAYOUT).plan(ctx));
        assertFalse(a.contains("spell_1"));
        assertFalse(a.contains("spell_2"));
        assertTrue(a.contains("spell_5"), "RB layer still has a spell");
    }

    @Test
    void summaryUsesFirstAvailableSlot() {
        Ctx ctx = new Ctx();
        ctx.levels.put("spell_1", 0);
        assertEquals("spell_2", new GuidePlanner(LAYOUT).plan(ctx).entries().getFirst().action());
    }

    @Test
    void heldModifierListsItsLayerOnly() {
        Ctx ctx = new Ctx();
        ctx.verbosity = 1;
        ctx.down.add(LB);
        GuidePlanner.Plan plan = new GuidePlanner(LAYOUT).plan(ctx);
        assertEquals(List.of("spell_1", "spell_2", "skills"), actions(plan));
        assertTrue(plan.entries().stream().allMatch(e -> e.form() == GuidePlanner.Form.LAYER_BUTTON));
        assertEquals(List.of(RB), plan.entries().get(2).buttons(), "chord partner shown as the button to press");
        assertTrue(plan.overridden().containsAll(Set.of(A, X, RB)), "A's jump entry hides while LB is held");
    }

    @Test
    void heldModifierSkipsEmptySlots() {
        Ctx ctx = new Ctx();
        ctx.levels.put("spell_2", 0);
        ctx.down.add(LB);
        assertEquals(List.of("spell_1", "skills"), actions(new GuidePlanner(LAYOUT).plan(ctx)));
    }

    @Test
    void scopedShowsZoomAndKeepsOtherButtons() {
        Ctx ctx = new Ctx();
        ctx.scoped = true;
        ctx.down.add(LB); // held shoulder while scoped zooms, no layer list
        GuidePlanner.Plan plan = new GuidePlanner(LAYOUT).plan(ctx);
        List<String> a = actions(plan);
        assertEquals(List.of("zoom_in", "zoom_out"), a.subList(0, 2));
        assertFalse(a.contains("spell_1"));
        assertFalse(a.contains("skills"), "chord on the zoom buttons is hidden while scoped");
        assertTrue(a.contains("spyglass"), "D-up hold still shown, to stop the spyglass");
        assertTrue(plan.overridden().containsAll(Set.of(LB, RB)));
    }

    @Test
    void emptyLayoutPlansNothing() {
        GuidePlanner.Plan plan = new GuidePlanner(List.of()).plan(new Ctx());
        assertTrue(plan.entries().isEmpty());
        assertTrue(plan.overridden().isEmpty());
    }
}
