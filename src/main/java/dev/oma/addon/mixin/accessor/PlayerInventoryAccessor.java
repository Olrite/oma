package dev.oma.addon.mixin.accessor;

import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInventory.class)
public interface PlayerInventoryAccessor {
    @Accessor("selectedSlot")
    @Mutable
    void setSelectedSlot(int slot);
    
    @Accessor("selectedSlot")
    int getSelectedSlot();
}
