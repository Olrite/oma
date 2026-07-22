package dev.oma.addon.util;

import dev.oma.addon.modules.Hunting.ItemFinder;
import dev.oma.addon.modules.Hunting.ItemHighlight;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * Shared sync flag between Item Highlight and Item Finder.
 * Enabled when either module has its sync setting turned on.
 */
public final class ItemListSync {
    private ItemListSync() {}

    public static boolean isEnabled() {
        Modules modules = Modules.get();
        if (modules == null) return false;

        ItemHighlight highlight = modules.get(ItemHighlight.class);
        if (highlight != null && highlight.isListSyncEnabled()) return true;

        ItemFinder itemFinder = modules.get(ItemFinder.class);
        return itemFinder != null && itemFinder.isListSyncEnabled();
    }
}
