package net.gameoverse.controllerplus.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One {@link TriggerEngine} per {@link InputContext}, ticked together for one controller. Each
 * engine only sees its own context's binds and is active only while its context is: the game engine
 * in the world with no screen, the screen engines in inventory and recipe screens. Their masks are
 * merged, so a button a screen bind uses is held back from Controlify's GUI actions only while that
 * screen is open, and a button held back in game stays held back (sticky) until released even if a
 * screen opens in between. Pure Java, unit tested.
 */
public final class EngineSet {
    private final Map<InputContext, TriggerEngine> engines = new EnumMap<>(InputContext.class);

    public EngineSet(List<Bind> binds, int modifierTapTicks) {
        Map<InputContext, List<Bind>> byContext = new EnumMap<>(InputContext.class);
        for (Bind b : binds) {
            byContext.computeIfAbsent(b.context(), k -> new ArrayList<>()).add(b);
        }
        byContext.forEach((ctx, list) -> engines.put(ctx, new TriggerEngine(list, modifierTapTicks)));
    }

    /** The contexts that have binds. */
    public Set<InputContext> contexts() {
        return Collections.unmodifiableSet(engines.keySet());
    }

    /** True while the player is scoped as of the last tick (game context only). */
    public boolean scopedNow() {
        TriggerEngine game = engines.get(InputContext.GAME);
        return game != null && game.scopedNow();
    }

    /**
     * @param active which contexts are active now (empty: none, e.g. a settings screen or a focused
     *               text field)
     * @param scoped the player looks through a spyglass (game context only)
     */
    public TriggerEngine.Result tick(Predicate<String> physical, Set<InputContext> active, boolean scoped) {
        if (engines.size() == 1) {
            Map.Entry<InputContext, TriggerEngine> only = engines.entrySet().iterator().next();
            return only.getValue().tick(physical, active.contains(only.getKey()),
                    scoped && only.getKey() == InputContext.GAME);
        }
        Set<String> masked = new LinkedHashSet<>();
        Set<String> replay = new LinkedHashSet<>();
        List<TriggerEngine.Event> events = new ArrayList<>();
        for (Map.Entry<InputContext, TriggerEngine> e : engines.entrySet()) {
            InputContext ctx = e.getKey();
            TriggerEngine.Result r = e.getValue().tick(physical, active.contains(ctx), scoped && ctx == InputContext.GAME);
            masked.addAll(r.masked());
            replay.addAll(r.replay());
            events.addAll(r.events());
        }
        // A button one engine holds back must not be replayed as pressed by another.
        replay.removeAll(masked);
        return new TriggerEngine.Result(masked, replay, events);
    }

    /** True while this TOGGLE bind is on. */
    public boolean isToggledOn(Bind bind) {
        TriggerEngine engine = engines.get(bind.context());
        return engine != null && engine.isToggledOn(bind.id());
    }

    /** Turns this TOGGLE bind off from outside: its STOP event, or nothing if it wasn't on. */
    public List<TriggerEngine.Event> dropToggle(Bind bind) {
        TriggerEngine engine = engines.get(bind.context());
        return engine == null ? List.of() : engine.dropToggle(bind.id());
    }

    /** Drops everything in every context (left the world, keyboard input, config change). */
    public List<TriggerEngine.Event> hardReset() {
        List<TriggerEngine.Event> events = new ArrayList<>();
        for (TriggerEngine engine : engines.values()) {
            events.addAll(engine.hardReset());
        }
        return events;
    }
}
