package dev.oma.addon.modules.Render;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChestESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General settings
    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to detect chests.")
        .defaultValue(64.0)
        .min(1.0)
        .sliderRange(1.0, 128.0)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("Ticks between chest detection updates.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    // Render settings
    private final Setting<Boolean> renderChests = sgRender.add(new BoolSetting.Builder()
        .name("render-chests")
        .description("Render ESP boxes around chests with shulker boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> chestColor = sgRender.add(new ColorSetting.Builder()
        .name("chest-color")
        .description("Color of the chest ESP.")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .build()
    );

    private final Setting<Double> boxHeight = sgRender.add(new DoubleSetting.Builder()
        .name("box-height")
        .description("Height of the ESP box.")
        .defaultValue(0.875)
        .min(0.1)
        .max(2.0)
        .sliderRange(0.1, 2.0)
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Shows all chests for debugging purposes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showDebugInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("show-debug-info")
        .description("Shows debug information in chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> testWithInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("test-with-inventory")
        .description("Test shulker detection with your own inventory instead of chests.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> memoryDuration = sgGeneral.add(new IntSetting.Builder()
        .name("memory-duration")
        .description("How long to remember chests with shulker boxes (in minutes).")
        .defaultValue(60)
        .min(1)
        .max(1440)
        .sliderRange(1, 240)
        .build()
    );

    private final Setting<Boolean> trackOpenedChests = sgGeneral.add(new BoolSetting.Builder()
        .name("track-opened-chests")
        .description("Remember chests with shulker boxes when you open them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> manualTest = sgGeneral.add(new BoolSetting.Builder()
        .name("manual-test")
        .description("Manually check current chest when enabled.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showDetectionMethod = sgGeneral.add(new BoolSetting.Builder()
        .name("show-detection-method")
        .description("Shows which detection method was used for shulker boxes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> testAllShulkerTypes = sgGeneral.add(new BoolSetting.Builder()
        .name("test-all-shulker-types")
        .description("Tests detection with all shulker box variants in your inventory.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoRemoveDestroyed = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-remove-destroyed")
        .description("Automatically remove destroyed chests from ESP rendering.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> supportDoubleChests = sgGeneral.add(new BoolSetting.Builder()
        .name("support-double-chests")
        .description("Detect and highlight double chests (two adjacent chests).")
        .defaultValue(true)
        .build()
    );


    private final Set<BlockPos> chestsWithShulkers = new HashSet<>();
    private final Map<BlockPos, Long> chestMemory = new HashMap<>(); // BlockPos -> timestamp when shulker was seen
    private final Map<BlockPos, BlockPos> doubleChestPairs = new HashMap<>(); // Primary chest -> Secondary chest
    private int tickCounter = 0;
    private final Minecraft mc = Minecraft.getInstance();

    public ChestESP() {
        super(Main.RENDER, "chest-esp", "Highlights chests that contain shulker boxes.");
    }

    @Override
    public void onActivate() {
        chestsWithShulkers.clear();
        chestMemory.clear();
        doubleChestPairs.clear();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        chestsWithShulkers.clear();
        chestMemory.clear();
        doubleChestPairs.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter < updateInterval.get()) return;
        tickCounter = 0;

        detectChestsWithShulkers();
        
        // Check if we're currently looking at a chest screen
        checkCurrentChestScreen();
        
        // Manual test mode
        if (manualTest.get()) {
            checkCurrentChest();
            manualTest.set(false); // Turn off after one check
        }
        
        // Test all shulker types
        if (testAllShulkerTypes.get()) {
            testAllShulkerTypesInInventory();
            testAllShulkerTypes.set(false); // Turn off after one test
        }
        
        // Clean up old memory entries and destroyed chests
        if (tickCounter % 1200 == 0) { // Every minute
            cleanupOldMemory();
            if (autoRemoveDestroyed.get()) {
                cleanupDestroyedChests();
            }
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!trackOpenedChests.get() || mc.level == null || mc.player == null) return;
        
        // Check if we're opening a chest screen
        if (event.screen instanceof ContainerScreen chestScreen) {
            AbstractContainerMenu handler = chestScreen.getMenu();
            
            // Check if this is a chest (not a shulker box or other container)
            if (handler instanceof ChestMenu genericHandler) {
                int containerSlots = genericHandler.slots.size() - 36; // Subtract player inventory
                
                if (showDebugInfo.get()) {
                    info("Opened container screen with " + containerSlots + " slots");
                }
                
                // Only process if it looks like a chest (27 slots)
                if (containerSlots == 27) {
                    if (showDebugInfo.get()) {
                        info("Chest screen opened - detection will happen in tick event");
                    }
                }
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!autoRemoveDestroyed.get() || mc.level == null || mc.player == null) return;
        
        BlockPos pos = event.pos;
        
        // Check if this is a chest that was destroyed
        if (chestMemory.containsKey(pos) || chestsWithShulkers.contains(pos)) {
            // Check if the block at this position is no longer a chest
            if (!(mc.level.getBlockEntity(pos) instanceof ChestBlockEntity)) {
                // Chest was destroyed, remove it from memory
                chestMemory.remove(pos);
                chestsWithShulkers.remove(pos);
                
                // Also remove from double chest pairs
                if (doubleChestPairs.containsKey(pos)) {
                    doubleChestPairs.remove(pos);
                }
                // Remove any double chest pairs where this pos is the secondary
                doubleChestPairs.entrySet().removeIf(entry -> entry.getValue().equals(pos));
                
                if (showDebugInfo.get()) {
                    info("Removed destroyed chest from memory at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderChests.get() || mc.level == null || mc.player == null) return;

        for (BlockPos chestPos : chestsWithShulkers) {
            if (mc.player.blockPosition().distSqr(chestPos) > maxDistance.get() * maxDistance.get()) {
                continue;
            }

            // Render the primary chest
            event.renderer.box(
                chestPos.getX(), chestPos.getY(), chestPos.getZ(),
                chestPos.getX() + 1, chestPos.getY() + boxHeight.get(), chestPos.getZ() + 1,
                chestColor.get(), chestColor.get(), shapeMode.get(),
                0
            );

            // If this is a double chest, also render the other half
            if (supportDoubleChests.get() && doubleChestPairs.containsKey(chestPos)) {
                BlockPos otherHalf = doubleChestPairs.get(chestPos);
                if (mc.player.blockPosition().distSqr(otherHalf) <= maxDistance.get() * maxDistance.get()) {
                    event.renderer.box(
                        otherHalf.getX(), otherHalf.getY(), otherHalf.getZ(),
                        otherHalf.getX() + 1, otherHalf.getY() + boxHeight.get(), otherHalf.getZ() + 1,
                        chestColor.get(), chestColor.get(), shapeMode.get(),
                        0
                    );
                }
            }
        }
    }

    private void detectChestsWithShulkers() {
        Set<BlockPos> currentChests = new HashSet<>();
        
        // Test with player inventory if enabled
        if (testWithInventory.get()) {
            testPlayerInventory();
            return;
        }
        
        List<LevelChunk> loadedChunks = getLoadedChunks();
        int totalChests = 0;
        int chestsWithShulkersCount = 0;
        
        for (LevelChunk chunk : loadedChunks) {
            for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                BlockEntity blockEntity = chunk.getBlockEntity(pos);
                
                if (blockEntity instanceof ChestBlockEntity chestEntity) {
                    double distance = mc.player.position().distanceTo(pos.getCenter());
                    
                    if (distance <= maxDistance.get()) {
                        totalChests++;
                        
                        if (debugMode.get()) {
                            // Debug mode: show all chests
                            currentChests.add(pos);
                            updateDoubleChestPairs(pos);
                        } else if (chestContainsShulker(chestEntity)) {
                            // Normal mode: only show chests with shulkers
                            currentChests.add(pos);
                            updateDoubleChestPairs(pos);
                            chestsWithShulkersCount++;
                        }
                    }
                }
            }
        }
        
        // Add remembered chests (those that were previously seen with shulkers)
        for (BlockPos rememberedChest : chestMemory.keySet()) {
            double distance = mc.player.position().distanceTo(rememberedChest.getCenter());
            if (distance <= maxDistance.get()) {
                currentChests.add(rememberedChest);
                chestsWithShulkersCount++;
            }
        }
        
        chestsWithShulkers.clear();
        chestsWithShulkers.addAll(currentChests);
        
        if (showDebugInfo.get()) {
            info("Found " + totalChests + " chests, " + chestsWithShulkersCount + " with shulker boxes (including " + chestMemory.size() + " remembered)");
        }
    }

    private void testPlayerInventory() {
        if (mc.player == null) return;
        
        int shulkerCount = 0;
        int totalSlots = mc.player.getInventory().getContainerSize();
        
        if (showDebugInfo.get()) {
            info("Testing player inventory with " + totalSlots + " slots");
        }
        
        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                if (showDebugInfo.get()) {
                    info("Slot " + i + ": " + stack.getItem().toString());
                }
                
                if (isShulkerBox(stack)) {
                    shulkerCount++;
                    if (showDebugInfo.get()) {
                        info("Found shulker box in slot " + i);
                    }
                }
            }
        }
        
        if (showDebugInfo.get()) {
            info("Player inventory contains " + shulkerCount + " shulker boxes");
        }
    }

    private boolean chestContainsShulker(ChestBlockEntity chestEntity) {
        // Check if the chest inventory contains any shulker boxes
        try {
            int inventorySize = chestEntity.getContainerSize();
            if (showDebugInfo.get()) {
                info("Checking chest with " + inventorySize + " slots");
            }
            
            for (int i = 0; i < inventorySize; i++) {
                ItemStack stack = chestEntity.getItem(i);
                if (stack != null && !stack.isEmpty()) {
                    if (showDebugInfo.get()) {
                        info("Slot " + i + ": " + stack.getItem().toString());
                    }
                    
                    // Use proper shulker box detection like in AutoShulker
                    if (isShulkerBox(stack)) {
                        if (showDebugInfo.get()) {
                            info("Found shulker box in slot " + i);
                        }
                        return true;
                    }
                    
                    // Alternative detection method using item name
                    String itemName = stack.getItem().toString().toLowerCase();
                    if (itemName.contains("shulker_box")) {
                        if (showDebugInfo.get()) {
                            info("Found shulker box by name in slot " + i + ": " + itemName);
                        }
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (showDebugInfo.get()) {
                info("Error checking chest inventory: " + e.getMessage());
            }
        }
        return false;
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        // Method 1: Check if it's a BlockItem and the block is a ShulkerBoxBlock
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof ShulkerBoxBlock) {
                if (showDetectionMethod.get()) {
                    info("Shulker box detected by Method 1 (BlockItem/ShulkerBoxBlock): " + stack.getItem().toString());
                }
                return true;
            }
        }
        
        // Method 2: Check by item name/translation key (fallback)
        String itemName = stack.getItem().toString().toLowerCase();
        if (itemName.contains("shulker_box")) {
            if (showDetectionMethod.get()) {
                info("Shulker box detected by Method 2 (name contains 'shulker_box'): " + stack.getItem().toString());
            }
            return true;
        }
        
        // Method 3: Check by item ID (additional fallback)
        String itemId = stack.getItem().toString();
        if (itemId.contains("shulker_box") || itemId.contains("minecraft:shulker_box")) {
            if (showDetectionMethod.get()) {
                info("Shulker box detected by Method 3 (item ID): " + stack.getItem().toString());
            }
            return true;
        }
        
        return false;
    }

    private List<LevelChunk> getLoadedChunks() {
        List<LevelChunk> chunks = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        int renderDistance = mc.options.renderDistance().get();
        
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                int chunkX = (playerPos.getX() >> 4) + x;
                int chunkZ = (playerPos.getZ() >> 4) + z;
                
                if (mc.level.hasChunk(chunkX, chunkZ)) {
                    chunks.add(mc.level.getChunk(chunkX, chunkZ));
                }
            }
        }
        
        return chunks;
    }


    private boolean chestScreenHandlerContainsShulker(ChestMenu handler) {
        try {
            // Check chest slots (first 27 slots, excluding player inventory)
            int chestSlotCount = handler.slots.size() - 36; // Subtract player inventory
            
            for (int i = 0; i < chestSlotCount; i++) {
                ItemStack stack = handler.getSlot(i).getItem();
                if (stack != null && !stack.isEmpty()) {
                    if (showDebugInfo.get()) {
                        info("Chest slot " + i + ": " + stack.getItem().toString());
                    }
                    
                    if (isShulkerBox(stack)) {
                        if (showDebugInfo.get()) {
                            info("Found shulker box in chest slot " + i);
                        }
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (showDebugInfo.get()) {
                info("Error checking chest screen handler: " + e.getMessage());
            }
        }
        
        return false;
    }

    private void cleanupOldMemory() {
        long currentTime = System.currentTimeMillis();
        long memoryDurationMs = memoryDuration.get() * 60L * 1000L; // Convert minutes to milliseconds
        
        chestMemory.entrySet().removeIf(entry -> {
            boolean shouldRemove = (currentTime - entry.getValue()) > memoryDurationMs;
            if (shouldRemove) {
                chestsWithShulkers.remove(entry.getKey());
                if (showDebugInfo.get()) {
                    info("Forgot chest at " + entry.getKey().getX() + ", " + entry.getKey().getY() + ", " + entry.getKey().getZ());
                }
            }
            return shouldRemove;
        });
    }

    private void checkCurrentChest() {
        if (mc.level == null || mc.player == null) return;
        
        // Check all chests in a small radius around the player
        List<LevelChunk> loadedChunks = getLoadedChunks();
        
        for (LevelChunk chunk : loadedChunks) {
            for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                BlockEntity blockEntity = chunk.getBlockEntity(pos);
                
                if (blockEntity instanceof ChestBlockEntity chestEntity) {
                    double distance = mc.player.position().distanceTo(pos.getCenter());
                    
                    if (distance <= 3.0) { // Very close chests
                        if (showDebugInfo.get()) {
                            info("Checking nearby chest at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                        }
                        
                        if (chestContainsShulker(chestEntity)) {
                            chestMemory.put(pos, System.currentTimeMillis());
                            chestsWithShulkers.add(pos);
                            
                            if (showDebugInfo.get()) {
                                info("Found shulker box in nearby chest at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkCurrentChestScreen() {
        if (mc.player == null || mc.level == null) return;
        
        // Check if we're currently in a chest screen
        if (mc.player.containerMenu instanceof ChestMenu handler) {
            // This is a chest or shulker box screen
            int containerSlots = handler.slots.size() - 36; // Subtract player inventory
            
            if (showDebugInfo.get()) {
                info("Currently in container screen with " + containerSlots + " slots");
            }
            
                // Process chests (27 slots) and double chests (54 slots)
                if (containerSlots == 27 || containerSlots == 54) {
                    // Try to find the chest position(s)
                    List<BlockPos> chestPositions = findChestPositionsForHandler(handler, containerSlots);
                    
                    if (!chestPositions.isEmpty()) {
                        if (showDebugInfo.get()) {
                            info("Found " + chestPositions.size() + " chest(s) for handler with " + containerSlots + " slots");
                            for (BlockPos pos : chestPositions) {
                                info("  Chest at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                            }
                        }
                        
                        // Check if this chest/double chest contains shulker boxes using the screen handler
                        if (chestScreenHandlerContainsShulker(handler)) {
                            // Remember all chest positions
                            for (BlockPos chestPos : chestPositions) {
                                chestMemory.put(chestPos, System.currentTimeMillis());
                                chestsWithShulkers.add(chestPos);
                                updateDoubleChestPairs(chestPos);
                            }
                            
                            if (showDebugInfo.get()) {
                                info("Found shulker box in chest/double chest with " + chestPositions.size() + " blocks");
                            }
                        } else {
                            if (showDebugInfo.get()) {
                                info("No shulker box found in chest/double chest");
                            }
                        }
                    }
                }
        }
    }

    private BlockPos findClosestChestToPlayer() {
        if (mc.player == null || mc.level == null) return null;
        
        List<LevelChunk> loadedChunks = getLoadedChunks();
        BlockPos closestChest = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (LevelChunk chunk : loadedChunks) {
            for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                BlockEntity blockEntity = chunk.getBlockEntity(pos);
                
                if (blockEntity instanceof ChestBlockEntity chestEntity) {
                    double distance = mc.player.position().distanceTo(pos.getCenter());
                    
                    // If this chest is close and has the right size
                    if (distance <= 6.0 && chestEntity.getContainerSize() == 27) {
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestChest = pos;
                        }
                    }
                }
            }
        }
        
        return closestChest;
    }

    private List<BlockPos> findChestPositionsForHandler(ChestMenu handler, int containerSlots) {
        List<BlockPos> positions = new ArrayList<>();
        
        if (mc.player == null || mc.level == null) return positions;
        
        try {
            List<LevelChunk> loadedChunks = getLoadedChunks();
            
            if (containerSlots == 27) {
                // Single chest - find the closest one
                BlockPos closestChest = findClosestChestToPlayer();
                if (closestChest != null) {
                    positions.add(closestChest);
                }
            } else if (containerSlots == 54) {
                // Double chest - find both chest blocks
                for (LevelChunk chunk : loadedChunks) {
                    for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                        BlockEntity blockEntity = chunk.getBlockEntity(pos);
                        
                        if (blockEntity instanceof ChestBlockEntity chestEntity) {
                            double distance = mc.player.position().distanceTo(pos.getCenter());
                            
                            // If this chest is close and has the right size
                            if (distance <= 6.0 && chestEntity.getContainerSize() == 27) {
                                // Check if this chest has a partner (double chest)
                                BlockPos partner = findDoubleChestPartner(pos);
                                if (partner != null) {
                                    // Found a double chest
                                    positions.add(pos);
                                    positions.add(partner);
                                    
                                    if (showDebugInfo.get()) {
                                        info("Found double chest: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + 
                                             " <-> " + partner.getX() + ", " + partner.getY() + ", " + partner.getZ());
                                    }
                                    
                                    return positions; // Return immediately for double chest
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (showDebugInfo.get()) {
                info("Error finding chest positions for handler: " + e.getMessage());
            }
        }
        
        return positions;
    }

    private BlockPos findDoubleChestPartner(BlockPos chestPos) {
        if (!supportDoubleChests.get() || mc.level == null) return null;
        
        BlockEntity chestEntity = mc.level.getBlockEntity(chestPos);
        if (!(chestEntity instanceof ChestBlockEntity)) return null;
        
        // Check all four horizontal directions for another chest
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        
        for (Direction direction : directions) {
            BlockPos adjacentPos = chestPos.relative(direction);
            BlockEntity adjacentEntity = mc.level.getBlockEntity(adjacentPos);
            
            if (adjacentEntity instanceof ChestBlockEntity) {
                // Check if this forms a double chest by checking the chest type
                if (isDoubleChest(chestPos, adjacentPos)) {
                    return adjacentPos;
                }
            }
        }
        
        return null;
    }

    private boolean isDoubleChest(BlockPos pos1, BlockPos pos2) {
        if (mc.level == null) return false;
        
        try {
            // Get the chest block entities
            BlockEntity entity1 = mc.level.getBlockEntity(pos1);
            BlockEntity entity2 = mc.level.getBlockEntity(pos2);
            
            if (!(entity1 instanceof ChestBlockEntity) || !(entity2 instanceof ChestBlockEntity)) {
                return false;
            }
            
            // Check if they're the same type of chest (both regular chests or both trapped chests)
            Block block1 = mc.level.getBlockState(pos1).getBlock();
            Block block2 = mc.level.getBlockState(pos2).getBlock();
            
            if (!(block1 instanceof ChestBlock) || !(block2 instanceof ChestBlock)) {
                return false;
            }
            
            // Check if they're the same type (regular chest + regular chest, or trapped chest + trapped chest)
            return block1.getClass().equals(block2.getClass());
        } catch (Exception e) {
            if (showDebugInfo.get()) {
                info("Error checking double chest: " + e.getMessage());
            }
            return false;
        }
    }

    private void updateDoubleChestPairs(BlockPos chestPos) {
        if (!supportDoubleChests.get()) return;
        
        BlockPos partner = findDoubleChestPartner(chestPos);
        if (partner != null) {
            // Store the double chest pair (use the position with smaller coordinates as primary)
            BlockPos primary = chestPos.compareTo(partner) < 0 ? chestPos : partner;
            BlockPos secondary = chestPos.compareTo(partner) < 0 ? partner : chestPos;
            
            doubleChestPairs.put(primary, secondary);
            
            if (showDebugInfo.get()) {
                info("Found double chest: " + primary.getX() + ", " + primary.getY() + ", " + primary.getZ() + 
                     " <-> " + secondary.getX() + ", " + secondary.getY() + ", " + secondary.getZ());
            }
        }
    }

    private void testAllShulkerTypesInInventory() {
        if (mc.player == null) return;
        
        info("Testing shulker box detection with all items in inventory...");
        
        int totalSlots = mc.player.getInventory().getContainerSize();
        int shulkerCount = 0;
        int testedItems = 0;
        
        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                testedItems++;
                String itemName = stack.getItem().toString();
                
                if (isShulkerBox(stack)) {
                    shulkerCount++;
                    info("✓ Shulker box detected in slot " + i + ": " + itemName);
                } else {
                    // Show what items are NOT detected as shulker boxes for debugging
                    if (showDetectionMethod.get() && itemName.toLowerCase().contains("shulker")) {
                        info("✗ Item NOT detected as shulker box in slot " + i + ": " + itemName);
                    }
                }
            }
        }
        
        info("Test complete: " + shulkerCount + " shulker boxes found out of " + testedItems + " items tested");
    }

    private void cleanupDestroyedChests() {
        if (mc.level == null) return;
        
        List<BlockPos> toRemove = new ArrayList<>();
        
        // Check all remembered chests to see if they still exist
        for (BlockPos pos : chestMemory.keySet()) {
            BlockEntity blockEntity = mc.level.getBlockEntity(pos);
            if (!(blockEntity instanceof ChestBlockEntity)) {
                toRemove.add(pos);
            }
        }
        
        // Remove destroyed chests from both memory and rendering
        for (BlockPos pos : toRemove) {
            chestMemory.remove(pos);
            chestsWithShulkers.remove(pos);
            
            // Also remove from double chest pairs
            if (doubleChestPairs.containsKey(pos)) {
                doubleChestPairs.remove(pos);
            }
            // Remove any double chest pairs where this pos is the secondary
            doubleChestPairs.entrySet().removeIf(entry -> entry.getValue().equals(pos));
            
            if (showDebugInfo.get()) {
                info("Cleaned up destroyed chest at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            }
        }
        
        // Also check the current chests set for any that no longer exist
        toRemove.clear();
        for (BlockPos pos : chestsWithShulkers) {
            if (!chestMemory.containsKey(pos)) {
                BlockEntity blockEntity = mc.level.getBlockEntity(pos);
                if (!(blockEntity instanceof ChestBlockEntity)) {
                    toRemove.add(pos);
                }
            }
        }
        
        for (BlockPos pos : toRemove) {
            chestsWithShulkers.remove(pos);
            
            // Also remove from double chest pairs
            if (doubleChestPairs.containsKey(pos)) {
                doubleChestPairs.remove(pos);
            }
            // Remove any double chest pairs where this pos is the secondary
            doubleChestPairs.entrySet().removeIf(entry -> entry.getValue().equals(pos));
            
            if (showDebugInfo.get()) {
                info("Cleaned up non-existent chest at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            }
        }
        
        if (showDebugInfo.get() && !toRemove.isEmpty()) {
            info("Cleaned up " + toRemove.size() + " destroyed/non-existent chests");
        }
    }
}
