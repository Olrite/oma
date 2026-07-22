package dev.oma.addon.mixin;

import dev.oma.addon.modules.Hunting.ItemHighlight;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void oma$highlightFoundItem(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;

        ItemHighlight highlight = modules.get(ItemHighlight.class);
        if (highlight == null || !highlight.shouldHighlightSlot(slot)) return;

        int color = highlight.getHighlightColor().getPacked();
        extractor.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }
}
