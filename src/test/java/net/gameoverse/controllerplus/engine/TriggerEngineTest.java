package net.gameoverse.controllerplus.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TriggerEngineTest {
    static final String A = "a", B = "b", X = "x", Y = "y", LB = "lb", RB = "rb", BACK = "back", UP = "up", DOWN = "down";

    /** Drives an engine tick by tick and records what the bindings would see. */
    static final class Rig {
        final TriggerEngine engine;
        final Set<String> down = new HashSet<>();
        final List<TriggerEngine.Result> results = new ArrayList<>();
        boolean active = true;

        Rig(int modifierTapTicks, Bind... binds) {
            engine = new TriggerEngine(List.of(binds), modifierTapTicks);
        }

        TriggerEngine.Result tick() {
            TriggerEngine.Result r = engine.tick(down::contains, active);
            results.add(r);
            return r;
        }

        TriggerEngine.Result press(String b) {
            down.add(b);
            return tick();
        }

        TriggerEngine.Result release(String b) {
            down.remove(b);
            return tick();
        }

        /** Runs n ticks with no change and returns every event seen. */
        List<TriggerEngine.Event> idle(int n) {
            List<TriggerEngine.Event> all = new ArrayList<>();
            for (int i = 0; i < n; i++) all.addAll(tick().events());
            return all;
        }

        /** What a binding on button b sees this tick. */
        static boolean sees(TriggerEngine.Result r, String b, boolean physical) {
            if (r.replay().contains(b)) return true;
            if (r.masked().contains(b)) return false;
            return physical;
        }
    }

    static TriggerEngine.Event only(List<TriggerEngine.Event> events) {
        assertEquals(1, events.size(), "expected one event, got " + events);
        return events.getFirst();
    }

    // ---- HOLD ------------------------------------------------------------------------------

    @Test
    void holdShortPressReplaysTapOnRelease() {
        Rig rig = new Rig(6, Bind.hold(1, BACK, 9, "guide", ActionMode.PRESS));
        TriggerEngine.Result r = rig.press(BACK);
        assertTrue(r.masked().contains(BACK), "held back while undecided");
        assertTrue(rig.idle(3).isEmpty());
        r = rig.release(BACK);
        assertTrue(r.events().isEmpty());
        assertTrue(r.replay().contains(BACK), "tap replayed on the release tick");
        r = rig.tick();
        assertFalse(r.replay().contains(BACK), "replay lasts exactly one tick");
        assertFalse(rig.tick().changesView());
    }

    @Test
    void holdReachedFiresOnceAndNeverReplays() {
        Rig rig = new Rig(6, Bind.hold(1, BACK, 9, "guide", ActionMode.PRESS));
        rig.press(BACK);
        List<TriggerEngine.Event> events = rig.idle(8);
        assertTrue(events.isEmpty(), "not yet at 8 ticks");
        TriggerEngine.Result r = rig.tick(); // 9th tick held
        assertEquals(TriggerEngine.Kind.PRESS, only(r.events()).kind());
        assertTrue(r.masked().contains(BACK));
        assertTrue(rig.idle(20).isEmpty(), "fires once");
        r = rig.release(BACK);
        assertTrue(r.events().isEmpty());
        assertTrue(r.replay().isEmpty(), "no tap after a hold");
        assertTrue(rig.idle(3).isEmpty());
    }

    @Test
    void holdWhileStopsOnRelease() {
        Rig rig = new Rig(6, Bind.hold(1, UP, 5, "zoom", ActionMode.HOLD_WHILE));
        rig.press(UP);
        List<TriggerEngine.Event> events = rig.idle(5);
        assertEquals(TriggerEngine.Kind.START, only(events).kind());
        assertEquals(TriggerEngine.Kind.STOP, only(rig.release(UP).events()).kind());
    }

    @Test
    void unrelatedButtonsAreNeverDelayed() {
        Rig rig = new Rig(6, Bind.hold(1, BACK, 9, "guide", ActionMode.PRESS));
        TriggerEngine.Result r = rig.press(A);
        assertFalse(r.masked().contains(A));
        assertTrue(Rig.sees(r, A, true));
        assertFalse(rig.engine.trackedButtons().contains(A));
    }

    // ---- TAP + HOLD on one button (D-pad split) --------------------------------------------

    @Test
    void tapAndHoldSplit() {
        Rig rig = new Rig(6,
                Bind.tap(1, UP, "spyglass", ActionMode.TOGGLE),
                Bind.hold(2, UP, 5, "zoom", ActionMode.HOLD_WHILE));
        // Tap: toggles spyglass on, the button's normal bindings never see it.
        TriggerEngine.Result r = rig.press(UP);
        assertTrue(r.masked().contains(UP));
        r = rig.release(UP);
        TriggerEngine.Event e = only(r.events());
        assertEquals(TriggerEngine.Kind.START, e.kind());
        assertEquals(1, e.bind().id());
        assertTrue(r.replay().isEmpty(), "an explicit TAP bind replaces the normal binding");
        // Tap again: toggles off.
        rig.press(UP);
        e = only(rig.release(UP).events());
        assertEquals(TriggerEngine.Kind.STOP, e.kind());
        // Hold: zoom while held, no tap.
        rig.press(UP);
        e = only(rig.idle(5));
        assertEquals(TriggerEngine.Kind.START, e.kind());
        assertEquals(2, e.bind().id());
        e = only(rig.release(UP).events());
        assertEquals(TriggerEngine.Kind.STOP, e.kind());
        assertEquals(2, e.bind().id());
    }

    @Test
    void tapOnlyButtonFiresOnPressWithoutDelay() {
        Rig rig = new Rig(6, Bind.tap(1, DOWN, "thing", ActionMode.PRESS));
        TriggerEngine.Result r = rig.press(DOWN);
        assertEquals(TriggerEngine.Kind.PRESS, only(r.events()).kind());
        assertTrue(r.masked().contains(DOWN));
        assertTrue(rig.release(DOWN).events().isEmpty());
    }

    // ---- LAYER -----------------------------------------------------------------------------

    static Rig spellRig() {
        return new Rig(6,
                Bind.layer(1, LB, A, "spell1", ActionMode.HOLD_WHILE),
                Bind.layer(2, LB, X, "spell2", ActionMode.HOLD_WHILE),
                Bind.layer(5, RB, A, "spell5", ActionMode.HOLD_WHILE),
                Bind.chord(9, LB, RB, "skills", ActionMode.PRESS),
                Bind.hold(10, Y, 8, "tier", ActionMode.PRESS),
                Bind.layer(3, LB, Y, "spell3", ActionMode.HOLD_WHILE));
    }

    @Test
    void layerButtonAloneIsNotDelayed() {
        Rig rig = spellRig();
        TriggerEngine.Result r = rig.press(A);
        assertTrue(Rig.sees(r, A, true), "A jumps immediately when LB is not held");
        assertTrue(r.events().isEmpty());
        assertTrue(rig.release(A).events().isEmpty());
    }

    @Test
    void layerFiresHoldWhileAndSuppressesBothButtons() {
        Rig rig = spellRig();
        TriggerEngine.Result r = rig.press(LB);
        assertTrue(r.masked().contains(LB));
        rig.idle(2);
        r = rig.press(A);
        TriggerEngine.Event e = only(r.events());
        assertEquals(TriggerEngine.Kind.START, e.kind());
        assertEquals("spell1", e.bind().action());
        assertTrue(r.masked().contains(A), "no jump");
        assertTrue(r.masked().contains(LB));
        assertTrue(rig.idle(10).isEmpty(), "held spell keeps going");
        assertEquals(TriggerEngine.Kind.STOP, only(rig.release(A).events()).kind());
        r = rig.release(LB);
        assertTrue(r.replay().isEmpty(), "no hotbar change after a layer was used");
        assertTrue(rig.idle(3).stream().noneMatch(x -> true));
        for (TriggerEngine.Result res : rig.results) assertFalse(res.replay().contains(LB));
    }

    @Test
    void releasingModifierFirstKeepsLayerButtonHeldBack() {
        Rig rig = spellRig();
        rig.press(LB);
        rig.press(A);
        TriggerEngine.Result r = rig.release(LB);
        assertTrue(r.events().isEmpty(), "spell keeps going while A is held");
        assertTrue(r.replay().isEmpty());
        assertTrue(r.masked().contains(A), "A stays held back: no jump leaks out");
        r = rig.tick();
        assertTrue(r.masked().contains(A));
        assertEquals(TriggerEngine.Kind.STOP, only(rig.release(A).events()).kind());
        // A works normally again afterwards.
        assertTrue(Rig.sees(rig.press(A), A, true));
    }

    @Test
    void modifierTapReplaysItsOwnBinding() {
        Rig rig = spellRig();
        rig.press(LB);
        rig.idle(2);
        TriggerEngine.Result r = rig.release(LB);
        assertTrue(r.replay().contains(LB), "LB alone still switches hotbar slot, on release");
        assertTrue(r.events().isEmpty());
    }

    @Test
    void modifierHeldTooLongFiresNothing() {
        Rig rig = spellRig();
        rig.press(LB);
        rig.idle(6);
        TriggerEngine.Result r = rig.release(LB);
        assertTrue(r.replay().isEmpty());
        assertTrue(r.events().isEmpty());
        assertTrue(rig.idle(3).isEmpty());
    }

    @Test
    void modifierHeldLongStillAllowsLayer() {
        Rig rig = spellRig();
        rig.press(LB);
        rig.idle(30);
        assertEquals("spell2", only(rig.press(X).events()).bind().action());
    }

    @Test
    void layerButtonPressedBeforeModifierIsNotLayered() {
        Rig rig = spellRig();
        rig.press(A); // jumping
        TriggerEngine.Result r = rig.press(LB);
        assertTrue(r.events().isEmpty());
        assertTrue(Rig.sees(r, A, true), "A keeps its normal action");
    }

    @Test
    void sameTickModifierAndLayerButtonStillLayers() {
        Rig rig = spellRig();
        rig.down.add(A);
        rig.down.add(LB);
        TriggerEngine.Result r = rig.tick();
        assertEquals("spell1", only(r.events()).bind().action());
        assertTrue(r.masked().contains(A));
    }

    @Test
    void layerButtonWithOwnHoldUsesLayerWhenModifierHeld() {
        Rig rig = spellRig();
        rig.press(LB);
        TriggerEngine.Result r = rig.press(Y);
        assertEquals("spell3", only(r.events()).bind().action());
        assertTrue(rig.idle(12).isEmpty(), "Y's own hold does not start while used as a layer button");
        rig.release(Y);
        // Without LB, Y's hold works.
        rig.release(LB);
        rig.press(Y);
        assertEquals("tier", only(rig.idle(8)).bind().action());
    }

    @Test
    void differentModifiersGiveDifferentLayers() {
        Rig rig = spellRig();
        rig.press(RB);
        assertEquals("spell5", only(rig.press(A).events()).bind().action());
    }

    // ---- CHORD -----------------------------------------------------------------------------

    @Test
    void chordEitherOrderAndSameTick() {
        for (int order = 0; order < 3; order++) {
            Rig rig = spellRig();
            TriggerEngine.Result r;
            if (order == 0) {
                rig.press(LB);
                r = rig.press(RB);
            } else if (order == 1) {
                rig.press(RB);
                r = rig.press(LB);
            } else {
                rig.down.add(LB);
                rig.down.add(RB);
                r = rig.tick();
            }
            assertEquals("skills", only(r.events()).bind().action(), "order " + order);
            assertTrue(r.masked().contains(LB) && r.masked().contains(RB));
            r = rig.release(LB);
            assertTrue(r.replay().isEmpty());
            r = rig.release(RB);
            assertTrue(r.replay().isEmpty(), "neither shoulder switches slot after the chord");
            assertTrue(rig.idle(3).isEmpty());
        }
    }

    // ---- MULTI_TAP -------------------------------------------------------------------------

    @Test
    void doubleTapFiresOnSecondPress() {
        Rig rig = new Rig(6, Bind.multiTap(1, DOWN, 2, 5, "double", ActionMode.PRESS));
        rig.press(DOWN);
        rig.release(DOWN);
        rig.tick();
        TriggerEngine.Result r = rig.press(DOWN);
        assertEquals("double", only(r.events()).bind().action());
        assertTrue(r.masked().contains(DOWN));
        r = rig.release(DOWN);
        assertTrue(r.replay().isEmpty());
        assertTrue(rig.idle(8).isEmpty());
        for (TriggerEngine.Result res : rig.results) assertFalse(res.replay().contains(DOWN));
    }

    @Test
    void singleTapReplayedAfterWindow() {
        Rig rig = new Rig(6, Bind.multiTap(1, DOWN, 2, 5, "double", ActionMode.PRESS));
        rig.press(DOWN);
        TriggerEngine.Result r = rig.release(DOWN);
        assertTrue(r.replay().isEmpty(), "waits for a second tap");
        int replayTick = -1;
        for (int i = 1; i <= 6; i++) {
            if (rig.tick().replay().contains(DOWN)) {
                replayTick = i;
                break;
            }
        }
        assertEquals(5, replayTick, "replayed once the 5-tick window closes");
        assertFalse(rig.tick().replay().contains(DOWN));
    }

    @Test
    void multiTapHeldBecomesNormalPress() {
        Rig rig = new Rig(6, Bind.multiTap(1, DOWN, 2, 5, "double", ActionMode.PRESS));
        rig.press(DOWN);
        TriggerEngine.Result r = null;
        for (int i = 0; i < 4; i++) {
            r = rig.tick();
            assertTrue(r.masked().contains(DOWN));
        }
        r = rig.tick();
        assertFalse(r.masked().contains(DOWN), "held past the window: passes through as a held press");
        assertTrue(Rig.sees(r, DOWN, true));
        assertTrue(rig.release(DOWN).events().isEmpty());
        assertTrue(rig.idle(8).isEmpty());
    }

    @Test
    void tripleTapWithDoubleAndTripleBinds() {
        Rig rig = new Rig(6,
                Bind.multiTap(1, DOWN, 2, 5, "double", ActionMode.PRESS),
                Bind.multiTap(2, DOWN, 3, 5, "triple", ActionMode.PRESS));
        rig.press(DOWN);
        rig.release(DOWN);
        rig.press(DOWN);
        rig.release(DOWN);
        assertEquals("double", only(rig.idle(5)).bind().action(), "two taps then silence");
        rig.press(DOWN);
        rig.release(DOWN);
        rig.press(DOWN);
        rig.release(DOWN);
        assertEquals("triple", only(rig.press(DOWN).events()).bind().action());
    }

    // ---- Scope -----------------------------------------------------------------------------

    @Test
    void deactivationDropsPendingButKeepsButtonHeldBack() {
        Rig rig = new Rig(6, Bind.hold(1, BACK, 9, "guide", ActionMode.PRESS));
        rig.press(BACK);
        rig.active = false; // a screen opened
        TriggerEngine.Result r = rig.tick();
        assertTrue(r.masked().contains(BACK), "stays held back until released");
        assertTrue(rig.idle(12).isEmpty(), "no hold fires while inactive");
        r = rig.release(BACK);
        assertTrue(r.replay().isEmpty(), "no tap replay either");
        r = rig.press(BACK);
        assertFalse(r.masked().contains(BACK), "new presses pass through while inactive");
    }

    @Test
    void holdWhileSurvivesScreenUntilRelease() {
        Rig rig = spellRig();
        rig.press(LB);
        rig.press(A);
        rig.active = false;
        TriggerEngine.Result r = rig.tick();
        assertTrue(r.events().isEmpty());
        assertTrue(r.masked().contains(A));
        assertEquals(TriggerEngine.Kind.STOP, only(rig.release(A).events()).kind());
    }

    @Test
    void deactivationStopsToggles() {
        Rig rig = new Rig(6, Bind.tap(1, DOWN, "t", ActionMode.TOGGLE));
        assertEquals(TriggerEngine.Kind.START, only(rig.press(DOWN).events()).kind());
        rig.release(DOWN);
        rig.active = false;
        assertEquals(TriggerEngine.Kind.STOP, only(rig.tick().events()).kind());
    }

    @Test
    void hardResetStopsHeldActions() {
        Rig rig = spellRig();
        rig.press(LB);
        rig.press(A);
        List<TriggerEngine.Event> events = rig.engine.hardReset();
        assertEquals(TriggerEngine.Kind.STOP, only(events).kind());
        rig.active = false;
        TriggerEngine.Result r = rig.tick();
        assertTrue(r.masked().contains(A), "still held back until released");
        assertTrue(rig.release(A).events().isEmpty(), "already stopped");
    }

    @Test
    void pendingMultiTapDroppedOnDeactivate() {
        Rig rig = new Rig(6, Bind.multiTap(1, DOWN, 2, 5, "double", ActionMode.PRESS));
        rig.press(DOWN);
        rig.release(DOWN);
        rig.active = false;
        for (int i = 0; i < 8; i++) {
            TriggerEngine.Result r = rig.tick();
            assertTrue(r.events().isEmpty());
            assertTrue(r.replay().isEmpty());
        }
    }
}
