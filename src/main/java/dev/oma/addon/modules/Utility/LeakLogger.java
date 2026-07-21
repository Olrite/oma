package dev.oma.addon.modules.Utility;

import dev.oma.addon.Main;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class LeakLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgFile = settings.createGroup("File Settings");

    // General Settings
    private final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable chat logging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logToConsole = sgGeneral.add(new BoolSetting.Builder()
        .name("log-to-console")
        .description("Also log messages to console.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> includeTimestamp = sgGeneral.add(new BoolSetting.Builder()
        .name("include-timestamp")
        .description("Include timestamp in logged messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeCoordinates = sgGeneral.add(new BoolSetting.Builder()
        .name("include-coordinates")
        .description("Include player coordinates in logged messages.")
        .defaultValue(true)
        .build()
    );

    // Filter Settings
    private final Setting<Boolean> filterUrls = sgFilters.add(new BoolSetting.Builder()
        .name("filter-urls")
        .description("Log messages containing URLs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterCoordinates = sgFilters.add(new BoolSetting.Builder()
        .name("filter-coordinates")
        .description("Log messages containing coordinates.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterPlayerNames = sgFilters.add(new BoolSetting.Builder()
        .name("filter-player-names")
        .description("Only log messages from specific players.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> playerNames = sgFilters.add(new StringListSetting.Builder()
        .name("player-names")
        .description("List of player names to filter for.")
        .defaultValue(new ArrayList<>())
        .visible(() -> filterPlayerNames.get())
        .build()
    );

    private final Setting<Boolean> useRegex = sgFilters.add(new BoolSetting.Builder()
        .name("use-regex")
        .description("Use regex pattern matching.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> regexPattern = sgFilters.add(new StringSetting.Builder()
        .name("regex-pattern")
        .description("Regex pattern to match against messages.")
        .defaultValue(".*")
        .visible(() -> useRegex.get())
        .build()
    );

    private final Setting<Boolean> caseSensitive = sgFilters.add(new BoolSetting.Builder()
        .name("case-sensitive")
        .description("Case sensitive filtering.")
        .defaultValue(false)
        .build()
    );

    // File Settings
    private final Setting<String> fileName = sgFile.add(new StringSetting.Builder()
        .name("file-name")
        .description("Name of the log file.")
        .defaultValue("leak_log")
        .build()
    );

    private final Setting<String> fileExtension = sgFile.add(new StringSetting.Builder()
        .name("file-extension")
        .description("Extension of the log file.")
        .defaultValue("txt")
        .build()
    );

    private final Setting<Boolean> appendToFile = sgFile.add(new BoolSetting.Builder()
        .name("append-to-file")
        .description("Append to existing file instead of overwriting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> createNewFilePerSession = sgFile.add(new BoolSetting.Builder()
        .name("new-file-per-session")
        .description("Create a new file for each session.")
        .defaultValue(false)
        .build()
    );

    // State tracking
    private File currentLogFile;
    private Pattern compiledRegex;
    private int messageCount = 0;
    private LocalDateTime sessionStart;

    public LeakLogger() {
        super(Main.MOD, "Leak Logger", "Logs chat messages that look like leaks (URLs and coordinates).");
    }

    @Override
    public void onActivate() {
        sessionStart = LocalDateTime.now();
        messageCount = 0;
        
        // Compile regex pattern if enabled
        if (useRegex.get()) {
            try {
                int flags = caseSensitive.get() ? 0 : Pattern.CASE_INSENSITIVE;
                compiledRegex = Pattern.compile(regexPattern.get(), flags);
            } catch (Exception e) {
                error("Invalid regex pattern: " + e.getMessage());
                toggle();
                return;
            }
        }

        // Initialize log file
        initializeLogFile();
        
        info("Leak logging started. Log file: " + currentLogFile.getName());
    }

    @Override
    public void onDeactivate() {
        if (currentLogFile != null) {
            info("Leak logging stopped. Total messages logged: " + messageCount);
        }
        currentLogFile = null;
        compiledRegex = null;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!enabled.get()) return;

        String message = event.getMessage().getString();
        if (message == null || message.trim().isEmpty()) return;

        // Apply filters
        if (!shouldLogMessage(message)) return;

        // Format and log the message
        String formattedMessage = formatMessage(message);
        logMessage(formattedMessage);
        
        messageCount++;
    }

    private boolean shouldLogMessage(String message) {
        // Leak filters: if either is enabled, require at least one match (OR).
        // If both are disabled, log all messages (full chat mode).
        if (filterUrls.get() || filterCoordinates.get()) {
            boolean urlMatch = filterUrls.get() && containsUrl(message);
            boolean coordMatch = filterCoordinates.get() && containsCoordinates(message);
            if (!urlMatch && !coordMatch) {
                return false;
            }
        }

        // Filter by player names
        if (filterPlayerNames.get() && !containsPlayerName(message)) {
            return false;
        }

        // Filter by regex
        if (useRegex.get() && compiledRegex != null) {
            Matcher matcher = compiledRegex.matcher(message);
            if (!matcher.find()) {
                return false;
            }
        }

        return true;
    }

    private boolean containsUrl(String message) {
        Pattern urlPattern = Pattern.compile(
            "(?i)(https?://\\S+|www\\.\\S+|\\b[a-z0-9.-]+\\.(com|net|org|gg|io|me|co|xyz|info|ru|tk)\\b\\S*)"
        );
        return urlPattern.matcher(message).find();
    }

    // Matches an int or decimal, optionally negative. \b doesn't work here since it
    // requires a word-char boundary and fails right before a leading '-' preceded by
    // whitespace, so plain lookaround on non-digit/non-dot characters is used instead.
    private static final String NUM = "-?\\d{1,7}(?:\\.\\d+)?";
    private static final String LEFT_BOUND = "(?<![\\w.-])";
    private static final String RIGHT_BOUND = "(?![\\w.])";

    private static final Pattern COORD_PATTERN = Pattern.compile(
        "\\[\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*\\]" +
        "|\\(\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*\\)" +
        "|" + LEFT_BOUND + NUM + "\\s*,\\s*" + NUM + "\\s*,\\s*" + NUM + RIGHT_BOUND +
        "|" + LEFT_BOUND + NUM + "\\s*/\\s*" + NUM + "\\s*/\\s*" + NUM + RIGHT_BOUND +
        "|" + LEFT_BOUND + NUM + "\\s+" + NUM + "\\s+" + NUM + RIGHT_BOUND
    );

    private boolean containsCoordinates(String message) {
        // Bracketed / parenthesized coords, or comma-, slash-, or space-separated x y z
        // triples. Supports negative and decimal values.
        return COORD_PATTERN.matcher(message).find();
    }

    private boolean containsPlayerName(String message) {
        if (playerNames.get().isEmpty()) return true;
        
        String lowerMessage = caseSensitive.get() ? message : message.toLowerCase();
        
        for (String playerName : playerNames.get()) {
            String lowerPlayerName = caseSensitive.get() ? playerName : playerName.toLowerCase();
            if (lowerMessage.contains(lowerPlayerName)) {
                return true;
            }
        }
        
        return false;
    }

    private String formatMessage(String message) {
        StringBuilder formatted = new StringBuilder();
        
        // Add timestamp
        if (includeTimestamp.get()) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            formatted.append("[").append(now.format(formatter)).append("] ");
        }
        
        // Add coordinates
        if (includeCoordinates.get() && mc.player != null) {
            int x = (int) mc.player.getX();
            int y = (int) mc.player.getY();
            int z = (int) mc.player.getZ();
            formatted.append("[Player at ").append(x).append(", ").append(y).append(", ").append(z).append("] ");
        }
        
        // Add the actual message
        formatted.append(message);
        
        return formatted.toString();
    }

    private void logMessage(String message) {
        // Log to console if enabled
        if (logToConsole.get()) {
            info(message);
        }
        
        // Log to file
        if (currentLogFile != null) {
            try (FileWriter writer = new FileWriter(currentLogFile, appendToFile.get())) {
                writer.write(message + System.lineSeparator());
                writer.flush();
            } catch (IOException e) {
                error("Failed to write to log file: " + e.getMessage());
            }
        }
    }

    private void initializeLogFile() {
        try {
            // Create logs directory if it doesn't exist
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            // Generate filename
            String baseName = fileName.get();
            String extension = fileExtension.get();
            
            if (createNewFilePerSession.get()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
                String timestamp = sessionStart.format(formatter);
                baseName = baseName + "_" + timestamp;
            }
            
            String fileName = baseName + "." + extension;
            currentLogFile = new File(logsDir, fileName);
            
            // Create file if it doesn't exist
            if (!currentLogFile.exists()) {
                currentLogFile.createNewFile();
            }
            
        } catch (IOException e) {
            error("Failed to initialize log file: " + e.getMessage());
            toggle();
        }
    }

    // Public methods for external access
    public int getMessageCount() {
        return messageCount;
    }

    public File getCurrentLogFile() {
        return currentLogFile;
    }

    public LocalDateTime getSessionStart() {
        return sessionStart;
    }

    // Method to manually log a message
    public void logCustomMessage(String message) {
        if (enabled.get()) {
            logMessage(formatMessage(message));
            messageCount++;
        }
    }
}
