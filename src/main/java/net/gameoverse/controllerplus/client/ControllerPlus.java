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
import net.gameoverse.controllerplus.compat.SpyglassImprovements;
import net.gameoverse.controllerplus.config.BindEntry;
import net.gameoverse.controllerplus.config.ControllerPlusConfig;
import net.gameoverse.controllerplus.engine.ActionMode;
import net.gameoverse.controllerplus.engine.Bind;
import net.gameoverse.controllerplus.engine.EngineSet;
import net.gameoverse.controllerplus.engine.InputContext;
import net.gameoverse.controllerplus.engine.GuidePlanner;
import net.gameoverse.controllerplus.engine.TriggerEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
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
        final EngineSet engine;
        final ControllerEntity controller;
        boolean hardActive;
        /** Spyglass toggles that are on: ticks since start without scoping or another item in use. */
        final Map<Bind, Integer> spyglassIdle = new HashMap<>();

        Slot(EngineSet engine, ControllerEntity controller) {
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
                k -> new Slot(new EngineSet(compiled, modifierTapTicks), controller));

        ControlifyApi api = ControlifyApi.get();
        Minecraft mc = Minecraft.getInstance();
        boolean current = api.getCurrentController().orElse(null) == controller;
        boolean hardActive = current && api.currentInputMode().isController() && mc.level != null && mc.player != null;
        if (slot.hardActive && !hardActive) {
            driver.handle(slot.engine.hardReset(), controller);
        }
        slot.hardActive = hardActive;
        Set<InputContext> contexts = hardActive ? Contexts.of(mc.screen) : Set.of();

        boolean scoped = contexts.contains(InputContext.GAME) && Scoping.isScoped(mc);
        TriggerEngine.Result r = slot.engine.tick(b -> {
            Identifier bid = id(b);
            return bid != null && raw.isButtonDown(bid);
        }, contexts, scoped);
        List<TriggerEngine.Event> events = spyglassGate(slot, r.events(), mc, scoped);
        if (!events.isEmpty()) driver.handle(events, current ? controller : null);
        if (!r.changesView()) return raw;
        return new MaskedStateView(raw, toIds(r.masked()), toIds(r.replay()));
    }

    /** Ticks a spyglass toggle may stay on without the spyglass coming up before it is released. */
    private static final int SPYGLASS_GRACE_TICKS = 10;

    /**
     * The spyglass key toggle only turns on when Spyglass Improvements would raise a spyglass; without
     * one the held key just keeps any other item use going (see {@link SpyglassImprovements}). A toggle
     * that is on but not scoping (the spyglass was dropped, or never came up) is released after a short
     * grace, not counting ticks where another item is in use (the spyglass comes up after it).
     */
    private List<TriggerEngine.Event> spyglassGate(Slot slot, List<TriggerEngine.Event> in, Minecraft mc, boolean scoped) {
        LocalPlayer player = mc.player;
        List<TriggerEngine.Event> out = new ArrayList<>(in.size());
        for (TriggerEngine.Event e : in) {
            if (!SpyglassImprovements.isKey(e.bind().action()) || e.bind().mode() != ActionMode.TOGGLE) {
                out.add(e);
                continue;
            }
            if (e.kind() == TriggerEngine.Kind.START) {
                if (player != null && !SpyglassImprovements.canScope(player)) {
                    slot.engine.dropToggle(e.bind());
                    player.sendOverlayMessage(Component.translatable("gameoverse_controller_plus.no_spyglass"));
                    continue;
                }
                slot.spyglassIdle.put(e.bind(), 0);
            } else if (e.kind() == TriggerEngine.Kind.STOP) {
                slot.spyglassIdle.remove(e.bind());
            }
            out.add(e);
        }
        var it = slot.spyglassIdle.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!slot.engine.isToggledOn(entry.getKey())) {
                it.remove();
            } else if (scoped || (player != null && player.isUsingItem())) {
                entry.setValue(0);
            } else if (entry.getValue() + 1 > SPYGLASS_GRACE_TICKS) {
                out.addAll(slot.engine.dropToggle(entry.getKey()));
                it.remove();
            } else {
                entry.setValue(entry.getValue() + 1);
            }
        }
        return out;
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
        if (!config.scopeRumble && !rumbleHookFailed) {
            try {
                Scoping.silenceSpyglassRumble(Minecraft.getInstance());
            } catch (RuntimeException | LinkageError e) {
                rumbleHookFailed = true;
                LOG.warn("Could not stop Controlify's spyglass rumble (Controlify changed?): {}", e.toString());
            }
        }
    }

    private static boolean rumbleHookFailed;

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
