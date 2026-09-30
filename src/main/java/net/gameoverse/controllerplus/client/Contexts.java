package net.gameoverse.controllerplus.client;

import java.util.EnumSet;
import java.util.Set;
import net.gameoverse.controllerplus.engine.InputContext;
import net.gameoverse.controllerplus.mixin.CreativeModeInventoryScreenAccessor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/** Which advanced-bind contexts are active for the open screen. */
final class Contexts {
    private static final String JEI_RECIPES = "mezz.jei.gui.recipes.RecipesGui";
    private static final Set<InputContext> GAME = EnumSet.of(InputContext.GAME);
    private static final Set<InputContext> NONE = EnumSet.noneOf(InputContext.class);
    private static final Set<InputContext> SCREEN = EnumSet.of(InputContext.SCREEN);
    private static final Set<InputContext> RECIPES = EnumSet.of(InputContext.SCREEN, InputContext.RECIPE_SCREEN);

    private Contexts() {
    }

    /**
     * No screen: in game. An inventory/container screen: screen binds. JEI's recipe screen: screen
     * and recipe-screen binds. Any other screen, or any screen with a text field focused (anvil name,
     * creative or JEI search, sign...): none, so typing and Controlify's own navigation are untouched.
     */
    static Set<InputContext> of(Screen screen) {
        if (screen == null) return GAME;
        boolean recipes = isJeiRecipes(screen);
        if (!recipes && !(screen instanceof AbstractContainerScreen<?>)) return NONE;
        if (textFocused(screen)) return NONE;
        return recipes ? RECIPES : SCREEN;
    }

    static boolean isJeiRecipes(Screen screen) {
        for (Class<?> c = screen.getClass(); c != null && c != Screen.class; c = c.getSuperclass()) {
            if (c.getName().equals(JEI_RECIPES)) return true;
        }
        return false;
    }

    private static boolean textFocused(Screen screen) {
        GuiEventListener focused = screen.getFocused();
        if (focused instanceof EditBox box && box.isFocused()) return true;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isFocused()) return true;
        }
        if (screen instanceof CreativeModeInventoryScreen) {
            EditBox search = ((CreativeModeInventoryScreenAccessor) screen).gcp$getSearchBox();
            return search != null && search.isFocused() && search.isVisible();
        }
        return false;
    }
}
