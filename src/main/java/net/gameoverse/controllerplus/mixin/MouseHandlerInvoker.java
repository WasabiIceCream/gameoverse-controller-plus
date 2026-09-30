package net.gameoverse.controllerplus.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Minecraft's private scroll and button handlers (the GLFW callbacks' targets). Calling them runs
 * every other mod's injection into them, exactly as a mouse wheel notch or click would.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onScroll")
    void gcp$invokeOnScroll(long window, double xOffset, double yOffset);

    @Invoker("onButton")
    void gcp$invokeOnButton(long window, MouseButtonInfo button, int action);
}
