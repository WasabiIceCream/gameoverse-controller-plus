package net.gameoverse.controllerplus.config;

import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.controller.input.GamepadInputs;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DropdownStringControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.gameoverse.controllerplus.client.ControllerPlus;
import net.gameoverse.controllerplus.engine.ActionMode;
import net.gameoverse.controllerplus.engine.TriggerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * YACL screen: general settings, then one collapsible group per advanced bind. Add, remove and
 * reset rebuild the screen around the same working copy, so nothing is written until Save.
 */
public final class ConfigScreen {
    private static final String K = "gameoverse_controller_plus.config.";

    private ConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return build(parent, ControllerPlus.get().config().copy());
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    private static OptionDescription desc(String key) {
        return OptionDescription.of(tr(key + ".desc"));
    }

    private static Screen build(Screen parent, ControllerPlusConfig work) {
        List<String> buttons = buttonIds();
        List<String> actions = actionIds();

        ConfigCategory.Builder general = ConfigCategory.createBuilder().name(tr("general"))
                .option(LabelOption.create(tr("intro")))
                .option(Option.<Boolean>createBuilder()
                        .name(tr("enabled")).description(desc("enabled"))
                        .binding(true, () -> work.enabled, v -> work.enabled = v)
                        .controller(TickBoxControllerBuilder::create).build())
                .option(Option.<Boolean>createBuilder()
                        .name(tr("rumble")).description(desc("rumble"))
                        .binding(true, () -> work.rumble, v -> work.rumble = v)
                        .controller(TickBoxControllerBuilder::create).build())
                .option(Option.<Integer>createBuilder()
                        .name(tr("modifier_tap_ms")).description(desc("modifier_tap_ms"))
                        .binding(300, () -> work.modifierTapMs, v -> work.modifierTapMs = v)
                        .controller(o -> IntegerSliderControllerBuilder.create(o).range(100, 1000).step(50)
                                .formatValue(v -> Component.literal(v + " ms")))
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(tr("reset")).description(desc("reset"))
                        .action((screen, button) -> {
                            work.binds = new ArrayList<>(Defaults.binds());
                            reopen(screen, parent, work);
                        }).build());

        ConfigCategory.Builder binds = ConfigCategory.createBuilder().name(tr("binds"))
                .option(LabelOption.create(tr("binds_help")))
                .option(ButtonOption.createBuilder()
                        .name(tr("add")).description(desc("add"))
                        .action((screen, button) -> {
                            work.binds.add(new BindEntry());
                            reopen(screen, parent, work);
                        }).build());

        for (int i = 0; i < work.binds.size(); i++) {
            BindEntry e = work.binds.get(i);
            BindEntry d = new BindEntry();
            final int index = i;
            OptionGroup.Builder g = OptionGroup.createBuilder()
                    .name(Component.literal((i + 1) + ". " + e.summary()))
                    .collapsed(true)
                    .option(Option.<Boolean>createBuilder()
                            .name(tr("bind.enabled"))
                            .binding(d.enabled, () -> e.enabled, v -> e.enabled = v)
                            .controller(TickBoxControllerBuilder::create).build())
                    .option(Option.<TriggerType>createBuilder()
                            .name(tr("bind.type")).description(desc("bind.type"))
                            .binding(d.type, () -> e.type, v -> e.type = v)
                            .controller(o -> EnumControllerBuilder.create(o).enumClass(TriggerType.class)
                                    .formatValue(v -> tr("type." + v.name().toLowerCase())))
                            .build())
                    .option(Option.<String>createBuilder()
                            .name(tr("bind.button")).description(desc("bind.button"))
                            .binding(d.button, () -> e.button, v -> e.button = v)
                            .controller(o -> DropdownStringControllerBuilder.create(o).values(buttons).allowAnyValue(true))
                            .build())
                    .option(Option.<String>createBuilder()
                            .name(tr("bind.modifier")).description(desc("bind.modifier"))
                            .binding(d.modifier, () -> e.modifier == null ? "" : e.modifier, v -> e.modifier = v)
                            .controller(o -> DropdownStringControllerBuilder.create(o).values(buttons).allowAnyValue(true).allowEmptyValue(true))
                            .build())
                    .option(Option.<Integer>createBuilder()
                            .name(tr("bind.ms")).description(desc("bind.ms"))
                            .binding(d.ms, () -> e.ms, v -> e.ms = v)
                            .controller(o -> IntegerSliderControllerBuilder.create(o).range(100, 2000).step(50)
                                    .formatValue(v -> Component.literal(v + " ms")))
                            .build())
                    .option(Option.<Integer>createBuilder()
                            .name(tr("bind.count")).description(desc("bind.count"))
                            .binding(d.count, () -> e.count, v -> e.count = v)
                            .controller(o -> IntegerSliderControllerBuilder.create(o).range(2, 5).step(1))
                            .build())
                    .option(Option.<Integer>createBuilder()
                            .name(tr("bind.window_ms")).description(desc("bind.window_ms"))
                            .binding(d.windowMs, () -> e.windowMs, v -> e.windowMs = v)
                            .controller(o -> IntegerSliderControllerBuilder.create(o).range(100, 1000).step(50)
                                    .formatValue(v -> Component.literal(v + " ms")))
                            .build())
                    .option(Option.<String>createBuilder()
                            .name(tr("bind.action")).description(desc("bind.action"))
                            .binding(d.action, () -> e.action, v -> e.action = v)
                            .controller(o -> DropdownStringControllerBuilder.create(o).values(actions).allowAnyValue(true))
                            .build())
                    .option(Option.<ActionMode>createBuilder()
                            .name(tr("bind.mode")).description(desc("bind.mode"))
                            .binding(d.mode, () -> e.mode, v -> e.mode = v)
                            .controller(o -> EnumControllerBuilder.create(o).enumClass(ActionMode.class)
                                    .formatValue(v -> tr("mode." + v.name().toLowerCase())))
                            .build())
                    .option(ButtonOption.createBuilder()
                            .name(tr("remove"))
                            .action((screen, button) -> {
                                applyPending(screen);
                                work.binds.remove(index);
                                Minecraft.getInstance().setScreen(build(parent, work));
                            }).build());
            binds.group(g.build());
        }

        return YetAnotherConfigLib.createBuilder()
                .title(tr("title"))
                .category(general.build())
                .category(binds.build())
                .save(() -> {
                    ControllerPlusConfig saved = work.copy();
                    ControllerPlus.get().saveAndApply(saved);
                })
                .build()
                .generateScreen(parent);
    }

