package dev.oma.addon.util;

import dev.oma.addon.modules.Render.ChestESP;
import dev.oma.addon.modules.Utility.ItemFinder;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * Shared sync flag between Item Finder and Chest ESP.
 * Enabled when either module has its sync setting turned on.
 */
public final class ItemListSync {
    private ItemListSync() {}

    public static boolean isEnabled() {
        Modules modules = Modules.get();
        if (modules == null) return false;

        ItemFinder finder = modules.get(ItemFinder.class);
        if (finder != null && finder.isListSyncEnabled()) return true;

        ChestESP chestEsp = modules.get(ChestESP.class);
        return chestEsp != null && chestEsp.isListSyncEnabled();
    }
}
