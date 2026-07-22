package dev.oma.addon.modules.Hunting;

import dev.oma.addon.Main;
import dev.oma.addon.util.ItemListSync;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AbstractSkullBlock;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights matching items inside open containers (chests, shulkers, etc.).
 */
public class ItemHighlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDefaults = settings.createGroup("Default Finds");
    private final SettingGroup sgCustom = settings.createGroup("Custom");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> containerSlotsOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("container-slots-only")
        .description("Only highlight items in the container, not your own inventory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> syncWithItemFinder = sgGeneral.add(new BoolSetting.Builder()
        .name("sync-with-item-finder")
        .description("Share custom item/block lists with Item Finder. Also lets Item Finder detect Item Highlight default finds when either sync option is on.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> negativeDurability = sgDefaults.add(new BoolSetting.Builder()
        .name("negative-durability")
        .description("Highlight items with negative or over-max damage values.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chainmailArmor = sgDefaults.add(new BoolSetting.Builder()
        .name("chainmail-armor")
        .description("Highlight chainmail armor.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> maps = sgDefaults.add(new BoolSetting.Builder()
        .name("maps")
        .description("Highlight empty and filled maps.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> mobHeads = sgDefaults.add(new BoolSetting.Builder()
        .name("mob-heads")
        .description("Highlight mob / player heads.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dyedArmor = sgDefaults.add(new BoolSetting.Builder()
        .name("dyed-armor")
        .description("Highlight dyed leather armor (and other dyed items).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> illegalEnchants = sgDefaults.add(new BoolSetting.Builder()
        .name("illegal-enchants")
        .description("Highlight items with incompatible, conflicting, or overleveled enchantments.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> musicDiscs = sgDefaults.add(new BoolSetting.Builder()
        .name("music-discs")
        .description("Highlight music discs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stackedItems = sgDefaults.add(new BoolSetting.Builder()
        .name("stacked-items")
        .description("Highlight unstackable items with count > 1, or stacks above max size.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> writtenBooks = sgDefaults.add(new BoolSetting.Builder()
        .name("written-books")
        .description("Highlight written / signed books.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> alphaSlabs = sgDefaults.add(new BoolSetting.Builder()
        .name("alpha-slabs")
        .description("Highlight alpha slabs (petrified oak slabs).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lagRockets = sgDefaults.add(new BoolSetting.Builder()
        .name("lag-rockets")
        .description("Highlight fireworks with 7+ explosions (classic lag rockets).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> illegalFish = sgDefaults.add(new BoolSetting.Builder()
        .name("illegal-fish")
        .description("Highlight tropical fish buckets with illegal black color variants.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renamedItems = sgDefaults.add(new BoolSetting.Builder()
        .name("renamed-items")
        .description("Highlight items with a custom name.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Item>> customItems = sgCustom.add(new ItemListSetting.Builder()
        .name("items")
        .description("Also highlight these items (blocks can be added too, e.g. \"Chest\"). Shared with Item Finder when sync is enabled.")
        .defaultValue()
        .build()
    );

    private final Setting<SettingColor> highlightColor = sgRender.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Overlay color for matching slots.")
        .defaultValue(new SettingColor(255, 50, 50, 100))
        .build()
    );

    public ItemHighlight() {
        super(Main.HUNT, "Item Highlight", "Highlights interesting or illegal items in open containers.");
    }

    public SettingColor getHighlightColor() {
        return highlightColor.get();
    }

    public boolean isListSyncEnabled() {
        return syncWithItemFinder.get();
    }

    public List<Item> getConfiguredItems() {
        return customItems.get();
    }

    public boolean shouldHighlightSlot(Slot slot) {
        if (!isActive() || slot == null || !slot.hasItem()) return false;
        if (containerSlotsOnly.get() && mc.player != null && slot.container instanceof Inventory) {
            return false;
        }
        return matches(slot.getItem());
    }

    public boolean matchesCustomLists(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        return customItems.get().contains(stack.getItem());
    }

    private boolean matchesSyncedLists(ItemStack stack) {
        if (!ItemListSync.isEnabled()) return false;

        Modules modules = Modules.get();
        if (modules == null) return false;

        ItemFinder itemFinder = modules.get(ItemFinder.class);
        return itemFinder != null && itemFinder.matchesCustomLists(stack);
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (matchesDefaultFinds(stack)) return true;
        if (matchesCustomLists(stack)) return true;
        return matchesSyncedLists(stack);
    }

    public boolean matchesDefaultFinds(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (negativeDurability.get() && hasNegativeDurability(stack)) return true;
        if (chainmailArmor.get() && isChainmail(stack)) return true;
        if (maps.get() && isMap(stack)) return true;
        if (mobHeads.get() && isMobHead(stack)) return true;
        if (dyedArmor.get() && isDyed(stack)) return true;
        if (illegalEnchants.get() && hasIllegalEnchantments(stack)) return true;
        if (musicDiscs.get() && isMusicDisc(stack)) return true;
        if (stackedItems.get() && isIllegallyStacked(stack)) return true;
        if (writtenBooks.get() && isWrittenBook(stack)) return true;
        if (alphaSlabs.get() && isAlphaSlab(stack)) return true;
        if (lagRockets.get() && isLagRocket(stack)) return true;
        if (illegalFish.get() && isIllegalFish(stack)) return true;
        if (renamedItems.get() && isRenamed(stack)) return true;
        return false;
    }

    private static boolean hasNegativeDurability(ItemStack stack) {
        if (!stack.isDamageableItem()) return false;
        int damage = stack.getDamageValue();
        return damage < 0 || damage > stack.getMaxDamage();
    }

    private static boolean isChainmail(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.CHAINMAIL_HELMET
            || item == Items.CHAINMAIL_CHESTPLATE
            || item == Items.CHAINMAIL_LEGGINGS
            || item == Items.CHAINMAIL_BOOTS;
    }

    private static boolean isMap(ItemStack stack) {
        return stack.is(Items.MAP)
            || stack.is(Items.FILLED_MAP)
            || stack.has(DataComponents.MAP_ID);
    }

    private static boolean isMobHead(ItemStack stack) {
        if (stack.is(ItemTags.SKULLS)) return true;
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof AbstractSkullBlock;
        }
        return false;
    }

    private static boolean isDyed(ItemStack stack) {
        return stack.has(DataComponents.DYED_COLOR);
    }

    private static boolean isMusicDisc(ItemStack stack) {
        return stack.has(DataComponents.JUKEBOX_PLAYABLE);
    }

    private static boolean isIllegallyStacked(ItemStack stack) {
        int count = stack.getCount();
        if (count > stack.getMaxStackSize()) return true;
        return !stack.isStackable() && count > 1;
    }

    private static boolean isWrittenBook(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) || stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
    }

    private static boolean isAlphaSlab(ItemStack stack) {
        return stack.is(Items.PETRIFIED_OAK_SLAB);
    }

    private static boolean isLagRocket(ItemStack stack) {
        if (!stack.has(DataComponents.FIREWORKS)) return false;
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        return fireworks != null && fireworks.explosions().size() >= 7;
    }

    private static boolean isIllegalFish(ItemStack stack) {
        if (!stack.is(Items.TROPICAL_FISH_BUCKET)) return false;

        DyeColor base = stack.get(DataComponents.TROPICAL_FISH_BASE_COLOR);
        DyeColor patternColor = stack.get(DataComponents.TROPICAL_FISH_PATTERN_COLOR);
        TropicalFish.Pattern pattern = stack.get(DataComponents.TROPICAL_FISH_PATTERN);
        if (base != null || patternColor != null || pattern != null) {
            TropicalFish.Variant variant = new TropicalFish.Variant(
                pattern != null ? pattern : TropicalFish.DEFAULT_VARIANT.pattern(),
                base != null ? base : TropicalFish.DEFAULT_VARIANT.baseColor(),
                patternColor != null ? patternColor : TropicalFish.DEFAULT_VARIANT.patternColor()
            );
            if (isBlackIllegalVariant(variant)) return true;
        }

        CustomData data = stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY);
        if (data.isEmpty()) return false;

        CompoundTag tag = data.copyTag();
        if (!tag.contains("BucketVariantTag")) return false;

        int packed = tag.getIntOr("BucketVariantTag", -1);
        if (packed < 0) return false;

        TropicalFish.Variant variant = new TropicalFish.Variant(
            TropicalFish.getPattern(packed),
            TropicalFish.getBaseColor(packed),
            TropicalFish.getPatternColor(packed)
        );
        return isBlackIllegalVariant(variant);
    }

    private static boolean isBlackIllegalVariant(TropicalFish.Variant variant) {
        if (TropicalFish.COMMON_VARIANTS.contains(variant)) return false;
        return variant.baseColor() == DyeColor.BLACK || variant.patternColor() == DyeColor.BLACK;
    }

    private static boolean isRenamed(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_NAME);
    }

    private static boolean hasIllegalEnchantments(ItemStack stack) {
        ItemEnchantments applied = stack.getEnchantments();
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

        if (checkEnchantSet(stack, applied)) return true;
        return checkEnchantSet(stack, stored);
    }

    private static boolean checkEnchantSet(ItemStack stack, ItemEnchantments enchantments) {
        if (enchantments.isEmpty()) return false;

        List<Holder<Enchantment>> holders = new ArrayList<>(enchantments.keySet());
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            Enchantment enchantment = holder.value();
            int level = entry.getIntValue();

            if (level > enchantment.getMaxLevel() || level < enchantment.getMinLevel()) {
                return true;
            }

            // Enchanted books store enchants; skip "supported item" checks for books themselves
            boolean isBook = stack.is(Items.ENCHANTED_BOOK) || stack.has(DataComponents.STORED_ENCHANTMENTS);
            if (!isBook && !enchantment.canEnchant(stack) && !enchantment.isSupportedItem(stack)) {
                return true;
            }
        }

        for (int i = 0; i < holders.size(); i++) {
            for (int j = i + 1; j < holders.size(); j++) {
                if (!Enchantment.areCompatible(holders.get(i), holders.get(j))) {
                    return true;
                }
            }
        }

        return false;
    }
}
