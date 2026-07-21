package dev.oma.addon.modules.Utility;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.network.chat.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class MapArchiver extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNaming = settings.createGroup("Naming");

    private final Setting<String> folderPath = sgGeneral.add(new StringSetting.Builder()
        .name("folder-path")
        .description("Full folder path to save maps (e.g., C:/Users/YourName/Desktop/maps or /home/user/maps)")
        .defaultValue(System.getProperty("user.home") + File.separator + "2b2t-maps")
        .build()
    );

    private final Setting<Boolean> autoSave = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-save-on-view")
        .description("Automatically save maps when you hold them")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> quietMode = sgGeneral.add(new BoolSetting.Builder()
        .name("quiet-mode")
        .description("Only show one message per session instead of every save")
        .defaultValue(false)
        .build()
    );

    private final Setting<NameFormat> nameFormat = sgNaming.add(new EnumSetting.Builder<NameFormat>()
        .name("name-format")
        .description("How to name the saved map files")
        .defaultValue(NameFormat.MapId)
        .build()
    );

    private final Setting<Boolean> includeTimestamp = sgNaming.add(new BoolSetting.Builder()
        .name("include-timestamp")
        .description("Add timestamp to filename")
        .defaultValue(true)
        .visible(() -> nameFormat.get() != NameFormat.Timestamp)
        .build()
    );

    private final Setting<Boolean> includeMapId = sgNaming.add(new BoolSetting.Builder()
        .name("include-map-id")
        .description("Add map ID to filename")
        .defaultValue(true)
        .visible(() -> nameFormat.get() == NameFormat.MapName)
        .build()
    );

    private final Set<Integer> savedMaps = new HashSet<>();
    private int checkDelay = 0;
    private boolean hasShownMessage = false;

    public MapArchiver() {
        super(Main.MOD, "Map Archiver", "Archives map art as PNG images");
    }

    @Override
    public void onActivate() {
        savedMaps.clear();
        hasShownMessage = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !autoSave.get()) return;

        checkDelay++;
        if (checkDelay < 20) return; // Check every 1 second
        checkDelay = 0;

        // Check held item
        ItemStack held = mc.player.getMainHandItem();
        if (held.getItem() == Items.FILLED_MAP) {
            saveMapImage(held);
        }

        // Check off-hand
        ItemStack offHand = mc.player.getOffhandItem();
        if (offHand.getItem() == Items.FILLED_MAP) {
            saveMapImage(offHand);
        }

        // Check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.FILLED_MAP) {
                saveMapImage(stack);
            }
        }
    }

    private void saveMapImage(ItemStack mapStack) {
        Minecraft mc = Minecraft.getInstance();

        // Get the map ID from the item
        MapId mapId = mapStack.get(DataComponents.MAP_ID);
        if (mapId == null) return;

        int id = mapId.id();

        // Check if we already saved this map
        if (savedMaps.contains(id)) return;

        // Get the map state from the world
        MapItemSavedData mapState = mc.level.getMapData(mapId);
        if (mapState == null || mapState.colors == null) return;

        try {
            // Create a 128x128 map image
            BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);

            // Extract actual map pixel data
            for (int x = 0; x < 128; x++) {
                for (int z = 0; z < 128; z++) {
                    int index = x + z * 128;
                    byte colorByte = mapState.colors[index];

                    // Convert Minecraft's map color to RGB
                    int color = getMapColor(colorByte);
                    image.setRGB(x, z, color);
                }
            }

            // Create folder
            File folder = new File(folderPath.get());
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    error("Failed to create folder: " + folderPath.get());
                    return;
                }
            }

            // Generate filename based on settings
            String filename = generateFilename(mapStack, id);
            File outputFile = new File(folder, filename);

            ImageIO.write(image, "png", outputFile);
            savedMaps.add(id);

            // Show message based on quiet mode
            if (quietMode.get()) {
                if (!hasShownMessage) {
                    info("Maps are being saved to: " + folder.getAbsolutePath());
                    hasShownMessage = true;
                }
            } else {
                info("Saved map to " + outputFile.getAbsolutePath());
            }
        } catch (IOException e) {
            error("Failed to save map: " + e.getMessage());
        } catch (Exception e) {
            error("Unexpected error: " + e.getMessage());
        }
    }

    private String generateFilename(ItemStack mapStack, int id) {
        StringBuilder filename = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());

        switch (nameFormat.get()) {
            case MapName -> {
                // Try to get custom map name from item
                Component customName = mapStack.getHoverName();
                String mapName = customName.getString();

                // Clean the name for filename
                mapName = mapName.replaceAll("[^a-zA-Z0-9-_]", "_");

                // If name is just "Map" or generic, use map_X format
                if (mapName.equalsIgnoreCase("Map") || mapName.equalsIgnoreCase("Filled_Map")) {
                    filename.append("map");
                    if (includeMapId.get()) {
                        filename.append("_").append(id);
                    }
                } else {
                    filename.append(mapName);
                    if (includeMapId.get()) {
                        filename.append("_").append(id);
                    }
                }

                if (includeTimestamp.get()) {
                    filename.append("_").append(timestamp);
                }
            }
            case MapId -> {
                filename.append("map_").append(id);
                if (includeTimestamp.get()) {
                    filename.append("_").append(timestamp);
                }
            }
            case Timestamp -> {
                filename.append("map_").append(timestamp);
            }
        }

        filename.append(".png");
        return filename.toString();
    }

    private int getMapColor(byte colorByte) {
        // Minecraft map colors are stored as bytes
        int colorIndex = colorByte & 0xFF;

        if (colorIndex < 4) {
            return 0; // Transparent/unused colors
        }

        // Get the base color and shade
        int baseIndex = colorIndex / 4;
        int shade = colorIndex % 4;

        // Try to get RGB from map color palette
        try {
            net.minecraft.world.level.material.MapColor mapColor = net.minecraft.world.level.material.MapColor.byId(baseIndex);
            if (mapColor != null) {
                // MapColor stores color as int, access it via reflection if needed
                // But first try the render color method with different shade calculation

                // Shade multipliers: 0 = 180/255, 1 = 220/255, 2 = 255/255, 3 = 135/255
                int rgb = mapColor.calculateARGBColor(net.minecraft.world.level.material.MapColor.Brightness.byId(shade));

                // If getRenderColor returns 0, try getting the base color directly
                if (rgb == 0 && mapColor.id != 0) {
                    rgb = mapColor.id;

                    // Apply shade manually
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    double shadeMult = switch (shade) {
                        case 0 -> 180.0 / 255.0;
                        case 1 -> 220.0 / 255.0;
                        case 2 -> 1.0;
                        case 3 -> 135.0 / 255.0;
                        default -> 1.0;
                    };

                    r = (int)(r * shadeMult);
                    g = (int)(g * shadeMult);
                    b = (int)(b * shadeMult);

                    return (r << 16) | (g << 8) | b;
                }

                return rgb;
            }
        } catch (Exception e) {
            // Fallback
        }

        // Fallback to grayscale if everything fails
        int gray = colorIndex * 2;
        return (gray << 16) | (gray << 8) | gray;
    }

    public enum NameFormat {
        MapName,
        MapId,
        Timestamp
    }
}