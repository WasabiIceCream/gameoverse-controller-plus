package net.gameoverse.controllerplus.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.gameoverse.controllerplus.engine.ActionMode;
import net.gameoverse.controllerplus.engine.Bind;
import net.gameoverse.controllerplus.engine.InputContext;
import net.gameoverse.controllerplus.engine.TriggerType;
import org.junit.jupiter.api.Test;

class ConfigTest {
    @Test
    void defaultsCompileWithTheirContexts() {
        List<BindEntry> defaults = Defaults.binds();
        long screen = 0, recipe = 0;
        for (int i = 0; i < defaults.size(); i++) {
            Bind b = defaults.get(i).compile(i);
            assertNotNull(b, "default " + defaults.get(i).summary());
            if (b.context() == InputContext.SCREEN) screen++;
            if (b.context() == InputContext.RECIPE_SCREEN) recipe++;
        }
        assertEquals(2, screen);
        assertEquals(1, recipe);
        assertEquals(25, defaults.size());
        assertFalse(defaults.contains(Defaults.legacyScopedB()), "1.0.4: no scoped B draw bind");
    }

    @Test
    void missingContextMeansGame() {
        BindEntry e = BindEntry.of(TriggerType.TAP, "a", null, 0, 2, 250, "x", ActionMode.PRESS);
        e.context = null; // a pre-1.0.3 file
        assertEquals(InputContext.GAME, e.compile(0).context());
        assertEquals(e, BindEntry.of(TriggerType.TAP, "a", null, 0, 2, 250, "x", ActionMode.PRESS));
    }

    @Test
    void scopedBindsAreGameOnly() {
        BindEntry e = BindEntry.of(TriggerType.SCOPED, "a", null, 300, 2, 100, "x", ActionMode.PRESS).in(InputContext.SCREEN);
        assertNull(e.compile(0));
    }

    @Test
    void version1ScopedPressBecomesRepeat() {
        ControllerPlusConfig c = new ControllerPlusConfig();
        c.version = 1;
        c.customized = true;
        c.binds.add(BindEntry.of(TriggerType.SCOPED, "rb", null, 300, 2, 100, Defaults.SCROLL_UP, ActionMode.PRESS));
        c.binds.add(BindEntry.of(TriggerType.TAP, "a", null, 0, 2, 250, "x", ActionMode.PRESS));
        c.migrate();
        assertEquals(ActionMode.REPEAT, c.binds.get(0).mode, "zoom keeps repeating");
        assertEquals(ActionMode.PRESS, c.binds.get(1).mode);
        assertEquals(ControllerPlusConfig.CURRENT_VERSION, c.version);
        c.binds.get(0).mode = ActionMode.PRESS;
        c.migrate();
        assertEquals(ActionMode.PRESS, c.binds.get(0).mode, "a version 2 file is left alone");
    }

    @Test
    void version2LegacyScopedBRemoved() {
        ControllerPlusConfig c = new ControllerPlusConfig();
        c.version = 2;
        c.customized = true;
        c.binds.addAll(Defaults.binds());
        BindEntry spells = c.binds.getFirst();
        spells.mode = ActionMode.PRESS; // one real customization
        c.binds.add(Defaults.legacyScopedB());
        c.migrate();
        assertFalse(c.binds.contains(Defaults.legacyScopedB()));
        assertTrue(c.customized);
        assertEquals(Defaults.binds().size(), c.binds.size());
        assertEquals(ControllerPlusConfig.CURRENT_VERSION, c.version);
    }

    @Test
    void version2DefaultsPlusLegacyBGoBackToFollowingDefaults() {
        ControllerPlusConfig c = new ControllerPlusConfig();
        c.version = 2;
        c.customized = true;
        c.binds.addAll(Defaults.binds());
        c.binds.add(Defaults.legacyScopedB());
        c.migrate();
        assertFalse(c.customized, "only the old default differed");
        assertTrue(c.binds.isEmpty());
        assertEquals(Defaults.binds(), c.effectiveBinds());
    }

    @Test
    void editedScopedBKeptAndVersion3LeftAlone() {
        ControllerPlusConfig c = new ControllerPlusConfig();
        c.version = 2;
        c.customized = true;
        BindEntry edited = Defaults.legacyScopedB();
        edited.mode = ActionMode.PRESS;
        c.binds.add(edited);
        c.migrate();
        assertEquals(2, c.binds.size(), "an edited B bind is the user's own (plus the version 4 boots toggle)");
        c.binds.add(Defaults.legacyScopedB());
        c.migrate(); // now version 4
        assertEquals(3, c.binds.size(), "a current file keeps a B bind the user added back");
    }

    @Test
    void version3CustomizedGetsBootsToggle() {
        ControllerPlusConfig c = new ControllerPlusConfig();
        c.version = 3;
        c.customized = true;
        c.binds.add(BindEntry.of(TriggerType.TAP, "controlify:button/dpad_right", null, 0, 2, 250, "x", ActionMode.PRESS));
        c.migrate();
        assertTrue(c.binds.contains(Defaults.bootsToggle()), "a tap on D-right doesn't block the hold");
    }

    @Test
    void version3CustomizedHoldOnDpadRightKept() {
        ControllerPlusConfig c = new ControllerPlusConfig();
        c.version = 3;
        c.customized = true;
        BindEntry own = BindEntry.of(TriggerType.HOLD, "controlify:button/dpad_right", null, 300, 2, 250, "y", ActionMode.PRESS);
        c.binds.add(own);
        c.migrate();
        assertEquals(1, c.binds.size(), "the player's own D-right hold stays, no boots toggle added");
    }
}
