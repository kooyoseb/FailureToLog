package org.kooyoseb.failureToLog;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FailureToLog extends JavaPlugin implements Listener, TabExecutor, PluginMessageListener {
    private static final String HELLO_CHANNEL = "failuretolog:hello";
    private static final String CLIENT_LOG_CHANNEL = "failuretolog:client_log";
    private static final int MAX_LOGS = 200;
    private static final int MAX_CLIENT_LOG_LENGTH = 24000;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private final Map<UUID, Long> kickedPlayers = new HashMap<>();
    private final Map<String, FailureLog> logs = new HashMap<>();
    private final Map<Integer, ErrorCode> errorCodes = new HashMap<>();

    private File logsFile;
    private FileConfiguration logsConfig;
    private boolean enabled;
    private boolean saveLogs;
    private int nextLogId;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().addDefault("enabled", true);
        getConfig().addDefault("save-logs", true);
        getConfig().options().copyDefaults(true);
        saveConfig();

        enabled = getConfig().getBoolean("enabled", true);
        saveLogs = getConfig().getBoolean("save-logs", true);

        registerErrorCodes();
        loadLogs();

        Bukkit.getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, HELLO_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CLIENT_LOG_CHANNEL, this);
        getCommand("failuretolog").setExecutor(this);
        getCommand("failuretolog").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        if (saveLogs && logsConfig != null) {
            saveLogs();
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled || event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        int code = classifyPreLogin(event.getLoginResult(), event.getKickMessage());
        String rawLog = buildRawLog(event.getName(), "LOGIN_FAILED", event.getKickMessage(), event.getAddress(), null);

        Bukkit.getScheduler().runTask(this, () -> recordAndBroadcast(
                event.getName(),
                code,
                rawLog,
                "failed to connect"
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        if (!enabled) {
            return;
        }

        Player player = event.getPlayer();
        kickedPlayers.put(player.getUniqueId(), System.currentTimeMillis());

        int code = classifyKick(event.getReason());
        String rawLog = buildRawLog(player.getName(), "DISCONNECTED_BY_ERROR", event.getReason(), player.getAddress() == null ? null : player.getAddress().getAddress(), player.getUniqueId());
        FailureLog log = recordLog(player.getName(), code, rawLog);

        event.setLeaveMessage(null);
        Bukkit.broadcast(formatFailureMessage(player.getName(), log, "lost connection"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Long kickedAt = kickedPlayers.remove(event.getPlayer().getUniqueId());
        if (enabled && kickedAt != null && System.currentTimeMillis() - kickedAt < 5000) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> sendHello(event.getPlayer()), 20L);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!enabled || !CLIENT_LOG_CHANNEL.equals(channel)) {
            return;
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            int version = input.readInt();
            int code = input.readInt();
            String screenTitle = trim(input.readUTF(), 240);
            String screenReason = trim(input.readUTF(), 2000);
            String details = trim(input.readUTF(), MAX_CLIENT_LOG_LENGTH);
            String rawLog = "type=FABRIC_CLIENT_REPORT\n"
                    + "version=" + version + "\n"
                    + "player=" + player.getName() + "\n"
                    + "uuid=" + player.getUniqueId() + "\n"
                    + "screenTitle=" + screenTitle + "\n"
                    + "screenReason=" + screenReason + "\n"
                    + "details=\n" + details;

            FailureLog log = recordLog(player.getName(), errorCodes.containsKey(code) ? code : 1, rawLog);
            Bukkit.broadcast(formatFailureMessage(player.getName(), log, "reported a client-side disconnect"));
        } catch (IOException exception) {
            getLogger().warning("Failed to read client log from " + player.getName() + ": " + exception.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "toggle" -> {
                if (!sender.hasPermission("failuretolog.admin")) {
                    sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                    return true;
                }
                enabled = !enabled;
                getConfig().set("enabled", enabled);
                saveConfig();
                sender.sendMessage(Component.text("FailureToLog is now " + state(enabled) + ".", NamedTextColor.GREEN));
            }
            case "savelogs" -> {
                if (!sender.hasPermission("failuretolog.admin")) {
                    sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2 || !isOnOff(args[1])) {
                    sender.sendMessage(Component.text("Usage: /" + label + " savelogs <on|off>", NamedTextColor.YELLOW));
                    return true;
                }
                saveLogs = args[1].equalsIgnoreCase("on");
                getConfig().set("save-logs", saveLogs);
                saveConfig();
                if (saveLogs) {
                    saveLogs();
                }
                sender.sendMessage(Component.text("Error log saving is now " + state(saveLogs) + ".", NamedTextColor.GREEN));
            }
            case "codes" -> sendCodes(sender);
            case "log" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " log <id>", NamedTextColor.YELLOW));
                    return true;
                }
                FailureLog log = logs.get(args[1]);
                if (log == null) {
                    sender.sendMessage(Component.text("No error log found for id " + args[1] + ".", NamedTextColor.RED));
                    return true;
                }
                sendLog(sender, log);
            }
            default -> sendHelp(sender, label);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("toggle", "savelogs", "codes", "log").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("savelogs")) {
            return List.of("on", "off").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("log")) {
            return logs.keySet().stream()
                    .filter(value -> value.startsWith(args[1]))
                    .limit(20)
                    .toList();
        }
        return List.of();
    }

    private void recordAndBroadcast(String playerName, int code, String rawLog, String action) {
        FailureLog log = recordLog(playerName, code, rawLog);
        Bukkit.broadcast(formatFailureMessage(playerName, log, action));
    }

    private void sendHello(Player player) {
        if (!player.isOnline()) {
            return;
        }

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(1);
            output.writeUTF(getDescription().getVersion());
            player.sendPluginMessage(this, HELLO_CHANNEL, bytes.toByteArray());
        } catch (IOException exception) {
            getLogger().warning("Failed to send hello packet to " + player.getName() + ": " + exception.getMessage());
        }
    }

    private FailureLog recordLog(String playerName, int code, String rawLog) {
        String id = String.valueOf(nextLogId++);
        FailureLog log = new FailureLog(id, playerName, code, Instant.now(), rawLog);
        logs.put(id, log);

        while (logs.size() > MAX_LOGS) {
            String oldest = logs.values().stream()
                    .min((left, right) -> left.time.compareTo(right.time))
                    .map(FailureLog::id)
                    .orElse(null);
            if (oldest == null) {
                break;
            }
            logs.remove(oldest);
        }

        if (saveLogs) {
            saveLogs();
        }
        return log;
    }

    private Component formatFailureMessage(String playerName, FailureLog log, String action) {
        ErrorCode code = errorCodes.getOrDefault(log.code(), errorCodes.get(1));
        Component button = Component.text("[View error]", NamedTextColor.AQUA, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/failuretolog log " + log.id()))
                .hoverEvent(HoverEvent.showText(Component.text("Show the collected server-side log", NamedTextColor.GRAY)));

        return Component.text("Due to an error, ", NamedTextColor.GRAY)
                .append(Component.text(playerName, NamedTextColor.YELLOW))
                .append(Component.text(" " + action + ". Error code: ", NamedTextColor.GRAY))
                .append(Component.text(log.code() + " (" + code.title() + ") ", NamedTextColor.RED))
                .append(button);
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("FailureToLog commands", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " toggle - Turn FailureToLog on/off", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " savelogs <on|off> - Enable or disable log saving", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " codes - Show error code list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " log <id> - Show a collected error log", NamedTextColor.GRAY));
    }

    private void sendCodes(CommandSender sender) {
        sender.sendMessage(Component.text("FailureToLog error codes", NamedTextColor.GOLD));
        errorCodes.values().stream()
                .sorted((left, right) -> Integer.compare(left.code(), right.code()))
                .forEach(code -> sender.sendMessage(Component.text(code.code() + ": " + code.title() + " - " + code.description(), NamedTextColor.GRAY)));
    }

    private void sendLog(CommandSender sender, FailureLog log) {
        ErrorCode code = errorCodes.getOrDefault(log.code(), errorCodes.get(1));
        sender.sendMessage(Component.text("FailureToLog #" + log.id(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Player: " + log.playerName(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Time: " + TIME_FORMAT.format(log.time()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Code: " + log.code() + " (" + code.title() + ")", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Server-side collected log:", NamedTextColor.YELLOW));
        for (String line : log.rawLog().split("\\R")) {
            sender.sendMessage(Component.text(line, NamedTextColor.WHITE));
        }
    }

    private int classifyPreLogin(AsyncPlayerPreLoginEvent.Result result, String reason) {
        String text = lower(reason);
        return switch (result) {
            case KICK_WHITELIST -> 6;
            case KICK_BANNED -> text.contains("blacklist") ? 9 : 7;
            case KICK_FULL -> 503;
            case KICK_OTHER -> classifyText(text);
            default -> 1;
        };
    }

    private int classifyKick(String reason) {
        return classifyText(lower(reason));
    }

    private int classifyText(String text) {
        if (text.contains("client") && (text.contains("outofmemory") || text.contains("out of memory") || text.contains("heap space"))) {
            return 201;
        }
        if (text.contains("outofmemory") || text.contains("out of memory") || text.contains("heap space")) {
            return 200;
        }
        if (text.contains("chunk") || text.contains("region") || text.contains("world")) {
            return 105;
        }
        if (text.contains("mod") || text.contains("fabric") || text.contains("forge") || text.contains("neoforge") || text.contains("socket")) {
            return 790;
        }
        if (text.contains("packet") || text.contains("decoder") || text.contains("payload") || text.contains("protocol")) {
            return 600;
        }
        if (text.contains("authentication") || text.contains("session") || text.contains("profile")) {
            return 401;
        }
        if (text.contains("timeout") || text.contains("timed out") || text.contains("keepalive")) {
            return 408;
        }
        if (text.contains("server") || text.contains("internal exception") || text.contains("exception")) {
            return 500;
        }
        return 1;
    }

    private String buildRawLog(String playerName, String type, String reason, InetAddress address, UUID uuid) {
        return "type=" + type + "\n"
                + "player=" + playerName + "\n"
                + "uuid=" + (uuid == null ? "unknown" : uuid) + "\n"
                + "address=" + (address == null ? "unknown" : address.getHostAddress()) + "\n"
                + "reason=" + (reason == null || reason.isBlank() ? "No reason supplied by the server." : reason);
    }

    private void registerErrorCodes() {
        addCode(1, "Unknown crash", "A failure happened but the server could not classify it.");
        addCode(6, "Not whitelisted", "The player is not on the server whitelist.");
        addCode(7, "Banned", "The player is banned by the server.");
        addCode(9, "Blacklisted", "The player was rejected by a blacklist or similar protection.");
        addCode(105, "World chunk load failure", "A world, region, or chunk problem interrupted the connection.");
        addCode(200, "Server memory shortage", "The server reported an out-of-memory condition.");
        addCode(201, "Client memory shortage", "The client likely ran out of memory. This can only be inferred server-side.");
        addCode(401, "Authentication/session failure", "The Mojang/Microsoft session or profile check failed.");
        addCode(408, "Connection timeout", "The player timed out or stopped responding to keepalive packets.");
        addCode(429, "Connection throttled", "The server or proxy rejected the player for connecting too quickly.");
        addCode(500, "Server-side problem", "The server raised an internal exception or generic server error.");
        addCode(503, "Server full", "The server rejected the player because the player limit was reached.");
        addCode(600, "Bad client data", "The client sent malformed packets, payloads, or protocol data.");
        addCode(601, "Unsupported client version", "The client version or protocol is not compatible with the server.");
        addCode(602, "Resource pack failure", "A required resource pack failed or was refused.");
        addCode(790, "Mod or socket conflict", "A mod, missing server-side mod code, or socket conflict interrupted the connection.");
    }

    private void addCode(int code, String title, String description) {
        errorCodes.put(code, new ErrorCode(code, title, description));
    }

    private void loadLogs() {
        logsFile = new File(getDataFolder(), "logs.yml");
        logsConfig = YamlConfiguration.loadConfiguration(logsFile);
        nextLogId = logsConfig.getInt("next-id", 1);

        ConfigurationSection section = logsConfig.getConfigurationSection("logs");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            String path = "logs." + id + ".";
            logs.put(id, new FailureLog(
                    id,
                    logsConfig.getString(path + "player", "unknown"),
                    logsConfig.getInt(path + "code", 1),
                    Instant.ofEpochMilli(logsConfig.getLong(path + "time", System.currentTimeMillis())),
                    logsConfig.getString(path + "raw", "")
            ));
        }
    }

    private void saveLogs() {
        logsConfig.set("next-id", nextLogId);
        logsConfig.set("logs", null);
        for (FailureLog log : logs.values()) {
            String path = "logs." + log.id() + ".";
            logsConfig.set(path + "player", log.playerName());
            logsConfig.set(path + "code", log.code());
            logsConfig.set(path + "time", log.time().toEpochMilli());
            logsConfig.set(path + "raw", log.rawLog());
        }
        try {
            logsConfig.save(logsFile);
        } catch (IOException exception) {
            getLogger().warning("Failed to save logs.yml: " + exception.getMessage());
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean isOnOff(String value) {
        return value.equalsIgnoreCase("on") || value.equalsIgnoreCase("off");
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String state(boolean value) {
        return value ? "ON" : "OFF";
    }

    private record ErrorCode(int code, String title, String description) {
    }

    private record FailureLog(String id, String playerName, int code, Instant time, String rawLog) {
    }
}
