package dev.oma.addon.mixin.accessor;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Inventory.class)
public interface PlayerInventoryAccessor {
    @Accessor("selectedSlot")
    @Mutable
    void setSelectedSlot(int slot);
    
    @Accessor("selectedSlot")
    int getSelectedSlot();
}
