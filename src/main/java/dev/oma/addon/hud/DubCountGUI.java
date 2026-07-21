package dev.oma.addon.hud;

import dev.oma.addon.Main;
import dev.oma.addon.modules.Utility.DubCount;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DubCountGUI extends HudElement {
    public static final HudElementInfo<DubCountGUI> INFO = new HudElementInfo<>(
        Main.HUD_GROUP,
        "dub-count",
        "Displays the count of double chests from DubCount module.",
        DubCountGUI::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("none-mode")
        .description("How to render the item when no count is available.")
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

    public DubCountGUI() {
        super(INFO);
        calculateSize();
    }

    @Override
    public void setSize(double width, double height) {
        super.setSize(width + border.get() * 2, height + border.get() * 2);
    }

    private void calculateSize() {
        setSize(17 * scale.get(), 17 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        int dubCount = DubCount.getDubCount();
        ItemStack chestStack = new ItemStack(Items.CHEST, dubCount);

        if (shouldHideInEditor(chestStack)) {
            renderEditorPlaceholder(renderer);
        } else {
            renderer.post(() -> {
                double xPos = this.x + border.get();
                double yPos = this.y + border.get();
                renderItem(renderer, chestStack, (int) xPos, (int) yPos);
            });
        }
    }

    private boolean shouldHideInEditor(ItemStack itemStack) {
        return mode.get() == Mode.HideItem && itemStack.isEmpty() && !isInEditor();
    }

    private void renderEditorPlaceholder(HudRenderer renderer) {
        if (isInEditor()) {
            renderer.line(x, y, x + getWidth(), y + getHeight(), Color.GRAY);
            renderer.line(x, y + getHeight(), x + getWidth(), y, Color.GRAY);
        }
    }

    private void renderItem(HudRenderer renderer, ItemStack itemStack, int x, int y) {
        if (mode.get() == Mode.HideItem) {
            renderer.item(itemStack, x, y, scale.get().floatValue(), true);
            return;
        }

        String countOverride = null;
        boolean needsReset = false;

        if (itemStack.isEmpty() || itemStack.getCount() == 0) {
            if (mode.get() == Mode.ShowCount) {
                countOverride = "0";
            }
            itemStack = new ItemStack(Items.CHEST, 1);
            needsReset = true;
        }

        renderer.item(itemStack, x, y, scale.get().floatValue(), true, countOverride);

        if (needsReset) {
            itemStack.setCount(0);
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
