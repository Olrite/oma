package dev.oma.addon.hud;

import dev.oma.addon.Main;
import dev.oma.addon.modules.Render.SignRender;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.List;

public class SignDisplay extends HudElement {
    public static final HudElementInfo<SignDisplay> INFO = new HudElementInfo<>(Main.HUD_GROUP, "Sign-Display", "Displays nearby sign text.", SignDisplay::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Display the HUD title.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCoordinates = sgGeneral.add(new BoolSetting.Builder()
        .name("show-coordinates")
        .description("Show coordinates of the signs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Show distance to signs.")
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

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Render shadow behind the text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> titleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color for the title.")
        .defaultValue(new SettingColor(255, 165, 0, 255))
        .build()
    );

    private final Setting<SettingColor> signColor = sgGeneral.add(new ColorSetting.Builder()
        .name("sign-color")
        .description("Color for sign text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );


    public SignDisplay() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        // Automatically enable SignRender module when SignDisplay HUD is active
        if (this.isActive()) {
            SignRender signRenderModule = Modules.get().get(SignRender.class);
            if (signRenderModule != null && !signRenderModule.isActive()) {
                signRenderModule.toggle();
            }
        }
        
        if (MeteorClient.mc.level == null || MeteorClient.mc.player == null) {
            if (isInEditor()) {
                renderer.text("SignDisplay", x, y, titleColor.get(), textShadow.get(), textScale.get());
                setSize(renderer.textWidth("Sign Display", textShadow.get(), textScale.get()), renderer.textHeight(textShadow.get(), textScale.get()));
            }
            return;
        }

        List<SignRender.SignInfo> signInfos = SignRender.signInfos;
        if (signInfos.isEmpty()) {
            if (isInEditor()) {
                renderer.text("Sign Display", x, y, titleColor.get(), textShadow.get(), textScale.get());
                setSize(renderer.textWidth("Sign Display", textShadow.get(), textScale.get()), renderer.textHeight(textShadow.get(), textScale.get()));
            }
            return;
        }

        double curX = x;
        double curY = y;
        double maxWidth = 0;
        double height = 0;
        double textHeight = renderer.textHeight(textShadow.get(), textScale.get());
        double spacing = 2;

        if (showTitle.get()) {
            String title = "Sign Display";
            double titleWidth = renderer.textWidth(title, textShadow.get(), textScale.get());
            renderer.text(title, curX, curY, titleColor.get(), textShadow.get(), textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, titleWidth);
        }

        for (SignRender.SignInfo signInfo : signInfos) {
            // Build sign text
            StringBuilder signText = new StringBuilder();
            boolean hasContent = false;
            
            for (String line : signInfo.lines) {
                if (!line.isEmpty()) {
                    if (hasContent) signText.append(" ");
                    signText.append(line);
                    hasContent = true;
                }
            }
            
            if (!hasContent) continue;
            
            String displayText = signText.toString();
            
            // Add coordinates if enabled
            if (showCoordinates.get()) {
                displayText += " §7(" + signInfo.pos.getX() + ", " + signInfo.pos.getY() + ", " + signInfo.pos.getZ() + ")";
            }
            
            // Add distance if enabled
            if (showDistance.get()) {
                displayText += " (" + (int) signInfo.distance + "m)";
            }
            
            double textWidth = renderer.textWidth(displayText, textShadow.get(), textScale.get());
            
            // Render the sign text with appropriate colors
            renderer.text(displayText, curX, curY, signColor.get(), textShadow.get(), textScale.get());
            
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, textWidth);
        }

        setSize(maxWidth, height - spacing);  // Subtract last spacing
    }
}
