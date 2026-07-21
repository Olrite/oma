package dev.oma.addon.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.oma.addon.modules.Utility.FpsLimiter;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void oma$capFpsLimit(CallbackInfoReturnable<Integer> info) {
        Modules modules = Modules.get();
        if (modules == null) return;

        FpsLimiter module = modules.get(FpsLimiter.class);
        if (module == null || !module.shouldLimit()) return;

        info.setReturnValue(Math.min(info.getReturnValueI(), module.getFpsLimit()));
    }
}
