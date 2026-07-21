package dev.oma.addon.modules.Utility;

import meteordevelopment.meteorclient.events.render.RenderBlockEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import dev.oma.addon.Main;
import dev.oma.addon.util.LogUtils;
import dev.oma.addon.util.TimerUtils;
import dev.oma.addon.util.ChunkUtils;

import java.util.ArrayList;
import java.util.List;

public class DubCount extends Module {
    public List<BlockPos> coords = new ArrayList<>();
    public TimerUtils timer = new TimerUtils();
    public TimerUtils autoUpdateTimer = new TimerUtils();
    private static int currentDubCount = 0;
    private static int currentChestCount = 0;
    private boolean isFirstCount = true;

    public SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoUpdate = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-update")
        .description("Automatically update the dub count at regular intervals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("How often to update the count in seconds.")
        .defaultValue(5)
        .min(1)
        .sliderMax(60)
        .visible(() -> autoUpdate.get())
        .build()
    );

    private final Setting<CountMode> countMode = sgGeneral.add(new EnumSetting.Builder<CountMode>()
        .name("count-mode")
        .description("The way the chests are counted.")
        .defaultValue(CountMode.Loaded)
        .build()
    );

    private final Setting<Integer> loadTime = sgGeneral.add(new IntSetting.Builder()
        .name("loading-time")
        .description("How much time it's going to take to load all the dubs.")
        .defaultValue(1)
        .min(1)
        .sliderMax(60)
        .visible(() -> countMode.get() == CountMode.Rendered)
        .build()
    );

    private final Setting<Boolean> showNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("show-notifications")
        .description("Show chat notifications when count updates.")
        .defaultValue(false)
        .build()
    );

    public DubCount() {
        super(Main.RENDER, "dub-counter", "Counts how many double chests are in render distance.");
    }

    @Override
    public void onActivate() {
        timer.reset();
        autoUpdateTimer.reset();
        isFirstCount = true;
        
        if (autoUpdate.get()) {
            LogUtils.info(Formatting.GRAY + "Dub Counter started. Auto-updating every " + Formatting.WHITE + updateInterval.get() + Formatting.GRAY + " second(s).");
            updateCount();
        } else {
            if (countMode.get() == CountMode.Rendered) {
                LogUtils.info(Formatting.GRAY + "Please wait " + Formatting.WHITE + loadTime.get() + Formatting.GRAY + " second(s)...");
            } else {
                updateCount();
                toggle();
            }
        }
    }
    
    private void updateCount() {
        if (countMode.get() == CountMode.Loaded) {
            int length = ChunkUtils.getChestCount();
            int dubs = length % 2 == 0 ? length / 2 : (length - 1) / 2;
            currentChestCount = length;
            currentDubCount = dubs;
            
            if (showNotifications.get() || !autoUpdate.get() || isFirstCount) {
                LogUtils.info(Formatting.GRAY + "There are roughly " + Formatting.WHITE + dubs + Formatting.GRAY + " (" + length + " normal chests)" + Formatting.GRAY + " loaded double chests.");
            }
            isFirstCount = false;
        }
    }
    
    public static int getDubCount() {
        return currentDubCount;
    }
    
    public static int getChestCount() {
        return currentChestCount;
    }

    @Override
    public void onDeactivate() {
        coords.clear();
        if (autoUpdate.get()) {
            LogUtils.info(Formatting.GRAY + "Dub Counter stopped.");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (countMode.get() == CountMode.Rendered) {
            if (timer.hasReached(loadTime.get() * 1000L)) {
                int length = coords.size();
                int dubs = length % 2 == 0 ? length / 2 : (length - 1) / 2;
                currentChestCount = length;
                currentDubCount = dubs;
                
                if (showNotifications.get() || !autoUpdate.get() || isFirstCount) {
                    LogUtils.info(Formatting.GRAY + "There are roughly " + Formatting.WHITE + dubs + Formatting.GRAY + " (" + length + " normal chests)" + Formatting.GRAY + " rendered double chests.");
                }
                isFirstCount = false;

                if (!autoUpdate.get()) {
                    toggle();
                } else {
                    coords.clear();
                }
                timer.reset();
            }
        } else if (countMode.get() == CountMode.Loaded && autoUpdate.get()) {
            // Auto-update for Loaded mode
            if (autoUpdateTimer.hasReached(updateInterval.get() * 1000L)) {
                updateCount();
                autoUpdateTimer.reset();
            }
        }
    }

    @EventHandler
    private void onRenderBlockEntity(RenderBlockEntityEvent event) {
        if (countMode.get() == CountMode.Rendered) {
            if (event.blockEntityState.type == BlockEntityType.CHEST || event.blockEntityState.type == BlockEntityType.TRAPPED_CHEST) {
                BlockPos pos = event.blockEntityState.pos;
                if (coords.contains(pos)) return;
                coords.add(pos);
            }
        }
    }

    public enum CountMode {
        Rendered,
        Loaded
    }
}