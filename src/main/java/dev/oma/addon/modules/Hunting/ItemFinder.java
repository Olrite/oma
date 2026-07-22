package dev.oma.addon.modules.Hunting;

import dev.oma.addon.Main;
import dev.oma.addon.util.ItemListSync;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ItemFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final SettingGroup sgFilters = settings.createGroup("Filters");

    private final Setting<Boolean> syncWithItemHighlight = sgFilters.add(new BoolSetting.Builder()
        .name("sync-with-item-highlight")
        .description("Share custom item/block lists with Item Highlight, and highlight containers containing Item Highlight matches (default finds + shared lists).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> detectShulkers = sgFilters.add(new BoolSetting.Builder()
        .name("detect-shulkers")
        .description("Highlight chests that contain any shulker box.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<net.minecraft.item.Item>> items = sgFilters.add(new ItemListSetting.Builder()
        .name("items")
        .description("Highlight containers that contain any of these items (blocks can be added too, e.g. \"Chest\"). Shared with Item Highlight when sync is enabled.")
        .defaultValue()
        .build()
    );

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
        .description("Render ESP boxes around chests with tracked items.")
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

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("opacity")
        .description("Opacity of the chest ESP (0-100).")
        .defaultValue(35)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
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

    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<Boolean> debugMode = sgDebug.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Shows all nearby chests regardless of contents, and unlocks testing options. Note: Minecraft only sends chest contents to the client once a chest has been opened, so normal mode can only detect tracked items in chests you have personally opened (they are then remembered for memory-duration).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showDebugInfo = sgDebug.add(new BoolSetting.Builder()
        .name("show-debug-info")
        .description("Shows debug information in chat.")
        .defaultValue(false)
        .visible(debugMode::get)
        .build()
    );

    private final Setting<Boolean> testWithInventory = sgDebug.add(new BoolSetting.Builder()
        .name("test-with-inventory")
        .description("Test shulker detection with your own inventory instead of chests.")
        .defaultValue(false)
        .visible(debugMode::get)
        .build()
    );

    private final Setting<Integer> memoryDuration = sgGeneral.add(new IntSetting.Builder()
        .name("memory-duration")
        .description("How long to remember chests with tracked items (in minutes).")
        .defaultValue(60)
        .min(1)
        .max(1440)
        .sliderRange(1, 240)
        .build()
    );

    private final Setting<Boolean> trackOpenedChests = sgGeneral.add(new BoolSetting.Builder()
        .name("track-opened-chests")
        .description("Remember chests with tracked items when you open them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> manualTest = sgDebug.add(new BoolSetting.Builder()
        .name("manual-test")
        .description("Manually check current chest when enabled.")
        .defaultValue(false)
        .visible(debugMode::get)
        .build()
    );

    private final Setting<Boolean> showDetectionMethod = sgDebug.add(new BoolSetting.Builder()
        .name("show-detection-method")
        .description("Shows which detection method was used for shulker boxes.")
        .defaultValue(false)
        .visible(debugMode::get)
        .build()
    );

    private final Setting<Boolean> testAllShulkerTypes = sgDebug.add(new BoolSetting.Builder()
        .name("test-all-shulker-types")
        .description("Tests detection with all shulker box variants in your inventory.")
        .defaultValue(false)
        .visible(debugMode::get)
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

    private final SettingGroup sgChat = settings.createGroup("Chat");

    private final Setting<Boolean> chatOutput = sgChat.add(new BoolSetting.Builder()
        .name("chat-output")
        .description("Client-side chat notification when a chest with tracked items is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatCoords = sgChat.add(new BoolSetting.Builder()
        .name("chat-coordinates")
        .description("Include coordinates in chat notifications.")
        .defaultValue(true)
        .visible(chatOutput::get)
        .build()
    );


    private final Set<BlockPos> chestsWithShulkers = new HashSet<>();
    private final Map<BlockPos, Long> chestMemory = new HashMap<>(); // BlockPos -> timestamp when shulker was seen
    private final Map<BlockPos, BlockPos> doubleChestPairs = new HashMap<>(); // Primary chest -> Secondary chest
    private int tickCounter = 0;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public ItemFinder() {
        super(Main.HUNT, "Item Finder", "Highlights containers that contain shulkers or user-specified items/blocks.");
    }

    public boolean isListSyncEnabled() {
        return syncWithItemHighlight.get();
    }

    public List<net.minecraft.item.Item> getConfiguredItems() {
        return items.get();
    }

    public boolean matchesCustomLists(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        return items.get().contains(stack.getItem());
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
        if (mc.world == null || mc.player == null) return;

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
        if (!trackOpenedChests.get() || mc.world == null || mc.player == null) return;

        if (event.screen instanceof HandledScreen<?> containerScreen) {
            ScreenHandler handler = containerScreen.getScreenHandler();
            int containerSlots = getContainerSlotCount(handler);

            if (showDebugInfo.get()) {
                info("Opened container screen with " + containerSlots + " slots");
            }

            if (containerSlots > 0) {
                checkCurrentContainerScreen();
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!autoRemoveDestroyed.get() || mc.world == null || mc.player == null) return;
        
        BlockPos pos = event.pos;
        
        // Check if this is a chest that was destroyed
        if (chestMemory.containsKey(pos) || chestsWithShulkers.contains(pos)) {
            if (!isTrackableContainer(mc.world.getBlockEntity(pos))) {
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
        if (!renderChests.get() || mc.world == null || mc.player == null) return;

        SettingColor base = chestColor.get();
        SettingColor renderColor = new SettingColor(base.r, base.g, base.b, opacity.get() * 255 / 100);

        for (BlockPos chestPos : chestsWithShulkers) {
            if (mc.player.getBlockPos().getSquaredDistance(chestPos) > maxDistance.get() * maxDistance.get()) {
                continue;
            }

            // Render the primary chest
            event.renderer.box(
                chestPos.getX(), chestPos.getY(), chestPos.getZ(),
                chestPos.getX() + 1, chestPos.getY() + boxHeight.get(), chestPos.getZ() + 1,
                renderColor, renderColor, shapeMode.get(),
                0
            );

            // If this is a double chest, also render the other half
            if (supportDoubleChests.get() && doubleChestPairs.containsKey(chestPos)) {
                BlockPos otherHalf = doubleChestPairs.get(chestPos);
                if (mc.player.getBlockPos().getSquaredDistance(otherHalf) <= maxDistance.get() * maxDistance.get()) {
                    event.renderer.box(
                        otherHalf.getX(), otherHalf.getY(), otherHalf.getZ(),
                        otherHalf.getX() + 1, otherHalf.getY() + boxHeight.get(), otherHalf.getZ() + 1,
                        renderColor, renderColor, shapeMode.get(),
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
        
        List<Chunk> loadedChunks = getLoadedChunks();
        int totalContainers = 0;
        int containersWithTrackedItems = 0;

        for (Chunk chunk : loadedChunks) {
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                BlockEntity blockEntity = chunk.getBlockEntity(pos);

                if (isTrackableContainer(blockEntity)) {
                    double distance = mc.player.getPos().distanceTo(pos.toCenterPos());

                    if (distance <= maxDistance.get()) {
                        totalContainers++;

                        if (debugMode.get()) {
                            currentChests.add(pos);
                            updateDoubleChestPairs(pos);
                        } else if (containerContainsTrackedItems(blockEntity)) {
                            currentChests.add(pos);
                            updateDoubleChestPairs(pos);
                            containersWithTrackedItems++;
                        }
                    }
                }
            }
        }

        // Add remembered containers (those that were previously seen with tracked items)
        for (BlockPos rememberedChest : chestMemory.keySet()) {
            double distance = mc.player.getPos().distanceTo(rememberedChest.toCenterPos());
            if (distance <= maxDistance.get()) {
                currentChests.add(rememberedChest);
                containersWithTrackedItems++;
            }
        }

        chestsWithShulkers.clear();
        chestsWithShulkers.addAll(currentChests);

        if (showDebugInfo.get()) {
            info("Found " + totalContainers + " containers, " + containersWithTrackedItems + " with tracked items (including " + chestMemory.size() + " remembered)");
        }
    }

    private void testPlayerInventory() {
        if (mc.player == null) return;
        
        int shulkerCount = 0;
        int totalSlots = mc.player.getInventory().size();
        
        if (showDebugInfo.get()) {
            info("Testing player inventory with " + totalSlots + " slots");
        }
        
        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
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

    private boolean isTrackableContainer(BlockEntity blockEntity) {
        return blockEntity instanceof Inventory && !(blockEntity instanceof EnderChestBlockEntity);
    }

    private int getContainerSlotCount(ScreenHandler handler) {
        return Math.max(0, handler.slots.size() - 36);
    }

    private boolean containerContainsTrackedItems(BlockEntity blockEntity) {
        if (!(blockEntity instanceof Inventory container)) return false;

        try {
            int inventorySize = container.size();
            if (showDebugInfo.get()) {
                info("Checking container with " + inventorySize + " slots");
            }

            for (int i = 0; i < inventorySize; i++) {
                ItemStack stack = container.getStack(i);
                if (stack != null && !stack.isEmpty()) {
                    if (showDebugInfo.get()) {
                        info("Slot " + i + ": " + stack.getItem().toString());
                    }

                    if (isTrackedContent(stack)) {
                        if (showDebugInfo.get()) {
                            info("Found tracked item in slot " + i);
                        }
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (showDebugInfo.get()) {
                info("Error checking container inventory: " + e.getMessage());
            }
        }
        return false;
    }

    private boolean chestContainsShulker(ChestBlockEntity chestEntity) {
        return containerContainsTrackedItems(chestEntity);
    }

    private boolean isTrackedContent(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (detectShulkers.get() && isShulkerBox(stack)) {
            return true;
        }

        if (matchesCustomLists(stack)) {
            return true;
        }

        if (ItemListSync.isEnabled()) {
            Modules modules = Modules.get();
            if (modules != null) {
                ItemHighlight highlight = modules.get(ItemHighlight.class);
                if (highlight != null && highlight.matches(stack)) {
                    return true;
                }
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

    private List<Chunk> getLoadedChunks() {
        List<Chunk> chunks = new ArrayList<>();
        BlockPos playerPos = mc.player.getBlockPos();
        int renderDistance = mc.options.getViewDistance().getValue();
        
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                int chunkX = (playerPos.getX() >> 4) + x;
                int chunkZ = (playerPos.getZ() >> 4) + z;
                
                if (mc.world.isChunkLoaded(chunkX, chunkZ)) {
                    chunks.add(mc.world.getChunk(chunkX, chunkZ));
                }
            }
        }
        
        return chunks;
    }


    private boolean containerScreenHandlerContainsTracked(ScreenHandler handler) {
        try {
            int containerSlotCount = getContainerSlotCount(handler);

            for (int i = 0; i < containerSlotCount; i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (stack != null && !stack.isEmpty()) {
                    if (showDebugInfo.get()) {
                        info("Container slot " + i + ": " + stack.getItem().toString());
                    }

                    if (isTrackedContent(stack)) {
                        if (showDebugInfo.get()) {
                            info("Found tracked item in container slot " + i);
                        }
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (showDebugInfo.get()) {
                info("Error checking container screen handler: " + e.getMessage());
            }
        }

        return false;
    }

    private void rememberChest(BlockPos pos) {
        boolean isNew = !chestMemory.containsKey(pos);
        chestMemory.put(pos, System.currentTimeMillis());
        chestsWithShulkers.add(pos);
        if (isNew && chatOutput.get()) {
            if (chatCoords.get()) {
                info("Tracked chest at %d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
            } else {
                info("Tracked chest found");
            }
        }
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
        if (mc.world == null || mc.player == null) return;
        
        // Check all chests in a small radius around the player
        List<Chunk> loadedChunks = getLoadedChunks();
        
        for (Chunk chunk : loadedChunks) {
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                BlockEntity blockEntity = chunk.getBlockEntity(pos);
                
                if (isTrackableContainer(blockEntity)) {
                    double distance = mc.player.getPos().distanceTo(pos.toCenterPos());

                    if (distance <= 3.0) {
                        if (showDebugInfo.get()) {
                            info("Checking nearby container at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                        }

                        if (containerContainsTrackedItems(blockEntity)) {
                            rememberChest(pos);

                            if (showDebugInfo.get()) {
                                info("Found tracked item in nearby container at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkCurrentContainerScreen() {
        if (mc.player == null || mc.world == null) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        int containerSlots = getContainerSlotCount(handler);

        if (showDebugInfo.get()) {
            info("Currently in container screen with " + containerSlots + " slots");
        }

        if (containerSlots <= 0) return;

        List<BlockPos> containerPositions = findContainerPositionsForHandler(containerSlots);

        if (!containerPositions.isEmpty()) {
            if (showDebugInfo.get()) {
                info("Found " + containerPositions.size() + " container block(s) for handler with " + containerSlots + " slots");
                for (BlockPos pos : containerPositions) {
                    info("  Container at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                }
            }

            if (containerScreenHandlerContainsTracked(handler)) {
                for (BlockPos containerPos : containerPositions) {
                    rememberChest(containerPos);
                    updateDoubleChestPairs(containerPos);
                }

                if (showDebugInfo.get()) {
                    info("Found tracked item in open container with " + containerPositions.size() + " block(s)");
                }
            } else if (showDebugInfo.get()) {
                info("No tracked items found in open container");
            }
        }
    }

    private void checkCurrentChestScreen() {
        checkCurrentContainerScreen();
    }

    private BlockPos findClosestContainerToPlayer(int slotCount) {
        if (mc.player == null || mc.world == null) return null;

        List<Chunk> loadedChunks = getLoadedChunks();
        BlockPos closestContainer = null;
        double closestDistance = Double.MAX_VALUE;

        for (Chunk chunk : loadedChunks) {
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                BlockEntity blockEntity = chunk.getBlockEntity(pos);

                if (!isTrackableContainer(blockEntity)) continue;
                if (!(blockEntity instanceof Inventory container) || container.size() != slotCount) continue;

                double distance = mc.player.getPos().distanceTo(pos.toCenterPos());
                if (distance <= 6.0 && distance < closestDistance) {
                    closestDistance = distance;
                    closestContainer = pos;
                }
            }
        }

        return closestContainer;
    }

    private BlockPos findClosestChestToPlayer() {
        return findClosestContainerToPlayer(27);
    }

    private List<BlockPos> findContainerPositionsForHandler(int containerSlots) {
        List<BlockPos> positions = new ArrayList<>();

        if (mc.player == null || mc.world == null) return positions;

        try {
            if (containerSlots == 54 && supportDoubleChests.get()) {
                List<Chunk> loadedChunks = getLoadedChunks();
                for (Chunk chunk : loadedChunks) {
                    for (BlockPos pos : chunk.getBlockEntityPositions()) {
                        BlockEntity blockEntity = chunk.getBlockEntity(pos);

                        if (blockEntity instanceof ChestBlockEntity chestEntity) {
                            double distance = mc.player.getPos().distanceTo(pos.toCenterPos());

                            if (distance <= 6.0 && chestEntity.size() == 27) {
                                BlockPos partner = findDoubleChestPartner(pos);
                                if (partner != null) {
                                    positions.add(pos);
                                    positions.add(partner);

                                    if (showDebugInfo.get()) {
                                        info("Found double chest: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() +
                                            " <-> " + partner.getX() + ", " + partner.getY() + ", " + partner.getZ());
                                    }

                                    return positions;
                                }
                            }
                        }
                    }
                }
            }

            BlockPos closestContainer = findClosestContainerToPlayer(containerSlots);
            if (closestContainer != null) {
                positions.add(closestContainer);
            }
        } catch (Exception e) {
            if (showDebugInfo.get()) {
                info("Error finding container positions for handler: " + e.getMessage());
            }
        }

        return positions;
    }

    private List<BlockPos> findChestPositionsForHandler(ScreenHandler handler, int containerSlots) {
        return findContainerPositionsForHandler(containerSlots);
    }

    private BlockPos findDoubleChestPartner(BlockPos chestPos) {
        if (!supportDoubleChests.get() || mc.world == null) return null;
        
        BlockEntity chestEntity = mc.world.getBlockEntity(chestPos);
        if (!(chestEntity instanceof ChestBlockEntity)) return null;
        
        // Check all four horizontal directions for another chest
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        
        for (Direction direction : directions) {
            BlockPos adjacentPos = chestPos.offset(direction);
            BlockEntity adjacentEntity = mc.world.getBlockEntity(adjacentPos);
            
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
        if (mc.world == null) return false;
        
        try {
            // Get the chest block entities
            BlockEntity entity1 = mc.world.getBlockEntity(pos1);
            BlockEntity entity2 = mc.world.getBlockEntity(pos2);
            
            if (!(entity1 instanceof ChestBlockEntity) || !(entity2 instanceof ChestBlockEntity)) {
                return false;
            }
            
            // Check if they're the same type of chest (both regular chests or both trapped chests)
            Block block1 = mc.world.getBlockState(pos1).getBlock();
            Block block2 = mc.world.getBlockState(pos2).getBlock();
            
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
        
        int totalSlots = mc.player.getInventory().size();
        int shulkerCount = 0;
        int testedItems = 0;
        
        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
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
        if (mc.world == null) return;
        
        List<BlockPos> toRemove = new ArrayList<>();
        
        // Check all remembered chests to see if they still exist
        for (BlockPos pos : chestMemory.keySet()) {
            BlockEntity blockEntity = mc.world.getBlockEntity(pos);
            if (!isTrackableContainer(blockEntity)) {
                toRemove.add(pos);
            }
        }
        
        // Remove destroyed containers from both memory and rendering
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
                BlockEntity blockEntity = mc.world.getBlockEntity(pos);
                if (!isTrackableContainer(blockEntity)) {
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
