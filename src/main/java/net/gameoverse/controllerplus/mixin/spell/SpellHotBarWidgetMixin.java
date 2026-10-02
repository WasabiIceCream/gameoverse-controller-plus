package net.gameoverse.controllerplus.mixin.spell;

import net.gameoverse.controllerplus.client.SpellHudGlyphs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.spell_engine.client.gui.Drawable;
import net.spell_engine.client.gui.HudRenderHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws our marker labels (see {@link SpellHudGlyphs}) as Controlify glyphs instead of a key cap with text. */
@Mixin(HudRenderHelper.SpellHotBarWidget.class)
public class SpellHotBarWidgetMixin {
    @Inject(method = "drawKeybinding", at = @At("HEAD"), cancellable = true)
    private static void gcp$drawGlyph(GuiGraphicsExtractor context, Font textRenderer,
                                      HudRenderHelper.SpellHotBarWidget.KeyBindingViewModel keybinding, int x, int y,
                                      Drawable.Anchor horizontalAnchor, Drawable.Anchor verticalAnchor, CallbackInfo ci) {
        if (keybinding.drawable() != null) return;
        if (SpellHudGlyphs.draw(context, textRenderer, keybinding.label(), x, y,
                horizontalAnchor == Drawable.Anchor.LEADING, horizontalAnchor == Drawable.Anchor.TRAILING,
                verticalAnchor == Drawable.Anchor.TRAILING)) {
            ci.cancel();
        }
    }
}
