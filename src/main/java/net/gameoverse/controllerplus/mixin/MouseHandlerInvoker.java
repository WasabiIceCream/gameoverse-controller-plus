package net.gameoverse.controllerplus.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Minecraft's private scroll handler (the GLFW scroll callback's target). Calling it runs every
 * other mod's injection into it, exactly as a mouse wheel notch would.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onScroll")
    void gcp$invokeOnScroll(long window, double xOffset, double yOffset);
}
