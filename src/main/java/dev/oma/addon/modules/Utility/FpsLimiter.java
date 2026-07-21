package dev.oma.addon.modules.Utility;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.List;

/**
 * Caps FPS when the game window is unfocused, and optionally while selected modules are enabled.
 */
public class FpsLimiter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> fps = sgGeneral.add(new IntSetting.Builder()
        .name("fps")
        .description("FPS limit while capped.")
        .defaultValue(10)
        .min(1)
        .max(260)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Boolean> limitWhenUnfocused = sgGeneral.add(new BoolSetting.Builder()
        .name("limit-when-unfocused")
        .description("Cap FPS when the game window is not focused.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableWithModules = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-with-modules")
        .description("Also cap FPS while any of the selected modules are enabled (even if focused).")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Module>> modules = sgGeneral.add(new ModuleListSetting.Builder()
        .name("modules")
        .description("Modules that trigger the FPS cap when enable-with-modules is on.")
        .defaultValue(List.of())
        .visible(enableWithModules::get)
        .build()
    );

    public FpsLimiter() {
        super(Main.MOD, "FPS Limiter", "Caps FPS when the game window is unfocused, or optionally while other modules are enabled.");
    }

    /** Whether the FPS cap should apply right now. */
    public boolean shouldLimit() {
        if (!isActive() || mc == null) return false;

        if (limitWhenUnfocused.get() && !mc.isWindowFocused()) {
            return true;
        }

        if (enableWithModules.get()) {
            for (Module module : modules.get()) {
                if (module != null && module != this && module.isActive()) {
                    return true;
                }
            }
        }

        return false;
    }

    public int getFpsLimit() {
        return fps.get();
    }
}
