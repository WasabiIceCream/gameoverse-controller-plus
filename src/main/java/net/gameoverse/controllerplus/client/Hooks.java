package net.gameoverse.controllerplus.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * State shared with the two Controlify mixins. Only touched on the client thread.
 */
public final class Hooks {
    /** Controlify binding ids held down by a HOLD_WHILE or TOGGLE action, with a reference count. */
    private static final Map<Identifier, Integer> FORCED = new HashMap<>();

    private Hooks() {
    }

    public static boolean isForced(Identifier bindingId) {
        return !FORCED.isEmpty() && FORCED.containsKey(bindingId);
    }

    static void force(Identifier id) {
        FORCED.merge(id, 1, Integer::sum);
    }

    static void unforce(Identifier id) {
        FORCED.computeIfPresent(id, (k, v) -> v <= 1 ? null : v - 1);
    }

    static void clearForced() {
        FORCED.clear();
    }
}
