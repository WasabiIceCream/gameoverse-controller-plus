package net.gameoverse.controllerplus.client;

/**
 * Written by the mixin plugin while classes are being transformed, so it must not touch any game
 * class. Tells whether each Controlify hook really landed.
 */
public final class HookStatus {
    public static volatile boolean maskHookApplied;
    public static volatile boolean forceHookApplied;
    public static volatile boolean maskHookChecked;
    public static volatile boolean forceHookChecked;

    private HookStatus() {
    }
}
