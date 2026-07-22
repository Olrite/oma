package dev.oma.addon.mixin;

import dev.oma.addon.modules.Hunting.ItemHighlight;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void oma$highlightFoundItem(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;

        ItemHighlight highlight = modules.get(ItemHighlight.class);
        if (highlight == null || !highlight.shouldHighlightSlot(slot)) return;

        int color = highlight.getHighlightColor().getPacked();
        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }
}
