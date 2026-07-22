package dev.oma.addon.modules.HUD;

import dev.oma.addon.Main;
import dev.oma.addon.util.HudFont;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LagDetector extends HudElement {
    public static final HudElementInfo<LagDetector> INFO = new HudElementInfo<>(
        Main.HUD_GROUP,
        "Lag Detector",
        "Detects lagbacks by analyzing server TPS and packet timing irregularities.",
        LagDetector::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General settings
    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Display the HUD title.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showTPS = sgGeneral.add(new BoolSetting.Builder()
        .name("show-tps")
        .description("Show server TPS estimation.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showPacketTiming = sgGeneral.add(new BoolSetting.Builder()
        .name("show-packet-timing")
        .description("Show packet timing analysis.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showLagbackAlerts = sgGeneral.add(new BoolSetting.Builder()
        .name("show-lagback-alerts")
        .description("Show lagback detection alerts.")
        .defaultValue(true)
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

    private final Setting<Boolean> customFont = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-font")
        .description("Use Meteor's custom font. Off uses the default Minecraft / resource pack font.")
        .defaultValue(true)
        .build()
    );

    // Detection settings
    private final Setting<Double> tpsThreshold = sgDetection.add(new DoubleSetting.Builder()
        .name("tps-threshold")
        .description("TPS threshold for lag detection.")
        .defaultValue(18.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(10.0, 20.0)
        .build()
    );

    private final Setting<Integer> packetTimingWindow = sgDetection.add(new IntSetting.Builder()
        .name("packet-timing-window")
        .description("Number of packets to analyze for timing patterns.")
        .defaultValue(75)
        .min(10)
        .max(200)
        .sliderRange(20, 100)
        .build()
    );

    private final Setting<Double> lagbackThreshold = sgDetection.add(new DoubleSetting.Builder()
        .name("lagback-threshold")
        .description("Threshold for detecting lagbacks (in blocks).")
        .defaultValue(1.5)
        .min(0.1)
        .max(5.0)
        .sliderRange(0.1, 2.0)
        .build()
    );

    private final Setting<Integer> alertDuration = sgDetection.add(new IntSetting.Builder()
        .name("alert-duration")
        .description("Duration to show lagback alerts (in ticks).")
        .defaultValue(100)
        .min(20)
        .max(200)
        .sliderRange(40, 120)
        .build()
    );

    // Color settings
    private final Setting<SettingColor> titleColor = sgColors.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color of the title.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> goodTpsColor = sgColors.add(new ColorSetting.Builder()
        .name("good-tps-color")
        .description("Color when TPS is good.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    private final Setting<SettingColor> badTpsColor = sgColors.add(new ColorSetting.Builder()
        .name("bad-tps-color")
        .description("Color when TPS is bad.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<SettingColor> normalTimingColor = sgColors.add(new ColorSetting.Builder()
        .name("normal-timing-color")
        .description("Color for normal packet timing.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> irregularTimingColor = sgColors.add(new ColorSetting.Builder()
        .name("irregular-timing-color")
        .description("Color for irregular packet timing.")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .build()
    );

    private final Setting<SettingColor> lagbackColor = sgColors.add(new ColorSetting.Builder()
        .name("lagback-color")
        .description("Color for lagback alerts.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );

    // Tracking variables
    private final ConcurrentLinkedQueue<Long> packetTimings = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> tickTimings = new ConcurrentLinkedQueue<>();
    private final List<Vec3d> positionHistory = new ArrayList<>();
    private final List<Long> positionTimestamps = new ArrayList<>();
    
    private long lastPacketTime = 0;
    private long lastTickTime = 0;
    private double estimatedTPS = 20.0;
    private boolean lagbackDetected = false;
    private int lagbackAlertTicks = 0;
    private Vec3d lastPosition = null;
    private long lastPositionUpdate = 0;

    public LagDetector() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket) {
            long currentTime = System.currentTimeMillis();
            if (lastPacketTime != 0) {
                long timing = currentTime - lastPacketTime;
                packetTimings.offer(timing);
                
                // Keep only the most recent timings
                while (packetTimings.size() > packetTimingWindow.get()) {
                    packetTimings.poll();
                }
            }
            lastPacketTime = currentTime;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            // This packet indicates the server is correcting our position (potential lagback)
            long currentTime = System.currentTimeMillis();
            if (lastPosition != null && lastPositionUpdate != 0) {
                double timeDiff = (currentTime - lastPositionUpdate) / 1000.0;
                if (timeDiff > 0.1) { // Only check if enough time has passed
                    lagbackDetected = true;
                    lagbackAlertTicks = alertDuration.get();
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long currentTime = System.currentTimeMillis();
        
        // Track tick timing for TPS estimation
        if (lastTickTime != 0) {
            long timing = currentTime - lastTickTime;
            tickTimings.offer(timing);
            
            // Keep only recent tick timings
            while (tickTimings.size() > 100) {
                tickTimings.poll();
            }
            
            // Calculate estimated TPS
            if (tickTimings.size() >= 20) {
                double avgTickTime = tickTimings.stream().mapToLong(Long::longValue).average().orElse(50.0);
                estimatedTPS = 1000.0 / avgTickTime;
            }
        }
        lastTickTime = currentTime;

        // Track player position for lagback detection
        if (MeteorClient.mc.player != null) {
            Vec3d currentPos = MeteorClient.mc.player.getPos();
            long currentPosTime = System.currentTimeMillis();
            
            if (lastPosition != null) {
                double distance = currentPos.distanceTo(lastPosition);
                double timeDiff = (currentPosTime - lastPositionUpdate) / 1000.0;
                
                // Detect sudden position changes that might indicate lagbacks
                if (timeDiff > 0.05 && distance > lagbackThreshold.get()) {
                    // Check if this is an unreasonable movement
                    double maxPossibleSpeed = 20.0; // blocks per second (roughly)
                    double actualSpeed = distance / timeDiff;
                    
                    if (actualSpeed > maxPossibleSpeed) {
                        lagbackDetected = true;
                        lagbackAlertTicks = alertDuration.get();
                    }
                }
                
                positionHistory.add(lastPosition);
                positionTimestamps.add(lastPositionUpdate);
                
                // Keep only recent position history
                while (positionHistory.size() > 20) {
                    positionHistory.remove(0);
                    positionTimestamps.remove(0);
                }
            }
            
            lastPosition = currentPos;
            lastPositionUpdate = currentPosTime;
        }

        // Decrease lagback alert counter
        if (lagbackAlertTicks > 0) {
            lagbackAlertTicks--;
        } else {
            lagbackDetected = false;
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) {
            if (isInEditor()) {
                HudFont.text(renderer, "Lag Detector", x, y, titleColor.get(), customFont.get(), true, textScale.get());
                setSize(HudFont.textWidth(renderer, "Lag Detector", customFont.get(), true, textScale.get()), HudFont.textHeight(renderer, customFont.get(), true, textScale.get()));
            }
            return;
        }

        double curX = this.x;
        double curY = this.y;
        double scale = textScale.get();
        double maxWidth = 0;
        double height = 0;
        double textHeight = HudFont.textHeight(renderer, customFont.get(), true, scale);
        double spacing = 2;

        if (showTitle.get()) {
            String title = "Lag Detector";
            double titleWidth = HudFont.textWidth(renderer, title, customFont.get(), true, scale);
            HudFont.text(renderer, title, curX, curY, titleColor.get(), customFont.get(), true, scale);
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, titleWidth);
        }

        if (showTPS.get()) {
            String tpsText = String.format("TPS: %.1f", estimatedTPS);
            SettingColor tpsColor = estimatedTPS >= tpsThreshold.get() ? goodTpsColor.get() : badTpsColor.get();
            double tpsWidth = HudFont.textWidth(renderer, tpsText, customFont.get(), true, scale);
            HudFont.text(renderer, tpsText, curX, curY, tpsColor, customFont.get(), true, scale);
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, tpsWidth);
        }

        if (showPacketTiming.get()) {
            String timingText = getPacketTimingAnalysis();
            SettingColor timingColor = hasIrregularTiming() ? irregularTimingColor.get() : normalTimingColor.get();
            double timingWidth = HudFont.textWidth(renderer, timingText, customFont.get(), true, scale);
            HudFont.text(renderer, timingText, curX, curY, timingColor, customFont.get(), true, scale);
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, timingWidth);
        }

        if (showLagbackAlerts.get() && lagbackDetected) {
            String lagbackText = "LAGBACK DETECTED!";
            double lagbackWidth = HudFont.textWidth(renderer, lagbackText, customFont.get(), true, scale);
            HudFont.text(renderer, lagbackText, curX, curY, lagbackColor.get(), customFont.get(), true, scale);
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, lagbackWidth);
        }

        setSize(maxWidth, height);
    }

    private String getPacketTimingAnalysis() {
        if (packetTimings.isEmpty()) {
            return "Timing: N/A";
        }

        double avgTiming = packetTimings.stream().mapToLong(Long::longValue).average().orElse(0.0);
        return String.format("Timing: %.1fms", avgTiming);
    }

    private boolean hasIrregularTiming() {
        if (packetTimings.size() < 10) return false;
        
        long[] timings = packetTimings.stream().mapToLong(Long::longValue).toArray();
        double avg = java.util.Arrays.stream(timings).average().orElse(0.0);
        double variance = java.util.Arrays.stream(timings)
            .mapToDouble(t -> Math.pow(t - avg, 2))
            .average().orElse(0.0);
        
        double standardDeviation = Math.sqrt(variance);
        
        // Consider timing irregular if standard deviation is high
        return standardDeviation > avg * 0.5;
    }
}
