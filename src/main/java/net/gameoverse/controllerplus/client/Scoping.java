package net.gameoverse.controllerplus.client;

import dev.isxander.controlify.rumble.ContinuousRumbleEffect;
import dev.isxander.controlify.rumble.effects.UseItemEffectHolder;
import net.gameoverse.controllerplus.mixin.MouseHandlerInvoker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemUseAnimation;

/** "Is the player scoped?" for the engine, and the synthetic mouse wheel step the scoped binds emit. */
final class Scoping {
    private static final String OK_ZOOMER_ZOOM = "key.ok_zoomer.zoom";

    private Scoping() {
    }

    /**
     * Vanilla spyglass, and Spyglass Improvements' spyglass key too: its {@code PlayerEntityMixin}
     * makes {@code Player.isScoping()} return true while its {@code force_spyglass} flag is set.
     * Ok Zoomer (not in the pack as of 1.0.1): its zoom key held down, if it is installed.
     */
    static boolean isScoped(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (player.isScoping()) return true;
        KeyMapping zoom = KeyMapping.get(OK_ZOOMER_ZOOM);
        return zoom != null && zoom.isDown();
    }

    /**
     * Stops Controlify's item-use rumble while a spyglass is in use. Controlify starts a
     * {@code ContinuousRumbleEffect} in {@code LocalPlayer.startUsingItem} for each use animation; for
     * SPYGLASS (and BLOCK, shields) it is a light pulse on the weak motor, 0.05 to 0.14, with no
     * timeout, stopped only by {@code stopUsingItem}. Spyglass Improvements' key (our D-up hold) uses
     * the spyglass item too, so the pad buzzed for as long as the spyglass was up. The effect is
     * exposed through Controlify's {@code UseItemEffectHolder} (implemented by its LocalPlayer mixin);
     * stopping it ends that one effect and leaves every other rumble (shield, bow, damage) alone.
     */
    static void silenceSpyglassRumble(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !player.isUsingItem()) return;
        if (player.getUseItem().getUseAnimation() != ItemUseAnimation.SPYGLASS) return;
        if (!(player instanceof UseItemEffectHolder holder)) return;
        ContinuousRumbleEffect effect = holder.controlify$getUseItemEffect();
        if (effect != null && !effect.isFinished()) effect.stop();
    }

    /**
     * One wheel notch (+1 up, -1 down) through {@code MouseHandler.onScroll}. Spyglass Improvements
     * cancels the hotbar change when it takes the scroll (scoped, first person); if nothing takes it,
     * the selected slot and flying speed are put back so the step never changes the held item.
     */
    static void scrollStep(int direction) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        Inventory inventory = player.getInventory();
        int slot = inventory.getSelectedSlot();
        float flyingSpeed = player.getAbilities().getFlyingSpeed();
        ((MouseHandlerInvoker) mc.mouseHandler).gcp$invokeOnScroll(mc.getWindow().handle(), 0, direction);
        if (mc.player != player) return;
        if (inventory.getSelectedSlot() != slot) inventory.setSelectedSlot(slot);
        if (player.getAbilities().getFlyingSpeed() != flyingSpeed) player.getAbilities().setFlyingSpeed(flyingSpeed);
    }
}
