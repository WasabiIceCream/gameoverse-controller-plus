package net.gameoverse.controllerplus.client;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.ControllerStateView;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.gameoverse.controllerplus.config.BindEntry;
import net.gameoverse.controllerplus.config.ControllerPlusConfig;
import net.gameoverse.controllerplus.engine.Bind;
import net.gameoverse.controllerplus.engine.GuidePlanner;
import net.gameoverse.controllerplus.engine.TriggerEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client runtime: config, one trigger engine per controller, and the action driver. */
public final class ControllerPlus {
    public static final String MOD_ID = "gameoverse_controller_plus";
    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    private static final ControllerPlus INSTANCE = new ControllerPlus();

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
    private final ActionDriver driver = new ActionDriver();
    private final Map<String, Slot> engines = new HashMap<>();
    private final Map<String, Identifier> ids = new HashMap<>();
    private ControllerPlusConfig config = new ControllerPlusConfig();
    private List<Bind> compiled = List.of();
    private GuidePlanner planner = new GuidePlanner(List.of());
    private int modifierTapTicks = 6;

    private static final class Slot {
        final TriggerEngine engine;
        final ControllerEntity controller;
        boolean hardActive;

        Slot(TriggerEngine engine, ControllerEntity controller) {
            this.engine = engine;
            this.controller = controller;
        }
    }

    public static ControllerPlus get() {
        return INSTANCE;
    }

    public ControllerPlusConfig config() {
        return config;
    }

    public void load() {
        apply(ControllerPlusConfig.load(file), false);
    }

    /** Saves an edited config and rebuilds the engines. */
    public void saveAndApply(ControllerPlusConfig edited) {
        edited.normalize();
        apply(edited, true);
    }

    private void apply(ControllerPlusConfig next, boolean save) {
        resetAll();
        config = next;
        if (save) config.save(file);
        List<Bind> binds = new ArrayList<>();
        int id = 0;
        for (BindEntry e : config.effectiveBinds()) {
            Bind b = e.compile(id++);
            if (b != null) binds.add(b);
        }
        compiled = List.copyOf(binds);
        planner = new GuidePlanner(compiled);
        ButtonGuide.invalidate();
        modifierTapTicks = BindEntry.msToTicks(config.modifierTapMs);
        driver.rumble = config.rumble;
        LOG.info("Loaded {} advanced controller binds ({})", compiled.size(), config.customized ? "customized" : "defaults");
    }

    /** The button guide planner for the binds in effect, or null while advanced binds are off. */
    GuidePlanner guidePlanner() {
        return config.enabled && !compiled.isEmpty() ? planner : null;
    }

    /** Called from the InputComponent mixin once per state push, before any binding reads it. */
    public ControllerStateView onStatePush(ControllerEntity controller, ControllerStateView raw) {
        if (!config.enabled || compiled.isEmpty() || controller == null) return raw;
        Slot slot = engines.computeIfAbsent(controller.uid(),
                k -> new Slot(new TriggerEngine(compiled, modifierTapTicks), controller));

        ControlifyApi api = ControlifyApi.get();
        Minecraft mc = Minecraft.getInstance();
        boolean current = api.getCurrentController().orElse(null) == controller;
        boolean hardActive = current && api.currentInputMode().isController() && mc.level != null && mc.player != null;
        if (slot.hardActive && !hardActive) {
            driver.handle(slot.engine.hardReset(), controller);
        }
        slot.hardActive = hardActive;
        boolean active = hardActive && mc.screen == null;

        boolean scoped = active && Scoping.isScoped(mc);
        TriggerEngine.Result r = slot.engine.tick(b -> {
            Identifier bid = id(b);
            return bid != null && raw.isButtonDown(bid);
        }, active, scoped);
        if (!r.events().isEmpty()) driver.handle(r.events(), current ? controller : null);
        if (!r.changesView()) return raw;
        return new MaskedStateView(raw, toIds(r.masked()), toIds(r.replay()));
    }

    private static boolean engineErrorLogged;

    /** An engine bug must not take Controlify's input down with it: log once and carry on. */
    public static void logEngineError(RuntimeException e) {
        if (!engineErrorLogged) {
            engineErrorLogged = true;
            LOG.error("Advanced bind engine failed; passing input through unchanged", e);
        }
    }

    public void onClientTick() {
        driver.tick();
    }

    public void onDisconnected(ControllerEntity controller) {
        Slot slot = engines.remove(controller.uid());
        if (slot != null) driver.handle(slot.engine.hardReset(), null);
    }

    public void resetAll() {
        for (Slot slot : engines.values()) {
            driver.handle(slot.engine.hardReset(), null);
        }
        engines.clear();
        driver.releaseAll();
    }

    Identifier id(String s) {
        if (ids.containsKey(s)) return ids.get(s);
        Identifier parsed = Identifier.tryParse(s);
        ids.put(s, parsed);
        return parsed;
    }

    private Set<Identifier> toIds(Set<String> in) {
        if (in.isEmpty()) return Set.of();
        Set<Identifier> out = new LinkedHashSet<>();
        for (String s : in) {
            Identifier i = id(s);
            if (i != null) out.add(i);
        }
        return out;
    }
}
