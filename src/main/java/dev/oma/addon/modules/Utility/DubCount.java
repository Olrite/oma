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

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import dev.oma.addon.Main;
import dev.oma.addon.util.LogUtils;
import dev.oma.addon.util.TimerUtils;
import dev.oma.addon.util.ChunkUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DubCount extends Module {
    public List<BlockPos> coords = new ArrayList<>();
    public TimerUtils timer = new TimerUtils();
    public TimerUtils autoUpdateTimer = new TimerUtils();
    private static double currentCount = 0;
    private static int currentChestCount = 0;
    private static CountingMode currentCountingMode = CountingMode.Dubs;
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

    private final Setting<CountingMode> countingMode = sgGeneral.add(new EnumSetting.Builder<CountingMode>()
        .name("counting-mode")
        .description("Dubs: each chest block is 0.5 (a double = 1). Single chests: each chest block counts as 1 (a double = 2).")
        .defaultValue(CountingMode.Dubs)
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
        super(Main.MOD, "Dub Counter", "Counts how many chests are in render distance.");
    }

    @Override
    public void onActivate() {
        timer.reset();
        autoUpdateTimer.reset();
        isFirstCount = true;

        if (autoUpdate.get()) {
            LogUtils.info(ChatFormatting.GRAY + "Dub Counter started. Auto-updating every " + ChatFormatting.WHITE + updateInterval.get() + ChatFormatting.GRAY + " second(s).");
            updateCount();
        } else {
            if (countMode.get() == CountMode.Rendered) {
                LogUtils.info(ChatFormatting.GRAY + "Please wait " + ChatFormatting.WHITE + loadTime.get() + ChatFormatting.GRAY + " second(s)...");
            } else {
                updateCount();
                toggle();
            }
        }
    }

    private double computeCount(int chestBlocks) {
        return countingMode.get() == CountingMode.Dubs ? chestBlocks / 2.0 : chestBlocks;
    }

    private String formatCount(double value) {
        if (countingMode.get() == CountingMode.Dubs) {
            return String.format(Locale.US, "%.1f", value);
        }
        return String.valueOf((int) value);
    }

    private void applyCount(int length, String sourceLabel) {
        double value = computeCount(length);
        currentChestCount = length;
        currentCount = value;
        currentCountingMode = countingMode.get();

        if (showNotifications.get() || !autoUpdate.get() || isFirstCount) {
            String unit = countingMode.get() == CountingMode.Dubs ? "dubs" : "single chests";
            LogUtils.info(ChatFormatting.GRAY + "There are roughly " + ChatFormatting.WHITE + formatCount(value) + ChatFormatting.GRAY + " " + unit + " (" + length + " chest blocks) " + sourceLabel + ".");
        }
        isFirstCount = false;
    }

    private void updateCount() {
        if (countMode.get() == CountMode.Loaded) {
            applyCount(ChunkUtils.getChestCount(), "loaded");
        }
    }

    /** Display value (dubs or single-chest units depending on mode). */
    public static double getDisplayCount() {
        return currentCount;
    }

    /** @deprecated use {@link #getDisplayCount()} */
    @Deprecated
    public static int getDubCount() {
        return (int) Math.floor(currentCount);
    }

    public static int getChestCount() {
        return currentChestCount;
    }

    public static CountingMode getCountingMode() {
        return currentCountingMode;
    }

    public static String getFormattedCount() {
        if (currentCountingMode == CountingMode.Dubs) {
            return String.format(Locale.US, "%.1f", currentCount);
        }
        return String.valueOf((int) currentCount);
    }

    @Override
    public void onDeactivate() {
        coords.clear();
        if (autoUpdate.get()) {
            LogUtils.info(ChatFormatting.GRAY + "Dub Counter stopped.");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (countMode.get() == CountMode.Rendered) {
            if (timer.hasReached(loadTime.get() * 1000L)) {
                applyCount(coords.size(), "rendered");

                if (!autoUpdate.get()) {
                    toggle();
                } else {
                    coords.clear();
                }
                timer.reset();
            }
        } else if (countMode.get() == CountMode.Loaded && autoUpdate.get()) {
            if (autoUpdateTimer.hasReached(updateInterval.get() * 1000L)) {
                updateCount();
                autoUpdateTimer.reset();
            }
        }
    }

    @EventHandler
    private void onRenderBlockEntity(RenderBlockEntityEvent event) {
        if (countMode.get() == CountMode.Rendered) {
            if (event.blockEntityState.blockEntityType == BlockEntityType.CHEST || event.blockEntityState.blockEntityType == BlockEntityType.TRAPPED_CHEST) {
                BlockPos pos = event.blockEntityState.blockPos;
                if (coords.contains(pos)) return;
                coords.add(pos);
            }
        }
    }

    public enum CountMode {
        Rendered,
        Loaded
    }

    public enum CountingMode {
        Dubs,
        SingleChests;

        @Override
        public String toString() {
            return switch (this) {
                case Dubs -> "Dubs";
                case SingleChests -> "Single chests";
            };
        }
    }
}