    /** Keeps unsaved edits in the working copy before the screen is rebuilt. */
    private static void applyPending(YACLScreen screen) {
        for (ConfigCategory category : screen.config.categories()) {
            for (OptionGroup group : category.groups()) {
                for (Option<?> option : group.options()) {
                    if (!(option instanceof ButtonOption) && option.changed()) option.applyValue();
                }
            }
        }
    }

    private static void reopen(YACLScreen screen, Screen parent, ControllerPlusConfig work) {
        applyPending(screen);
        Minecraft.getInstance().setScreen(build(parent, work));
    }

    private static List<String> buttonIds() {
        List<String> out = new ArrayList<>();
        for (Identifier id : new Identifier[]{
                GamepadInputs.SOUTH_BUTTON, GamepadInputs.EAST_BUTTON, GamepadInputs.WEST_BUTTON, GamepadInputs.NORTH_BUTTON,
                GamepadInputs.LEFT_SHOULDER_BUTTON, GamepadInputs.RIGHT_SHOULDER_BUTTON,
                GamepadInputs.LEFT_STICK_BUTTON, GamepadInputs.RIGHT_STICK_BUTTON,
                GamepadInputs.BACK_BUTTON, GamepadInputs.START_BUTTON, GamepadInputs.GUIDE_BUTTON,
                GamepadInputs.DPAD_UP_BUTTON, GamepadInputs.DPAD_DOWN_BUTTON, GamepadInputs.DPAD_LEFT_BUTTON, GamepadInputs.DPAD_RIGHT_BUTTON,
                GamepadInputs.MISC_1_BUTTON, GamepadInputs.LEFT_PADDLE_1_BUTTON, GamepadInputs.LEFT_PADDLE_2_BUTTON,
                GamepadInputs.RIGHT_PADDLE_1_BUTTON, GamepadInputs.RIGHT_PADDLE_2_BUTTON, GamepadInputs.TOUCHPAD_1_BUTTON}) {
            out.add(id.toString());
        }
        return out;
    }

    private static List<String> actionIds() {
        TreeSet<String> ids = new TreeSet<>();
        try {
            ControlifyBindApi.get().getAllBindIds().forEach(id -> ids.add(id.toString()));
        } catch (RuntimeException ignored) {
            // Controlify not initialised yet: the field still accepts any id.
        }
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= 9; i++) out.add(Defaults.SPELL_SLOT + i);
        out.add(Defaults.SCROLL_UP);
        out.add(Defaults.SCROLL_DOWN);
        out.add(Defaults.DROP_ONE);
        out.addAll(ids);
        return out;
    }
}
