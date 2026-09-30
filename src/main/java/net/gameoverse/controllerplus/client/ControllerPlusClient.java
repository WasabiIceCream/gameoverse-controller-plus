package net.gameoverse.controllerplus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class ControllerPlusClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ControllerPlus.get().load();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ControllerPlus.get().onClientTick());
    }
}
