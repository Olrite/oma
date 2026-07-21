package dev.oma.addon.modules.Render;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DecorESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgItems = settings.createGroup("Items");

    // General settings
    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to detect vanity items.")
        .defaultValue(64.0)
        .min(1.0)
        .sliderRange(1.0, 128.0)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("Ticks between vanity item detection updates.")
        .defaultValue(40)
        .min(5)
        .sliderRange(5, 200)
        .build()
    );

    private final Setting<Boolean> enableCaching = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-caching")
        .description("Cache detected items to reduce lag.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cacheTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("cache-timeout")
        .description("Ticks before cached items expire.")
        .defaultValue(200)
        .min(20)
        .sliderRange(20, 1000)
        .visible(() -> enableCaching.get())
        .build()
    );

    // Item type settings
    private final Setting<Boolean> showItemFrames = sgItems.add(new BoolSetting.Builder()
        .name("item-frames")
        .description("Show item frames.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showBanners = sgItems.add(new BoolSetting.Builder()
        .name("banners")
        .description("Show banners.")
        .defaultValue(true)
        .build()
    );


    // Render settings
    private final Setting<Boolean> renderESP = sgRender.add(new BoolSetting.Builder()
        .name("render-esp")
        .description("Render ESP boxes around vanity items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> itemFrameColor = sgRender.add(new ColorSetting.Builder()
        .name("item-frame-color")
        .description("Color of the item frame ESP.")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .build()
    );

    private final Setting<SettingColor> bannerColor = sgRender.add(new ColorSetting.Builder()
        .name("banner-color")
        .description("Color of the banner ESP.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );


    private final Setting<Double> boxHeight = sgRender.add(new DoubleSetting.Builder()
        .name("box-height")
        .description("Height of the ESP box.")
        .defaultValue(1.0)
        .min(0.1)
        .max(3.0)
        .sliderRange(0.1, 3.0)
        .build()
    );


    private final Setting<Boolean> showCount = sgRender.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show count of vanity items in chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> performanceMode = sgGeneral.add(new BoolSetting.Builder()
        .name("performance-mode")
        .description("Enable performance optimizations (reduces accuracy but improves FPS).")
        .defaultValue(true)
        .build()
    );

    private final Set<BlockPos> itemFrames = new HashSet<>();
    private final Set<BlockPos> banners = new HashSet<>();
    
    // Caching system
    private final Map<BlockPos, Long> itemFrameCache = new HashMap<>();
    private final Map<BlockPos, Long> bannerCache = new HashMap<>();
    
    private int tickCounter = 0;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    private final Minecraft mc = Minecraft.getInstance();

    public DecorESP() {
        super(Main.RENDER, "DecorESP", "Highlights decorative items like item frames and banners.");
    }

    @Override
    public void onActivate() {
        itemFrames.clear();
        banners.clear();
        itemFrameCache.clear();
        bannerCache.clear();
        tickCounter = 0;
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
    }

    @Override
    public void onDeactivate() {
        itemFrames.clear();
        banners.clear();
        itemFrameCache.clear();
        bannerCache.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter < updateInterval.get()) return;
        tickCounter = 0;

        // Check if player moved to a new chunk
        BlockPos playerPos = mc.player.blockPosition();
        int currentChunkX = playerPos.getX() >> 4;
        int currentChunkZ = playerPos.getZ() >> 4;
        
        boolean playerMoved = (currentChunkX != lastPlayerChunkX || currentChunkZ != lastPlayerChunkZ);
        lastPlayerChunkX = currentChunkX;
        lastPlayerChunkZ = currentChunkZ;

        // Only do expensive detection if player moved or caching is disabled
        if (!enableCaching.get() || playerMoved) {
            detectVanityItems();
        } else {
            // Just update cache expiration
            updateCache();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderESP.get() || mc.level == null || mc.player == null) return;

        double maxDist = maxDistance.get() * maxDistance.get();

        // Render item frames
        if (showItemFrames.get()) {
            for (BlockPos pos : itemFrames) {
                if (mc.player.blockPosition().distSqr(pos) > maxDist) continue;
                
                event.renderer.box(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + boxHeight.get(), pos.getZ() + 1,
                    itemFrameColor.get(), itemFrameColor.get(), shapeMode.get(),
                    0
                );
            }
        }

        // Render banners
        if (showBanners.get()) {
            for (BlockPos pos : banners) {
                if (mc.player.blockPosition().distSqr(pos) > maxDist) continue;
                
                event.renderer.box(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + boxHeight.get(), pos.getZ() + 1,
                    bannerColor.get(), bannerColor.get(), shapeMode.get(),
                    0
                );
            }
        }

    }

    private void detectVanityItems() {
        if (mc.level == null || mc.player == null) return;

        // Clear previous detections
        itemFrames.clear();
        banners.clear();

        double maxDist = maxDistance.get();
        BlockPos playerPos = mc.player.blockPosition();
        long currentTime = System.currentTimeMillis();

        // Get chunks around player (reduced radius for performance)
        int chunkRadius = performanceMode.get() ? 
            Math.min((int) Math.ceil(maxDist / 16.0), 3) : // Limit to 3 chunks in performance mode
            Math.min((int) Math.ceil(maxDist / 16.0), 4);  // Limit to 4 chunks max
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                LevelChunk chunk = mc.level.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                // Check block entities (banners) - much more efficient
                if (showBanners.get()) {
                    for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                        // Check cache first
                        if (enableCaching.get() && bannerCache.containsKey(pos)) {
                            if (currentTime - bannerCache.get(pos) < cacheTimeout.get() * 50L) {
                                banners.add(pos);
                                continue;
                            }
                        }
                        
                        BlockEntity blockEntity = chunk.getBlockEntity(pos);
                        if (blockEntity instanceof BannerBlockEntity) {
                            if (playerPos.distSqr(pos) <= maxDist * maxDist) {
                                banners.add(pos);
                                if (enableCaching.get()) {
                                    bannerCache.put(pos, currentTime);
                                }
                            }
                        }
                    }
                }

            }
        }

        // Check for item frames (entities) - only if enabled
        if (showItemFrames.get()) {
            // Limit entity scanning to reduce lag
            int entityCount = 0;
            int maxEntities = performanceMode.get() ? 50 : 100; // Limit to prevent lag
            
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entityCount >= maxEntities) break;
                entityCount++;
                
                if (entity instanceof ItemFrame) {
                    BlockPos pos = entity.blockPosition();
                    if (playerPos.distSqr(pos) <= maxDist * maxDist) {
                        itemFrames.add(pos);
                        if (enableCaching.get()) {
                            itemFrameCache.put(pos, currentTime);
                        }
                    }
                }
            }
        }

        // Show count if enabled
        if (showCount.get()) {
            int totalCount = itemFrames.size() + banners.size();
            if (totalCount > 0) {
                info("Vanity items found: %d item frames, %d banners", 
                    itemFrames.size(), banners.size());
            }
        }
    }

    private void updateCache() {
        long currentTime = System.currentTimeMillis();
        long timeout = cacheTimeout.get() * 50L; // Convert ticks to milliseconds
        double maxDist = maxDistance.get();
        BlockPos playerPos = mc.player.blockPosition();

        // Remove expired items from cache
        itemFrameCache.entrySet().removeIf(entry -> currentTime - entry.getValue() > timeout);
        bannerCache.entrySet().removeIf(entry -> currentTime - entry.getValue() > timeout);

        // Update active sets with cached items that are still within range
        itemFrames.clear();
        banners.clear();

        // Add cached items that are still within distance
        for (BlockPos pos : itemFrameCache.keySet()) {
            if (playerPos.distSqr(pos) <= maxDist * maxDist) {
                itemFrames.add(pos);
            }
        }

        for (BlockPos pos : bannerCache.keySet()) {
            if (playerPos.distSqr(pos) <= maxDist * maxDist) {
                banners.add(pos);
            }
        }
    }
}
