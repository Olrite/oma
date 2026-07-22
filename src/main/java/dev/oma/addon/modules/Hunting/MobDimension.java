package dev.oma.addon.modules.Hunting;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MobDimension extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgChat = settings.createGroup("Chat");

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to detect out-of-dimension mobs.")
        .defaultValue(128.0)
        .min(1.0)
        .sliderRange(1.0, 256.0)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("Ticks between detection updates.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> animals = sgFilters.add(new BoolSetting.Builder()
        .name("animals")
        .description("Highlight animals that do not spawn in this dimension.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> monsters = sgFilters.add(new BoolSetting.Builder()
        .name("monsters")
        .description("Highlight monsters that do not spawn in this dimension.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ambient = sgFilters.add(new BoolSetting.Builder()
        .name("ambient")
        .description("Highlight ambient mobs (e.g. bats) that do not spawn in this dimension.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> water = sgFilters.add(new BoolSetting.Builder()
        .name("water")
        .description("Highlight water creatures that do not spawn in this dimension.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderEsp = sgRender.add(new BoolSetting.Builder()
        .name("render-esp")
        .description("Render ESP boxes around out-of-dimension mobs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> espColor = sgRender.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color of the ESP box.")
        .defaultValue(new SettingColor(255, 80, 80, 200))
        .build()
    );

    private final Setting<Integer> opacity = sgRender.add(new IntSetting.Builder()
        .name("opacity")
        .description("Opacity of the ESP box (0-100).")
        .defaultValue(35)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> chatOutput = sgChat.add(new BoolSetting.Builder()
        .name("chat-output")
        .description("Client-side chat notification when an out-of-dimension mob is found.")
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

    private static final Set<EntityType<?>> NETHER_NATIVE = Set.of(
        EntityType.GHAST,
        EntityType.PIGLIN,
        EntityType.PIGLIN_BRUTE,
        EntityType.HOGLIN,
        EntityType.ZOGLIN,
        EntityType.MAGMA_CUBE,
        EntityType.BLAZE,
        EntityType.STRIDER,
        EntityType.WITHER_SKELETON,
        EntityType.ZOMBIFIED_PIGLIN
    );

    private static final Set<EntityType<?>> END_NATIVE = Set.of(
        EntityType.SHULKER,
        EntityType.ENDER_DRAGON
    );

    /** Spawns naturally in more than one dimension. */
    private static final Set<EntityType<?>> ANY_DIMENSION = Set.of(
        EntityType.ENDERMAN
    );

    private final Map<UUID, Entity> found = new HashMap<>();
    private final Set<UUID> announced = new HashSet<>();
    private int tickCounter;
    private RegistryKey<World> lastDimension;

    public MobDimension() {
        super(Main.HUNT, "Mob Dimension", "ESP for mobs and animals that do not normally spawn in the current dimension.");
    }

    @Override
    public void onActivate() {
        found.clear();
        announced.clear();
        tickCounter = 0;
        lastDimension = null;
    }

    @Override
    public void onDeactivate() {
        found.clear();
        announced.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        RegistryKey<World> dimension = mc.world.getRegistryKey();
        if (lastDimension != null && lastDimension != dimension) {
            found.clear();
            announced.clear();
        }
        lastDimension = dimension;

        tickCounter++;
        if (tickCounter < updateInterval.get()) return;
        tickCounter = 0;

        scan();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderEsp.get() || mc.world == null || mc.player == null) return;

        SettingColor base = espColor.get();
        SettingColor renderColor = new SettingColor(base.r, base.g, base.b, opacity.get() * 255 / 100);

        for (Entity entity : found.values()) {
            if (entity == null || entity.isRemoved()) continue;

            Box box = entity.getBoundingBox();
            event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                renderColor, renderColor, shapeMode.get(),
                0
            );
        }
    }

    private void scan() {
        found.clear();

        HomeDimension current = homeOf(mc.world.getRegistryKey());
        if (current == HomeDimension.UNKNOWN) return;

        double maxDistSq = maxDistance.get() * maxDistance.get();
        int checked = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (checked++ > 500) break;
            if (!(entity instanceof MobEntity)) continue;
            if (!passesFilter(entity)) continue;
            if (mc.player.squaredDistanceTo(entity) > maxDistSq) continue;

            HomeDimension home = homeOf(entity.getType());
            if (home == HomeDimension.ANY || home == current) continue;

            found.put(entity.getUuid(), entity);

            if (chatOutput.get() && announced.add(entity.getUuid())) {
                announce(entity, home, current);
            }
        }

        found.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isRemoved());
    }

    private boolean passesFilter(Entity entity) {
        if (entity instanceof AnimalEntity) return animals.get();

        SpawnGroup group = entity.getType().getSpawnGroup();
        return switch (group) {
            case CREATURE, AXOLOTLS -> animals.get();
            case MONSTER -> monsters.get();
            case AMBIENT -> ambient.get();
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE -> water.get();
            default -> monsters.get() || animals.get();
        };
    }

    private void announce(Entity entity, HomeDimension home, HomeDimension current) {
        String name = entity.getName().getString();
        String msg = name + " (normally " + home.label + ") in " + current.label;
        if (chatCoords.get()) {
            msg += String.format(" at %d, %d, %d",
                (int) Math.floor(entity.getX()),
                (int) Math.floor(entity.getY()),
                (int) Math.floor(entity.getZ())
            );
        }
        info(msg);
    }

    private static HomeDimension homeOf(RegistryKey<World> dimension) {
        if (dimension == World.OVERWORLD) return HomeDimension.OVERWORLD;
        if (dimension == World.NETHER) return HomeDimension.NETHER;
        if (dimension == World.END) return HomeDimension.END;
        return HomeDimension.UNKNOWN;
    }

    private static HomeDimension homeOf(EntityType<?> type) {
        if (ANY_DIMENSION.contains(type)) return HomeDimension.ANY;
        if (NETHER_NATIVE.contains(type)) return HomeDimension.NETHER;
        if (END_NATIVE.contains(type)) return HomeDimension.END;
        return HomeDimension.OVERWORLD;
    }

    @Override
    public String getInfoString() {
        return found.isEmpty() ? null : String.valueOf(found.size());
    }

    private enum HomeDimension {
        OVERWORLD("Overworld"),
        NETHER("Nether"),
        END("End"),
        ANY("any"),
        UNKNOWN("unknown");

        final String label;

        HomeDimension(String label) {
            this.label = label;
        }
    }
}
