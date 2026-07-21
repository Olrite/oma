package dev.oma.addon.modules.Render;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MobItemESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgMobs = settings.createGroup("Mobs");

    // General settings
    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to detect mobs with modified items.")
        .defaultValue(64.0)
        .min(1.0)
        .sliderRange(1.0, 128.0)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("Ticks between mob detection updates.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    // Mob type settings
    private final Setting<Boolean> showZombies = sgMobs.add(new BoolSetting.Builder()
        .name("zombies")
        .description("Show zombies with modified items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showSkeletons = sgMobs.add(new BoolSetting.Builder()
        .name("skeletons")
        .description("Show skeletons with modified items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showStrays = sgMobs.add(new BoolSetting.Builder()
        .name("strays")
        .description("Show strays with modified items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showWitherSkeletons = sgMobs.add(new BoolSetting.Builder()
        .name("wither-skeletons")
        .description("Show wither skeletons with modified items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showHusks = sgMobs.add(new BoolSetting.Builder()
        .name("husks")
        .description("Show husks with modified items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showZombifiedPiglins = sgMobs.add(new BoolSetting.Builder()
        .name("zombified-piglins")
        .description("Show zombified piglins with modified items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPiglins = sgMobs.add(new BoolSetting.Builder()
        .name("piglins")
        .description("Show piglins with modified items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showBogged = sgMobs.add(new BoolSetting.Builder()
        .name("bogged")
        .description("Show bogged with modified items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDrowned = sgMobs.add(new BoolSetting.Builder()
        .name("drowned")
        .description("Show drowned with modified items.")
        .defaultValue(true)
        .build()
    );

    // Render settings
    private final Setting<Boolean> renderESP = sgRender.add(new BoolSetting.Builder()
        .name("render-esp")
        .description("Render ESP boxes around mobs with modified items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> mobColor = sgRender.add(new ColorSetting.Builder()
        .name("mob-color")
        .description("Color of the mob ESP.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );


    private final Setting<Boolean> showCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show count of detected mobs in chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showChatMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("show-chat-messages")
        .description("Show chat messages when mobs with modified items are found.")
        .defaultValue(false)
        .build()
    );

    private final Set<Entity> mobsWithModifiedItems = new HashSet<>();
    private final Map<Entity, ItemStack> mobModifiedItems = new HashMap<>();
    private int tickCounter = 0;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public MobItemESP() {
        super(Main.RENDER, "Mob Item ESP", "Highlights mobs holding items they wouldn't normally spawn with.");
    }

    @Override
    public void onActivate() {
        mobsWithModifiedItems.clear();
        mobModifiedItems.clear();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        mobsWithModifiedItems.clear();
        mobModifiedItems.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter < updateInterval.get()) return;
        tickCounter = 0;

        detectMobsWithModifiedItems();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderESP.get() || mc.world == null || mc.player == null) return;

        double maxDist = maxDistance.get() * maxDistance.get();

        for (Entity mob : mobsWithModifiedItems) {
            if (mob.isRemoved() || !mob.isAlive()) continue;
            if (mc.player.getPos().squaredDistanceTo(mob.getPos()) > maxDist) continue;

            Box box = mob.getBoundingBox();
            event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                mobColor.get(), mobColor.get(), shapeMode.get(),
                0
            );
        }
    }

    private void detectMobsWithModifiedItems() {
        if (mc.world == null || mc.player == null) return;

        mobsWithModifiedItems.clear();
        mobModifiedItems.clear();

        double maxDist = maxDistance.get();
        int mobCount = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (mobCount >= 100) break; // Limit to prevent lag
            mobCount++;

            if (!isTargetMob(entity)) continue;
            if (mc.player.getPos().squaredDistanceTo(entity.getPos()) > maxDist * maxDist) continue;

            ItemStack mainHand = ItemStack.EMPTY;
            ItemStack offHand = ItemStack.EMPTY;
            
            if (entity instanceof LivingEntity livingEntity) {
                mainHand = livingEntity.getEquippedStack(EquipmentSlot.MAINHAND);
                offHand = livingEntity.getEquippedStack(EquipmentSlot.OFFHAND);
            }

            // Check if mob is holding modified items
            ItemStack modifiedItem = null;
            if (isModifiedItem(mainHand)) {
                modifiedItem = mainHand;
            } else if (isModifiedItem(offHand)) {
                modifiedItem = offHand;
            }

            if (modifiedItem != null) {
                // Only add to set if not already detected (to avoid duplicate messages)
                boolean isNewDetection = !mobsWithModifiedItems.contains(entity);
                mobsWithModifiedItems.add(entity);
                mobModifiedItems.put(entity, modifiedItem);
                
                // Send individual chat message if enabled and this is a new detection
                if (showChatMessages.get() && isNewDetection) {
                    String mobName = getMobTypeName(entity);
                    String itemName = modifiedItem.getItem().toString().replace("minecraft:", "");
                    info("Found %s holding %s", mobName, itemName);
                }
            }
        }

        if (showCount.get() && !mobsWithModifiedItems.isEmpty()) {
            info("Found %d mobs with modified items", mobsWithModifiedItems.size());
        }
    }

    private boolean isTargetMob(Entity entity) {
        if (entity instanceof ZombieEntity && showZombies.get()) return true;
        if (entity instanceof SkeletonEntity && showSkeletons.get()) return true;
        if (entity instanceof StrayEntity && showStrays.get()) return true;
        if (entity instanceof WitherSkeletonEntity && showWitherSkeletons.get()) return true;
        if (entity instanceof HuskEntity && showHusks.get()) return true;
        if (entity instanceof ZombifiedPiglinEntity && showZombifiedPiglins.get()) return true;
        if (entity instanceof PiglinEntity && showPiglins.get()) return true;
        if (entity instanceof BoggedEntity && showBogged.get()) return true;
        if (entity instanceof DrownedEntity && showDrowned.get()) return true;
        return false;
    }

    private String getMobTypeName(Entity entity) {
        if (entity instanceof ZombieEntity) return "Zombie";
        if (entity instanceof SkeletonEntity) return "Skeleton";
        if (entity instanceof StrayEntity) return "Stray";
        if (entity instanceof WitherSkeletonEntity) return "Wither Skeleton";
        if (entity instanceof HuskEntity) return "Husk";
        if (entity instanceof ZombifiedPiglinEntity) return "Zombified Piglin";
        if (entity instanceof PiglinEntity) return "Piglin";
        if (entity instanceof BoggedEntity) return "Bogged";
        if (entity instanceof DrownedEntity) return "Drowned";
        return entity.getType().toString().replace("minecraft:", "").replace("_", " ");
    }

    private boolean isModifiedItem(ItemStack item) {
        if (item.isEmpty()) return false;

        // List of items that mobs wouldn't normally spawn with
        return item.isOf(Items.SHULKER_BOX) ||
               item.isOf(Items.ENDER_CHEST) ||
               item.isOf(Items.ENDER_PEARL) ||
               item.isOf(Items.ENDER_EYE) ||
               item.isOf(Items.NETHER_STAR) ||
               item.isOf(Items.BEACON) ||
               item.isOf(Items.DRAGON_EGG) ||
               item.isOf(Items.TOTEM_OF_UNDYING) ||
               item.isOf(Items.ELYTRA) ||
               item.isOf(Items.TRIDENT) ||
               item.isOf(Items.CROSSBOW) ||
               item.isOf(Items.SHIELD) ||
               item.isOf(Items.ARROW) ||
               item.isOf(Items.SPECTRAL_ARROW) ||
               item.isOf(Items.TIPPED_ARROW) ||
               item.isOf(Items.ENCHANTED_BOOK) ||
               item.isOf(Items.NAME_TAG) ||
               item.isOf(Items.SADDLE) ||
               item.isOf(Items.DIAMOND_HORSE_ARMOR) ||
               item.isOf(Items.GOLDEN_HORSE_ARMOR) ||
               item.isOf(Items.IRON_HORSE_ARMOR) ||
               item.isOf(Items.LEATHER_HORSE_ARMOR) ||
               item.isOf(Items.MUSIC_DISC_11) ||
               item.isOf(Items.MUSIC_DISC_13) ||
               item.isOf(Items.MUSIC_DISC_BLOCKS) ||
               item.isOf(Items.MUSIC_DISC_CAT) ||
               item.isOf(Items.MUSIC_DISC_CHIRP) ||
               item.isOf(Items.MUSIC_DISC_FAR) ||
               item.isOf(Items.MUSIC_DISC_MALL) ||
               item.isOf(Items.MUSIC_DISC_MELLOHI) ||
               item.isOf(Items.MUSIC_DISC_PIGSTEP) ||
               item.isOf(Items.MUSIC_DISC_STAL) ||
               item.isOf(Items.MUSIC_DISC_STRAD) ||
               item.isOf(Items.MUSIC_DISC_WAIT) ||
               item.isOf(Items.MUSIC_DISC_WARD) ||
               item.isOf(Items.MUSIC_DISC_5) ||
               item.isOf(Items.MUSIC_DISC_OTHERSIDE) ||
               item.isOf(Items.MUSIC_DISC_RELIC);
    }
}
