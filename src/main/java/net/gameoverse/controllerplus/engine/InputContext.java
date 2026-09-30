package net.gameoverse.controllerplus.engine;

/** Where an advanced bind works. Each context gets its own {@link TriggerEngine} (see {@link EngineSet}). */
public enum InputContext {
    /** In the world with no screen open (the only context before 1.0.3). */
    GAME,
    /**
     * An inventory/container screen, or JEI's recipe screen, with no text field focused. The engine
     * masks only the buttons its screen binds use, so Controlify's own GUI actions keep working.
     */
    SCREEN,
    /** JEI's recipe screen only (also counts as {@link #SCREEN}). */
    RECIPE_SCREEN
}
