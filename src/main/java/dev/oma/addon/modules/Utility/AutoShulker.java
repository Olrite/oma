package dev.oma.addon.modules.Utility;

import dev.oma.addon.Main;
import dev.oma.addon.mixin.accessor.PlayerInventoryAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoShulker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");

    // General Settings
    private final Setting<Integer> fillThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("fill-threshold")
        .description("Number of free slots before placing a shulker (0 = completely full).")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Delay in ticks between item transfers.")
        .defaultValue(1)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> itemsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("items-per-tick")
        .description("How many items to move per tick.")
        .defaultValue(1)
        .min(1)
        .sliderMax(9)
        .build()
    );

    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close")
        .description("Automatically close the shulker when done filling.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-toggle")
        .description("Toggle off after filling a shulker.")
        .defaultValue(false)
        .build()
    );

    private final Setting<PlacementMode> placementMode = sgGeneral.add(new EnumSetting.Builder<PlacementMode>()
        .name("placement-mode")
        .description("Where to place the shulker box.")
        .defaultValue(PlacementMode.InFront)
        .build()
    );

    // Filter Settings
    private final Setting<Boolean> keepHotbar = sgFilter.add(new BoolSetting.Builder()
        .name("keep-hotbar")
        .description("Don't move items from the hotbar. (Work In Progress)")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> keepShulkers = sgFilter.add(new BoolSetting.Builder()
        .name("keep-shulkers")
        .description("Don't move shulker boxes into the shulker.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepArmor = sgFilter.add(new BoolSetting.Builder()
        .name("keep-armor")
        .description("Don't move armor pieces.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepTools = sgFilter.add(new BoolSetting.Builder()
        .name("keep-tools")
        .description("Don't move tools (pickaxe, axe, shovel, hoe).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepWeapons = sgFilter.add(new BoolSetting.Builder()
        .name("keep-weapons")
        .description("Don't move weapons (sword, bow, crossbow, trident).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepFood = sgFilter.add(new BoolSetting.Builder()
        .name("keep-food")
        .description("Don't move food items.")
        .defaultValue(false)
        .build()
    );

    // State tracking
    private State currentState = State.IDLE;
    private BlockPos shulkerPos = null;
    private int delayCounter = 0;
    private int itemsMoved = 0;
    private int shulkerSlot = -1;
    private int previousSlot = -1;

    public AutoShulker() {
        super(Main.UTILS, "Auto Shulker", "Automatically places and fills shulker boxes when inventory is full.");
    }

    @Override
    public void onActivate() {
        currentState = State.IDLE;
        shulkerPos = null;
        delayCounter = 0;
        itemsMoved = 0;
        shulkerSlot = -1;
        previousSlot = -1;
    }

    @Override
    public void onDeactivate() {
        // Close any open screens
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            mc.player.closeHandledScreen();
        }
        
        // Restore previous slot if needed
        if (previousSlot != -1 && mc.player != null) {
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
        
        currentState = State.IDLE;
        shulkerPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        delayCounter++;

        switch (currentState) {
            case IDLE -> {
                if (shouldActivate()) {
                    shulkerSlot = findShulkerBox();
                    if (shulkerSlot == -1) {
                        error("No shulker box found in inventory!");
                        if (autoToggle.get()) toggle();
                        return;
                    }
                    currentState = State.PLACING;
                    delayCounter = 0;
                }
            }
            case PLACING -> {
                if (delayCounter >= tickDelay.get()) {
                    if (placeShulker()) {
                        currentState = State.OPENING;
                        delayCounter = 0;
                        if (previousSlot != -1 && mc.player != null) {
                            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(previousSlot);
                            previousSlot = -1;
                        }
                    } else {
                        error("Failed to place shulker box!");
                        currentState = State.IDLE;
                        if (autoToggle.get()) toggle();
                    }
                }
            }
            case OPENING -> {
                if (delayCounter >= tickDelay.get()) {
                    if (openShulker()) {
                        currentState = State.FILLING;
                        delayCounter = 0;
                        itemsMoved = 0;
                    }
                }
            }
            case FILLING -> {
                if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
                    // Screen was closed unexpectedly
                    currentState = State.IDLE;
                    shulkerPos = null;
                    return;
                }
                
                if (delayCounter >= tickDelay.get()) {
                    boolean filled = fillShulker();
                    if (filled) {
                        currentState = State.CLOSING;
                        delayCounter = 0;
                        info("Moved " + itemsMoved + " items into shulker box.");
                    }
                    delayCounter = 0;
                }
            }
            case CLOSING -> {
                if (autoClose.get()) {
                    mc.player.closeHandledScreen();
                }
                if (previousSlot != -1 && mc.player != null) {
                    ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(previousSlot);
                    previousSlot = -1;
                }
                currentState = State.IDLE;
                shulkerPos = null;
                if (autoToggle.get()) {
                    toggle();
                } else {
                    // Reset for next cycle
                    delayCounter = 0;
                }
            }
        }
    }

    private boolean shouldActivate() {
        int freeSlots = 0;
        for (int i = 9; i < 36; i++) { // Only check main inventory, not hotbar
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                freeSlots++;
            }
        }
        return freeSlots <= fillThreshold.get();
    }

    private int findShulkerBox() {
        // First check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isShulkerBox(stack)) {
                return i;
            }
        }
        // Then check main inventory
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isShulkerBox(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        return block instanceof ShulkerBoxBlock;
    }

    private boolean placeShulker() {
        if (shulkerSlot == -1) return false;

        // Save current slot and switch to shulker
        if (shulkerSlot >= 9) {
            // Shulker is not in hotbar, need to swap it
            int emptyHotbarSlot = findEmptyHotbarSlot();
            if (emptyHotbarSlot == -1) {
                // Use slot 0 if no empty slots
                emptyHotbarSlot = 0;
            }
            swapSlots(shulkerSlot, emptyHotbarSlot);
            shulkerSlot = emptyHotbarSlot;
        }

        PlayerInventoryAccessor inventory = (PlayerInventoryAccessor) mc.player.getInventory();
        previousSlot = inventory.getSelectedSlot();
        inventory.setSelectedSlot(shulkerSlot);

        // Determine placement position
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos placePos = switch (placementMode.get()) {
            case BelowPlayer -> playerPos.down();
            case InFront -> playerPos.offset(mc.player.getHorizontalFacing());
            case Above -> playerPos.up(2);
        };

        // Check if position is valid
        if (!mc.world.getBlockState(placePos).isReplaceable()) {
            error("Cannot place shulker - position blocked!");
            return false;
        }

        // Place the shulker
        shulkerPos = placePos;
        BlockHitResult hitResult = new BlockHitResult(
            Vec3d.ofCenter(placePos),
            Direction.UP,
            placePos,
            false
        );

        if (mc.interactionManager != null) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }

        return false;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void swapSlots(int slot1, int slot2) {
        if (mc.interactionManager == null) return;
        
        ScreenHandler handler = mc.player.currentScreenHandler;
        
        mc.interactionManager.clickSlot(
            handler.syncId,
            slot1,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
        
        mc.interactionManager.clickSlot(
            handler.syncId,
            slot2,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
        
        mc.interactionManager.clickSlot(
            handler.syncId,
            slot1,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }

    private boolean openShulker() {
        if (shulkerPos == null || mc.interactionManager == null) return false;

        // Verify shulker is still there
        if (!(mc.world.getBlockState(shulkerPos).getBlock() instanceof ShulkerBoxBlock)) {
            error("Shulker box not found at placement position!");
            return false;
        }

        BlockHitResult hitResult = new BlockHitResult(
            Vec3d.ofCenter(shulkerPos),
            Direction.UP,
            shulkerPos,
            false
        );

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        return true;
    }

    private boolean fillShulker() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == mc.player.playerScreenHandler) return true; // No shulker open

        int moved = 0;
        // Shulker slots are 0-26, player inventory starts after that
        int playerInvStart = handler.slots.size() - 36;

        for (int i = 0; i < 36 && moved < itemsPerTick.get(); i++) {
            // Skip hotbar if keepHotbar is enabled
            if (keepHotbar.get() && i < 9) continue;

            int slotIndex = playerInvStart + i;
            ItemStack stack = handler.getSlot(slotIndex).getStack();

            if (stack.isEmpty() || shouldKeepItem(stack)) continue;

            // Find empty slot in shulker (slots 0-26)
            for (int j = 0; j < 27; j++) {
                if (handler.getSlot(j).getStack().isEmpty()) {
                    if (mc.interactionManager != null) {
                        mc.interactionManager.clickSlot(
                            handler.syncId,
                            slotIndex,
                            0,
                            SlotActionType.QUICK_MOVE,
                            mc.player
                        );
                        moved++;
                        itemsMoved++;
                        break;
                    }
                }
            }
        }

        // Check if we're done (no more items to move or shulker is full)
        boolean hasItemsToMove = false;
        for (int i = 0; i < 36; i++) {
            if (keepHotbar.get() && i < 9) continue;
            
            int slotIndex = playerInvStart + i;
            ItemStack stack = handler.getSlot(slotIndex).getStack();
            
            if (!stack.isEmpty() && !shouldKeepItem(stack)) {
                hasItemsToMove = true;
                break;
            }
        }

        // Check if shulker is full
        boolean shulkerFull = true;
        for (int j = 0; j < 27; j++) {
            if (handler.getSlot(j).getStack().isEmpty()) {
                shulkerFull = false;
                break;
            }
        }

        return !hasItemsToMove || shulkerFull;
    }

    private boolean shouldKeepItem(ItemStack stack) {
        if (stack.isEmpty()) return true;

        // Keep shulker boxes
        if (keepShulkers.get() && isShulkerBox(stack)) return true;

        // Keep armor
        if (keepArmor.get() && isArmor(stack)) return true;

        // Keep tools
        if (keepTools.get() && isTool(stack)) return true;

        // Keep weapons
        if (keepWeapons.get() && isWeapon(stack)) return true;

        // Keep food
        if (keepFood.get() && stack.contains(DataComponentTypes.FOOD)) return true;

        return false;
    }

    private boolean isArmor(ItemStack stack) {
        String itemName = stack.getItem().toString().toLowerCase();
        return itemName.contains("helmet") || itemName.contains("chestplate") || 
               itemName.contains("leggings") || itemName.contains("boots") ||
               itemName.contains("elytra");
    }

    private boolean isTool(ItemStack stack) {
        String itemName = stack.getItem().toString().toLowerCase();
        return itemName.contains("pickaxe") || itemName.contains("axe") || 
               itemName.contains("shovel") || itemName.contains("hoe");
    }

    private boolean isWeapon(ItemStack stack) {
        String itemName = stack.getItem().toString().toLowerCase();
        return itemName.contains("sword") || itemName.contains("bow") || 
               itemName.contains("crossbow") || itemName.contains("trident");
    }

    private enum State {
        IDLE,
        PLACING,
        OPENING,
        FILLING,
        CLOSING
    }

    public enum PlacementMode {
        BelowPlayer,
        InFront,
        Above
    }
}

