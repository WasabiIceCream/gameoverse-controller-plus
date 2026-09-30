package net.gameoverse.controllerplus.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StickScrollerTest {
    private static int total(StickScroller s, double deflection, double rate, int ticks) {
        int sum = 0;
        for (int i = 0; i < ticks; i++) sum += s.tick(deflection, rate);
        return sum;
    }

    @Test
    void insideDeadzoneNothing() {
        StickScroller s = new StickScroller();
        assertEquals(0, total(s, 0.0, 20, 40));
        assertEquals(0, total(s, 0.15, 20, 40));
        assertEquals(0, total(s, -0.1, 20, 40));
    }

    @Test
    void firstNotchAtOnce() {
        StickScroller s = new StickScroller();
        assertEquals(1, s.tick(0.2, 4));
        StickScroller d = new StickScroller();
        assertEquals(-1, d.tick(-0.2, 4));
    }

    @Test
    void fullDeflectionRunsAtMaxRate() {
        StickScroller s = new StickScroller();
        // 1 at once, then 20 per second for the remaining 19 ticks (0.95 s): 1 + 19 = 20
        assertEquals(20, total(s, 1.0, 20, 20));
        StickScroller slow = new StickScroller();
        // 4 per second: 1 at once, then one every 5 ticks
        assertEquals(1, total(slow, 1.0, 4, 5));
        assertEquals(1, slow.tick(1.0, 4)); // sixth tick: the second notch
    }

    @Test
    void rateProportionalToDeflection() {
        StickScroller full = new StickScroller();
        StickScroller half = new StickScroller();
        double halfway = StickScroller.DEADZONE + (1 - StickScroller.DEADZONE) / 2;
        int f = total(full, 1.0, 20, 201);
        int h = total(half, halfway, 20, 201);
        assertEquals(201, f); // 1 + 200 ticks * 1
        assertEquals(101, h); // 1 + 200 ticks * 0.5
    }

    @Test
    void neverMoreThanRateAllows() {
        StickScroller s = new StickScroller();
        assertEquals(1 + 30, total(s, 1.0, 40, 16)); // 1 + 15 ticks * 2
    }

    @Test
    void directionChangeStartsOver() {
        StickScroller s = new StickScroller();
        assertEquals(1, s.tick(1.0, 4));
        assertEquals(0, s.tick(1.0, 4));
        assertEquals(-1, s.tick(-1.0, 4));
        assertEquals(0, s.tick(-1.0, 4));
    }

    @Test
    void releaseClearsLeftover() {
        StickScroller s = new StickScroller();
        s.tick(1.0, 4);
        s.tick(1.0, 4);
        s.tick(1.0, 4); // 0.4 accumulated
        assertEquals(0, s.tick(0.0, 4));
        assertEquals(1, s.tick(1.0, 4)); // a new push: one at once again
        assertEquals(0, s.tick(1.0, 4));
    }

    @Test
    void offRateNothing() {
        StickScroller s = new StickScroller();
        assertEquals(0, total(s, 1.0, 0, 20));
    }
}
