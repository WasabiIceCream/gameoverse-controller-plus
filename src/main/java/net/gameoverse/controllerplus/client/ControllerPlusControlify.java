package net.gameoverse.controllerplus.client;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import dev.isxander.controlify.api.event.ControlifyEvents;
import dev.isxander.controlify.bindings.BindContext;
import net.gameoverse.controllerplus.config.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Controlify entrypoint: our own "open config" binding and lifecycle events. */
public final class ControllerPlusControlify implements ControlifyEntrypoint {
    private static final Logger LOG = LoggerFactory.getLogger(ControllerPlus.MOD_ID);
    private static InputBindingSupplier openConfig;

    @Override
    public void onControlifyPreInit(PreInitContext ctx) {
        openConfig = ctx.bindings().registerBinding(b -> b
                .id(ControllerPlus.MOD_ID, "open_config")
                .name(Component.translatable("gameoverse_controller_plus.binding.open_config"))
                .description(Component.translatable("gameoverse_controller_plus.binding.open_config.desc"))
                .category(Component.translatable("gameoverse_controller_plus.title"))
                .allowedContexts(BindContext.IN_GAME)
                .radialCandidate(true));
    }

    @Override
    public void onControlifyInit(InitContext ctx) {
        ControlifyEvents.CONTROLLER_DISCONNECTED.register(e -> ControllerPlus.get().onDisconnected(e.controller()));
        ControlifyEvents.INPUT_MODE_CHANGED.register(e -> {
            if (!e.mode().isController()) ControllerPlus.get().resetAll();
        });
        ControlifyEvents.ACTIVE_CONTROLLER_TICKED.register(e -> {
            if (openConfig == null) return;
            InputBinding binding = openConfig.onOrNull(e.controller());
            if (binding != null && binding.justPressed()) {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(ConfigScreen.create(mc.screen));
            }
        });
    }

    @Override
    public void onControllersDiscovered(ControlifyApi api) {
        if (HookStatus.maskHookChecked && !HookStatus.maskHookApplied) {
            LOG.error("Gameoverse Controller Plus is inactive: its Controlify input hook did not apply (Controlify version changed?)");
        }
    }
}
