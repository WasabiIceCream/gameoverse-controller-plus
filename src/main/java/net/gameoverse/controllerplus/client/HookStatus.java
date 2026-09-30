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

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("gameoverse_controller_plus");

    /** Called by the input hook each time it runs; logs once, the first time a controller state passes through. */
    public static void confirmMaskHook() {
        if (!maskHookApplied) {
            maskHookApplied = true;
            LOG.info("Controlify hook applied: binding state mask (InputComponent.pushState), confirmed by its first run");
        }
    }

    private HookStatus() {
    }
}
