package dev.oma.addon.modules.Utility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.oma.addon.Main;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Sorts the inventory to match a saved kit loadout when a container opens (or a keybind is
 * pressed). Kits map inventory slot (0-35) -> item and are stored as JSON under
 * {@code <meteor>/oma/kits/<name>.json}. Rearrangement is planned as slot moves and executed
 * with a per-tick move budget, pulling extra items from an open container when available.
 */
public class AutoRekit extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<RekitMode> mode = sgGeneral.add(new EnumSetting.Builder<RekitMode>()
        .name("mode")
        .description("Auto triggers on container open, On-Key triggers on a keybind.")
        .defaultValue(RekitMode.Auto)
        .build()
    );

    public final Setting<Keybind> key = sgGeneral.add(new KeybindSetting.Builder()
        .name("key")
        .description("Keybind to trigger rekit.")
        .defaultValue(Keybind.none())
        .visible(() -> mode.get() == RekitMode.OnKey)
        .build()
    );

    public final Setting<String> loadout = sgGeneral.add(new StringSetting.Builder()
        .name("kit-name")
        .description("Name of the active kit. Leave blank to disable.")
        .defaultValue("")
        .build()
    );

    public final Setting<Integer> movesPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("moves-per-tick")
        .description("Maximum number of slot-move operations to execute per tick.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final HashMap<String, HashMap<Integer, Item>> kitCache = new HashMap<>();
    private boolean keyWasPressed = false;
    private int openDelayTicks = -1;
    private ScreenHandler pendingHandler = null;

    private final Deque<MoveOp> pendingOps = new ArrayDeque<>();
    private int pendingSyncId = -1;

    public AutoRekit() {
        super(Main.MOD, "Auto Rekit", "Sorts inventory to match a saved kit loadout when opening a container or pressing a keybind.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton saveBtn = list.add(theme.button("Save current loadout")).widget();
        WButton clearBtn = list.add(theme.button("Clear kit cache")).widget();
        saveBtn.action = () -> saveKit(loadout.get());
        clearBtn.action = this::clearCache;
        return list;
    }

    @Override
    public void onActivate() {
        String name = loadout.get();
        if (name.isEmpty()) {
            info("No kit selected.");
            return;
        }
        HashMap<Integer, Item> kit = loadKit(name);
        if (kit == null) {
            info("Kit '§b%s§r' not found. Save one first with the module's Save current loadout button.", name);
        } else {
            kitCache.put(name, kit);
            info("Loaded kit '§b%s§r' (§b%d§r slots).", name, kit.size());
        }
    }

    @Override
    public void onDeactivate() {
        pendingOps.clear();
        pendingHandler = null;
        openDelayTicks = -1;
        keyWasPressed = false;
    }

    @Override
    public String getInfoString() {
        String name = loadout.get();
        return name.isEmpty() ? null : name;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (mode.get() != RekitMode.Auto || mc.player == null) return;
        if (event.screen instanceof HandledScreen && !(event.screen instanceof InventoryScreen)) {
            ScreenHandler handler = mc.player.currentScreenHandler;
            if (handler != null && handler != mc.player.playerScreenHandler) {
                pendingHandler = handler;
                openDelayTicks = 1;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (openDelayTicks > 0 && --openDelayTicks == 0) {
            ScreenHandler current = mc.player.currentScreenHandler;
            if (current != null && current == pendingHandler) {
                queueKit(pendingHandler);
            }
            pendingHandler = null;
        }

        if (mode.get() == RekitMode.OnKey) {
            boolean pressed = key.get().isPressed();
            if (pressed && !keyWasPressed) {
                keyWasPressed = true;
                ScreenHandler handler = null;
                if (mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen)) {
                    ScreenHandler current = mc.player.currentScreenHandler;
                    if (current != null && current != mc.player.playerScreenHandler) handler = current;
                }
                queueKit(handler);
            } else if (!pressed) {
                keyWasPressed = false;
            }
        }

        executePendingOps();
    }

    /** Plans the slot moves needed to satisfy the active kit within {@code containerHandler} and queues them. */
    private void queueKit(ScreenHandler containerHandler) {
        if (mc.player == null) return;
        String name = loadout.get();
        if (name.isEmpty()) return;

        HashMap<Integer, Item> kit = kitCache.computeIfAbsent(name, this::loadKit);
        if (kit == null) {
            info("Kit '§b%s§r' not found.", name);
            return;
        }

        pendingOps.clear();
        pendingOps.addAll(buildPlan(kit, containerHandler));
        pendingSyncId = getSyncId(containerHandler);
    }

    /** Executes up to {@code moves-per-tick} queued slot moves via rate-limited container clicks. */
    private void executePendingOps() {
        if (pendingOps.isEmpty() || mc.interactionManager == null) return;

        if (getSyncId(mc.player.currentScreenHandler) != pendingSyncId) {
            pendingOps.clear();
            return;
        }

        int budget = movesPerTick.get();
        while (budget-- > 0) {
            MoveOp op = pendingOps.poll();
            if (op == null) break;

            if (op.to() == -1) {
                mc.interactionManager.clickSlot(pendingSyncId, op.from(), 0, SlotActionType.QUICK_MOVE, mc.player);
            } else {
                mc.interactionManager.clickSlot(pendingSyncId, op.from(), 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(pendingSyncId, op.to(), 0, SlotActionType.PICKUP, mc.player);
                if (op.returnTo() != -1) {
                    mc.interactionManager.clickSlot(pendingSyncId, op.returnTo(), 0, SlotActionType.PICKUP, mc.player);
                }
            }
        }
    }

    /** Builds the list of slot moves needed to satisfy {@code kit} within {@code containerHandler}. */
    private List<MoveOp> buildPlan(HashMap<Integer, Item> kit, ScreenHandler containerHandler) {
        List<MoveOp> plan = new ArrayList<>();
        if (mc.player == null) return plan;

        Set<Integer> kitSlots = kit.keySet();
        boolean hasContainer = containerHandler != null && containerHandler != mc.player.playerScreenHandler;
        int containerSize = hasContainer ? containerHandler.slots.size() - 36 : 0;

        Item[] invItem = new Item[36];
        int[] invCount = new int[36];
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            invItem[i] = s.getItem();
            invCount[i] = s.getCount();
        }

        Item[] conItem = new Item[containerSize];
        int[] conCount = new int[containerSize];
        for (int i = 0; i < containerSize; i++) {
            ItemStack s = containerHandler.getSlot(i).getStack();
            conItem[i] = s.getItem();
            conCount[i] = s.getCount();
        }

        for (Map.Entry<Integer, Item> entry : kit.entrySet()) {
            int targetInv = entry.getKey();
            Item targetItem = entry.getValue();
            int maxCount = targetItem.getMaxCount();
            int targetScreen = toScreenSlot(targetInv, containerHandler);
            int current = invItem[targetInv] == targetItem ? invCount[targetInv] : 0;
            if (current >= maxCount) continue;

            // Evict a wrong item occupying the target slot.
            if (!(invCount[targetInv] <= 0 || invItem[targetInv] == targetItem)) {
                int freeSlot = findFreeSlot(invCount, kitSlots, targetInv);
                if (freeSlot != -1) {
                    plan.add(new MoveOp(targetScreen, toScreenSlot(freeSlot, containerHandler), -1));
                    invItem[freeSlot] = invItem[targetInv];
                    invCount[freeSlot] = invCount[targetInv];
                } else {
                    if (containerSize <= 0) continue;
                    plan.add(new MoveOp(targetScreen, -1, -1));
                }
                invItem[targetInv] = Items.AIR;
                invCount[targetInv] = 0;
                current = 0;
            }

            int remaining = maxCount - current;

            // Consolidate matching stacks from the inventory into the target slot.
            for (int i = 0; i < 36 && remaining > 0; i++) {
                if (invItem[i] == targetItem && i != targetInv && invCount[i] != 0) {
                    Item kitAtI = kit.get(i);
                    if (kitAtI == null || kitAtI != targetItem) {
                        int fromScreen = toScreenSlot(i, containerHandler);
                        int moved = Math.min(invCount[i], remaining);
                        plan.add(new MoveOp(fromScreen, targetScreen, invCount[i] > remaining ? fromScreen : -1));
                        invCount[i] -= moved;
                        if (invCount[i] == 0) invItem[i] = Items.AIR;
                        invCount[targetInv] += moved;
                        invItem[targetInv] = targetItem;
                        remaining -= moved;
                    }
                }
            }

            // Pull the remainder from the open container.
            for (int i = 0; i < containerSize && remaining > 0; i++) {
                if (conItem[i] == targetItem && conCount[i] != 0) {
                    int moved = Math.min(conCount[i], remaining);
                    plan.add(new MoveOp(i, targetScreen, conCount[i] > remaining ? i : -1));
                    conCount[i] -= moved;
                    if (conCount[i] == 0) conItem[i] = Items.AIR;
                    invCount[targetInv] += moved;
                    invItem[targetInv] = targetItem;
                    remaining -= moved;
                }
            }
        }

        return plan;
    }

    /** Maps an inventory slot index (0-35) to the screen-handler slot index for the current screen. */
    private int toScreenSlot(int invSlot, ScreenHandler containerHandler) {
        if (containerHandler != null && containerHandler != mc.player.playerScreenHandler) {
            int containerSize = containerHandler.slots.size() - 36;
            return invSlot <= 8 ? containerSize + 27 + invSlot : containerSize + (invSlot - 9);
        }
        return invSlot <= 8 ? 36 + invSlot : invSlot;
    }

    private int getSyncId(ScreenHandler containerHandler) {
        return containerHandler != null && containerHandler != mc.player.playerScreenHandler
            ? containerHandler.syncId
            : mc.player.playerScreenHandler.syncId;
    }

    /** Finds an empty, non-kit inventory slot (hotbar last), or -1. */
    private int findFreeSlot(int[] invCount, Set<Integer> kitSlots, int exclude) {
        for (int i = 35; i >= 9; i--) {
            if (i != exclude && !kitSlots.contains(i) && invCount[i] == 0) return i;
        }
        for (int i = 8; i >= 0; i--) {
            if (i != exclude && !kitSlots.contains(i) && invCount[i] == 0) return i;
        }
        return -1;
    }

    /** Saves the current inventory (slots 0-35) as a kit JSON file and caches it. */
    public void saveKit(String kitName) {
        if (kitName == null || kitName.isEmpty()) {
            info("Set a kit name first.");
            return;
        }
        if (mc.player == null) return;

        Map<String, String> kitData = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                kitData.put(String.valueOf(i), Registries.ITEM.getId(stack.getItem()).toString());
            }
        }

        File file = kitFile(kitName);
        if (file == null) return;
        file.getParentFile().mkdirs();
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(kitData, writer);
        } catch (IOException e) {
            error("Failed to save kit: %s", e.getMessage());
            return;
        }
        HashMap<Integer, Item> itemMap = parseKit(kitData);
        kitCache.put(kitName, itemMap);
        info("Saved kit '§b%s§r' (§b%d§r slots).", kitName, itemMap.size());
    }

    /** Loads a kit from disk (slot -> item), or null if missing/unreadable. */
    public HashMap<Integer, Item> loadKit(String kitName) {
        File file = kitFile(kitName);
        if (file == null || !file.exists()) return null;
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<HashMap<String, String>>() {}.getType();
            HashMap<String, String> raw = GSON.fromJson(reader, type);
            return raw == null ? null : parseKit(raw);
        } catch (Exception e) {
            error("Failed to load kit '§b%s§r': %s", kitName, e.getMessage());
            return null;
        }
    }

    /** Parses a raw slot->item-id map into a slot->Item map (slots 0-35 only). */
    private HashMap<Integer, Item> parseKit(Map<String, String> raw) {
        HashMap<Integer, Item> map = new HashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            try {
                int slot = Integer.parseInt(e.getKey());
                if (slot >= 0 && slot <= 35) {
                    Identifier id = Identifier.tryParse(e.getValue());
                    if (id != null) {
                        Item item = Registries.ITEM.get(id);
                        if (item != Items.AIR) map.put(slot, item);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return map;
    }

    /** The JSON file backing a kit name, or null. */
    public File kitFile(String kitName) {
        if (kitName == null || kitName.isEmpty()) return null;
        try {
            return new File(new File(new File(MeteorClient.FOLDER, "oma"), "kits"), kitName + ".json");
        } catch (NullPointerException e) {
            return null;
        }
    }

    /** Clears the in-memory kit cache. */
    public void clearCache() {
        kitCache.clear();
        info("Cleared kit cache.");
    }

    /** Deletes a kit from the cache and disk. */
    public void deleteKit(String name) {
        kitCache.remove(name);
        File file = kitFile(name);
        if (file != null && file.exists() && file.delete()) {
            info("Deleted kit '§b%s§r'.", name);
        }
    }

    /** True if a kit with the given name is cached or exists on disk. */
    public boolean hasKit(String name) {
        if (kitCache.containsKey(name)) return true;
        File file = kitFile(name);
        return file != null && file.exists();
    }

    /** Names of all saved kits on disk. */
    public List<String> listKits() {
        File kitsDir = new File(new File(MeteorClient.FOLDER, "oma"), "kits");
        if (!kitsDir.exists()) return Collections.emptyList();
        File[] files = kitsDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (File f : files) names.add(f.getName().replace(".json", ""));
        return names;
    }

    /** A single planned slot move: pick up from {@code from}, place at {@code to} (-1 = quick-move), optionally return leftovers to {@code returnTo}. */
    private record MoveOp(int from, int to, int returnTo) { }

    /** Trigger mode. */
    public enum RekitMode { Auto, OnKey }
}
