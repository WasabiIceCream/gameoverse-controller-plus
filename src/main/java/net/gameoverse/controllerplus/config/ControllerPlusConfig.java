package net.gameoverse.controllerplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code config/gameoverse_controller_plus.json}. While {@link #customized} is false the file holds
 * no binds and the shipped {@link Defaults} are used, so layout updates reach everyone who hasn't
 * edited theirs.
 */
public final class ControllerPlusConfig {
    private static final Logger LOG = LoggerFactory.getLogger("gameoverse_controller_plus");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 2 since 1.0.3: a Scoped bind in Press mode fires once; Repeat mode repeats. 3 since 1.0.4: the
     * 1.0.3 default scoped B draw/select bind is gone.
     */
    public static final int CURRENT_VERSION = 4;

    public int version = CURRENT_VERSION;
    public boolean enabled = true;
    public boolean rumble = true;
    /**
     * Controlify's own continuous rumble while a spyglass is in use (its item-use rumble for the
     * spyglass animation). Off by default: it buzzed for as long as the spyglass stayed up.
     */
    public boolean scopeRumble = false;
    /**
     * Right-stick scrolling in whole wheel notches for screens that need them (1.0.5, see
     * {@code client.StickScroll}). Off gives those screens Controlify's own scrolling back.
     */
    public boolean stickScroll = true;
    /** A modifier held this long without a layer button does nothing on release. */
    public int modifierTapMs = 300;
    public boolean customized = false;
    public List<BindEntry> binds = new ArrayList<>();

    /** The binds in effect: the user's list when customized, otherwise the shipped defaults. */
    public List<BindEntry> effectiveBinds() {
        return customized ? binds : Defaults.binds();
    }

    public ControllerPlusConfig copy() {
        ControllerPlusConfig c = new ControllerPlusConfig();
        c.enabled = enabled;
        c.rumble = rumble;
        c.scopeRumble = scopeRumble;
        c.stickScroll = stickScroll;
        c.modifierTapMs = modifierTapMs;
        c.customized = true; // the copy always holds the full list; normalize() decides on save
        for (BindEntry e : effectiveBinds()) c.binds.add(e.copy());
        return c;
    }

    /** Called before saving an edited copy: stores binds only if they differ from the defaults. */
    public void normalize() {
        if (binds.equals(Defaults.binds())) {
            customized = false;
            binds = new ArrayList<>();
        } else {
            customized = true;
        }
        modifierTapMs = Math.clamp(modifierTapMs, 50, 2000);
        for (BindEntry e : binds) {
            e.ms = Math.clamp(e.ms, 50, 5000);
            e.windowMs = Math.clamp(e.windowMs, 50, 2000);
            e.count = Math.clamp(e.count, 2, 5);
        }
    }

    public static ControllerPlusConfig load(Path file) {
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                ControllerPlusConfig c = GSON.fromJson(r, ControllerPlusConfig.class);
                if (c != null) {
                    if (c.binds == null) c.binds = new ArrayList<>();
                    c.binds.removeIf(e -> e == null);
                    c.migrate();
                    return c;
                }
            } catch (IOException | JsonParseException e) {
                LOG.error("Could not read {}, using defaults: {}", file, e.toString());
            }
        }
        return new ControllerPlusConfig();
    }

    /**
     * Version 1 (1.0.0-1.0.2) Scoped binds in Press mode repeated while held; since version 2 that is
     * Repeat mode, and Press fires once. Keeps a customized zoom repeating.
     * <p>Version 2 (1.0.3) shipped a scoped "hold B = draw/select" default; a customized list still
     * holding exactly that entry loses it (an edited one, other button/action/mode/times, stays). If
     * the list then matches the defaults it goes back to following them.
     * <p>Version 4 (1.0.11) adds hold D-right = Apotheosis's boots toggle; a customized list gets it too unless it
     * already has a Hold bind on D-right.
     */
    void migrate() {
        if (version < 2) {
            for (BindEntry e : binds) {
                if (e.type == net.gameoverse.controllerplus.engine.TriggerType.SCOPED
                        && e.mode == net.gameoverse.controllerplus.engine.ActionMode.PRESS) {
                    e.mode = net.gameoverse.controllerplus.engine.ActionMode.REPEAT;
                }
            }
        }
        if (version < 3 && customized) {
            BindEntry legacy = Defaults.legacyScopedB();
            if (binds.removeIf(legacy::equals) && binds.equals(Defaults.binds())) {
                customized = false;
                binds = new ArrayList<>();
            }
        }
        if (version < 4 && customized) {
            BindEntry boots = Defaults.bootsToggle();
            boolean taken = binds.stream().anyMatch(e -> e.type == boots.type && java.util.Objects.equals(e.button, boots.button));
            if (!taken) {
                binds.add(boots);
            }
        }
        version = CURRENT_VERSION;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.error("Could not save {}: {}", file, e.toString());
        }
    }
}
