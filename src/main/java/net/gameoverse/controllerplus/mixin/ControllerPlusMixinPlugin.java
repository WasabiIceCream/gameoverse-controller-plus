package net.gameoverse.controllerplus.mixin;

import java.util.List;
import java.util.Set;
import net.gameoverse.controllerplus.client.HookStatus;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Confirms after transformation that each hook really landed in {@code pushState}. Both injectors
 * use {@code require = 0} so a Controlify change can't crash the game; this is what makes such a
 * change loud in the log instead of silent.
 */
public final class ControllerPlusMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("gameoverse_controller_plus");

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith("InputComponentMixin")) {
            boolean ok = callsHandler(targetClass, "gcp$maskBindingState");
            HookStatus.maskHookApplied = ok;
            HookStatus.maskHookChecked = true;
            report(ok, targetClassName, "binding state mask (InputComponent.pushState)",
                    "Gameoverse Controller Plus is DISABLED: advanced binds will not work until it is updated for this Controlify version.");
        } else if (mixinClassName.endsWith("InputBindingImplMixin")) {
            boolean ok = callsHandler(targetClass, "gcp$forceHeldBinding");
            HookStatus.forceHookApplied = ok;
            HookStatus.forceHookChecked = true;
            report(ok, targetClassName, "held binding (InputBindingImpl.pushState)",
                    "Hold-while and toggle actions on Controlify bindings fall back to short presses.");
        }
    }

    private static void report(boolean ok, String target, String what, String consequence) {
        if (ok) {
            LOG.info("Controlify hook applied: {}", what);
        } else {
            LOG.error("==========================================================================");
            LOG.error("Controlify hook NOT applied: {} in {}.", what, target);
            LOG.error("{} Controlify's internals changed; the mixin targets need a recheck.", consequence);
            LOG.error("==========================================================================");
        }
    }

    private static boolean callsHandler(ClassNode cls, String handlerName) {
        for (MethodNode m : cls.methods) {
            if (!m.name.equals("pushState")) continue;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.contains(handlerName)) return true;
            }
        }
        return false;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
