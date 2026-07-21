package dev.oma.addon.util;

import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Helpers for drawing text with either Meteor's custom font or the vanilla / resource-pack font.
 */
public final class HudFont {
    private HudFont() {}

    public static double text(HudRenderer renderer, String text, double x, double y, Color color, boolean customFont, boolean shadow, double scale) {
        if (customFont) {
            return renderer.text(text, x, y, color, shadow, scale);
        }
        return drawVanilla(text, x, y, color, shadow, scale);
    }

    public static double textWidth(HudRenderer renderer, String text, boolean customFont, boolean shadow, double scale) {
        if (customFont) {
            return renderer.textWidth(text, shadow, scale);
        }
        VanillaTextRenderer tr = VanillaTextRenderer.INSTANCE;
        tr.begin(scale, false, false);
        double w = tr.getWidth(text, shadow);
        tr.end();
        return w;
    }

    public static double textHeight(HudRenderer renderer, boolean customFont, boolean shadow, double scale) {
        if (customFont) {
            return renderer.textHeight(shadow, scale);
        }
        VanillaTextRenderer tr = VanillaTextRenderer.INSTANCE;
        tr.begin(scale, false, false);
        double h = tr.getHeight(shadow);
        tr.end();
        return h;
    }

    /** World / nametag style: Meteor TextRenderer vs vanilla. */
    public static TextRenderer worldRenderer(boolean customFont) {
        return customFont ? TextRenderer.get() : VanillaTextRenderer.INSTANCE;
    }

    private static double drawVanilla(String text, double x, double y, Color color, boolean shadow, double scale) {
        VanillaTextRenderer tr = VanillaTextRenderer.INSTANCE;
        tr.begin(scale, false, false);
        double w = tr.render(text, x, y, color, shadow);
        tr.end();
        return w;
    }
}
