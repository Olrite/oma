package dev.oma.addon.modules.Render;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ESP for blocks whose orientation/position differs from how they naturally generate
 * (e.g. sideways deepslate, basalt pillars laid on their side, bedrock above the floor).
 */
public class WeirdBlockESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFinds = settings.createGroup("Finds");
    private final SettingGroup sgCustom = settings.createGroup("Custom");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgChat = settings.createGroup("Chat");

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to detect weird blocks.")
        .defaultValue(64.0)
        .min(1.0)
        .sliderRange(1.0, 128.0)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("Ticks between detection updates.")
        .defaultValue(40)
        .min(5)
        .sliderRange(5, 200)
        .build()
    );

    private final Setting<Boolean> performanceMode = sgGeneral.add(new BoolSetting.Builder()
        .name("performance-mode")
        .description("Limit chunk scan radius for better FPS.")
        .defaultValue(true)
        .build()
    );

    private final EnumMap<WeirdType, Setting<Boolean>> show = new EnumMap<>(WeirdType.class);
    private final EnumMap<WeirdType, Setting<SettingColor>> colors = new EnumMap<>(WeirdType.class);

    private final Setting<List<Block>> customBlocks = sgCustom.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Also highlight these blocks when rotated away from their natural/default orientation.")
        .defaultValue()
        .filter(block -> {
            BlockState state = block.getDefaultState();
            return state.contains(Properties.AXIS)
                || state.contains(Properties.FACING)
                || state.contains(Properties.HORIZONTAL_FACING);
        })
        .build()
    );

    private final Setting<Integer> bedrockMaxY;

    private final Setting<Boolean> renderEsp = sgRender.add(new BoolSetting.Builder()
        .name("render-esp")
        .description("Render ESP boxes around weird blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> chatOutput = sgChat.add(new BoolSetting.Builder()
        .name("chat-output")
        .description("Client-side chat notification when a new weird block is found.")
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

    private final EnumMap<WeirdType, Set<BlockPos>> found = new EnumMap<>(WeirdType.class);
    private final EnumMap<WeirdType, Set<BlockPos>> announced = new EnumMap<>(WeirdType.class);

    private int tickCounter;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public WeirdBlockESP() {
        super(Main.MOD, "Weird Block ESP", "Highlights blocks placed in orientations or positions that do not occur naturally.");

        for (WeirdType type : WeirdType.values()) {
            show.put(type, sgFinds.add(new BoolSetting.Builder()
                .name(type.settingName)
                .description(type.description)
                .defaultValue(type.defaultEnabled)
                .build()
            ));
            colors.put(type, sgRender.add(new ColorSetting.Builder()
                .name(type.settingName + "-color")
                .description("Color for " + type.label.toLowerCase() + ".")
                .defaultValue(type.defaultColor)
                .visible(() -> show.get(type).get())
                .build()
            ));
            found.put(type, new HashSet<>());
            announced.put(type, new HashSet<>());
        }

        bedrockMaxY = sgFinds.add(new IntSetting.Builder()
            .name("bedrock-max-y")
            .description("Overworld/Nether floor bedrock above this Y is flagged (natural bedrock stays near the floor).")
            .defaultValue(5)
            .range(-64, 320)
            .sliderRange(-64, 64)
            .visible(() -> show.get(WeirdType.BEDROCK).get())
            .build()
        );
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
        for (WeirdType type : WeirdType.values()) {
            found.get(type).clear();
            announced.get(type).clear();
        }
    }

    @Override
    public String getInfoString() {
        int total = 0;
        for (WeirdType type : WeirdType.values()) total += found.get(type).size();
        return total > 0 ? Integer.toString(total) : null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter < updateInterval.get()) return;
        tickCounter = 0;

        detect();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderEsp.get() || mc.world == null || mc.player == null) return;

        double maxDist = maxDistance.get() * maxDistance.get();
        for (WeirdType type : WeirdType.values()) {
            if (!show.get(type).get()) continue;
            SettingColor color = colors.get(type).get();
            for (BlockPos pos : found.get(type)) {
                if (mc.player.getBlockPos().getSquaredDistance(pos) > maxDist) continue;
                event.renderer.box(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                    color, color, shapeMode.get(),
                    0
                );
            }
        }
    }

    private void detect() {
        for (WeirdType type : WeirdType.values()) {
            found.get(type).clear();
        }

        BlockPos playerPos = mc.player.getBlockPos();
        double maxDistSq = maxDistance.get() * maxDistance.get();
        int chunkRadius = performanceMode.get()
            ? Math.min((int) Math.ceil(maxDistance.get() / 16.0), 3)
            : Math.min((int) Math.ceil(maxDistance.get() / 16.0), 4);
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                scanChunk(chunk, playerPos, maxDistSq);
            }
        }
    }

    private void scanChunk(WorldChunk chunk, BlockPos playerPos, double maxDistSq) {
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

                        BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                        if (playerPos.getSquaredDistance(pos) > maxDistSq) continue;

                        WeirdType type = classify(state, pos);
                        if (type == null || !show.get(type).get()) continue;

                        found.get(type).add(pos);
                        if (chatOutput.get() && announced.get(type).add(pos)) {
                            if (chatCoords.get()) {
                                info("Weird %s at %d, %d, %d", type.label, pos.getX(), pos.getY(), pos.getZ());
                            } else {
                                info("Weird %s found", type.label);
                            }
                        }
                    }
                }
            }
        }
    }

    private WeirdType classify(BlockState state, BlockPos pos) {
        Block block = state.getBlock();

        if (block == Blocks.DEEPSLATE && isUnnaturalAxis(state)) return WeirdType.DEEPSLATE;
        if (block == Blocks.INFESTED_DEEPSLATE && isUnnaturalAxis(state)) return WeirdType.INFESTED_DEEPSLATE;
        if (block == Blocks.BASALT && isUnnaturalAxis(state)) return WeirdType.BASALT;
        if (block == Blocks.POLISHED_BASALT && isUnnaturalAxis(state)) return WeirdType.POLISHED_BASALT;
        if (block == Blocks.BONE_BLOCK && isUnnaturalAxis(state)) return WeirdType.BONE_BLOCK;
        if (block == Blocks.HAY_BLOCK && isUnnaturalAxis(state)) return WeirdType.HAY;
        if (block == Blocks.QUARTZ_PILLAR && isUnnaturalAxis(state)) return WeirdType.QUARTZ_PILLAR;
        if (block == Blocks.PURPUR_PILLAR && isUnnaturalAxis(state)) return WeirdType.PURPUR_PILLAR;

        if (block == Blocks.BEDROCK && isWeirdBedrock(pos)) return WeirdType.BEDROCK;

        if (customBlocks.get().contains(block) && isUnnaturalOrientation(state)) return WeirdType.CUSTOM;

        return null;
    }

    private boolean isWeirdBedrock(BlockPos pos) {
        RegistryKey<World> dim = mc.world.getRegistryKey();
        int y = pos.getY();

        if (dim == World.OVERWORLD) {
            return y > bedrockMaxY.get();
        }
        if (dim == World.NETHER) {
            return y > bedrockMaxY.get() && y < 123;
        }
        if (dim == World.END) {
            return y < 50 || y > 70;
        }
        return false;
    }

    private static boolean isUnnaturalAxis(BlockState state) {
        if (!state.contains(Properties.AXIS)) return false;
        return state.get(Properties.AXIS) != Direction.Axis.Y;
    }

    private static boolean isUnnaturalOrientation(BlockState state) {
        if (isUnnaturalAxis(state)) return true;

        BlockState def = state.getBlock().getDefaultState();
        if (state.contains(Properties.FACING) && def.contains(Properties.FACING)) {
            return state.get(Properties.FACING) != def.get(Properties.FACING);
        }
        if (state.contains(Properties.HORIZONTAL_FACING) && def.contains(Properties.HORIZONTAL_FACING)) {
            return state.get(Properties.HORIZONTAL_FACING) != def.get(Properties.HORIZONTAL_FACING);
        }
        return false;
    }

    private enum WeirdType {
        DEEPSLATE("deepslate", "Deepslate", "Highlight deepslate with a non-Y axis (natural deepslate is upright).", true, new SettingColor(80, 80, 90, 160)),
        INFESTED_DEEPSLATE("infested-deepslate", "Infested Deepslate", "Highlight infested deepslate with a non-Y axis.", true, new SettingColor(120, 80, 90, 160)),
        BASALT("basalt", "Basalt", "Highlight basalt with a non-Y axis (pillars generate upright).", true, new SettingColor(60, 60, 70, 160)),
        POLISHED_BASALT("polished-basalt", "Polished Basalt", "Highlight polished basalt with a non-Y axis.", true, new SettingColor(90, 90, 100, 160)),
        BONE_BLOCK("bone-block", "Bone Block", "Highlight bone blocks with a non-Y axis.", false, new SettingColor(230, 230, 200, 160)),
        HAY("hay", "Hay", "Highlight hay bales with a non-Y axis.", false, new SettingColor(200, 180, 40, 160)),
        QUARTZ_PILLAR("quartz-pillar", "Quartz Pillar", "Highlight quartz pillars with a non-Y axis.", false, new SettingColor(240, 240, 240, 160)),
        PURPUR_PILLAR("purpur-pillar", "Purpur Pillar", "Highlight purpur pillars with a non-Y axis.", false, new SettingColor(180, 100, 180, 160)),
        BEDROCK("bedrock", "Bedrock", "Highlight bedrock in positions where it does not naturally generate.", true, new SettingColor(40, 40, 40, 180)),
        CUSTOM("custom", "Custom", "Highlight blocks from the custom list when rotated unnaturally.", true, new SettingColor(255, 80, 80, 160));

        final String settingName;
        final String label;
        final String description;
        final boolean defaultEnabled;
        final SettingColor defaultColor;

        WeirdType(String settingName, String label, String description, boolean defaultEnabled, SettingColor defaultColor) {
            this.settingName = settingName;
            this.label = label;
            this.description = description;
            this.defaultEnabled = defaultEnabled;
            this.defaultColor = defaultColor;
        }
    }
}
