package net.gameoverse.controllerplus.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spyglass Improvements (client mod, nothing copied). Its key ({@code key.spyglass-improvements.use},
 * our D-up hold toggle) raises a spyglass from the hands, the inventory or a Trinkets slot, or with none
 * at all in creative or with its "force spyglass" setting on. Without one it does nothing, but a held key
 * still counts as vanilla's use key ({@code MinecraftClientMixin}), so a toggled-on key with no spyglass
 * keeps eating or drawing a bow going. {@link #canScope} mirrors its checks so the toggle only turns on
 * when the key does something.
 */
public final class SpyglassImprovements {
    private static final Logger LOG = LoggerFactory.getLogger("gameoverse_controller_plus");
    public static final String KEY_ACTION = "controlify_modded:key.spyglass-improvements.use";
    private static final boolean TRINKETS = FabricLoader.getInstance().isModLoaded("trinkets");
    private static Method trinketsAttachment;
    private static Method trinketsEquipped;
    private static boolean trinketsFailed;
    private static boolean settingsFailed;

    private SpyglassImprovements() {
    }

    public static boolean isKey(String action) {
        return KEY_ACTION.equals(action);
    }

    /** The spyglass key would raise a spyglass for this player right now. */
    public static boolean canScope(LocalPlayer player) {
        if (player.isCreative() || forceSpyglassSetting()) return true;
        if (player.getMainHandItem().is(Items.SPYGLASS) || player.getOffhandItem().is(Items.SPYGLASS)) return true;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(Items.SPYGLASS)) return true;
        }
        return trinketSpyglass(player);
    }

    /** Spyglass Improvements' own {@code userForceSpyglass} setting (zoom with no spyglass at all). */
    private static boolean forceSpyglassSetting() {
        if (settingsFailed) return false;
        try {
            Class<?> client = Class.forName("me.juancarloscp52.spyglass_improvements.client.SpyglassImprovementsClient");
            Object instance = client.getMethod("getInstance").invoke(null);
            Object settings = client.getField("settings").get(instance);
            if (settings == null) return false;
            Field force = settings.getClass().getField("userForceSpyglass");
            return force.getBoolean(settings);
        } catch (ReflectiveOperationException | LinkageError e) {
            settingsFailed = true;
            LOG.warn("Could not read Spyglass Improvements' settings (mod changed?): {}", e.toString());
            return false;
        }
    }

    private static boolean trinketSpyglass(LocalPlayer player) {
        if (!TRINKETS || trinketsFailed) return false;
        try {
            if (trinketsAttachment == null) {
                Class<?> api = Class.forName("eu.pb4.trinkets.api.TrinketsApi");
                trinketsAttachment = api.getMethod("getAttachment", LivingEntity.class);
                trinketsEquipped = Class.forName("eu.pb4.trinkets.api.TrinketAttachment").getMethod("isEquipped", Item.class);
            }
            Object attachment = trinketsAttachment.invoke(null, player);
            return attachment != null && (boolean) trinketsEquipped.invoke(attachment, Items.SPYGLASS);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            trinketsFailed = true;
            LOG.warn("Could not check Trinkets for a spyglass (Trinkets changed?): {}", e.toString());
            return false;
        }
    }
}
