package dev.oma.addon.modules.Utility;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.EntityType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class AutoLogPlus extends Module {
    private final SettingGroup sgTimeLog = settings.createGroup("Time Log");
    private final SettingGroup sgLocationLog = settings.createGroup("Location Log");
    private final SettingGroup sgPingLog = settings.createGroup("Ping Log");
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgEntities = settings.createGroup("Entities");

    private final Setting<Set<EntityType<?>>> entities = sgEntities.add(new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Disconnects when a specified entity is present within a specified range.")
            .defaultValue(EntityType.END_CRYSTAL)
            .build());

    private final Setting<Integer> range = sgEntities.add(new IntSetting.Builder()
            .name("range")
            .description("How close an entity has to be to you before you disconnect.")
            .defaultValue(5)
            .min(1)
            .sliderMax(16)
            .visible(() -> !entities.get().isEmpty())
            .build());

    private final Setting<Boolean> useTotalCount = sgEntities.add(new BoolSetting.Builder()
            .name("use-total-count")
            .description("Use total count of all selected entities instead of individual counts.")
            .defaultValue(false)
            .visible(() -> !entities.get().isEmpty())
            .build());

    private final Setting<Integer> combinedEntityThreshold = sgEntities.add(new IntSetting.Builder()
            .name("combined-entity-threshold")
            .description("Total number of selected entities before disconnecting.")
            .defaultValue(10)
            .min(1)
            .sliderMax(50)
            .visible(() -> !entities.get().isEmpty() && useTotalCount.get())
            .build());

    private final Setting<Integer> individualEntityThreshold = sgEntities.add(new IntSetting.Builder()
            .name("individual-entity-threshold")
            .description("Number of each entity type before disconnecting.")
            .defaultValue(5)
            .min(1)
            .sliderMax(50)
            .visible(() -> !entities.get().isEmpty() && !useTotalCount.get())
            .build());

    private final Setting<Boolean> proximityDetection = sgEntities.add(new BoolSetting.Builder()
            .name("proximity-detection")
            .description("Instantly disconnect when specific entities are within close range.")
            .defaultValue(false)
            .build());

    private final Setting<Set<EntityType<?>>> proximityEntities = sgEntities.add(new EntityTypeListSetting.Builder()
            .name("proximity-entities")
            .description("Entities that trigger instant disconnect when within close range.")
            .defaultValue(EntityType.PLAYER)
            .visible(proximityDetection::get)
            .build());

    private final Setting<Integer> proximityRange = sgEntities.add(new IntSetting.Builder()
            .name("proximity-range")
            .description("Range for instant disconnect (in blocks).")
            .defaultValue(3)
            .min(1)
            .sliderMax(10)
            .visible(proximityDetection::get)
            .build());

    private final Object2IntMap<EntityType<?>> entityCounts = new Object2IntOpenHashMap<>();

    private final Setting<Boolean> timeLog = sgTimeLog.add(new BoolSetting.Builder()
            .name("time-log")
            .description("Logs you out after a certain amount of time.")
            .defaultValue(false)
            .build());

    private final Setting<String> logTime = sgTimeLog.add(new StringSetting.Builder()
            .name("time")
            .description("The time to log you out (uses 24 hour time).")
            .defaultValue("12:00")
            .visible(timeLog::get)
            .build());

    private final Setting<Boolean> locationLog = sgLocationLog.add(new BoolSetting.Builder()
            .name("location-log")
            .description("Disconnects when a you reach set coordinates.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> oneAxis = sgLocationLog.add(new BoolSetting.Builder()
            .name("one-axis-log")
            .description("Disconnects when a you reach set coordinates on a specific axis.")
            .defaultValue(false)
            .visible(locationLog::get)
            .build());

    private final Setting<axisOptions> selectAxis = sgLocationLog.add(new EnumSetting.Builder<axisOptions>()
            .name("select-axis")
            .description("The axis with the exact log coords.")
            .defaultValue(axisOptions.X)
            .visible(oneAxis::get)
            .build());

    private final Setting<Dimension> dimension = sgLocationLog.add(new EnumSetting.Builder<Dimension>()
            .name("dimension")
            .description("Dimension for the coords.")
            .defaultValue(Dimension.Nether)
            .visible(locationLog::get)
            .build());

    private final Setting<Integer> xCoords = sgLocationLog.add(new IntSetting.Builder()
            .name("x-coords")
            .description("The X coords it should log you out.")
            .defaultValue(0)
            .range(-2147483648, 2147483647)
            .sliderRange(-2147483648, 2147483647)
            .visible(locationLog::get)
            .build());

    private final Setting<Integer> zCoords = sgLocationLog.add(new IntSetting.Builder()
            .name("z-coords")
            .description("The Z coords it should log you out.")
            .defaultValue(-1000)
            .range(-2147483648, 2147483647)
            .sliderRange(-2147483648, 2147483647)
            .visible(locationLog::get)
            .build());

    private final Setting<Integer> radius = sgLocationLog.add(new IntSetting.Builder()
            .name("radius")
            .description("The radius of coords from the exact coords it will log you out.")
            .defaultValue(64)
            .min(0)
            .sliderRange(0, 256)
            .visible(locationLog::get)
            .build());

    private final Setting<Boolean> pingLog = sgPingLog.add(new BoolSetting.Builder()
            .name("ping-log")
            .description("Disconnects when your ping is above a certain value.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> pingValue = sgPingLog.add(new IntSetting.Builder()
            .name("ping-value")
            .defaultValue(1000)
            .range(0, 10000)
            .sliderRange(0, 10000)
            .visible(pingLog::get)
            .build());

    private final Setting<Boolean> onlyTrusted = sgGeneral.add(new BoolSetting.Builder()
            .name("enemy")
            .description("Disconnects when a player not on your friends list appears in render distance.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> toggleAutoReconnect = sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-auto-reconnect")
            .description("Turns off auto reconnect when disconnecting.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> toggleOff = sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-off")
            .description("Disables Time Log after usage.")
            .defaultValue(true)
            .build());

    public AutoLogPlus() {
        super(Main.UTILS, "AutoLog-Plus", "Disconnects you when a specific condition is reached.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc == null || mc.level == null || mc.player == null)
            return;

        playerLog();
        entitiesLog();
        proximityLog();
        disconnectOnHighPing();
        timeLog();
        locationLog();
    }

    private void playerLog() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player && entity.getUUID() != mc.player.getUUID()) {
                if (onlyTrusted.get() && entity != mc.player && !Friends.get().isFriend((Player) entity)) {
                    disconnect(Component.literal("[AutoLogout] A non trusted player [" + entity.getName().getString()
                            + "] has entered your render distance."));
                }
            }

        }
    }

    private void entitiesLog() {
        if (!entities.get().isEmpty()) {
            int totalEntities = 0;
            entityCounts.clear();

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (PlayerUtils.isWithin(entity, range.get()) && entities.get().contains(entity.getType())) {
                    totalEntities++;
                    if (!useTotalCount.get()) {
                        entityCounts.put(entity.getType(), entityCounts.getOrDefault(entity.getType(), 0) + 1);
                    }
                }
            }

            if (useTotalCount.get() && totalEntities >= combinedEntityThreshold.get()) {
                disconnect(Component.literal("[AutoLogout] Total number of selected entities within range exceeded the limit."));
                if (toggleOff.get())
                    this.toggle();
            } else if (!useTotalCount.get()) {
                for (Object2IntMap.Entry<EntityType<?>> entry : entityCounts.object2IntEntrySet()) {
                    if (entry.getIntValue() >= individualEntityThreshold.get()) {
                        disconnect(Component.literal("[AutoLogout] Number of " + entry.getKey().getDescription().getString()
                                + " within range exceeded the limit."));
                        if (toggleOff.get())
                            this.toggle();
                        return;
                    }
                }
            }
        }
    }

    private void proximityLog() {
        if (!proximityDetection.get() || proximityEntities.get().isEmpty())
            return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getUUID().equals(mc.player.getUUID()))
                continue;

            if (proximityEntities.get().contains(entity.getType())
                    && PlayerUtils.isWithin(entity, proximityRange.get())) {
                disconnect(Component.literal("[AutoLogout] " + entity.getType().getDescription().getString()
                        + " detected within close range (" + proximityRange.get() + " blocks)."));
                if (toggleOff.get())
                    this.toggle();
                return;
            }
        }
    }

    private void timeLog() {
        if (timeLog.get()) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");
            LocalDateTime now = LocalDateTime.now();
            if (dtf.format(now).equals(logTime.get())) {
                disconnect(Component.literal("[AutoLogout] Log time has been reached " + logTime.get() + "."));
            }
        }
    }

    private void locationLog() {
        if (locationLog.get() && PlayerUtils.getDimension() == dimension.get()) {
            if (xCoordsMatch() && zCoordsMatch()) {
                disconnect(Component.literal("[AutoLogout] You have reached your destination."));
            } else if (oneAxis.get()) {
                if (selectAxis.get() == axisOptions.X && xCoordsMatch()) {
                    disconnect(Component.literal("[AutoLogout] You have reached your destination."));
                } else if (selectAxis.get() == axisOptions.Z && zCoordsMatch()) {
                    disconnect(Component.literal("[AutoLogout] You have reached your destination."));
                }
            }
        }
    }

    private void disconnectOnHighPing() {
        if (!pingLog.get())
            return;
        if (mc.getConnection() == null || mc.player == null)
            return;
        PlayerInfo playerListEntry = mc.getConnection().getPlayerInfo(mc.player.getUUID());

        int ping = playerListEntry.getLatency();

        if (ping >= pingValue.get())
            disconnect(Component.literal("[AutoLogout] High ping [" + ping + "]"));
    }

    private boolean xCoordsMatch() {
        return (mc.player.getX() <= xCoords.get() + radius.get() && mc.player.getX() >= xCoords.get() - radius.get());
    }

    private boolean zCoordsMatch() {
        return (mc.player.getZ() <= zCoords.get() + radius.get() && mc.player.getZ() >= zCoords.get() - radius.get());
    }

    private void disconnect(Component text) {
        if (mc.getConnection() == null)
            return;
        mc.player.connection.handleDisconnect(new ClientboundDisconnectPacket(text));

        if (toggleOff.get())
            toggle();

        if (toggleAutoReconnect.get() && Modules.get().isActive(AutoReconnect.class))
            Modules.get().get(AutoReconnect.class).toggle();
    }

    public enum axisOptions {
        X,
        Z,
    }
}