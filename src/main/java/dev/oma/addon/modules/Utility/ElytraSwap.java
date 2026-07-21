package dev.oma.addon.modules.Utility;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ElytraSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCombat = settings.createGroup("Combat Protection");

    // General Settings
    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Swap elytra when durability drops below this percentage.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> onlyWhileFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-flying")
        .description("Only swap elytras while actively flying.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseInInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-inventory")
        .description("Don't swap while inventory is open to prevent desync.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swapCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("swap-cooldown")
        .description("Ticks to wait after swapping before checking again.")
        .defaultValue(100)
        .min(20)
        .max(200)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Boolean> notifySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-swap")
        .description("Send a chat message when swapping elytras.")
        .defaultValue(true)
        .build()
    );

    // Combat Protection Settings
    private final Setting<Boolean> swapOnHit = sgCombat.add(new BoolSetting.Builder()
        .name("swap-on-hit")
        .description("Automatically swap elytra to chestplate when hit by an entity.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitProtectionDuration = sgCombat.add(new IntSetting.Builder()
        .name("protection-duration")
        .description("Ticks to keep chestplate equipped after being hit.")
        .defaultValue(60)
        .min(20)
        .max(200)
        .sliderRange(20, 200)
        .visible(swapOnHit::get)
        .build()
    );

    private final Setting<Boolean> autoSwapBack = sgCombat.add(new BoolSetting.Builder()
        .name("auto-swap-back")
        .description("Automatically swap back to elytra after protection duration.")
        .defaultValue(true)
        .visible(swapOnHit::get)
        .build()
    );

    private final Setting<Boolean> prioritizeNetherite = sgCombat.add(new BoolSetting.Builder()
        .name("prioritize-netherite")
        .description("Prioritize netherite chestplates over diamond.")
        .defaultValue(true)
        .visible(swapOnHit::get)
        .build()
    );

    // State tracking
    private int cooldownTimer = 0;
    private boolean needsSwap = false;
    private int swapStage = 0;
    private int stageTimer = 0;
    private int targetSlot = -1;
    private int newElytraOriginalSlot = -1;
    private int hotbarSlotUsed = -1;
    private ItemStack hotbarOriginalItem = ItemStack.EMPTY;
    private boolean protectionActive = false;
    private int protectionTimer = 0;
    private int lastHurtTime = 0;
    private boolean needsChestplateSwap = false;
    private int chestplateSwapStage = 0;
    private int chestplateSlot = -1;
    private ItemStack storedElytra = ItemStack.EMPTY;

    public ElytraSwap() {
        super(Main.UTILS, "elytra-swap", "Automatically swaps elytras when they reach low durability.");
    }

    @Override
    public void onActivate() {
        resetSwapState();
    }

    @Override
    public void onDeactivate() {
        resetSwapState();
    }

    private void resetSwapState() {
        cooldownTimer = 0;
        needsSwap = false;
        swapStage = 0;
        stageTimer = 0;
        targetSlot = -1;
        newElytraOriginalSlot = -1;
        hotbarSlotUsed = -1;
        hotbarOriginalItem = ItemStack.EMPTY;
        protectionActive = false;
        protectionTimer = 0;
        lastHurtTime = 0;
        needsChestplateSwap = false;
        chestplateSwapStage = 0;
        chestplateSlot = -1;
        storedElytra = ItemStack.EMPTY;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        
        // Don't swap if player is dead or in critical state
        if (mc.player.isRemoved() || mc.player.getHealth() <= 0) {
            resetSwapState();
            return;
        }
        
        if (swapOnHit.get()) {
            handleCombatProtection();
        }
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }
        if (pauseInInventory.get() && mc.player.containerMenu != mc.player.inventoryMenu) {
            resetSwapState();
            return;
        }
        ItemStack chestItem = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (protectionActive) {
            return;
        }
        if (!chestItem.getItem().equals(Items.ELYTRA)) {
            return;
        }
        if (onlyWhileFlying.get() && !mc.player.isFallFlying()) {
            return;
        }
        if (needsSwap) {
            processSwapStages();
            return;
        }
        int currentDurability = chestItem.getMaxDamage() - chestItem.getDamageValue();
        int maxDurability = chestItem.getMaxDamage();
        int durabilityPercentage = (int) ((double) currentDurability / maxDurability * 100);
        if (durabilityPercentage <= durabilityThreshold.get()) {
            initiateSwap();
        }
    }

    private void initiateSwap() {
        int bestSlot = -1;
        int bestDurabilityPercentage = durabilityThreshold.get();
        
        // Find the best elytra in inventory
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem().equals(Items.ELYTRA)) {
                int currentDurability = stack.getMaxDamage() - stack.getDamageValue();
                int maxDurability = stack.getMaxDamage();
                int durabilityPercentage = (int) ((double) currentDurability / maxDurability * 100);
                if (durabilityPercentage > bestDurabilityPercentage) {
                    bestDurabilityPercentage = durabilityPercentage;
                    bestSlot = i;
                }
            }
        }
        
        if (bestSlot == -1) {
            if (notifySwap.get()) {
                info("No suitable elytra found in inventory!");
            }
            return;
        }
        
        targetSlot = bestSlot;
        needsSwap = true;
        swapStage = 1;
        stageTimer = 0;
        
        if (notifySwap.get()) {
            ItemStack targetElytra = mc.player.getInventory().getItem(bestSlot);
            int targetDurability = targetElytra.getMaxDamage() - targetElytra.getDamageValue();
            int targetMaxDurability = targetElytra.getMaxDamage();
            int targetDurabilityPercentage = (int) ((double) targetDurability / targetMaxDurability * 100);
            info("Initiating swap to elytra with %d%% durability", targetDurabilityPercentage);
        }
    }

    private void processSwapStages() {
        stageTimer++;
        if (stageTimer < 5) return;
        
        // Safety check to prevent infinite loops
        if (stageTimer > 100) {
            if (notifySwap.get()) {
                info("Swap operation timed out, resetting");
            }
            resetSwapState();
            return;
        }
        
        // Validate that we still have a valid target slot
        if (targetSlot < 0 || targetSlot >= 36) {
            resetSwapState();
            return;
        }
        
        switch (swapStage) {
            case 1 -> {
                newElytraOriginalSlot = targetSlot;
                if (targetSlot >= 9) {
                    int hotbarSlot = -1;
                    if (hotbarSlotUsed != -1 && hotbarSlotUsed < 9) {
                        hotbarSlot = hotbarSlotUsed;
                    } else {
                        for (int i = 0; i < 9; i++) {
                            ItemStack stack = mc.player.getInventory().getItem(i);
                            if (stack.isEmpty() || !isEssentialItem(stack)) {
                                hotbarSlot = i;
                                break;
                            }
                        }
                        if (hotbarSlot == -1) {
                            hotbarSlot = 0;
                        }
                    }
                    hotbarOriginalItem = mc.player.getInventory().getItem(hotbarSlot).copy();
                    hotbarSlotUsed = hotbarSlot;
                    InvUtils.move().from(targetSlot).toHotbar(hotbarSlot);
                    targetSlot = hotbarSlot;
                    swapStage = 2;
                    stageTimer = 0;
                } else {
                    hotbarSlotUsed = targetSlot;
                    hotbarOriginalItem = ItemStack.EMPTY;
                    swapStage = 2;
                    stageTimer = 0;
                }
            }
            case 2 -> {
                ItemStack toEquip = mc.player.getInventory().getItem(targetSlot);
                if (!toEquip.getItem().equals(Items.ELYTRA)) {
                    resetSwapState();
                    return;
                }
                // Equip the elytra from the target slot
                InvUtils.swap(targetSlot, false);
                // Use right-click to equip the elytra
                mc.options.keyUse.setDown(true);
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                mc.options.keyUse.setDown(false);
                InvUtils.swapBack();
                swapStage = 3;
                stageTimer = 0;
            }
            case 3 -> {
                // Validate that the swap was successful
                ItemStack newChest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
                if (!newChest.getItem().equals(Items.ELYTRA)) {
                    // Swap failed, reset and try again later
                    resetSwapState();
                    cooldownTimer = swapCooldown.get() / 2; // Shorter cooldown for retry
                    if (notifySwap.get()) {
                        info("Elytra swap failed, will retry later");
                    }
                    return;
                }
                
                if (newElytraOriginalSlot >= 9) {
                    InvUtils.move().fromHotbar(targetSlot).to(newElytraOriginalSlot);
                    if (!hotbarOriginalItem.isEmpty()) {
                        swapStage = 4;
                        stageTimer = 0;
                        return;
                    }
                }
                
                if (notifySwap.get()) {
                    int newDurability = newChest.getMaxDamage() - newChest.getDamageValue();
                    int newMaxDurability = newChest.getMaxDamage();
                    int newDurabilityPercentage = (int) ((double) newDurability / newMaxDurability * 100);
                    info("Successfully swapped to elytra with %d%% durability", newDurabilityPercentage);
                }
                
                needsSwap = false;
                swapStage = 0;
                stageTimer = 0;
                targetSlot = -1;
                cooldownTimer = swapCooldown.get();
            }
            case 4 -> {
                if (stageTimer < 3) {
                    stageTimer++;
                    return;
                }
                for (int i = 9; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (ItemStack.isSameItemSameComponents(stack, hotbarOriginalItem)) {
                        InvUtils.move().from(i).toHotbar(hotbarSlotUsed);
                        break;
                    }
                }
                needsSwap = false;
                swapStage = 0;
                stageTimer = 0;
                targetSlot = -1;
                cooldownTimer = swapCooldown.get();
            }
        }
    }

    private boolean isEssentialItem(ItemStack stack) {
        return stack.getItem().equals(Items.TOTEM_OF_UNDYING) ||
                stack.getItem().equals(Items.GOLDEN_APPLE) ||
                stack.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE) ||
                stack.getItem().equals(Items.ENDER_PEARL) ||
                stack.getItem().equals(Items.CHORUS_FRUIT);
    }

    private void handleCombatProtection() {
        if (mc.player == null) return;
        
        // Check if player was recently hurt
        if (mc.player.hurtTime > 0 && mc.player.hurtTime != lastHurtTime) {
            lastHurtTime = mc.player.hurtTime;
            ItemStack chestItem = mc.player.getItemBySlot(EquipmentSlot.CHEST);
            
            // If wearing elytra and not already in protection mode
            if (chestItem.getItem().equals(Items.ELYTRA) && !protectionActive) {
                int bestChestplate = findBestChestplate();
                if (bestChestplate != -1) {
                    storedElytra = chestItem.copy();
                    chestplateSlot = bestChestplate;
                    needsChestplateSwap = true;
                    chestplateSwapStage = 1;
                    stageTimer = 0;
                    protectionActive = true;
                    protectionTimer = hitProtectionDuration.get();
                    if (notifySwap.get()) {
                        info("Swapping to chestplate for protection!");
                    }
                }
            } else if (protectionActive && !chestItem.getItem().equals(Items.ELYTRA)) {
                // Reset protection timer if we get hit while wearing chestplate
                protectionTimer = hitProtectionDuration.get();
            }
        }
        if (needsChestplateSwap) {
            processChestplateSwap();
            return;
        }
        if (protectionActive && !needsChestplateSwap) {
            protectionTimer--;
            if (protectionTimer <= 0 && autoSwapBack.get()) {
                ItemStack chestItem = mc.player.getItemBySlot(EquipmentSlot.CHEST);
                if (!chestItem.getItem().equals(Items.ELYTRA) && !storedElytra.isEmpty()) {
                    int elytraSlot = findStoredElytra();
                    if (elytraSlot != -1) {
                        chestplateSlot = elytraSlot;
                        needsChestplateSwap = true;
                        chestplateSwapStage = 1;
                        stageTimer = 0;
                        if (notifySwap.get()) {
                            info("Protection period ended, swapping back to elytra.");
                        }
                    } else {
                        protectionActive = false;
                        storedElytra = ItemStack.EMPTY;
                    }
                } else if (chestItem.getItem().equals(Items.ELYTRA)) {
                    // Already wearing elytra, just clear protection
                    protectionActive = false;
                    storedElytra = ItemStack.EMPTY;
                }
            }
        }
    }

    private void processChestplateSwap() {
        stageTimer++;
        if (stageTimer < 3) return;
        
        // Safety check to prevent infinite loops
        if (stageTimer > 100) {
            if (notifySwap.get()) {
                info("Chestplate swap operation timed out, resetting");
            }
            needsChestplateSwap = false;
            chestplateSwapStage = 0;
            stageTimer = 0;
            chestplateSlot = -1;
            return;
        }
        switch (chestplateSwapStage) {
            case 1 -> {
                if (chestplateSlot >= 9) {
                    int hotbarSlot = 0;
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = mc.player.getInventory().getItem(i);
                        if (stack.isEmpty() || !isEssentialItem(stack)) {
                            hotbarSlot = i;
                            break;
                        }
                    }
                    InvUtils.move().from(chestplateSlot).toHotbar(hotbarSlot);
                    chestplateSlot = hotbarSlot;
                }
                chestplateSwapStage = 2;
                stageTimer = 0;
            }
            case 2 -> {
                ItemStack toEquip = mc.player.getInventory().getItem(chestplateSlot);
                if (!isChestplateItem(toEquip)) {
                    needsChestplateSwap = false;
                    chestplateSwapStage = 0;
                    return;
                }
                // Equip the chestplate from the target slot
                InvUtils.swap(chestplateSlot, false);
                // Use right-click to equip the chestplate
                mc.options.keyUse.setDown(true);
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                mc.options.keyUse.setDown(false);
                InvUtils.swapBack();
                chestplateSwapStage = 3;
                stageTimer = 0;
            }
            case 3 -> {
                needsChestplateSwap = false;
                chestplateSwapStage = 0;
                stageTimer = 0;
                chestplateSlot = -1;
                
                // Check if we successfully equipped the item
                ItemStack chestItem = mc.player.getItemBySlot(EquipmentSlot.CHEST);
                if (chestItem.getItem().equals(Items.ELYTRA)) {
                    // Successfully swapped back to elytra
                    protectionActive = false;
                    storedElytra = ItemStack.EMPTY;
                    if (notifySwap.get()) {
                        info("Successfully swapped back to elytra");
                    }
                } else if (isChestplateItem(chestItem) && !chestItem.getItem().equals(Items.ELYTRA)) {
                    // Successfully equipped chestplate, protection is active
                    if (notifySwap.get()) {
                        info("Successfully equipped chestplate for protection");
                    }
                } else {
                    // Swap failed
                    if (notifySwap.get()) {
                        info("Chestplate swap failed");
                    }
                }
            }
        }
    }

    private int findBestChestplate() {
        int bestSlot = -1;
        int bestValue = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            int value = getChestplateValue(stack);
            if (value > bestValue) {
                bestValue = value;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int getChestplateValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.getItem().equals(Items.NETHERITE_CHESTPLATE)) {
            if (prioritizeNetherite.get()) {
                return 1000 + (stack.getMaxDamage() - stack.getDamageValue());
            }
            return 400 + (stack.getMaxDamage() - stack.getDamageValue());
        } else if (stack.getItem().equals(Items.DIAMOND_CHESTPLATE)) {
            return 300 + (stack.getMaxDamage() - stack.getDamageValue());
        } else if (stack.getItem().equals(Items.IRON_CHESTPLATE)) {
            return 200 + (stack.getMaxDamage() - stack.getDamageValue());
        } else if (stack.getItem().equals(Items.GOLDEN_CHESTPLATE)) {
            return 100 + (stack.getMaxDamage() - stack.getDamageValue());
        } else if (stack.getItem().equals(Items.CHAINMAIL_CHESTPLATE)) {
            return 150 + (stack.getMaxDamage() - stack.getDamageValue());
        } else if (stack.getItem().equals(Items.LEATHER_CHESTPLATE)) {
            return 50 + (stack.getMaxDamage() - stack.getDamageValue());
        }
        return 0;
    }

    private boolean isChestplateItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem().equals(Items.ELYTRA) ||
                stack.getItem().equals(Items.NETHERITE_CHESTPLATE) ||
                stack.getItem().equals(Items.DIAMOND_CHESTPLATE) ||
                stack.getItem().equals(Items.IRON_CHESTPLATE) ||
                stack.getItem().equals(Items.GOLDEN_CHESTPLATE) ||
                stack.getItem().equals(Items.CHAINMAIL_CHESTPLATE) ||
                stack.getItem().equals(Items.LEATHER_CHESTPLATE);
    }

    private int findStoredElytra() {
        if (storedElytra.isEmpty()) return -1;
        
        // First try to find the exact elytra by damage value
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem().equals(Items.ELYTRA)) {
                if (Math.abs(stack.getDamageValue() - storedElytra.getDamageValue()) <= 5) {
                    return i;
                }
            }
        }
        
        // If not found, return any elytra
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem().equals(Items.ELYTRA)) {
                return i;
            }
        }
        return -1;
    }
}