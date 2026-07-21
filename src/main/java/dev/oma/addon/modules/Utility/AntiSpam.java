package dev.oma.addon.modules.Utility;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.oma.addon.Main;

public class AntiSpam extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Blocklist");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Group repeats into (xN), or hide them entirely.")
        .defaultValue(Mode.Group)
        .build()
    );

    private final Setting<Integer> windowSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("window-seconds")
        .description("How long to remember messages for grouping/hiding.")
        .defaultValue(15)
        .min(1)
        .max(120)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Boolean> groupSimilar = sgGeneral.add(new BoolSetting.Builder()
        .name("group-similar")
        .description("Treat messages that only differ by numbers as the same (e.g. kit ads).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideEmpty = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-empty-messages")
        .description("Also prevents empty messages from showing in the chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> blockedKeywords = sgFilters.add(new StringListSetting.Builder()
        .name("blocked-keywords")
        .description("Cancel any message containing these keywords or phrases.")
        .defaultValue(List.of())
        .build()
    );

    private final Map<String, TrackedMessage> recent = new LinkedHashMap<>();

    public AntiSpam() {
        super(Main.MOD, "Anti Spam", "Groups or hides repeated chat spam and optional keyword blocks.");
    }

    @Override
    public void onActivate() {
        recent.clear();
    }

    @Override
    public void onDeactivate() {
        recent.clear();
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        String content = event.getMessage().getString();
        if (content == null) return;

        if (hideEmpty.get() && content.replaceAll("\\s+", "").isEmpty()) {
            event.cancel();
            return;
        }

        for (String keyword : blockedKeywords.get()) {
            if (keyword == null || keyword.isEmpty()) continue;
            if (content.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                event.cancel();
                return;
            }
        }

        long now = System.currentTimeMillis();
        prune(now);

        String key = normalizeKey(content);
        TrackedMessage tracked = recent.get(key);
        if (tracked != null && now - tracked.lastSeenMs <= windowSeconds.get() * 1000L) {
            tracked.count++;
            tracked.lastSeenMs = now;
            tracked.displayText = stripCountSuffix(content);

            if (mode.get() == Mode.Hide) {
                event.cancel();
                return;
            }

            // Group: replace the incoming line with an updated count suffix
            event.setMessage(Text.literal(tracked.displayText + " (x" + tracked.count + ")"));
            return;
        }

        TrackedMessage created = new TrackedMessage();
        created.displayText = stripCountSuffix(content);
        created.count = 1;
        created.lastSeenMs = now;
        recent.put(key, created);
    }

    private void prune(long now) {
        long windowMs = windowSeconds.get() * 1000L;
        Iterator<Map.Entry<String, TrackedMessage>> it = recent.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TrackedMessage> entry = it.next();
            if (now - entry.getValue().lastSeenMs > windowMs) {
                it.remove();
            }
        }
        while (recent.size() > 64) {
            Iterator<String> keys = recent.keySet().iterator();
            if (!keys.hasNext()) break;
            keys.next();
            keys.remove();
        }
    }

    private String normalizeKey(String message) {
        String normalized = stripCountSuffix(message).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (groupSimilar.get()) {
            normalized = normalized.replaceAll("\\d+", "#");
        }
        return normalized;
    }

    private static String stripCountSuffix(String message) {
        return message.replaceAll("\\s*\\(x\\d+\\)\\s*$", "");
    }

    private static class TrackedMessage {
        String displayText;
        int count;
        long lastSeenMs;
    }

    public enum Mode {
        Group,
        Hide;

        @Override
        public String toString() {
            return switch (this) {
                case Group -> "Group";
                case Hide -> "Hide";
            };
        }
    }
}
