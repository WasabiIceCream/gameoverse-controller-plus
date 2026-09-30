package net.gameoverse.controllerplus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.controller.input.ControllerStateView;
import dev.isxander.controlify.controller.input.InputComponent;
import net.gameoverse.controllerplus.client.ControllerPlus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hook 1 of 2. {@code InputComponent.pushState} stores the new controller state, then hands it to
 * every binding in a loop. We wrap that per-binding call: the first call of each push runs the
 * trigger engine on the raw state, and every binding then receives the masked view (held-back
 * buttons released, replayed taps pressed). {@code stateNow()} is left raw.
 *
 * <p>{@code require = 0}: if Controlify changes this method, the mixin plugin logs an error and the
 * mod stays inactive instead of crashing the game.
 */
@Mixin(value = InputComponent.class)
abstract class InputComponentMixin {
    @Unique
    private ControllerStateView gcp$lastRaw;
    @Unique
    private ControllerStateView gcp$view;

    @WrapOperation(
            method = "pushState",
            at = @At(value = "INVOKE",
                    target = "Ldev/isxander/controlify/api/bind/InputBinding;pushState(Ldev/isxander/controlify/controller/input/ControllerStateView;)V"),
            require = 0)
    private void gcp$maskBindingState(InputBinding binding, ControllerStateView state, Operation<Void> original) {
        if (state != gcp$lastRaw) {
            // A new push: each push builds a fresh deadzone view, so identity marks the first binding.
            gcp$lastRaw = state;
            ControllerStateView view = state;
            try {
                view = ControllerPlus.get().onStatePush(((InputComponent) (Object) this).getController(), state);
            } catch (RuntimeException e) {
                ControllerPlus.logEngineError(e);
            }
            gcp$view = view;
        }
        original.call(binding, gcp$view);
    }
}
