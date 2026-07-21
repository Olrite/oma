package dev.oma.addon.modules.Render;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

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
    private final Minecraft mc = Minecraft.getInstance();

    public MobItemESP() {
        super(Main.RENDER, "MobItemESP", "Highlights mobs holding items they wouldn't normally spawn with.");
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
        if (mc.level == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter < updateInterval.get()) return;
        tickCounter = 0;

        detectMobsWithModifiedItems();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderESP.get() || mc.level == null || mc.player == null) return;

        double maxDist = maxDistance.get() * maxDistance.get();

        for (Entity mob : mobsWithModifiedItems) {
            if (mob.isRemoved() || !mob.isAlive()) continue;
            if (mc.player.position().distanceToSqr(mob.position()) > maxDist) continue;

            AABB box = mob.getBoundingBox();
            event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                mobColor.get(), mobColor.get(), shapeMode.get(),
                0
            );
        }
    }

    private void detectMobsWithModifiedItems() {
        if (mc.level == null || mc.player == null) return;

        mobsWithModifiedItems.clear();
        mobModifiedItems.clear();

        double maxDist = maxDistance.get();
        int mobCount = 0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (mobCount >= 100) break; // Limit to prevent lag
            mobCount++;

            if (!isTargetMob(entity)) continue;
            if (mc.player.position().distanceToSqr(entity.position()) > maxDist * maxDist) continue;

            ItemStack mainHand = ItemStack.EMPTY;
            ItemStack offHand = ItemStack.EMPTY;
            
            if (entity instanceof LivingEntity livingEntity) {
                mainHand = livingEntity.getItemBySlot(EquipmentSlot.MAINHAND);
                offHand = livingEntity.getItemBySlot(EquipmentSlot.OFFHAND);
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
        if (entity instanceof Zombie && showZombies.get()) return true;
        if (entity instanceof Skeleton && showSkeletons.get()) return true;
        if (entity instanceof Stray && showStrays.get()) return true;
        if (entity instanceof WitherSkeleton && showWitherSkeletons.get()) return true;
        if (entity instanceof Husk && showHusks.get()) return true;
        if (entity instanceof ZombifiedPiglin && showZombifiedPiglins.get()) return true;
        if (entity instanceof Piglin && showPiglins.get()) return true;
        if (entity instanceof Bogged && showBogged.get()) return true;
        if (entity instanceof Drowned && showDrowned.get()) return true;
        return false;
    }

    private String getMobTypeName(Entity entity) {
        if (entity instanceof Zombie) return "Zombie";
        if (entity instanceof Skeleton) return "Skeleton";
        if (entity instanceof Stray) return "Stray";
        if (entity instanceof WitherSkeleton) return "Wither Skeleton";
        if (entity instanceof Husk) return "Husk";
        if (entity instanceof ZombifiedPiglin) return "Zombified Piglin";
        if (entity instanceof Piglin) return "Piglin";
        if (entity instanceof Bogged) return "Bogged";
        if (entity instanceof Drowned) return "Drowned";
        return entity.getType().toString().replace("minecraft:", "").replace("_", " ");
    }

    private boolean isModifiedItem(ItemStack item) {
        if (item.isEmpty()) return false;

        // List of items that mobs wouldn't normally spawn with
        return item.getItem() == Items.SHULKER_BOX ||
               item.getItem() == Items.ENDER_CHEST ||
               item.getItem() == Items.ENDER_PEARL ||
               item.getItem() == Items.ENDER_EYE ||
               item.getItem() == Items.NETHER_STAR ||
               item.getItem() == Items.BEACON ||
               item.getItem() == Items.DRAGON_EGG ||
               item.getItem() == Items.TOTEM_OF_UNDYING ||
               item.getItem() == Items.ELYTRA ||
               item.getItem() == Items.TRIDENT ||
               item.getItem() == Items.CROSSBOW ||
               item.getItem() == Items.SHIELD ||
               item.getItem() == Items.ARROW ||
               item.getItem() == Items.SPECTRAL_ARROW ||
               item.getItem() == Items.TIPPED_ARROW ||
               item.getItem() == Items.ENCHANTED_BOOK ||
               item.getItem() == Items.NAME_TAG ||
               item.getItem() == Items.SADDLE ||
               item.getItem() == Items.DIAMOND_HORSE_ARMOR ||
               item.getItem() == Items.GOLDEN_HORSE_ARMOR ||
               item.getItem() == Items.IRON_HORSE_ARMOR ||
               item.getItem() == Items.LEATHER_HORSE_ARMOR ||
               item.getItem() == Items.MUSIC_DISC_11 ||
               item.getItem() == Items.MUSIC_DISC_13 ||
               item.getItem() == Items.MUSIC_DISC_BLOCKS ||
               item.getItem() == Items.MUSIC_DISC_CAT ||
               item.getItem() == Items.MUSIC_DISC_CHIRP ||
               item.getItem() == Items.MUSIC_DISC_FAR ||
               item.getItem() == Items.MUSIC_DISC_MALL ||
               item.getItem() == Items.MUSIC_DISC_MELLOHI ||
               item.getItem() == Items.MUSIC_DISC_PIGSTEP ||
               item.getItem() == Items.MUSIC_DISC_STAL ||
               item.getItem() == Items.MUSIC_DISC_STRAD ||
               item.getItem() == Items.MUSIC_DISC_WAIT ||
               item.getItem() == Items.MUSIC_DISC_WARD ||
               item.getItem() == Items.MUSIC_DISC_5 ||
               item.getItem() == Items.MUSIC_DISC_OTHERSIDE ||
               item.getItem() == Items.MUSIC_DISC_RELIC;
    }
}
