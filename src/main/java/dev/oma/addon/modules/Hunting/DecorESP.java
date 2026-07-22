package dev.oma.addon.modules.Hunting;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.AbstractTorchBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ChiseledBookshelfBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.SeaPickleBlock;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DecorESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Items");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgChat = settings.createGroup("Chat");

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
        .description("Keep previously found items visible for a grace period even if a later scan misses them (e.g. due to a chunk briefly unloading).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cacheTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("cache-timeout")
        .description("Ticks a found item stays visible after it was last actually seen.")
        .defaultValue(200)
        .min(20)
        .sliderRange(20, 1000)
        .visible(enableCaching::get)
        .build()
    );

    private final Setting<Boolean> performanceMode = sgGeneral.add(new BoolSetting.Builder()
        .name("performance-mode")
        .description("Enable performance optimizations (reduces accuracy but improves FPS).")
        .defaultValue(true)
        .build()
    );

    private final EnumMap<DecorType, Setting<Boolean>> show = new EnumMap<>(DecorType.class);
    private final EnumMap<DecorType, Setting<SettingColor>> colors = new EnumMap<>(DecorType.class);

    private final Setting<Boolean> renderEsp = sgRender.add(new BoolSetting.Builder()
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

    private final Setting<Double> boxHeight = sgRender.add(new DoubleSetting.Builder()
        .name("box-height")
        .description("Height of the ESP box.")
        .defaultValue(1.0)
        .min(0.1)
        .max(3.0)
        .sliderRange(0.1, 3.0)
        .build()
    );

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("opacity")
        .description("Opacity of the decor ESP (0-100).")
        .defaultValue(35)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> chatOutput = sgChat.add(new BoolSetting.Builder()
        .name("chat-output")
        .description("Client-side chat notification when a new decor item is found. Disables all chat output from this module when off.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chatCoords = sgChat.add(new BoolSetting.Builder()
        .name("chat-coordinates")
        .description("Include coordinates in chat notifications.")
        .defaultValue(true)
        .visible(chatOutput::get)
        .build()
    );

    private final Setting<Boolean> showCount = sgChat.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show count of vanity items in chat.")
        .defaultValue(false)
        .visible(chatOutput::get)
        .build()
    );

    private final EnumMap<DecorType, Set<BlockPos>> found = new EnumMap<>(DecorType.class);
    private final EnumMap<DecorType, Set<BlockPos>> announced = new EnumMap<>(DecorType.class);
    private final EnumMap<DecorType, Map<BlockPos, Long>> caches = new EnumMap<>(DecorType.class);

    private int tickCounter;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public DecorESP() {
        super(Main.HUNT, "Decor ESP", "Highlights decorative user-placed items.");

        for (DecorType type : DecorType.values()) {
            show.put(type, sgItems.add(new BoolSetting.Builder()
                .name(type.settingName)
                .description("Show " + type.label.toLowerCase() + ".")
                .defaultValue(type.defaultEnabled)
                .build()
            ));
            colors.put(type, sgRender.add(new ColorSetting.Builder()
                .name(type.settingName + "-color")
                .description("Color of the " + type.label.toLowerCase() + " ESP.")
                .defaultValue(type.defaultColor)
                .visible(() -> show.get(type).get())
                .build()
            ));
            found.put(type, new HashSet<>());
            announced.put(type, new HashSet<>());
            caches.put(type, new HashMap<>());
        }
    }

    @Override
    public void onActivate() {
        clearAll();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        clearAll();
    }

    private void clearAll() {
        for (DecorType type : DecorType.values()) {
            found.get(type).clear();
            announced.get(type).clear();
            caches.get(type).clear();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter < updateInterval.get()) return;
        tickCounter = 0;

        detectVanityItems();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderEsp.get() || mc.world == null || mc.player == null) return;

        double maxDist = maxDistance.get() * maxDistance.get();
        double height = boxHeight.get();
        BlockPos playerPos = mc.player.getBlockPos();

        int alpha = opacity.get() * 255 / 100;

        for (DecorType type : DecorType.values()) {
            if (!show.get(type).get()) continue;
            SettingColor base = colors.get(type).get();
            SettingColor color = new SettingColor(base.r, base.g, base.b, alpha);
            for (BlockPos pos : found.get(type)) {
                if (playerPos.getSquaredDistance(pos) > maxDist) continue;
                event.renderer.box(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + height, pos.getZ() + 1,
                    color, color, shapeMode.get(),
                    0
                );
            }
        }
    }

    private void detectVanityItems() {
        for (DecorType type : DecorType.values()) {
            found.get(type).clear();
        }

        double maxDist = maxDistance.get();
        double maxDistSq = maxDist * maxDist;
        BlockPos playerPos = mc.player.getBlockPos();
        long currentTime = System.currentTimeMillis();

        boolean needBlockEntities = show.get(DecorType.BANNER).get()
            || show.get(DecorType.MOB_HEAD).get()
            || show.get(DecorType.CAMPFIRE).get()
            || show.get(DecorType.LECTERN).get();
        boolean needBlockScan = show.get(DecorType.FLOWER_POT).get()
            || show.get(DecorType.BOOKSHELF).get()
            || show.get(DecorType.CANDLE).get()
            || show.get(DecorType.LANTERN).get()
            || show.get(DecorType.SEA_PICKLE).get()
            || show.get(DecorType.TORCH).get()
            || show.get(DecorType.CAMPFIRE).get()
            || show.get(DecorType.LECTERN).get()
            || show.get(DecorType.MOB_HEAD).get();
        boolean needEntities = show.get(DecorType.ITEM_FRAME).get()
            || show.get(DecorType.ARMOR_STAND).get()
            || show.get(DecorType.PAINTING).get();

        int chunkRadius = performanceMode.get()
            ? Math.min((int) Math.ceil(maxDist / 16.0), 3)
            : Math.min((int) Math.ceil(maxDist / 16.0), 4);
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;

        if (needBlockEntities || needBlockScan) {
            for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
                for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                    WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                    if (chunk == null) continue;

                    if (needBlockEntities) {
                        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                            BlockPos pos = entry.getKey();
                            if (playerPos.getSquaredDistance(pos) > maxDistSq) continue;
                            BlockEntity be = entry.getValue();
                            if (be instanceof BannerBlockEntity) {
                                add(DecorType.BANNER, pos, currentTime);
                            } else if (be instanceof SkullBlockEntity) {
                                add(DecorType.MOB_HEAD, pos, currentTime);
                            } else if (be instanceof CampfireBlockEntity) {
                                add(DecorType.CAMPFIRE, pos, currentTime);
                            } else if (be instanceof LecternBlockEntity) {
                                add(DecorType.LECTERN, pos, currentTime);
                            }
                        }
                    }

                    if (needBlockScan) {
                        scanChunkBlocks(chunk, playerPos, maxDistSq, currentTime);
                    }
                }
            }
        }

        if (needEntities) {
            int entityCount = 0;
            int maxEntities = performanceMode.get() ? 150 : 300;
            for (Entity entity : mc.world.getEntities()) {
                if (entityCount++ >= maxEntities) break;
                BlockPos pos = entity.getBlockPos();
                if (playerPos.getSquaredDistance(pos) > maxDistSq) continue;

                if (entity instanceof ItemFrameEntity) {
                    add(DecorType.ITEM_FRAME, pos, currentTime);
                } else if (entity instanceof ArmorStandEntity) {
                    add(DecorType.ARMOR_STAND, pos, currentTime);
                } else if (entity instanceof PaintingEntity) {
                    add(DecorType.PAINTING, pos, currentTime);
                }
            }
        }

        // Grace buffer: keep recently-seen items visible even if this particular scan
        // missed them (e.g. their chunk briefly wasn't returned by getChunk), so ESP
        // boxes don't flicker away between scans.
        if (enableCaching.get()) {
            long timeout = cacheTimeout.get() * 50L;
            for (DecorType type : DecorType.values()) {
                Map<BlockPos, Long> cache = caches.get(type);
                cache.entrySet().removeIf(entry -> currentTime - entry.getValue() > timeout);

                if (!show.get(type).get()) continue;
                Set<BlockPos> set = found.get(type);
                for (BlockPos pos : cache.keySet()) {
                    if (playerPos.getSquaredDistance(pos) <= maxDistSq) {
                        set.add(pos);
                    }
                }
            }
        }

        if (chatOutput.get() && showCount.get()) {
            int total = 0;
            StringBuilder sb = new StringBuilder("Decor found:");
            for (DecorType type : DecorType.values()) {
                int count = found.get(type).size();
                if (count == 0) continue;
                total += count;
                sb.append(' ').append(count).append(' ').append(type.label.toLowerCase()).append(',');
            }
            if (total > 0) {
                if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
                info(sb.toString());
            }
        }
    }

    private void scanChunkBlocks(WorldChunk chunk, BlockPos playerPos, double maxDistSq, long currentTime) {
        ChunkSection[] sections = chunk.getSectionArray();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) continue;

            int baseY = chunk.sectionIndexToCoord(sectionIndex) << 4;
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;

                        Block block = state.getBlock();
                        DecorType type = classifyBlock(block);
                        if (type == null) continue;

                        BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                        if (playerPos.getSquaredDistance(pos) > maxDistSq) continue;
                        add(type, pos, currentTime);
                    }
                }
            }
        }
    }

    private DecorType classifyBlock(Block block) {
        if (block instanceof FlowerPotBlock) return DecorType.FLOWER_POT;
        if (block instanceof AbstractSkullBlock) return DecorType.MOB_HEAD;
        if (block instanceof CampfireBlock) return DecorType.CAMPFIRE;
        if (block == Blocks.BOOKSHELF || block instanceof ChiseledBookshelfBlock) return DecorType.BOOKSHELF;
        if (block instanceof AbstractCandleBlock) return DecorType.CANDLE;
        if (block instanceof LanternBlock) return DecorType.LANTERN;
        if (block instanceof LecternBlock) return DecorType.LECTERN;
        if (block instanceof SeaPickleBlock) return DecorType.SEA_PICKLE;
        if (block instanceof AbstractTorchBlock) return DecorType.TORCH;
        return null;
    }

    private void add(DecorType type, BlockPos pos, long currentTime) {
        if (!show.get(type).get()) return;
        found.get(type).add(pos);
        if (enableCaching.get()) {
            caches.get(type).put(pos, currentTime);
        }
        announceDecor(type.label, pos, announced.get(type));
    }

    private void announceDecor(String label, BlockPos pos, Set<BlockPos> announcedSet) {
        if (!chatOutput.get() || !announcedSet.add(pos.toImmutable())) return;
        if (chatCoords.get()) {
            info("%s at %d, %d, %d", label, pos.getX(), pos.getY(), pos.getZ());
        } else {
            info("%s found", label);
        }
    }

    private enum DecorType {
        ITEM_FRAME("item-frames", "Item frame", true, new SettingColor(255, 255, 0, 255)),
        BANNER("banners", "Banner", true, new SettingColor(255, 0, 255, 255)),
        FLOWER_POT("flower-pots", "Flower pot", true, new SettingColor(100, 200, 80, 255)),
        MOB_HEAD("mob-heads", "Mob head", true, new SettingColor(220, 220, 220, 255)),
        ARMOR_STAND("armor-stands", "Armor stand", true, new SettingColor(200, 180, 140, 255)),
        CAMPFIRE("campfires", "Campfire", true, new SettingColor(255, 120, 40, 255)),
        BOOKSHELF("bookshelves", "Bookshelf", true, new SettingColor(140, 90, 40, 255)),
        CANDLE("candles", "Candle", true, new SettingColor(255, 230, 150, 255)),
        LANTERN("lanterns", "Lantern", true, new SettingColor(255, 200, 60, 255)),
        LECTERN("lecterns", "Lectern", true, new SettingColor(160, 110, 60, 255)),
        PAINTING("paintings", "Painting", true, new SettingColor(180, 80, 200, 255)),
        SEA_PICKLE("sea-pickles", "Sea pickle", true, new SettingColor(80, 220, 120, 255)),
        TORCH("torches", "Torch", true, new SettingColor(255, 160, 40, 255));

        final String settingName;
        final String label;
        final boolean defaultEnabled;
        final SettingColor defaultColor;

        DecorType(String settingName, String label, boolean defaultEnabled, SettingColor defaultColor) {
            this.settingName = settingName;
            this.label = label;
            this.defaultEnabled = defaultEnabled;
            this.defaultColor = defaultColor;
        }
    }
}
