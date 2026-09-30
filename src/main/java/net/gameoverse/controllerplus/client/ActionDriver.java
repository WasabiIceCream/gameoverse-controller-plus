package net.gameoverse.controllerplus.client;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.rumble.BasicRumbleEffect;
import dev.isxander.controlify.rumble.RumbleSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.gameoverse.controllerplus.compat.SpellSlots;
import net.gameoverse.controllerplus.config.Defaults;
import net.gameoverse.controllerplus.engine.Bind;
import net.gameoverse.controllerplus.engine.TriggerEngine;
import net.gameoverse.controllerplus.engine.TriggerType;
import net.gameoverse.controllerplus.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs engine events: presses and holds Controlify bindings, or Spell Engine's cast keys. */
final class ActionDriver {
    private static final Logger LOG = LoggerFactory.getLogger("gameoverse_controller_plus");
    private static final int PULSE_TICKS = 3;

    private final boolean spellEngine = FabricLoader.getInstance().isModLoaded("spell_engine");
    private final Map<Integer, KeyMapping> heldKeys = new HashMap<>();
    private final Map<Integer, Identifier> forcedByBind = new HashMap<>();
    private final Map<KeyMapping, Integer> pulses = new HashMap<>();
    private final Map<String, Identifier> ids = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    boolean rumble = true;

    void handle(List<TriggerEngine.Event> events, ControllerEntity controller) {
        for (TriggerEngine.Event e : events) {
            try {
                handle(e, controller);
            } catch (RuntimeException ex) {
                warnOnce(e.bind().action() + ":error", "Action {} failed: {}", e.bind().action(), ex.toString());
            }
        }
    }

    private void handle(TriggerEngine.Event e, ControllerEntity controller) {
        if (e.kind() == TriggerEngine.Kind.REPEAT) {
            // A REPEAT-mode follow-up: the same as a press, without the rumble.
            run(new TriggerEngine.Event(TriggerEngine.Kind.PRESS, e.bind()), controller, false);
        } else {
            run(e, controller, rumble);
        }
    }

    private void run(TriggerEngine.Event e, ControllerEntity controller, boolean rumble) {
        Bind bind = e.bind();
        if (Defaults.DROP_ONE.equals(bind.action())) {
            if (e.kind() != TriggerEngine.Kind.STOP) {
                rumble(e, bind, controller, rumble);
                dropOne();
            }
            return;
        }
        if (Defaults.SCROLL_UP.equals(bind.action()) || Defaults.SCROLL_DOWN.equals(bind.action())) {
            // Every press or repeat is one wheel notch; Hold While and Toggle just step once on start.
            if (e.kind() != TriggerEngine.Kind.STOP) Scoping.scrollStep(Defaults.SCROLL_UP.equals(bind.action()) ? 1 : -1);
            return;
        }
        int spellSlot = spellSlot(bind.action());
        rumble(e, bind, controller, rumble);
        if (spellSlot > 0) {
            if (!spellEngine) return;
            switch (e.kind()) {
                case PRESS -> {
                    KeyMapping key = SpellSlots.resolve(spellSlot);
                    if (key != null) {
                        key.setDown(true);
                        pulses.put(key, PULSE_TICKS);
                    }
                }
                case START -> {
                    KeyMapping key = SpellSlots.resolve(spellSlot);
                    if (key != null) {
                        key.setDown(true);
                        heldKeys.put(bind.id(), key);
                    }
                }
                case STOP -> {
                    KeyMapping key = heldKeys.remove(bind.id());
                    if (key != null && !heldKeys.containsValue(key) && !pulses.containsKey(key)) key.setDown(false);
                }
            }
            return;
        }

        Identifier id = id(bind.action());
        if (id == null) return;
        switch (e.kind()) {
            case PRESS -> {
                InputBinding binding = controller == null ? null : controller.input().map(i -> i.getBinding(id)).orElse(null);
                if (binding != null) {
                    binding.fakePress();
                } else {
                    warnOnce(bind.action(), "No Controlify binding {} (is its mod installed?)", bind.action());
                }
            }
            case START -> {
                if (!HookStatus.forceHookApplied) {
                    // Hold hook missing (Controlify changed): best effort is a short press.
                    InputBinding binding = controller == null ? null : controller.input().map(i -> i.getBinding(id)).orElse(null);
                    if (binding != null) binding.fakePress();
                    return;
                }
                if (forcedByBind.put(bind.id(), id) == null) Hooks.force(id);
            }
            case STOP -> {
                Identifier was = forcedByBind.remove(bind.id());
                if (was != null) Hooks.unforce(was);
            }
        }
    }

    private static void rumble(TriggerEngine.Event e, Bind bind, ControllerEntity controller, boolean rumble) {
        if (e.kind() != TriggerEngine.Kind.STOP && rumble && controller != null
                && (bind.type() == TriggerType.HOLD || bind.type() == TriggerType.MULTI_TAP)) {
            ControlifyApi.get().playRumbleEffect(RumbleSource.INTERACTION, BasicRumbleEffect.constant(0.25f, 0.1f, 3));
        }
    }

    /**
     * One click of vanilla's Drop key: Minecraft's own key handling then drops one item from the held
     * stack ({@code while (keyDrop.consumeClick())}), exactly like a keyboard press of Q.
     */
    private static void dropOne() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        KeyMappingAccessor drop = (KeyMappingAccessor) mc.options.keyDrop;
        drop.gcp$setClickCount(drop.gcp$getClickCount() + 1);
    }

    /** Once per client tick: keep held spell keys down, end short presses. */
    void tick() {
        for (KeyMapping key : heldKeys.values()) {
            if (!key.isDown()) key.setDown(true);
        }
        Iterator<Map.Entry<KeyMapping, Integer>> it = pulses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<KeyMapping, Integer> p = it.next();
            if (p.getValue() <= 1) {
                it.remove();
                if (!heldKeys.containsValue(p.getKey())) p.getKey().setDown(false);
            } else {
                p.setValue(p.getValue() - 1);
            }
        }
    }

    /** Drops everything still held (used after all engines were reset). */
    void releaseAll() {
        for (KeyMapping key : heldKeys.values()) key.setDown(false);
        heldKeys.clear();
        for (KeyMapping key : pulses.keySet()) key.setDown(false);
        pulses.clear();
        forcedByBind.clear();
        Hooks.clearForced();
    }

    static int spellSlot(String action) {
        if (action == null || !action.startsWith(Defaults.SPELL_SLOT)) return 0;
        try {
            return Integer.parseInt(action.substring(Defaults.SPELL_SLOT.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Identifier id(String action) {
        return ids.computeIfAbsent(action, a -> {
            Identifier parsed = Identifier.tryParse(a);
            if (parsed == null) warnOnce(a, "Invalid action id {}", a);
            return parsed;
        });
    }

    private void warnOnce(String key, String msg, Object... args) {
        if (warned.add(key)) LOG.warn(msg, args);
    }
}
