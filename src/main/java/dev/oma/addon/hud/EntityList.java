package dev.oma.addon.hud;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.oma.addon.Main;
import dev.oma.addon.util.HudFont;

public class EntityList extends HudElement {
    public static final HudElementInfo<EntityList> INFO = new HudElementInfo<>(Main.HUD_GROUP, "Entity List", "Displays nearby entities in a list.", EntityList::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Display the HUD title.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showItems = sgGeneral.add(new BoolSetting.Builder()
        .name("show-items")
        .description("Show dropped items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showMobs = sgGeneral.add(new BoolSetting.Builder()
        .name("show-mobs")
        .description("Show mobs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("show-players")
        .description("Show players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showProjectiles = sgGeneral.add(new BoolSetting.Builder()
        .name("show-projectiles")
        .description("Show thrown projectiles (ender pearls, arrows, etc).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showRockets = sgGeneral.add(new BoolSetting.Builder()
        .name("show-rockets")
        .description("Show firework rockets.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to show entities.")
        .defaultValue(100.0)
        .min(0.0)
        .sliderRange(0.0, 500.0)
        .build()
    );

    private final Setting<Boolean> sortByDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("sort-by-distance")
        .description("Sort entities by distance.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Show distance to entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeYLevel = sgGeneral.add(new BoolSetting.Builder()
        .name("include-y-level")
        .description("Include Y level in distance calculation (3D distance).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of the text.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 3.0)
        .build()
    );

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Render shadow behind the text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customFont = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-font")
        .description("Use Meteor's custom font. Off uses the default Minecraft / resource pack font.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> playerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("player-color")
        .description("Color for player entities.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    private final Setting<SettingColor> mobColor = sgGeneral.add(new ColorSetting.Builder()
        .name("mob-color")
        .description("Color for mob entities.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<SettingColor> itemColor = sgGeneral.add(new ColorSetting.Builder()
        .name("item-color")
        .description("Color for item entities.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> projectileColor = sgGeneral.add(new ColorSetting.Builder()
        .name("projectile-color")
        .description("Color for projectile entities.")
        .defaultValue(new SettingColor(150, 100, 255, 255))
        .build()
    );

    private final Setting<SettingColor> rocketColor = sgGeneral.add(new ColorSetting.Builder()
        .name("rocket-color")
        .description("Color for firework rockets.")
        .defaultValue(new SettingColor(255, 165, 0, 255))
        .build()
    );

    private final Setting<SettingColor> countColor = sgGeneral.add(new ColorSetting.Builder()
        .name("count-color")
        .description("Color for item / entity count text (e.g. x64).")
        .defaultValue(new SettingColor(200, 200, 200, 255))
        .build()
    );

    private final Setting<SettingColor> distanceColor = sgGeneral.add(new ColorSetting.Builder()
        .name("distance-color")
        .description("Color for distance text (e.g. 12m).")
        .defaultValue(new SettingColor(180, 180, 180, 255))
        .build()
    );

    public EntityList() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean font = customFont.get();
        boolean shadow = textShadow.get();
        double scale = textScale.get();

        if (MeteorClient.mc.level == null || MeteorClient.mc.player == null) {
            if (isInEditor()) {
                HudFont.text(renderer, "Entity List", x, y, playerColor.get(), font, shadow, scale);
                setSize(HudFont.textWidth(renderer, "Entity List", font, shadow, scale), HudFont.textHeight(renderer, font, shadow, scale));
            }
            return;
        }

        Map<String, Aggregated> map = new HashMap<>();
        for (Entity entity : MeteorClient.mc.level.entitiesForRendering()) {
            if (entity == MeteorClient.mc.player) continue;

            double dx = entity.getX() - MeteorClient.mc.player.getX();
            double dz = entity.getZ() - MeteorClient.mc.player.getZ();
            double distance;

            if (includeYLevel.get()) {
                double dy = entity.getY() - MeteorClient.mc.player.getY();
                distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            } else {
                distance = Math.sqrt(dx * dx + dz * dz);
            }

            if (distance > maxDistance.get()) continue;

            boolean isRocket = entity instanceof FireworkRocketEntity;
            if (isRocket && !showRockets.get()) continue;

            boolean isItem = entity instanceof ItemEntity && showItems.get();
            boolean isMob = entity instanceof Mob && showMobs.get();
            boolean isPlayer = entity instanceof Player && showPlayers.get();
            boolean isProjectile = !isRocket && (entity instanceof Projectile || entity instanceof ThrownEnderpearl) && showProjectiles.get();

            if (!isItem && !isMob && !isPlayer && !isProjectile && !isRocket) continue;

            String name = getEntityName(entity);
            SettingColor color = getEntityColor(entity);

            Aggregated agg = map.get(name);
            if (agg == null) {
                agg = new Aggregated();
                agg.name = name;
                agg.color = color;
                agg.minDist = distance;
                if (isItem) {
                    agg.count = ((ItemEntity) entity).getItem().getCount();
                } else {
                    agg.count = 1;
                }
                map.put(name, agg);
            } else {
                agg.minDist = Math.min(agg.minDist, distance);
                if (isItem) {
                    agg.count += ((ItemEntity) entity).getItem().getCount();
                } else {
                    agg.count++;
                }
            }
        }

        List<Aggregated> aggregatedList = new ArrayList<>(map.values());
        if (sortByDistance.get()) {
            aggregatedList.sort(Comparator.comparingDouble(a -> a.minDist));
        }

        double curX = x;
        double curY = y;
        double maxWidth = 0;
        double height = 0;
        double textHeight = HudFont.textHeight(renderer, font, shadow, scale);
        double spacing = 2;

        if (showTitle.get()) {
            String title = "Entity List";
            double titleWidth = HudFont.textWidth(renderer, title, font, shadow, scale);
            HudFont.text(renderer, title, curX, curY, playerColor.get(), font, shadow, scale);
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, titleWidth);
        }

        for (Aggregated agg : aggregatedList) {
            double rowX = curX;
            double rowWidth = 0;

            double nameW = HudFont.text(renderer, agg.name, rowX, curY, agg.color, font, shadow, scale);
            rowX += nameW;
            rowWidth += nameW;

            if (agg.count > 1) {
                String countText = " x" + agg.count;
                double countW = HudFont.text(renderer, countText, rowX, curY, countColor.get(), font, shadow, scale);
                rowX += countW;
                rowWidth += countW;
            }

            if (showDistance.get()) {
                String distText = " (" + (int) agg.minDist + "m)";
                double distW = HudFont.text(renderer, distText, rowX, curY, distanceColor.get(), font, shadow, scale);
                rowWidth += distW;
            }

            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, rowWidth);
        }

        setSize(maxWidth, Math.max(0, height - spacing));
    }

    private String getEntityName(Entity entity) {
        if (entity instanceof ItemEntity item) {
            return item.getItem().getHoverName().getString();
        } else if (entity instanceof Player player) {
            return player.getName().getString();
        } else if (entity instanceof FireworkRocketEntity) {
            return "Firework Rocket";
        } else if (entity instanceof ThrownEnderpearl) {
            return "Ender Pearl";
        } else if (entity instanceof ThrownTrident) {
            return "Trident";
        } else if (entity instanceof Projectile) {
            // Get simple name for projectiles
            String className = entity.getClass().getSimpleName();
            return className.replace("Entity", "").replaceAll("([A-Z])", " $1").trim();
        } else {
            return entity.getDisplayName().getString();
        }
    }

    private SettingColor getEntityColor(Entity entity) {
        if (entity instanceof ItemEntity) {
            return itemColor.get();
        } else if (entity instanceof Mob) {
            return mobColor.get();
        } else if (entity instanceof Player) {
            return playerColor.get();
        } else if (entity instanceof FireworkRocketEntity) {
            return rocketColor.get();
        } else if (entity instanceof Projectile || entity instanceof ThrownEnderpearl) {
            return projectileColor.get();
        }
        return new SettingColor(255, 255, 255, 255);  // Fallback
    }

    private static class Aggregated {
        String name;
        int count;
        double minDist;
        SettingColor color;
    }
}
