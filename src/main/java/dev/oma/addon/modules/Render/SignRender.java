package dev.oma.addon.modules.Render;

import dev.oma.addon.Main;
import dev.oma.addon.util.LogUtils;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.oma.addon.util.HudFont;

public class SignRender extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChat = settings.createGroup("Chat");
    private final SettingGroup sgHUD = settings.createGroup("HUD");

    // General settings
    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to detect signs.")
        .defaultValue(64.0)
        .min(1.0)
        .sliderRange(1.0, 128.0)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("Ticks between sign detection updates.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    // Chat settings
    private final Setting<Boolean> outputToChat = sgChat.add(new BoolSetting.Builder()
        .name("output-to-chat")
        .description("Output sign text to chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCoordinates = sgChat.add(new BoolSetting.Builder()
        .name("show-coordinates")
        .description("Show coordinates of the sign in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterEmpty = sgChat.add(new BoolSetting.Builder()
        .name("filter-empty")
        .description("Don't output empty signs.")
        .defaultValue(true)
        .build()
    );


    private final Setting<Integer> maxHUDEntries = sgHUD.add(new IntSetting.Builder()
        .name("max-hud-entries")
        .description("Maximum number of signs to show in HUD.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    // 3D Render settings
    private final SettingGroup sgRender = settings.createGroup("3D Render");

    private final Setting<Boolean> render3D = sgRender.add(new BoolSetting.Builder()
        .name("render-3d")
        .description("Render sign text in 3D above signs.")
        .defaultValue(true)
        .build()
    );


    private final Setting<Double> renderScale = sgRender.add(new DoubleSetting.Builder()
        .name("render-scale")
        .description("Scale of the 3D text.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 3.0)
        .build()
    );

    private final Setting<Double> renderDistance = sgRender.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Maximum distance to render 3D text.")
        .defaultValue(32.0)
        .min(1.0)
        .sliderRange(1.0, 64.0)
        .build()
    );

    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Color of the 3D text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<Boolean> customFont = sgRender.add(new BoolSetting.Builder()
        .name("custom-font")
        .description("Use Meteor's custom font. Off uses the default Minecraft / resource pack font.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgRender.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of the background.")
        .defaultValue(new SettingColor(0, 0, 0, 100))
        .build()
    );

    private final Setting<Boolean> showBackground = sgRender.add(new BoolSetting.Builder()
        .name("show-background")
        .description("Show background behind text.")
        .defaultValue(true)
        .build()
    );


    private final Set<BlockPos> processedSigns = new HashSet<>();
    private int tickCounter = 0;
    public static final List<SignInfo> signInfos = new ArrayList<>();
    private final List<SignRenderInfo> signsToRender = new ArrayList<>();

    public SignRender() {
        super(Main.MOD, "Sign Render", "Detects signs in render distance and outputs their text to chat and HUD.");
    }

    @Override
    public void onActivate() {
        processedSigns.clear();
        signInfos.clear();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        processedSigns.clear();
        signInfos.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter < updateInterval.get()) return;
        tickCounter = 0;

        detectSigns();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render3D.get() || mc.level == null || mc.player == null) return;

        // Clear list for this frame
        signsToRender.clear();

        double maxDist = renderDistance.get();

        for (SignInfo signInfo : signInfos) {
            if (signInfo.distance > maxDist) continue;

            BlockPos pos = signInfo.pos;
            Vec3 renderPos = Vec3.atCenterOf(pos).add(0, 1.5, 0);

            // Build the text to display (keep as 4 lines)
            StringBuilder displayText = new StringBuilder();
            boolean hasContent = false;
            
            for (int i = 0; i < 4; i++) {
                String line = i < signInfo.lines.size() ? signInfo.lines.get(i) : "";
                if (!line.isEmpty()) {
                    hasContent = true;
                }
                displayText.append(line);
                if (i < 3) displayText.append("\n");
            }
            
            if (!hasContent) continue;

            String text = displayText.toString();
            
            // Convert 3D position to 2D screen coordinates
            Vector3d vector3d = new Vector3d(renderPos.x, renderPos.y, renderPos.z);
            if (NametagUtils.to2D(vector3d, renderScale.get())) {
                signsToRender.add(new SignRenderInfo(text, vector3d.x, vector3d.y));
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!render3D.get() || signsToRender.isEmpty()) return;

        TextRenderer textRenderer = HudFont.worldRenderer(customFont.get());

        for (SignRenderInfo signInfo : signsToRender) {
            // Calculate text dimensions for multi-line text
            String[] lines = signInfo.text.split("\n");
            double maxWidth = 0;
            double totalHeight = 0;
            
            // Calculate dimensions for each line
            for (String line : lines) {
                double lineWidth = textRenderer.getWidth(line, true) * renderScale.get();
                double lineHeight = textRenderer.getHeight(true) * renderScale.get();
                maxWidth = Math.max(maxWidth, lineWidth);
                totalHeight += lineHeight;
            }
            
            // Center the text
            double y = signInfo.y - totalHeight / 2;

            // Render background if enabled
            if (showBackground.get()) {
                // Calculate background dimensions
                double padding = 4;
                double bgX = signInfo.x - maxWidth / 2 - padding;
                double bgY = y - padding;
                double bgWidth = maxWidth + (padding * 2);
                double bgHeight = totalHeight + (padding * 2);
                
                // Render background using Renderer2D
                Renderer2D.COLOR.begin();
                Renderer2D.COLOR.quad(bgX, bgY, bgWidth, bgHeight, backgroundColor.get());
                Renderer2D.COLOR.render();
            }

            // Render each line separately
            textRenderer.begin(renderScale.get());
            double currentY = y;
            for (String line : lines) {
                if (!line.isEmpty()) {
                    double lineWidth = textRenderer.getWidth(line, true) * renderScale.get();
                    double lineX = signInfo.x - lineWidth / 2; // Center each line individually
                    textRenderer.render(line, lineX, currentY, textColor.get(), true);
                }
                currentY += textRenderer.getHeight(true) * renderScale.get();
            }
            textRenderer.end();
        }
    }

    private void detectSigns() {
        double maxDist = maxDistance.get();
        
        // Clear old sign infos
        signInfos.clear();
        
        // Get all loaded chunks within render distance
        List<LevelChunk> chunks = getLoadedChunks();
        
        for (LevelChunk chunk : chunks) {
            for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                BlockEntity blockEntity = chunk.getBlockEntity(pos);
                
                if (blockEntity instanceof SignBlockEntity signEntity) {
                    double distance = mc.player.position().distanceTo(Vec3.atCenterOf(pos));
                    
                    if (distance <= maxDist) {
                        SignText frontText = signEntity.getFrontText();
                        SignText backText = signEntity.getBackText();
                        
                        List<String> lines = new ArrayList<>();
                        boolean hasContent = false;
                        
                        // Get front text
                        for (int i = 0; i < 4; i++) {
                            String line = frontText.getMessage(i, false).getString();
                            if (!line.isEmpty()) {
                                hasContent = true;
                            }
                            lines.add(line);
                        }
                        
                        // Get back text if different
                        for (int i = 0; i < 4; i++) {
                            String backLine = backText.getMessage(i, false).getString();
                            String frontLine = frontText.getMessage(i, false).getString();
                            if (!backLine.isEmpty() && !backLine.equals(frontLine)) {
                                hasContent = true;
                                lines.add("(Back) " + backLine);
                            }
                        }
                        
                        if (!hasContent && filterEmpty.get()) continue;
                        
                        SignInfo signInfo = new SignInfo(pos, lines, distance);
                        signInfos.add(signInfo);
                        
                        // Output to chat if enabled and not already processed
                        if (outputToChat.get() && !processedSigns.contains(pos)) {
                            outputSignToChat(signInfo);
                            processedSigns.add(pos);
                        }
                    }
                }
            }
        }
        
        // Limit HUD entries
        if (signInfos.size() > maxHUDEntries.get()) {
            signInfos.sort((a, b) -> Double.compare(a.distance, b.distance));
            signInfos.subList(maxHUDEntries.get(), signInfos.size()).clear();
        }
    }

    private void outputSignToChat(SignInfo signInfo) {
        StringBuilder message = new StringBuilder();
        message.append("§6[Sign] ");
        
        if (showCoordinates.get()) {
            message.append("(").append(signInfo.pos.getX()).append(", ")
                   .append(signInfo.pos.getY()).append(", ")
                   .append(signInfo.pos.getZ()).append(") ");
        }
        
        for (String line : signInfo.lines) {
            if (!line.isEmpty()) {
                message.append(line).append(" ");
            }
        }
        
        LogUtils.info(message.toString().trim());
    }

    private List<LevelChunk> getLoadedChunks() {
        List<LevelChunk> chunks = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        int renderDistance = mc.options.renderDistance().get();
        
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                int chunkX = (playerPos.getX() >> 4) + x;
                int chunkZ = (playerPos.getZ() >> 4) + z;
                
                if (mc.level.hasChunk(chunkX, chunkZ)) {
                    chunks.add(mc.level.getChunk(chunkX, chunkZ));
                }
            }
        }
        
        return chunks;
    }

    public static class SignInfo {
        public final BlockPos pos;
        public final List<String> lines;
        public final double distance;
        
        public SignInfo(BlockPos pos, List<String> lines, double distance) {
            this.pos = pos;
            this.lines = lines;
            this.distance = distance;
        }
    }

    private static class SignRenderInfo {
        final String text;
        final double x, y;

        SignRenderInfo(String text, double x, double y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}
