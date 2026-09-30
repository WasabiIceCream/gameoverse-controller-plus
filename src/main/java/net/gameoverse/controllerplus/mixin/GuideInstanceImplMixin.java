package net.gameoverse.controllerplus.mixin;

import dev.isxander.controlify.api.contextual.Context;
import dev.isxander.controlify.contextual.ContextualDomainImpl;
import dev.isxander.controlify.contextual.GuideRule;
import dev.isxander.controlify.gui.guide.GuideInstanceImpl;
import dev.isxander.controlify.gui.guide.PrecomputedLines;
import java.util.List;
import net.gameoverse.controllerplus.client.ButtonGuide;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controlify's button guide (in-game domain only; screen guides are left alone). Controlify's API
 * lets mods add data-driven guide rules, but a rule shows one binding's glyph with fixed text, and
 * nothing in the API can hide another rule. Our lines need live text (spell names), two glyphs
 * (chords) and to replace entries our binds make wrong, so two small hooks into
 * {@code GuideInstanceImpl.update}:
 * <ol>
 *   <li>the list of winning rules, as it is stored, loses the rules for buttons our binds change
 *       right now ({@link ButtonGuide#filter});</li>
 *   <li>at the end, our lines are appended to the left/right line lists ({@link ButtonGuide#extend}).</li>
 * </ol>
 * {@code require = 0}: if Controlify changes this method the mixin plugin logs a warning and the
 * guide simply shows Controlify's own entries.
 */
@Mixin(value = GuideInstanceImpl.class)
abstract class GuideInstanceImplMixin {
    @Shadow
    @Final
    private ContextualDomainImpl<?> domain;
    @Shadow
    @Final
    private Font font;
    @Shadow
    private PrecomputedLines leftGuides;
    @Shadow
    private PrecomputedLines rightGuides;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyVariable(method = "update", at = @At("STORE"), ordinal = 0, require = 0)
    private List gcp$filterGuideRules(List guides, Context context) {
        return ButtonGuide.filter((List<GuideRule>) guides, domain.id(), context);
    }

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void gcp$appendGuideLines(Context context, CallbackInfo ci) {
        PrecomputedLines[] lines = ButtonGuide.extend(leftGuides, rightGuides, font, domain.id());
        if (lines != null) {
            leftGuides = lines[0];
            rightGuides = lines[1];
        }
    }
}
