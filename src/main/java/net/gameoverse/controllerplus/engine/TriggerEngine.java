package net.gameoverse.controllerplus.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Per-controller state machine that turns raw button presses into advanced-bind actions and
 * decides which buttons the controller's normal bindings should see.
 *
 * <p>Called once per controller state push (20 Hz). Pure Java, no Minecraft classes, so it can be
 * unit tested. Rules:
 * <ul>
 *   <li>Only buttons that take part in an advanced bind are ever held back. A button that is only a
 *       layer button (A under LB) is held back only while its modifier is held.</li>
 *   <li>A held-back button stays held back until it is physically released ("sticky"), even if the
 *       engine is deactivated or the modifier is released first.</li>
 *   <li>When a press resolves to a plain tap and the button has no TAP bind, the tap is replayed to
 *       the normal bindings as one pressed tick followed by one released tick.</li>
 * </ul>
 */
public final class TriggerEngine {

    public enum Kind { PRESS, START, STOP }

    /** An action to run. START/STOP bracket a held action (HOLD_WHILE, or TOGGLE on/off). */
    public record Event(Kind kind, Bind bind) {
    }

    /** What the normal bindings see this tick, plus the actions to run. */
    public record Result(Set<String> masked, Set<String> replay, List<Event> events) {
        public boolean changesView() {
            return !masked.isEmpty() || !replay.isEmpty();
        }
    }

    private enum Phase { IDLE, PRESSED, RESOLVED, PASSTHROUGH, WAIT_WINDOW }

    /** Everything configured on one primary button (a button with its own lifecycle). */
    private static final class Plan {
        final List<Bind> taps = new ArrayList<>();
        final List<Bind> holds = new ArrayList<>();
        final List<Bind> multiTaps = new ArrayList<>();
        final Map<String, List<Bind>> layers = new LinkedHashMap<>();
        boolean modifier;
        int holdTicks = Integer.MAX_VALUE;
        int maxCount;
        int windowTicks;

        boolean tapOnly() {
            return holds.isEmpty() && multiTaps.isEmpty() && !modifier;
        }
    }

    private static final class State {
        boolean now;
        boolean prev;
        Phase phase = Phase.IDLE;
        int t;
        int pendingTaps;
        int windowLeft;
        boolean layerUsed;
        boolean consumedAsLayer;
        boolean sticky;
        long pressOrder;
        int replayRemaining;
        boolean replayHigh;
        final List<Bind> heldActions = new ArrayList<>();
    }

    private final Map<String, Plan> plans = new LinkedHashMap<>();
    private final Map<String, State> states = new LinkedHashMap<>();
    private final Set<Integer> toggledOn = new LinkedHashSet<>();
    private final Map<Integer, Bind> bindsById = new LinkedHashMap<>();
    private final int modifierTapTicks;
    private boolean wasActive;
    private long pressCounter;

    /**
     * @param binds            enabled binds
     * @param modifierTapTicks a modifier (LB in "LB + A") held this long without a layer button
     *                         being used no longer replays its own tap on release
     */
    public TriggerEngine(List<Bind> binds, int modifierTapTicks) {
        this.modifierTapTicks = Math.max(1, modifierTapTicks);
        for (Bind bind : binds) {
            bindsById.put(bind.id(), bind);
            switch (bind.type()) {
                case TAP -> plan(bind.button()).taps.add(bind);
                case HOLD -> {
                    Plan p = plan(bind.button());
                    p.holds.add(bind);
                    p.holdTicks = Math.min(p.holdTicks, bind.ticks());
                }
                case MULTI_TAP -> {
                    Plan p = plan(bind.button());
                    p.multiTaps.add(bind);
                    p.maxCount = Math.max(p.maxCount, bind.count());
                    p.windowTicks = Math.max(p.windowTicks, bind.windowTicks());
                }
                case LAYER -> {
                    Plan m = plan(bind.other());
                    m.modifier = true;
                    m.layers.computeIfAbsent(bind.button(), k -> new ArrayList<>()).add(bind);
                    state(bind.button());
                }
                case CHORD -> {
                    Plan a = plan(bind.button());
                    Plan b = plan(bind.other());
                    a.modifier = true;
                    b.modifier = true;
                    a.layers.computeIfAbsent(bind.other(), k -> new ArrayList<>()).add(bind);
                    b.layers.computeIfAbsent(bind.button(), k -> new ArrayList<>()).add(bind);
                }
            }
        }
    }

    private Plan plan(String button) {
        state(button);
        return plans.computeIfAbsent(button, k -> new Plan());
    }

    private State state(String button) {
        return states.computeIfAbsent(button, k -> new State());
    }

    /** Every button any bind refers to. */
    public Set<String> trackedButtons() {
        return Collections.unmodifiableSet(states.keySet());
    }

