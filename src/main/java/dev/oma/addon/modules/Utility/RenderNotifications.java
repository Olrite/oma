package dev.oma.addon.modules.Utility;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RenderNotifications extends Module {
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgLogging = settings.createGroup("Logging");

    private final Setting<Boolean> logFriends = sgFilter.add(new BoolSetting.Builder()
            .name("log-friends")
            .description("Log when friends are spotted.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> logEnemies = sgFilter.add(new BoolSetting.Builder()
            .name("log-enemies")
            .description("Log when non-friends are spotted.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> logSelf = sgFilter.add(new BoolSetting.Builder()
            .name("log-self")
            .description("Log your own position.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> logToChat = sgLogging.add(new BoolSetting.Builder()
            .name("log-to-chat")
            .description("Log player sightings to chat.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> logToConsole = sgLogging.add(new BoolSetting.Builder()
            .name("log-to-console")
            .description("Log player sightings to console.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> includeCoordinates = sgLogging.add(new BoolSetting.Builder()
            .name("include-coordinates")
            .description("Include coordinates in the log.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> includeDimension = sgLogging.add(new BoolSetting.Builder()
            .name("include-dimension")
            .description("Include dimension in the log.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> includeTime = sgLogging.add(new BoolSetting.Builder()
            .name("include-time")
            .description("Include timestamp in the log.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> logOnlyOnce = sgLogging.add(new BoolSetting.Builder()
            .name("log-only-once")
            .description("Only log each player once per session.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> logDelay = sgLogging.add(new IntSetting.Builder()
            .name("log-delay")
            .description("Delay between logs in ticks (20 ticks = 1 second).")
            .defaultValue(20)
            .min(1)
            .sliderMax(200)
            .build());

    private final Set<PlayerEntity> loggedPlayers = ConcurrentHashMap.newKeySet();
    private int delayTimer = 0;

    public RenderNotifications() {
        super(Main.MOD, "Render Notifications", "Logs player information when they are spotted.");
    }

    @Override
    public void onActivate() {
        loggedPlayers.clear();
        delayTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc == null || mc.world == null || mc.player == null)
            return;

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player) {
                // Skip self if not logging self
                if (player == mc.player && !logSelf.get()) {
                    continue;
                }

                // Skip if already logged this session and logOnlyOnce is enabled
                if (logOnlyOnce.get() && loggedPlayers.contains(player)) {
                    continue;
                }

                boolean isFriend = Friends.get().isFriend(player);
                boolean shouldLog = false;

                // Determine if we should log this player
                if (isFriend && logFriends.get()) {
                    shouldLog = true;
                } else if (!isFriend && logEnemies.get()) {
                    shouldLog = true;
                }

                if (shouldLog) {
                    logPlayer(player);
                    loggedPlayers.add(player);
                    delayTimer = logDelay.get();
                }
            }
        }
    }

    private void logPlayer(PlayerEntity player) {
        String logMessage = buildLogMessage(player);

        if (logToChat.get()) {
            info(logMessage);
        }

        if (logToConsole.get()) {
            Main.LOG.info("[RenderNotifications] " + logMessage);
        }
    }

    private String buildLogMessage(PlayerEntity player) {
        StringBuilder message = new StringBuilder();
        
        // Player name and type
        boolean isFriend = Friends.get().isFriend(player);
        String playerType = isFriend ? "Friend" : "Enemy";
        message.append(String.format("%s spotted: %s", playerType, player.getName().getString()));

        // Coordinates
        if (includeCoordinates.get()) {
            int x = (int) player.getX();
            int y = (int) player.getY();
            int z = (int) player.getZ();
            message.append(String.format(" at [%d, %d, %d]", x, y, z));
        }

        // Dimension
        if (includeDimension.get()) {
            Dimension dimension = PlayerUtils.getDimension();
            String dimensionName = getDimensionName(dimension);
            message.append(String.format(" in %s", dimensionName));
        }

        // Time
        if (includeTime.get()) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            String time = now.format(formatter);
            message.append(String.format(" at %s", time));
        }

        return message.toString();
    }

    private String getDimensionName(Dimension dimension) {
        return switch (dimension) {
            case Overworld -> "Overworld";
            case Nether -> "Nether";
            case End -> "End";
            default -> "Unknown";
        };
    }
}
