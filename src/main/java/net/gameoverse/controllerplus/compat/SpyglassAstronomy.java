package net.gameoverse.controllerplus.compat;

import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spyglass Astronomy (client mod, LGPL; nothing copied). It has no key bindings of its own: its
 * {@code SpyglassAstronomyClient.update()} (every client tick) polls vanilla keys while the player
 * is scoping:
 * <ul>
 *   <li>{@code key.pickItem} (middle mouse) going down cycles {@code editMode}: 0 normal, 1 draw
 *       constellations, 2 select stars/planets;</li>
 *   <li>{@code key.attack} held: mode 1 draws a line from the star nearest the crosshair to the one
 *       nearest where you look on release (the line follows the view while held); mode 2 selects the
 *       nearest object.</li>
 * </ul>
 * Naming and info are client commands ({@code /sga:name ...}, {@code /sga:info}). With a controller
 * RT is Controlify's attack (it emulates {@code key.attack}), so RT already draws; the pick key is
 * never set by Controlify (its pick block binding picks directly), so the mode could not be changed.
 * We read {@code editMode} (a public static int) by reflection for the guide and to gate the draw
 * action; the keys are driven through vanilla's own KeyMappings.
 */
public final class SpyglassAstronomy {
    private static final Logger LOG = LoggerFactory.getLogger("gameoverse_controller_plus");
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("spyglass_astronomy");
    private static Field editMode;
    private static boolean failed;

    private SpyglassAstronomy() {
    }

    /** 0 normal, 1 draw constellations, 2 select; -1 when the mod is missing or unreadable. */
    public static int editMode() {
        if (!LOADED || failed) return -1;
        try {
            if (editMode == null) {
                editMode = Class.forName("com.nettakrim.spyglass_astronomy.SpyglassAstronomyClient").getField("editMode");
            }
            return editMode.getInt(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            LOG.warn("Could not read Spyglass Astronomy's edit mode; its controller binds show no hints: {}", e.toString());
            return -1;
        }
    }
}