    /**
     * Advances one tick.
     *
     * @param physical which buttons are physically down now
     * @param active   true when advanced binds should work (in game, no screen, this is the current
     *                 controller in controller input mode). While inactive, new presses pass straight
     *                 through; buttons already held back stay held back until released.
     */
    public Result tick(Predicate<String> physical, boolean active) {
        List<Event> events = new ArrayList<>();
        if (!active && wasActive) {
            softDeactivate(events);
        }
        wasActive = active;

        List<String> pressed = new ArrayList<>();
        List<String> released = new ArrayList<>();
        for (Map.Entry<String, State> e : states.entrySet()) {
            State s = e.getValue();
            s.now = physical.test(e.getKey());
            if (s.now && !s.prev) pressed.add(e.getKey());
            if (!s.now && s.prev) released.add(e.getKey());
        }

        // 1. Releases (before presses, so a modifier released on the same tick a layer button is
        //    pressed counts as released).
        for (String b : released) {
            onRelease(b, events);
        }

        Set<String> pressedThisTick = new HashSet<>(pressed);
        if (active) {
            // 2. Presses. Modifiers first, so "LB and A on the same tick" still makes a layer.
            pressed.sort((x, y) -> Boolean.compare(isModifier(y), isModifier(x)));
            for (String b : pressed) {
                onPress(b, events);
            }

            // 3. Held buttons: hold thresholds and timeouts.
            for (Map.Entry<String, State> e : states.entrySet()) {
                State s = e.getValue();
                if (s.phase == Phase.PRESSED && s.now && !pressedThisTick.contains(e.getKey())) {
                    s.t++;
                    onHeldTick(e.getKey(), s, events);
                }
            }

            // 4. Multi-tap windows.
            for (Map.Entry<String, State> e : states.entrySet()) {
                State s = e.getValue();
                if (s.phase == Phase.WAIT_WINDOW && !pressedThisTick.contains(e.getKey())
                        && !released.contains(e.getKey())) {
                    if (--s.windowLeft <= 0) {
                        resolveWindow(e.getKey(), events);
                    }
                }
            }
        }

        // 5. Output view.
        Set<String> masked = new LinkedHashSet<>();
        Set<String> replay = new LinkedHashSet<>();
        for (Map.Entry<String, State> e : states.entrySet()) {
            State s = e.getValue();
            if (s.replayRemaining > 0) {
                if (!s.replayHigh) {
                    replay.add(e.getKey());
                    s.replayHigh = true;
                } else {
                    s.replayHigh = false;
                    s.replayRemaining--;
                }
            }
            if (s.now && !replay.contains(e.getKey())
                    && (s.sticky || s.phase == Phase.PRESSED || s.phase == Phase.RESOLVED)) {
                masked.add(e.getKey());
            }
            s.prev = s.now;
        }
        return new Result(masked, replay, events);
    }

    private boolean isModifier(String b) {
        Plan p = plans.get(b);
        return p != null && p.modifier;
    }

    private void onRelease(String b, List<Event> events) {
        State s = states.get(b);
        for (Bind held : s.heldActions) {
            events.add(new Event(Kind.STOP, held));
        }
        s.heldActions.clear();
        s.sticky = false;
        s.consumedAsLayer = false;
        switch (s.phase) {
            case PRESSED -> {
                Plan p = plans.get(b);
                if (!p.multiTaps.isEmpty()) {
                    s.pendingTaps++;
                    s.phase = Phase.WAIT_WINDOW;
                    s.windowLeft = p.windowTicks;
                } else {
                    s.phase = Phase.IDLE;
                    singleTap(b, 1, events);
                }
            }
            case RESOLVED, PASSTHROUGH -> s.phase = Phase.IDLE;
            default -> {
            }
        }
    }

    private void onPress(String b, List<Event> events) {
        State s = states.get(b);

        // A layer button pressed while one of its modifiers is held.
        String modifier = null;
        long best = Long.MIN_VALUE;
        for (Map.Entry<String, Plan> e : plans.entrySet()) {
            String m = e.getKey();
            if (m.equals(b) || !e.getValue().modifier || !e.getValue().layers.containsKey(b)) continue;
            State ms = states.get(m);
            if (!ms.now || ms.consumedAsLayer) continue;
            if (ms.phase != Phase.PRESSED && ms.phase != Phase.RESOLVED) continue;
            if (ms.pressOrder > best) {
                best = ms.pressOrder;
                modifier = m;
            }
        }
        if (modifier != null) {
            if (s.phase == Phase.WAIT_WINDOW) {
                resolveWindow(b, events);
            }
            State ms = states.get(modifier);
            ms.layerUsed = true;
            ms.pendingTaps = 0;
            if (ms.phase == Phase.PRESSED) ms.phase = Phase.RESOLVED;
            s.sticky = true;
            s.consumedAsLayer = true;
            s.phase = Phase.IDLE;
            fire(plans.get(modifier).layers.get(b), b, events);
            return;
        }

        Plan p = plans.get(b);
        if (p == null) return; // only a layer button, and no modifier is held: untouched

        int count = s.phase == Phase.WAIT_WINDOW ? s.pendingTaps + 1 : 1;
        s.pressOrder = ++pressCounter;
        s.t = 0;
        s.layerUsed = false;
        s.consumedAsLayer = false;

        if (p.maxCount > 0 && count == p.maxCount) {
            s.pendingTaps = 0;
            s.phase = Phase.RESOLVED;
            fire(multiTapsWithCount(p, count), b, events);
            return;
        }
        if (p.tapOnly()) {
            s.pendingTaps = 0;
            s.phase = Phase.RESOLVED;
            fire(p.taps, b, events);
            return;
        }
        s.pendingTaps = count - 1;
        s.phase = Phase.PRESSED;
    }

