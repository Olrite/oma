package dev.oma.addon.hud;

import dev.oma.addon.Main;
import dev.oma.addon.util.HudFont;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CrystalCount extends HudElement {
    public static final HudElementInfo<CrystalCount> INFO = new HudElementInfo<>(
        Main.HUD_GROUP,
        "Crystal Count",
        "Displays a count of crystals in inventory.",
        CrystalCount::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("none-mode")
        .description("How to render the item when you don't have the specified item in your inventory.")
        .defaultValue(Mode.HideCount)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the item.")
        .defaultValue(2)
        .onChanged(value -> calculateSize())
        .min(1)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Integer> border = sgGeneral.add(new IntSetting.Builder()
        .name("border")
        .description("How much space to add around the element.")
        .defaultValue(0)
        .onChanged(value -> calculateSize())
        .build()
    );

    private final Setting<Boolean> customFont = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-font")
        .description("Use Meteor's custom font for the count. Off uses the default Minecraft / resource pack font.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> countColor = sgGeneral.add(new ColorSetting.Builder()
        .name("count-color")
        .description("Color of the count text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    public CrystalCount() {
        super(INFO);
        calculateSize();
    }

    @Override
    public void setSize(double width, double height) {
        super.setSize(width + border.get() * 2, height + border.get() * 2);
    }

    private void calculateSize() {
        setSize(17 * scale.get() + 36, 17 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        int crystalCount = InvUtils.find(stack -> stack.getItem() == Items.END_CRYSTAL).count();
        ItemStack crystalStack = new ItemStack(Items.END_CRYSTAL, Math.max(crystalCount, 1));
        boolean empty = crystalCount <= 0;

        if (mode.get() == Mode.HideItem && empty && !isInEditor()) {
            renderEditorPlaceholder(renderer);
            return;
        }

        double xPos = this.x + border.get();
        double yPos = this.y + border.get();
        renderer.post(() -> renderer.item(crystalStack, (int) xPos, (int) yPos, scale.get().floatValue(), false));

        if (mode.get() != Mode.HideCount || !empty || isInEditor()) {
            String text = empty ? (mode.get() == Mode.ShowCount || isInEditor() ? "0" : "") : String.valueOf(crystalCount);
            if (!text.isEmpty()) {
                double textX = xPos + 17 * scale.get() + 2;
                double textY = yPos + (17 * scale.get() - HudFont.textHeight(renderer, customFont.get(), false, scale.get() * 0.5)) / 2.0;
                HudFont.text(renderer, text, textX, textY, countColor.get(), customFont.get(), false, scale.get() * 0.5);
            }
        }
    }

    private void renderEditorPlaceholder(HudRenderer renderer) {
        if (isInEditor()) {
            renderer.line(x, y, x + getWidth(), y + getHeight(), Color.GRAY);
            renderer.line(x, y + getHeight(), x + getWidth(), y, Color.GRAY);
        }
    }

    public enum Mode {
        HideItem,
        HideCount,
        ShowCount;

        @Override
        public String toString() {
            return switch (this) {
                case HideItem -> "Hide Item";
                case HideCount -> "Hide Count";
                case ShowCount -> "Show Count";
            };
        }
    }
}
