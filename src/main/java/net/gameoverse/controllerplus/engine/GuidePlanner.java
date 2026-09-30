package net.gameoverse.controllerplus.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which advanced binds Controlify's in-game button guide should show right now, and which
 * buttons' own Controlify guide entries are wrong at the moment. Pure Java (no Minecraft or
 * Controlify classes) so it can be unit tested; the client side turns the entries into glyphs and
 * text.
 *
 * <p>Situations, most specific first:
 * <ol>
 *   <li>Scoped: the Scoped binds (LB/RB zoom), plus the general entries of buttons without one.</li>
 *   <li>A layer modifier held (LB): that layer's buttons with what they do now (spell names), and
 *       the modifier's chord partners. Nothing else, so the list answers "what does A do now".</li>
 *   <li>Otherwise: "Hold LB: Spells" for each modifier whose layer has a spell right now, then every
 *       tap/hold/multi-tap/chord bind whose action the context rates as worth showing at this
 *       verbosity.</li>
 * </ol>
 */
public final class GuidePlanner {

    public enum Side { LEFT, RIGHT }

    /** How the entry reads: which buttons, and whether it is a hold, a layer summary, ... */
    public enum Form { TAP, HOLD, MULTI_TAP, CHORD, SCOPED, LAYER_BUTTON, LAYER_SUMMARY }

    /**
     * One guide line.
     *
     * @param buttons the buttons whose glyphs lead the line (two for a chord)
     * @param action  the bind's action; for LAYER_SUMMARY the action of the layer's first bind that
     *                is available
     * @param count   MULTI_TAP press count, otherwise 0
     */
    public record Entry(Side side, Form form, List<String> buttons, String action, int count) {
    }

    /** What the guide needs to know about the game right now. */
    public interface Context {
        /** Controlify's verbosity: 1 minimal, 2 reduced, 3 full. */
        int verbosity();

        boolean scoped();

        /** Whether the button is physically held. */
        boolean isDown(String button);

        /**
         * The lowest verbosity at which a hint for this action is worth a line right now (1-3), or 0
         * to hide it (nothing to do: no item to drop, no spell in that slot, mod missing).
         */
        int level(String action);
    }

    /** The planner's answer. {@code overridden}: buttons whose own Controlify guide entries are wrong now. */
    public record Plan(List<Entry> entries, Set<String> overridden) {
    }

    private final List<Bind> binds;
    /** Modifier -> its layer binds, in bind order. */
    private final Map<String, List<Bind>> layers = new LinkedHashMap<>();
    /** Button -> chords it takes part in. */
    private final Map<String, List<Bind>> chords = new LinkedHashMap<>();
    private final Set<String> scopedButtons = new LinkedHashSet<>();
    /** Buttons whose tap no longer reaches their normal Controlify binding. */
    private final Set<String> tapReplaced = new LinkedHashSet<>();

    public GuidePlanner(List<Bind> binds) {
        this.binds = List.copyOf(binds);
        for (Bind b : binds) {
            switch (b.type()) {
                case LAYER -> layers.computeIfAbsent(b.other(), k -> new ArrayList<>()).add(b);
                case CHORD -> {
                    chords.computeIfAbsent(b.button(), k -> new ArrayList<>()).add(b);
                    chords.computeIfAbsent(b.other(), k -> new ArrayList<>()).add(b);
                }
                case SCOPED -> scopedButtons.add(b.button());
                case TAP -> tapReplaced.add(b.button());
                default -> {
                }
            }
        }
    }

    public Plan plan(Context ctx) {
        List<Entry> out = new ArrayList<>();
        Set<String> overridden = new LinkedHashSet<>(tapReplaced);

        if (ctx.scoped() && !scopedButtons.isEmpty()) {
            for (Bind b : binds) {
                if (b.type() == TriggerType.SCOPED && ctx.level(b.action()) > 0) {
                    out.add(new Entry(Side.LEFT, Form.SCOPED, List.of(b.button()), b.action(), 0));
                }
            }
            overridden.addAll(scopedButtons);
            general(ctx, out, scopedButtons);
            return new Plan(List.copyOf(out), Set.copyOf(overridden));
        }

        String heldModifier = null;
        for (String m : layers.keySet()) {
            if (ctx.isDown(m)) {
                heldModifier = m;
                break;
            }
        }
        if (heldModifier != null) {
            for (Bind b : layers.getOrDefault(heldModifier, List.of())) {
                overridden.add(b.button());
                if (ctx.level(b.action()) > 0) {
                    out.add(new Entry(Side.LEFT, Form.LAYER_BUTTON, List.of(b.button()), b.action(), 0));
                }
            }
            for (Bind c : chords.getOrDefault(heldModifier, List.of())) {
                String partner = c.button().equals(heldModifier) ? c.other() : c.button();
                overridden.add(partner);
                if (ctx.level(c.action()) > 0) {
                    out.add(new Entry(Side.LEFT, Form.LAYER_BUTTON, List.of(partner), c.action(), 0));
                }
            }
            return new Plan(List.copyOf(out), Set.copyOf(overridden));
        }

        for (Map.Entry<String, List<Bind>> e : layers.entrySet()) {
            for (Bind b : e.getValue()) {
                int level = ctx.level(b.action());
                if (level > 0 && level <= ctx.verbosity()) {
                    out.add(new Entry(Side.LEFT, Form.LAYER_SUMMARY, List.of(e.getKey()), b.action(), 0));
                    break;
                }
            }
        }
        general(ctx, out, Set.of());
        return new Plan(List.copyOf(out), Set.copyOf(overridden));
    }

    /** Tap, hold, multi-tap and chord binds, skipping {@code skip} buttons. */
    private void general(Context ctx, List<Entry> out, Set<String> skip) {
        for (Bind b : binds) {
            Form form = switch (b.type()) {
                case TAP -> Form.TAP;
                case HOLD -> Form.HOLD;
                case MULTI_TAP -> Form.MULTI_TAP;
                case CHORD -> Form.CHORD;
                default -> null;
            };
            if (form == null || skip.contains(b.button()) || form == Form.CHORD && skip.contains(b.other())) continue;
            int level = ctx.level(b.action());
            if (level <= 0 || level > ctx.verbosity()) continue;
            List<String> buttons = form == Form.CHORD ? List.of(b.button(), b.other()) : List.of(b.button());
            Side side = form == Form.CHORD || isFaceOrMenu(b.button()) ? Side.LEFT : Side.RIGHT;
            out.add(new Entry(side, form, buttons, b.action(), form == Form.MULTI_TAP ? b.count() : 0));
        }
    }

    /** D-pad hints go right (next to Controlify's drop/use lines), everything else left. */
    private static boolean isFaceOrMenu(String button) {
        return !button.contains("dpad");
    }
}