    private void onHeldTick(String b, State s, List<Event> events) {
        Plan p = plans.get(b);
        if (!p.holds.isEmpty()) {
            if (!s.layerUsed && s.t >= p.holdTicks) {
                s.phase = Phase.RESOLVED;
                s.pendingTaps = 0;
                List<Bind> due = new ArrayList<>();
                for (Bind h : p.holds) {
                    if (h.ticks() == p.holdTicks) due.add(h);
                }
                fire(due, b, events);
            }
            return;
        }
        if (p.modifier) {
            if (!s.layerUsed && s.t >= modifierTapTicks) {
                // Held too long without a layer button: drop the tap, fire nothing.
                s.phase = Phase.RESOLVED;
                s.pendingTaps = 0;
            }
            return;
        }
        if (!p.multiTaps.isEmpty() && s.t >= p.windowTicks) {
            // Held longer than a tap with no hold bind: becomes a normal held press.
            s.phase = Phase.PASSTHROUGH;
            s.pendingTaps = 0;
        }
    }

    private void resolveWindow(String b, List<Event> events) {
        State s = states.get(b);
        Plan p = plans.get(b);
        int n = s.pendingTaps;
        s.pendingTaps = 0;
        s.phase = Phase.IDLE;
        if (n <= 0) return;
        List<Bind> matching = multiTapsWithCount(p, n);
        if (!matching.isEmpty()) {
            fire(matching, null, events);
        } else {
            singleTap(b, n, events);
        }
    }

    private void singleTap(String b, int times, List<Event> events) {
        Plan p = plans.get(b);
        if (p != null && !p.taps.isEmpty()) {
            for (int i = 0; i < times; i++) {
                fire(p.taps, null, events);
            }
        } else {
            states.get(b).replayRemaining += times;
        }
    }

    private static List<Bind> multiTapsWithCount(Plan p, int count) {
        List<Bind> out = new ArrayList<>();
        for (Bind m : p.multiTaps) {
            if (m.count() == count) out.add(m);
        }
        return out;
    }

    /**
     * @param tie the button whose release ends HOLD_WHILE actions, or null if the trigger is
     *            already over (HOLD_WHILE then acts as PRESS)
     */
    private void fire(List<Bind> binds, String tie, List<Event> events) {
        for (Bind bind : binds) {
            switch (bind.mode()) {
                case PRESS -> events.add(new Event(Kind.PRESS, bind));
                case HOLD_WHILE -> {
                    State ts = tie == null ? null : states.get(tie);
                    if (ts != null && ts.now) {
                        ts.heldActions.add(bind);
                        events.add(new Event(Kind.START, bind));
                    } else {
                        events.add(new Event(Kind.PRESS, bind));
                    }
                }
                case TOGGLE -> {
                    if (toggledOn.remove(bind.id())) {
                        events.add(new Event(Kind.STOP, bind));
                    } else {
                        toggledOn.add(bind.id());
                        events.add(new Event(Kind.START, bind));
                    }
                }
            }
        }
    }

    /** Screen opened, or this controller stopped being the active one: drop pending work, keep holds. */
    private void softDeactivate(List<Event> events) {
        for (State s : states.values()) {
            if (s.now && (s.phase == Phase.PRESSED || s.phase == Phase.RESOLVED)) {
                s.sticky = true;
            }
            s.phase = Phase.IDLE;
            s.pendingTaps = 0;
            s.windowLeft = 0;
            s.replayRemaining = 0;
            s.replayHigh = false;
            s.layerUsed = false;
        }
        for (Integer id : toggledOn) {
            events.add(new Event(Kind.STOP, bindsById.get(id)));
        }
        toggledOn.clear();
    }

    /**
     * Left the world, switched to keyboard, disconnected or the config changed: drop everything,
     * including held actions. Buttons still down stay held back until released.
     */
    public List<Event> hardReset() {
        List<Event> events = new ArrayList<>();
        if (wasActive) softDeactivate(events);
        else {
            for (Integer id : toggledOn) events.add(new Event(Kind.STOP, bindsById.get(id)));
            toggledOn.clear();
        }
        wasActive = false;
        for (State s : states.values()) {
            for (Bind held : s.heldActions) events.add(new Event(Kind.STOP, held));
            s.heldActions.clear();
        }
        return events;
    }
}
