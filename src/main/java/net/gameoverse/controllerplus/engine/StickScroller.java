package net.gameoverse.controllerplus.engine;

/**
 * Turns a stick deflection into whole mouse wheel notches, one controller tick (50 ms) at a time.
 * <p>Controlify's virtual mouse scrolls by fractions of a notch every frame. Screens that read the
 * wheel as whole steps get nothing (Oracle Index truncates each call to an int) or a full step per
 * frame (page turns and zoom levels that read only the sign). This hands them whole notches: the
 * first as soon as the stick leaves the deadzone, then at a rate proportional to how far past the
 * deadzone it is pushed, up to {@code maxPerSecond} at full deflection.
 */
public final class StickScroller {
    /** Extra deadzone on top of Controlify's own stick deadzone, against drift. */
    public static final double DEADZONE = 0.15;
    public static final double TICKS_PER_SECOND = 20.0;

    private double accumulated;
    private int direction;

    /**
     * @param deflection   -1 to 1, positive = up (scroll up / wheel away from you)
     * @param maxPerSecond notches per second at full deflection
     * @return notches this tick, positive = up
     */
    public int tick(double deflection, double maxPerSecond) {
        double magnitude = Math.min(1.0, Math.abs(deflection));
        if (magnitude <= DEADZONE || !(maxPerSecond > 0)) {
            reset();
            return 0;
        }
        int dir = deflection > 0 ? 1 : -1;
        if (dir != direction) {
            direction = dir;
            accumulated = 1.0; // first notch at once
        } else {
            accumulated += (magnitude - DEADZONE) / (1.0 - DEADZONE) * maxPerSecond / TICKS_PER_SECOND;
        }
        int notches = (int) (accumulated + 1e-9); // no lost notch to rounding
        accumulated -= notches;
        return notches * dir;
    }

    public void reset() {
        accumulated = 0;
        direction = 0;
    }
}
