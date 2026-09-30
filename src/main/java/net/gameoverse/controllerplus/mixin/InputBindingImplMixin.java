package net.gameoverse.controllerplus.mixin;

import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.bindings.InputBindingImpl;
import net.gameoverse.controllerplus.client.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Hook 2 of 2. {@code InputBindingImpl.pushState} computes the binding's analogue value and pushes
 * it into the history every output reads from. While a HOLD_WHILE or TOGGLE action holds this
 * binding, the pushed value is 1. Works for bindings with no button of their own.
 *
 * <p>A {@code @ModifyArg} on the boxing call rather than a {@code @ModifyVariable} on the
 * {@code analogue} local: the local is already loaded onto the stack before the push call, so the
 * boxing argument is the one place a change still reaches the history.
 */
@Mixin(value = InputBindingImpl.class)
abstract class InputBindingImplMixin {
    @ModifyArg(
            method = "pushState",
            at = @At(value = "INVOKE", target = "Ljava/lang/Float;valueOf(F)Ljava/lang/Float;"),
            require = 0)
    private float gcp$forceHeldBinding(float analogue) {
        if (Hooks.isForced(((InputBinding) (Object) this).id())) return 1f;
        return analogue;
    }
}
