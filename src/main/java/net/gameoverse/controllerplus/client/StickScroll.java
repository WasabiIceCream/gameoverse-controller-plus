package net.gameoverse.controllerplus.client;

import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.controller.ControllerEntity;
import java.util.Map;
import net.gameoverse.controllerplus.engine.StickScroller;
import net.gameoverse.controllerplus.mixin.MouseHandlerInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Right-stick scrolling in screens that need whole wheel notches (see {@link StickScroller}).
 * Called from Controlify's {@code VirtualMouseHandler.handleScroll} (once per controller tick while
 * the virtual mouse is on); for a screen in {@link #RATES} Controlify's own fractional scroll is
 * skipped and whole notches go through {@code MouseHandler.onScroll} at the cursor instead, the
 * same path as a mouse wheel. Every other screen keeps Controlify's scrolling.
 */
public final class StickScroll {
    /**
     * Screen class (or superclass) name -> notches per second at full deflection. Matched against the
     * screen's class and its superclasses by name, so nothing here is loaded.
     */
    static final Map<String, Double> RATES = Map.of(
            // Oracle Index (Gameoverse Guide): ScrollWidget does (int) amount * 12 px, so fractions scrolled nothing.
            "rearth.oracle.ui.WikiBaseScreen", 25.0,
            // Field Guide: each call turns a page / cycles a variant (sign only).
            "com.evandev.fieldguide.client.gui.screens.BookScreen", 4.0,
            // Scholar books and lecterns: each call turns a page (sign only).
            "io.github.mortuusars.scholar.client.gui.screen.SpreadBookScreen", 4.0,
            // MapStitch world map: each call is one zoom level (-2..1).
            "me.pajic.mapstitch.worldmap.WorldMapScreen", 5.0,
            // Create's Ponder: each call steps a scene.
            "com.zurrtum.create.client.ponder.foundation.ui.PonderUI", 4.0,
            // Create's value settings (scroll on a value board): each call is one value step.
            "com.zurrtum.create.client.foundation.blockEntity.ValueSettingsScreen", 8.0,
            // JEI's recipe screen: each call is one recipe page (or one notch of a recipe's scroll area).
            "mezz.jei.gui.recipes.RecipesGui", 6.0,
            // Penchant's enchanting table: ScrollbarComponent does addPosition((int) -amount), so fractions scrolled nothing.
            "archives.tater.penchant.client.gui.screen.PenchantmentScreen", 10.0);

    private static final StickScroller SCROLLER = new StickScroller();
    private static Screen lastScreen;

    private StickScroll() {
    }

    /** Notches per second for this screen, or 0 when Controlify's own scrolling should run. */
    static double rateFor(Screen screen) {
        for (Class<?> c = screen.getClass(); c != null && c != Screen.class; c = c.getSuperclass()) {
            Double rate = RATES.get(c.getName());
            if (rate != null) return rate;
        }
        return 0;
    }

    /**
     * @return true when this screen is handled here (Controlify's scroll must be skipped)
     */
    public static boolean handle(ControllerEntity controller) {
        if (!ControllerPlus.get().config().stickScroll) return false;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return false;
        double rate = rateFor(screen);
        if (screen != lastScreen) {
            lastScreen = screen;
            SCROLLER.reset();
        }
        if (rate <= 0) return false;

        double deflection = ControlifyBindings.VMOUSE_SCROLL_UP.on(controller).analogueNow()
                - ControlifyBindings.VMOUSE_SCROLL_DOWN.on(controller).analogueNow();
        int notches = SCROLLER.tick(deflection, rate);
        int step = Integer.signum(notches);
        long window = mc.getWindow().handle();
        // One call per notch: sign-only screens count calls, not amounts.
        for (int i = 0; i < Math.abs(notches) && mc.screen == screen; i++) {
            ((MouseHandlerInvoker) mc.mouseHandler).gcp$invokeOnScroll(window, 0, step);
        }
        return true;
    }
}
