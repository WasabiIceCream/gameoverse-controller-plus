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
        boolean byUse;
        int astronomy = -1;
        final Set<String> down = new HashSet<>();
        final Map<String, Integer> levels = new HashMap<>(Map.of(
                "spell_1", 2, "spell_2", 2, "spell_5", 2, "skills", 2, "guide", 2, "tier", 2,
                "map", 3, "spyglass", 2, "crawl", 3, "drop", 2));

        @Override public int verbosity() { return verbosity; }
        @Override public boolean scoped() { return scoped; }
        @Override public boolean scopedByUse() { return byUse; }
        @Override public int astronomyMode() { return astronomy; }
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

    // ---- 1.0.3 -----------------------------------------------------------------------------

    @Test
    void scopedHidesUseAndAttackAndOffersStopOnlyWhenHeldByUse() {
        Ctx ctx = new Ctx();
        ctx.scoped = true;
        GuidePlanner.Plan plan = new GuidePlanner(LAYOUT).plan(ctx);
        assertEquals(GuidePlanner.SCOPED_HIDDEN_BINDINGS, plan.hiddenBindings(), "Controlify's Zoom (LT) line is wrong while scoped");
        assertTrue(plan.entries().stream().noneMatch(e -> e.form() == GuidePlanner.Form.STOP_SCOPE),
                "scoped through the D-up toggle: LT does nothing, no line");
        ctx.byUse = true;
        plan = new GuidePlanner(LAYOUT).plan(ctx);
        GuidePlanner.Entry stop = plan.entries().stream().filter(e -> e.form() == GuidePlanner.Form.STOP_SCOPE).findFirst().orElseThrow();
        assertEquals(List.of(GuidePlanner.USE_BINDING), stop.buttons());
        assertEquals(GuidePlanner.Side.RIGHT, stop.side());
        ctx.scoped = false;
        assertTrue(new GuidePlanner(LAYOUT).plan(ctx).hiddenBindings().isEmpty(), "nothing hidden outside a scope");
    }

    @Test
    void scopedAstronomyButtonsListedAndTheirOwnEntriesHidden() {
        List<Bind> layout = new java.util.ArrayList<>(LAYOUT);
        layout.add(Bind.scoped(20, Y, 6, 2, "astronomy_mode", ActionMode.PRESS));
        layout.add(Bind.scoped(21, X, 6, 2, "astronomy_info", ActionMode.PRESS));
        Ctx ctx = new Ctx();
        ctx.scoped = true;
        ctx.levels.put("astronomy_mode", 1);
        GuidePlanner.Plan plan = new GuidePlanner(layout).plan(ctx);
        List<String> a = actions(plan);
        assertTrue(a.contains("astronomy_mode"));
        assertFalse(a.contains("astronomy_info"), "level 0 (not in select mode): no line");
        assertFalse(a.contains("tier"), "Y's World Tier hold hidden while Y is an astronomy button");
        assertTrue(plan.overridden().containsAll(Set.of(X, Y)), "Controlify's Swap Hands (X) and Inventory (Y) lines hidden");
    }

    // ---- 1.0.4: RT hints by Spyglass Astronomy's mode -----------------------------------------

    static List<GuidePlanner.Entry> attackEntries(GuidePlanner.Plan plan) {
        return plan.entries().stream().filter(e -> e.form() == GuidePlanner.Form.SCOPED_ATTACK).toList();
    }

    @Test
    void scopedDrawModeShowsHoldDrawOnRt() {
        Ctx ctx = new Ctx();
        ctx.scoped = true;
        ctx.astronomy = 1;
        GuidePlanner.Plan plan = new GuidePlanner(LAYOUT).plan(ctx);
        GuidePlanner.Entry rt = attackEntries(plan).stream().findFirst().orElseThrow();
        assertEquals(GuidePlanner.ASTRONOMY_DRAW, rt.action());
        assertEquals(List.of(GuidePlanner.ATTACK_BINDING), rt.buttons());
        assertEquals(GuidePlanner.Side.RIGHT, rt.side());
        assertEquals(1, attackEntries(plan).size());
        assertTrue(plan.hiddenBindings().contains(GuidePlanner.ATTACK_BINDING), "Controlify's own RT line stays hidden");
    }

    @Test
    void scopedSelectModeShowsSelectOnRt() {
        Ctx ctx = new Ctx();
        ctx.scoped = true;
        ctx.astronomy = 2;
        ctx.byUse = true;
        GuidePlanner.Plan plan = new GuidePlanner(LAYOUT).plan(ctx);
        assertEquals(List.of(GuidePlanner.ASTRONOMY_SELECT), attackEntries(plan).stream().map(GuidePlanner.Entry::action).toList());
        List<GuidePlanner.Form> right = plan.entries().stream().filter(e -> e.side() == GuidePlanner.Side.RIGHT)
                .map(GuidePlanner.Entry::form).toList();
        assertEquals(GuidePlanner.Form.SCOPED_ATTACK, right.getFirst(), "RT hint above LT's Stop");
        assertTrue(right.contains(GuidePlanner.Form.STOP_SCOPE));
    }

    @Test
    void scopedNormalModeOrNoAstronomyShowsNothingOnRt() {
        Ctx ctx = new Ctx();
        ctx.scoped = true;
        ctx.astronomy = 0;
        GuidePlanner.Plan plan = new GuidePlanner(LAYOUT).plan(ctx);
        assertTrue(attackEntries(plan).isEmpty(), "normal mode: RT does nothing while scoped");
        assertTrue(plan.hiddenBindings().contains(GuidePlanner.ATTACK_BINDING), "and Controlify's Attack line is hidden");
        ctx.astronomy = -1;
        assertTrue(attackEntries(new GuidePlanner(LAYOUT).plan(ctx)).isEmpty(), "mod missing: nothing");
        ctx.astronomy = 1;
        ctx.scoped = false;
        assertTrue(attackEntries(new GuidePlanner(LAYOUT).plan(ctx)).isEmpty(), "outside a scope: no RT hint");
    }

    @Test
    void scopedBKeepsControlifysOwnLineWithoutAScopedBind() {
        List<Bind> layout = new java.util.ArrayList<>(LAYOUT);
        layout.add(Bind.scoped(20, Y, 6, 2, "astronomy_mode", ActionMode.PRESS));
        layout.add(Bind.scoped(21, X, 6, 2, "astronomy_info", ActionMode.PRESS));
        Ctx ctx = new Ctx();
        ctx.scoped = true;
        ctx.astronomy = 1;
        assertFalse(new GuidePlanner(layout).plan(ctx).overridden().contains("b"), "B's roll line shows while scoped");
    }

    static final String RS = "right_stick";
    static final List<Bind> SCREEN_LAYOUT = List.of(
            Bind.hold(0, Y, 8, "tier", ActionMode.PRESS),
            Bind.tap(1, RS, "recipes", ActionMode.PRESS).in(InputContext.SCREEN),
            Bind.hold(2, RS, 6, "uses", ActionMode.PRESS).in(InputContext.SCREEN),
            Bind.tap(3, Y, "back", ActionMode.PRESS).in(InputContext.RECIPE_SCREEN));

    record ScreenCtx(int verbosity, boolean recipeScreen, Map<String, Integer> levels) implements GuidePlanner.ScreenContext {
        @Override public int level(String action) { return levels.getOrDefault(action, 0); }
    }

    @Test
    void screenGuideListsScreenBindsOnly() {
        GuidePlanner planner = new GuidePlanner(SCREEN_LAYOUT);
        GuidePlanner.Plan plan = planner.planScreen(new ScreenCtx(2, false, Map.of("recipes", 2, "uses", 2, "back", 2, "tier", 2)));
        assertEquals(List.of("recipes", "uses"), actions(plan));
        assertEquals(GuidePlanner.Form.HOLD, plan.entries().get(1).form());
        assertTrue(plan.entries().stream().allMatch(e -> e.side() == GuidePlanner.Side.RIGHT));
        assertEquals(Set.of(RS), plan.overridden());
        plan = planner.planScreen(new ScreenCtx(2, true, Map.of("recipes", 2, "uses", 2, "back", 2)));
        assertEquals(List.of("recipes", "uses", "back"), actions(plan), "Back only in JEI's recipe screen");
    }

    @Test
    void screenGuideSkipsActionsWithNothingToDo() {
        GuidePlanner.Plan plan = new GuidePlanner(SCREEN_LAYOUT).planScreen(new ScreenCtx(2, false, Map.of()));
        assertTrue(plan.entries().isEmpty(), "no item under the cursor: no Recipes/Uses lines");
    }

    @Test
    void inGameGuideIgnoresScreenBinds() {
        Ctx ctx = new Ctx();
        ctx.levels.put("recipes", 2);
        GuidePlanner.Plan plan = new GuidePlanner(SCREEN_LAYOUT).plan(ctx);
        assertEquals(List.of("tier"), actions(plan));
        assertFalse(plan.overridden().contains(Y), "the recipe screen's Y tap doesn't hide Controlify's in-game Y line");
    }
}
