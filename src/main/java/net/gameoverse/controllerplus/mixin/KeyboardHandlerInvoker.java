package net.gameoverse.controllerplus.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Minecraft's private key handler (the GLFW key callback's target). Calling it runs every other
 * mod's injection into it (Fabric's screen keyboard events, which JEI listens to) exactly as a key
 * press would; Controlify's keyboard-mode switch wraps the GLFW callback above it and isn't hit.
 */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInvoker {
    @Invoker("keyPress")
    void gcp$invokeKeyPress(long window, int action, KeyEvent event);
}
