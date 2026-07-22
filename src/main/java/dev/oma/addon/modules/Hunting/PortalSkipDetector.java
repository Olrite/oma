package dev.oma.addon.modules.Hunting;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PortalSkipDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Radius in chunks to scan around you.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("How many chunks to fully scan each tick. Raise for faster detection, lower if it lags.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> minBoundedSides = sgGeneral.add(new IntSetting.Builder()
        .name("min-bounded-sides")
        .description("Minimum number of the frame's four sides (top/bottom/left/right) that must be fully solid to count it as enclosed.")
        .defaultValue(2)
        .min(0)
        .max(4)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Integer> minCaveAirNearby = sgGeneral.add(new IntSetting.Builder()
        .name("min-cave-air-nearby")
        .description("Minimum CAVE_AIR blocks found along the frame's four sides. Lower this if it's not detecting your skip, raise it if you get false positives.")
        .defaultValue(10)
        .min(0)
        .max(18)
        .sliderRange(0, 18)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Send a chat notification when a portal skip pattern is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render ESP boxes around detected portal patterns.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the ESP boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color of found portal patterns.")
        .defaultValue(new SettingColor(170, 0, 255, 55))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of found portal patterns.")
        .defaultValue(new SettingColor(170, 0, 255, 200))
        .build()
    );

    private final Minecraft mc = Minecraft.getInstance();

    private final Set<Long> scannedChunks = new HashSet<>();
    private final Set<Long> foundKeys = new HashSet<>();
    private final List<AABB> foundBoxes = new ArrayList<>();
    private final ArrayDeque<ChunkPos> pendingChunks = new ArrayDeque<>();

    private int refreshCounter;

    private static final int PORTAL_WIDTH = 4;
    private static final int PORTAL_HEIGHT = 5;
    private static final int MAX_CANDIDATES_PER_CHUNK = 6000;

    public PortalSkipDetector() {
        super(Main.HUNT, "Portal Skip Detector", "Scans loaded chunks for air pockets carved into solid terrain that indicate a portal-skip setup.");
    }

    @Override
    public void onActivate() {
        scannedChunks.clear();
        foundKeys.clear();
        foundBoxes.clear();
        pendingChunks.clear();
        refreshCounter = 0;
    }

    @Override
    public void onDeactivate() {
        pendingChunks.clear();
    }

    @Override
    public String getInfoString() {
        if (foundBoxes.isEmpty() && scannedChunks.isEmpty()) return null;
        return foundBoxes.size() + " | " + scannedChunks.size();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (refreshCounter-- <= 0) {
            refreshCounter = 20;
            pruneFarChunks();
            queueNewChunks();
        }

        int budget = chunksPerTick.get();
        while (budget-- > 0) {
            ChunkPos pos = pendingChunks.poll();
            if (pos == null) break;

            if (!mc.level.hasChunk(pos.x(), pos.z())) continue;

            LevelChunk chunk = mc.level.getChunk(pos.x(), pos.z());
            if (chunk == null) continue;

            scanChunk(chunk);
            scannedChunks.add(chunkKey(pos.x(), pos.z()));
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || foundBoxes.isEmpty()) return;

        for (AABB box : foundBoxes) {
            event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }

    private void queueNewChunks() {
        BlockPos playerPos = mc.player.blockPosition();
        int r = chunkRadius.get();
        int centerX = playerPos.getX() >> 4;
        int centerZ = playerPos.getZ() >> 4;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;

                ChunkPos pos = new ChunkPos(centerX + dx, centerZ + dz);
                long key = chunkKey(pos.x(), pos.z());
                if (scannedChunks.contains(key)) continue;
                if (pendingChunks.contains(pos)) continue;
                if (!mc.level.hasChunk(pos.x(), pos.z())) continue;

                pendingChunks.add(pos);
            }
        }
    }

    private void pruneFarChunks() {
        BlockPos playerPos = mc.player.blockPosition();
        int centerX = playerPos.getX() >> 4;
        int centerZ = playerPos.getZ() >> 4;
        int keep = chunkRadius.get() + 2;
        int keepSq = keep * keep;

        scannedChunks.removeIf(key -> {
            long k = key;
            int cx = (int) k;
            int cz = (int) (k >> 32);
            int dx = cx - centerX;
            int dz = cz - centerZ;
            return dx * dx + dz * dz > keepSq;
        });
    }

    private void scanChunk(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        int maxY = Level.NETHER.equals(mc.level.dimension()) ? 128 : Math.min(mc.level.getMaxY(), 180);

        int candidates = 0;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) continue;

            int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            if (baseY > maxY) continue;

            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;

            for (int x = 0; x < 16 && candidates < MAX_CANDIDATES_PER_CHUNK; x++) {
                for (int y = 0; y < 16 && candidates < MAX_CANDIDATES_PER_CHUNK; y++) {
                    int worldY = baseY + y;
                    if (worldY > maxY) continue;

                    for (int z = 0; z < 16 && candidates < MAX_CANDIDATES_PER_CHUNK; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!isEmpty(state.getBlock())) continue;

                        BlockPos pos = new BlockPos(baseX + x, worldY, baseZ + z);
                        if (!hasSolidNeighbor(pos)) continue;

                        candidates++;
                        tryFitPortalNear(pos, true);
                        tryFitPortalNear(pos, false);
                    }
                }
            }
        }
    }

    /** Cheap pre-filter: only bother trying to fit a frame around air blocks that border something solid. */
    private boolean hasSolidNeighbor(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!isEmpty(mc.level.getBlockState(pos.relative(dir)).getBlock())) return true;
        }
        return false;
    }

    private void tryFitPortalNear(BlockPos anchor, boolean xAligned) {
        for (int wOff = 0; wOff < PORTAL_WIDTH; wOff++) {
            for (int hOff = 0; hOff < PORTAL_HEIGHT; hOff++) {
                BlockPos corner = xAligned
                    ? anchor.offset(-wOff, -hOff, 0)
                    : anchor.offset(0, -hOff, -wOff);
                checkFrame(corner, xAligned);
            }
        }
    }

    private void checkFrame(BlockPos corner, boolean xAligned) {
        long key = packKey(corner, xAligned);
        if (foundKeys.contains(key)) return;

        int airCount = 0;

        // Corners are always allowed to be solid (broken/dug portals often miss corners);
        // every other cell must be air, or the frame doesn't match.
        for (int h = 0; h < PORTAL_HEIGHT; h++) {
            for (int w = 0; w < PORTAL_WIDTH; w++) {
                BlockPos checkPos = xAligned ? corner.offset(w, h, 0) : corner.offset(0, h, w);
                boolean empty = isEmpty(mc.level.getBlockState(checkPos).getBlock());
                boolean isCorner = (h == 0 || h == PORTAL_HEIGHT - 1) && (w == 0 || w == PORTAL_WIDTH - 1);

                if (empty) {
                    airCount++;
                } else if (!isCorner) {
                    return;
                }
            }
        }

        if (airCount < PORTAL_WIDTH * PORTAL_HEIGHT - 4) return;
        if (!isPortalSkipContext(corner, xAligned)) return;

        foundKeys.add(key);

        BlockPos end = xAligned
            ? corner.offset(PORTAL_WIDTH - 1, PORTAL_HEIGHT - 1, 0)
            : corner.offset(0, PORTAL_HEIGHT - 1, PORTAL_WIDTH - 1);

        AABB box = new AABB(
            Math.min(corner.getX(), end.getX()),
            Math.min(corner.getY(), end.getY()),
            Math.min(corner.getZ(), end.getZ()),
            Math.max(corner.getX(), end.getX()) + 1,
            Math.max(corner.getY(), end.getY()) + 1,
            Math.max(corner.getZ(), end.getZ()) + 1
        );
        foundBoxes.add(box);

        if (notify.get()) {
            info("Possible portal skip at %d, %d, %d!", corner.getX(), corner.getY(), corner.getZ());
        }
    }

    /**
     * True if the frame is sufficiently bounded and surrounded by cave-air - a portal-skip
     * signature carved near a cave rather than sitting in an open cavity by itself.
     */
    private boolean isPortalSkipContext(BlockPos corner, boolean xAligned) {
        int caveAirNearby = 0;
        int boundedSides = 0;

        boolean leftBounded = true;
        for (int h = 0; h < PORTAL_HEIGHT; h++) {
            BlockPos pos = xAligned ? corner.offset(-1, h, 0) : corner.offset(0, h, -1);
            Block block = mc.level.getBlockState(pos).getBlock();
            if (block == Blocks.AIR) leftBounded = false;
            if (block == Blocks.CAVE_AIR) caveAirNearby++;
        }
        if (leftBounded) boundedSides++;

        boolean rightBounded = true;
        for (int h = 0; h < PORTAL_HEIGHT; h++) {
            BlockPos pos = xAligned ? corner.offset(PORTAL_WIDTH, h, 0) : corner.offset(0, h, PORTAL_WIDTH);
            Block block = mc.level.getBlockState(pos).getBlock();
            if (block == Blocks.AIR) rightBounded = false;
            if (block == Blocks.CAVE_AIR) caveAirNearby++;
        }
        if (rightBounded) boundedSides++;

        boolean bottomBounded = true;
        for (int w = 0; w < PORTAL_WIDTH; w++) {
            BlockPos pos = xAligned ? corner.offset(w, -1, 0) : corner.offset(0, -1, w);
            Block block = mc.level.getBlockState(pos).getBlock();
            if (block == Blocks.AIR) bottomBounded = false;
            if (block == Blocks.CAVE_AIR) caveAirNearby++;
        }
        if (bottomBounded) boundedSides++;

        boolean topBounded = true;
        for (int w = 0; w < PORTAL_WIDTH; w++) {
            BlockPos pos = xAligned ? corner.offset(w, PORTAL_HEIGHT, 0) : corner.offset(0, PORTAL_HEIGHT, w);
            Block block = mc.level.getBlockState(pos).getBlock();
            if (block == Blocks.AIR) topBounded = false;
            if (block == Blocks.CAVE_AIR) caveAirNearby++;
        }
        if (topBounded) boundedSides++;

        return boundedSides >= minBoundedSides.get() && caveAirNearby >= minCaveAirNearby.get();
    }

    private static boolean isEmpty(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    private static long packKey(BlockPos corner, boolean xAligned) {
        return (corner.asLong() << 1) | (xAligned ? 1L : 0L);
    }
}
