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
            // MixinExtras applies @WrapOperation after this callback, so the call isn't visible yet; the handler
            // confirms itself on its first run instead (HookStatus.confirmMaskHook).
            HookStatus.maskHookChecked = true;
        } else if (mixinClassName.endsWith("InputBindingImplMixin")) {
            boolean ok = callsHandler(targetClass, "pushState", "gcp$forceHeldBinding");
            HookStatus.forceHookApplied = ok;
            HookStatus.forceHookChecked = true;
            report(ok, targetClassName, "held binding (InputBindingImpl.pushState)",
                    "Hold-while and toggle actions on Controlify bindings fall back to short presses.");
        } else if (mixinClassName.endsWith("VirtualMouseHandlerMixin")) {
            if (callsHandler(targetClass, "handleScroll", "gcp$stickScroll")) {
                LOG.info("Controlify hook applied: stick scrolling (VirtualMouseHandler.handleScroll)");
            } else {
                LOG.warn("Controlify hook NOT applied: stick scrolling; the Guide, Field Guide, Scholar books and other "
                        + "whole-notch screens scroll with Controlify's own (fractional) scrolling. "
                        + "Controlify's VirtualMouseHandler.handleScroll changed.");
            }
        } else if (mixinClassName.endsWith("GuideInstanceImplMixin")) {
            boolean filter = callsHandler(targetClass, "update", "gcp$filterGuideRules");
            boolean append = callsHandler(targetClass, "update", "gcp$appendGuideLines");
            if (filter && append) {
                LOG.info("Controlify hook applied: in-game button guide (GuideInstanceImpl.update)");
            } else {
                // Not worth the boxed error: only the guide hints are lost, every bind still works.
                LOG.warn("Controlify hook NOT applied: in-game button guide (filter {}, lines {}); the button guide "
                        + "shows Controlify's own entries only. Controlify's GuideInstanceImpl.update changed.", filter, append);
            }
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

    private static boolean callsHandler(ClassNode cls, String method, String handlerName) {
        for (MethodNode m : cls.methods) {
            if (!m.name.equals(method)) continue;
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
