package dev.oma.addon.mixin;

import dev.oma.addon.modules.Utility.FpsLimiter;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public class InactivityFpsLimiterMixin {
    @Inject(method = "update", at = @At("RETURN"), cancellable = true)
    private void oma$capFpsLimit(CallbackInfoReturnable<Integer> info) {
        Modules modules = Modules.get();
        if (modules == null) return;

        FpsLimiter module = modules.get(FpsLimiter.class);
        if (module == null || !module.shouldLimit()) return;

        info.setReturnValue(Math.min(info.getReturnValueI(), module.getFpsLimit()));
    }
}
