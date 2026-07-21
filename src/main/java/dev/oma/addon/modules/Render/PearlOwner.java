package dev.oma.addon.modules.Render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
// import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import dev.oma.addon.Main;
import java.util.ArrayList;
import java.util.List;

public class PearlOwner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to display pearl owner names.")
        .defaultValue(500.0)
        .min(0.0)
        .sliderRange(0.0, 1000.0)
        .build()
    );

    private final Setting<Double> scale = sgRender.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the nametag.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 5.0)
        .build()
    );

    private final Setting<SettingColor> nameColor = sgRender.add(new ColorSetting.Builder()
        .name("name-color")
        .description("The color of the owner's name.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<Boolean> showDistance = sgRender.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Show distance to the pearl.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> culling = sgRender.add(new BoolSetting.Builder()
        .name("culling")
        .description("Only render nametags when you're looking at the pearl.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> cullingDotValue = sgRender.add(new DoubleSetting.Builder()
        .name("culling-dot-value")
        .description("Dot product value for culling.")
        .defaultValue(0.5)
        .min(-1.0)
        .max(1.0)
        .sliderRange(-1.0, 1.0)
        .visible(culling::get)
        .build()
    );

    private final Vector3d pos = new Vector3d();
    private final List<PearlInfo> pearlsToRender = new ArrayList<>();

    public PearlOwner() {
        super(Main.RENDER, "Pearl Owner", "Displays the name of the player who threw an ender pearl.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        // Clear list for this frame
        pearlsToRender.clear();

        // Collect pearls to render
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl)) continue;

            double distance = mc.player.distanceTo(pearl);
            if (distance > maxDistance.get()) continue;

            // Culling check
            if (culling.get()) {
                Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
                Vec3d pearlPos = pearl.getEntityPos();
                Vec3d cameraToEntity = pearlPos.subtract(cameraPos).normalize();
                Vec3d cameraDirection = Vec3d.fromPolar(mc.gameRenderer.getCamera().getPitch(), mc.gameRenderer.getCamera().getYaw()).normalize();

                double dot = cameraDirection.dotProduct(cameraToEntity);
                if (dot < cullingDotValue.get()) continue;
            }

            // Get owner of the pearl
            Entity owner = pearl.getOwner();
            if (owner == null) continue;

            String ownerName = owner.getName().getString();
            if (showDistance.get()) {
                ownerName += String.format(" [%.1fm]", distance);
            }

            // Calculate render position above the pearl
            pos.set(pearl.getX(), pearl.getY() + pearl.getHeight() + 0.5, pearl.getZ());

            // Convert 3D position to 2D screen coordinates
            if (NametagUtils.to2D(pos, scale.get())) {
                pearlsToRender.add(new PearlInfo(ownerName, pos.x, pos.y));
            }
        }

        // Render all collected pearls
        for (PearlInfo info : pearlsToRender) {
            renderNametag(info.text, info.x, info.y, event);
        }
    }

    private void renderNametag(String text, double x, double y, Render2DEvent event) {
        TextRenderer textRenderer = TextRenderer.get();
        double textWidth = textRenderer.getWidth(text, true);
        double textHeight = textRenderer.getHeight(true);

        // Center the text
        x -= textWidth / 2;
        y -= textHeight / 2;

        // Render background
        // Renderer2D.COLOR.begin();
        // Renderer2D.COLOR.quad(x - 2, y - 2, textWidth + 4, textHeight + 4, backgroundColor.get());
        // Renderer2D.COLOR.render(null);

        // Render text
        textRenderer.begin(scale.get());
        textRenderer.render(text, x, y, nameColor.get(), true);
        textRenderer.end();
    }

    private static class PearlInfo {
        final String text;
        final double x, y;

        PearlInfo(String text, double x, double y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}