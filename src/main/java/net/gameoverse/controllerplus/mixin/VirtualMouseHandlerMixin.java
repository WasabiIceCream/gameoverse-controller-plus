package net.gameoverse.controllerplus.mixin;

import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler;
import net.gameoverse.controllerplus.client.StickScroll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controlify's virtual mouse adds the right stick to a pending scroll every controller tick
 * ({@code handleScroll}) and hands it out in fractions of a notch every frame ({@code updateMouse}).
 * For the screens {@link StickScroll} knows need whole notches, that accumulation is skipped and
 * {@link StickScroll} scrolls instead. {@code require = 0}: if the method changes, stick scrolling
 * stays Controlify's own everywhere (the mixin plugin logs which).
 */
@Mixin(value = VirtualMouseHandler.class)
abstract class VirtualMouseHandlerMixin {
    @Unique
    private static final Logger GCP$LOG = LoggerFactory.getLogger("gameoverse_controller_plus");
    @Unique
    private static boolean gcp$failed;

    @Inject(method = "handleScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void gcp$stickScroll(ControllerEntity controller, CallbackInfo ci) {
        if (gcp$failed) return;
        try {
            if (StickScroll.handle(controller)) ci.cancel();
        } catch (RuntimeException | LinkageError e) {
            gcp$failed = true;
            GCP$LOG.error("Stick scrolling failed; Controlify's own scrolling takes over", e);
        }
    }
}
