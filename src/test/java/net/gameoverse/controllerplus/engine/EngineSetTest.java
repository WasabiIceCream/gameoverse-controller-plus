package net.gameoverse.controllerplus.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EngineSetTest {
    static final String Y = "y", RS = "rs", X = "x", A = "a";
    static final Set<InputContext> GAME = EnumSet.of(InputContext.GAME);
    static final Set<InputContext> SCREEN = EnumSet.of(InputContext.SCREEN);
    static final Set<InputContext> RECIPES = EnumSet.of(InputContext.SCREEN, InputContext.RECIPE_SCREEN);
    static final Set<InputContext> NONE = EnumSet.noneOf(InputContext.class);

    /** The shipped screen binds plus the in-game Y hold and the scoped X bind. */
    static final class Rig {
        final EngineSet engines = new EngineSet(List.of(
                Bind.hold(1, Y, 8, "tier", ActionMode.PRESS),
                Bind.scoped(2, X, 6, 2, "astronomy_info", ActionMode.PRESS),
                Bind.tap(3, RS, "recipes", ActionMode.PRESS).in(InputContext.SCREEN),
                Bind.hold(4, RS, 6, "uses", ActionMode.PRESS).in(InputContext.SCREEN),
                Bind.tap(5, Y, "back", ActionMode.PRESS).in(InputContext.RECIPE_SCREEN)), 6);
        final Set<String> down = new HashSet<>();
        Set<InputContext> contexts = GAME;
        boolean scoped;

        TriggerEngine.Result tick() {
            return engines.tick(down::contains, contexts, scoped);
        }

        TriggerEngine.Result press(String b) {
            down.add(b);
            return tick();
        }

        TriggerEngine.Result release(String b) {
            down.remove(b);
            return tick();
        }

        List<TriggerEngine.Event> idle(int n) {
            List<TriggerEngine.Event> all = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) all.addAll(tick().events());
            return all;
        }
    }

    static String only(TriggerEngine.Result r) {
        assertEquals(1, r.events().size(), "expected one event, got " + r.events());
        return r.events().getFirst().bind().action();
    }

    @Test
    void screenBindsAreSilentInGame() {
        Rig rig = new Rig();
        TriggerEngine.Result r = rig.press(RS);
        assertTrue(r.events().isEmpty());
        assertFalse(r.masked().contains(RS), "sneak (RS) untouched in game");
        assertTrue(rig.idle(10).isEmpty());
        assertTrue(rig.release(RS).events().isEmpty());
    }

    @Test
    void tapRsShowsRecipesInAnInventory() {
        Rig rig = new Rig();
        rig.contexts = SCREEN;
        TriggerEngine.Result r = rig.press(RS);
        assertTrue(r.events().isEmpty(), "waits: RS also has a hold");
        assertTrue(r.masked().contains(RS));
        assertEquals("recipes", only(rig.release(RS)));
    }

    @Test
    void holdRsShowsUses() {
        Rig rig = new Rig();
        rig.contexts = SCREEN;
        rig.press(RS);
        List<TriggerEngine.Event> events = rig.idle(6);
        assertEquals(1, events.size());
        assertEquals("uses", events.getFirst().bind().action());
        assertTrue(rig.release(RS).events().isEmpty(), "no Recipes after Uses");
    }

    @Test
    void inventoryButtonsOtherThanTheScreenBindsPassThrough() {
        Rig rig = new Rig();
        rig.contexts = SCREEN;
        TriggerEngine.Result r = rig.press(Y);
        assertTrue(r.events().isEmpty());
        assertFalse(r.masked().contains(Y), "Y quick-moves as usual: no delay, no World Tier");
        assertTrue(rig.idle(12).isEmpty());
        r = rig.press(X);
        assertFalse(r.masked().contains(X), "X takes half as usual");
    }

    @Test
    void yIsRecipeBackOnlyInJeiRecipeScreen() {
        Rig rig = new Rig();
        rig.contexts = RECIPES;
        TriggerEngine.Result r = rig.press(Y);
        assertEquals("back", only(r));
        assertTrue(r.masked().contains(Y));
        assertTrue(rig.idle(12).isEmpty(), "no World Tier");
        assertTrue(rig.release(Y).replay().isEmpty());
        rig.press(RS);
        assertEquals("recipes", only(rig.release(RS)), "screen binds work in the recipe screen too");
    }

    @Test
    void textFieldFocusedMeansNoContext() {
        Rig rig = new Rig();
        rig.contexts = NONE;
        TriggerEngine.Result r = rig.press(RS);
        assertTrue(r.events().isEmpty());
        assertFalse(r.masked().contains(RS));
        assertTrue(rig.idle(10).isEmpty());
        r = rig.press(Y);
        assertFalse(r.masked().contains(Y));
    }

    @Test
    void buttonHeldIntoAScreenStaysHeldBackUntilReleased() {
        Rig rig = new Rig();
        rig.press(Y); // in game: undecided between inventory tap and World Tier hold
        rig.contexts = RECIPES;
        TriggerEngine.Result r = rig.tick();
        assertTrue(r.events().isEmpty(), "no Back: Y was pressed before the screen opened");
        assertTrue(r.masked().contains(Y), "and the screen doesn't see it either");
        r = rig.release(Y);
        assertTrue(r.events().isEmpty() && r.replay().isEmpty());
        assertEquals("back", only(rig.press(Y)), "a fresh press works");
    }

    @Test
    void scopedIsGameOnly() {
        Rig rig = new Rig();
        rig.contexts = SCREEN;
        rig.scoped = true;
        assertFalse(rig.press(X).masked().contains(X), "no scoped capture in a screen");
        assertFalse(rig.engines.scopedNow());
        rig.release(X);
        rig.contexts = GAME;
        assertEquals("astronomy_info", only(rig.press(X)));
        assertTrue(rig.engines.scopedNow());
    }

    @Test
    void hardResetCoversEveryContext() {
        Rig rig = new Rig();
        rig.contexts = SCREEN;
        rig.press(RS);
        assertTrue(rig.engines.hardReset().isEmpty(), "nothing held, nothing to stop");
        TriggerEngine.Result r = rig.tick();
        assertTrue(r.masked().contains(RS), "still held back until released");
        assertTrue(rig.idle(10).isEmpty(), "the pending tap/hold was dropped");
    }

    @Test
    void contextsListsOnlyUsedOnes() {
        EngineSet set = new EngineSet(List.of(Bind.tap(1, A, "x", ActionMode.PRESS)), 6);
        assertEquals(Set.of(InputContext.GAME), set.contexts());
    }
}
