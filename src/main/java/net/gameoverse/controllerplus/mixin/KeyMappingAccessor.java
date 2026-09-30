package net.gameoverse.controllerplus.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A KeyMapping's pending click count, so an action can click one key (vanilla Drop) without a keyboard key bound. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int gcp$getClickCount();

    @Accessor("clickCount")
    void gcp$setClickCount(int count);

    /** The bound key (screen key presses need it). */
    @Accessor("key")
    InputConstants.Key gcp$getKey();
}
