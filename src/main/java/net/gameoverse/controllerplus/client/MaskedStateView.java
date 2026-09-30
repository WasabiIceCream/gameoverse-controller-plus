package net.gameoverse.controllerplus.client;

import dev.isxander.controlify.controller.input.ControllerStateView;
import dev.isxander.controlify.controller.input.HatState;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * The state handed to Controlify's bindings: the real state, except that buttons the engine is
 * holding back read as released and buttons it is replaying read as pressed. The component's own
 * {@code stateNow()} stays raw.
 */
final class MaskedStateView implements ControllerStateView {
    private final ControllerStateView raw;
    private final Set<Identifier> masked;
    private final Set<Identifier> replay;

    MaskedStateView(ControllerStateView raw, Set<Identifier> masked, Set<Identifier> replay) {
        this.raw = raw;
        this.masked = masked;
        this.replay = replay;
    }

    @Override
    public boolean isButtonDown(Identifier button) {
        if (replay.contains(button)) return true;
        if (masked.contains(button)) return false;
        return raw.isButtonDown(button);
    }

    @Override
    public Set<Identifier> getButtons() {
        return raw.getButtons();
    }

    @Override
    public float getAxisState(Identifier axis) {
        return raw.getAxisState(axis);
    }

    @Override
    public Set<Identifier> getAxes() {
        return raw.getAxes();
    }

    @Override
    public float getAxisResting(Identifier axis) {
        return raw.getAxisResting(axis);
    }

    @Override
    public HatState getHatState(Identifier hat) {
        return raw.getHatState(hat);
    }

    @Override
    public Set<Identifier> getHats() {
        return raw.getHats();
    }
}
