package dev.oma.addon.modules.Hunting;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scans (on a background thread) for locations around a target position where a Nether portal
 * could spawn: the highest floor of four contiguous solid blocks (running north) with 4 blocks
 * of air above each, then renders the resulting portal footprints.
 */
public class PortalPredicter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<BlockPos> targetPos = sgGeneral.add(new BlockPosSetting.Builder()
        .name("target-position")
        .description("Block position to look around for possible / valid portal spawn locations.")
        .defaultValue(new BlockPos(0, 0, 0))
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("The range at which portal locations are scanned and rendered.")
        .defaultValue(32)
        .min(8)
        .sliderRange(8, 128)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the predicted portal locations are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of predicted portal locations.")
        .defaultValue(new SettingColor(25, 25, 225, 255))
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color of predicted portal locations.")
        .defaultValue(new SettingColor(25, 25, 225, 25))
        .build()
    );

    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final List<BlockPos> portalBases = new CopyOnWriteArrayList<>();

    public PortalPredicter() {
        super(Main.HUNT, "Portal Predicter", "Client-side scan for where a Nether portal could spawn around a target position.");
    }

    @Override
    public void onDeactivate() {
        portalBases.clear();
        scanning.set(false);
    }

    @Override
    public String getInfoString() {
        if (portalBases.isEmpty()) return null;
        return String.valueOf(portalBases.size());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player != null && mc.world != null) {
            if (scanning.compareAndSet(false, true)) {
                new Thread(this::scan, "PortalPredicterScanThread").start();
            }
        } else {
            portalBases.clear();
            scanning.set(false);
        }
    }

    /** Background thread: scans the columns around the target and records valid portal footprints. */
    private void scan() {
        World world = mc.world;
        if (world == null) {
            scanning.set(false);
            return;
        }

        BlockPos center = targetPos.get();
        int horizontalRadius = radius.get();
        int yMin = world.getBottomY();
        List<BlockPos> newPortals = new ArrayList<>();

        for (int x = center.getX() - horizontalRadius; x <= center.getX() + horizontalRadius; x++) {
            for (int z = center.getZ() - horizontalRadius; z <= center.getZ() + horizontalRadius; z++) {
                int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                int yMax = Math.min(surfaceY, center.getY() + horizontalRadius);

                for (int y = yMax; y >= yMin; y--) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (isValidPortalSpawn(world, candidate)) {
                        newPortals.add(candidate);
                        break;
                    }
                }
            }
        }

        portalBases.clear();
        portalBases.addAll(newPortals);
        scanning.set(false);
    }

    /** True if {@code basePos} and the three blocks north form a solid floor with 4 blocks of air above. */
    private boolean isValidPortalSpawn(World world, BlockPos basePos) {
        List<BlockPos> requiredSolidBlocks = List.of(
            basePos,
            basePos.offset(Direction.NORTH, 1),
            basePos.offset(Direction.NORTH, 2),
            basePos.offset(Direction.NORTH, 3)
        );

        for (BlockPos pos : requiredSolidBlocks) {
            if (!world.getBlockState(pos).isSideSolidFullSquare(world, pos, Direction.UP)) return false;
        }
        for (BlockPos pos : requiredSolidBlocks) {
            for (int i = 1; i < 5; i++) {
                if (!world.getBlockState(pos.up(i)).isAir()) return false;
            }
        }
        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (BlockPos portalBase : portalBases) {
            for (int i = 0; i < 5; i++) {
                event.renderer.box(portalBase.offset(Direction.NORTH, i), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }
}
