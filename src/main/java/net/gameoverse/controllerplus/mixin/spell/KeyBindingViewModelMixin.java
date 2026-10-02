package net.gameoverse.controllerplus.mixin.spell;

import net.gameoverse.controllerplus.client.SpellHudGlyphs;
import net.minecraft.client.KeyMapping;
import net.spell_engine.client.gui.HudRenderHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spell Engine's spell hotbar key labels: the controller glyph's marker while a controller is in use. */
@Mixin(HudRenderHelper.SpellHotBarWidget.KeyBindingViewModel.class)
public class KeyBindingViewModelMixin {
    @Inject(method = "from", at = @At("HEAD"), cancellable = true)
    private static void gcp$controllerLabel(KeyMapping keyBinding,
                                            CallbackInfoReturnable<HudRenderHelper.SpellHotBarWidget.KeyBindingViewModel> cir) {
        String label = SpellHudGlyphs.label(keyBinding);
        if (label != null) cir.setReturnValue(new HudRenderHelper.SpellHotBarWidget.KeyBindingViewModel(label, null));
    }
}
