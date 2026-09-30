package net.gameoverse.controllerplus.compat;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.gameoverse.controllerplus.mixin.KeyboardHandlerInvoker;
import net.gameoverse.controllerplus.mixin.KeyMappingAccessor;
import net.gameoverse.controllerplus.mixin.MouseHandlerInvoker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Presses the key a KeyMapping is bound to, as a real key event for the open screen. Screen mods
 * (JEI: show recipes, show uses, recipe back) read screen key presses as events matched against
 * their KeyMapping, not through {@code isDown()}, and Controlify's key emulation is off while a
 * screen is open, so the press goes through Minecraft's private {@code KeyboardHandler.keyPress}
 * (or {@code MouseHandler.onButton} for a mouse-bound key), the method the GLFW callback calls.
 * Every mod's hook into it (Fabric's screen keyboard events, which JEI uses) sees the press;
 * Controlify's own input-mode switch sits one level up on the GLFW callback, so the synthetic press
 * doesn't flip it to keyboard/mouse mode.
 */
public final class ScreenKeys {
    private static final Logger LOG = LoggerFactory.getLogger("gameoverse_controller_plus");
    private static final Set<String> WARNED = new HashSet<>();

    private ScreenKeys() {
    }

    /** Press and release the key bound to {@code name}; false if the KeyMapping is missing or unbound. */
    public static boolean press(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return false;
        KeyMapping mapping = KeyMapping.get(name);
        if (mapping == null || mapping.isUnbound()) {
            if (WARNED.add(name)) {
                LOG.warn("Key {} is {}; its controller bind does nothing", name, mapping == null ? "not registered (mod missing?)" : "unbound");
            }
            return false;
        }
        InputConstants.Key key = ((KeyMappingAccessor) mapping).gcp$getKey();
        long window = mc.getWindow().handle();
        switch (key.getType()) {
            case KEYSYM -> {
                int scancode = GLFW.glfwGetKeyScancode(key.getValue());
                KeyEvent event = new KeyEvent(key.getValue(), scancode, 0);
                ((KeyboardHandlerInvoker) mc.keyboardHandler).gcp$invokeKeyPress(window, InputConstants.PRESS, event);
                ((KeyboardHandlerInvoker) mc.keyboardHandler).gcp$invokeKeyPress(window, InputConstants.RELEASE, event);
            }
            case SCANCODE -> {
                KeyEvent event = new KeyEvent(InputConstants.UNKNOWN.getValue(), key.getValue(), 0);
                ((KeyboardHandlerInvoker) mc.keyboardHandler).gcp$invokeKeyPress(window, InputConstants.PRESS, event);
                ((KeyboardHandlerInvoker) mc.keyboardHandler).gcp$invokeKeyPress(window, InputConstants.RELEASE, event);
            }
            case MOUSE -> {
                MouseButtonInfo button = new MouseButtonInfo(key.getValue(), 0);
                ((MouseHandlerInvoker) mc.mouseHandler).gcp$invokeOnButton(window, button, InputConstants.PRESS);
                ((MouseHandlerInvoker) mc.mouseHandler).gcp$invokeOnButton(window, button, InputConstants.RELEASE);
            }
        }
        return true;
    }
}
