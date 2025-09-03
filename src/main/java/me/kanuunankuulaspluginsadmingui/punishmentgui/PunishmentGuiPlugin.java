package me.kanuunankuulaspluginsadmingui.punishmentgui;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import me.kanuunankuulaspluginsadmingui.punishmentgui.Checkers.*;
import me.kanuunankuulaspluginsadmingui.punishmentgui.EventListeners.*;
import me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.*;
import me.kanuunankuulaspluginsadmingui.punishmentgui.discord.*;
import me.kanuunankuulaspluginsadmingui.punishmentgui.executers.*;
import me.kanuunankuulaspluginsadmingui.punishmentgui.gui.*;
import net.dv8tion.jda.api.JDA;

import org.bukkit.*;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.Checkers.Bancheckers.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.Log.logPunishment;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.discord.Discord.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.discord.Discord.DiscordEventListener.shutdownDiscordSystem;

public class PunishmentGuiPlugin extends JavaPlugin implements Listener, CommandExecutor {
    // Files
    public static File dataFolder;
    public static File punishmentDataFolder;
    public static File keyFile;

    // Strings
    public static String discordBotToken;
    public static String discordBotId;
    public static String discordChannelId;
    public static String ServerName;
    public static String getPlayerIP(String playerName) {
        for (Map.Entry<String, String> entry : playerIPs.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(playerName)) {
                return entry.getValue();
            }
        }
        return "Unknown";
    }

    // Booleans (True/false)
    public static boolean discordActive;
    public static boolean debugActive;
    public static boolean discordEnabled = false;
    public static volatile boolean discordInitializing = false;

    public static boolean experimentalPunishSystem = false;
    public static boolean builtInSystemActive = false;

    // Maps
    public static final Map<String, List<PunishmentRecord>> punishmentHistory = new ConcurrentHashMap<>();
    public static final Map<String, String> playerIPs = new ConcurrentHashMap<>();
    public static final Map<Player, BanSession> activeSessions = new ConcurrentHashMap<>();
    public static final Map<Player, String> chatInputWaiting = new ConcurrentHashMap<>();
    public static final Map<String, String> banReasonDurations = new HashMap<>();
    public static final Map<String, String> muteReasonDurations = new HashMap<>();
    public static final Map<String, Long> lastDiscordMessageTime = new ConcurrentHashMap<>();
    public static final Map<Player, HistoryPageInfo> historyPages = new ConcurrentHashMap<>();
    public static final Map<String, HistorySession> historySessions = new ConcurrentHashMap<>();
    public static final Map<String, Long> sessionExpiry = new ConcurrentHashMap<>();
    public static final Map<String, String> messageToSession = new ConcurrentHashMap<>();
    public static final Map<String, String> sessionIdMapping = new ConcurrentHashMap<>();
    public static final Map<String, ActiveBan> activeBans = new ConcurrentHashMap<>();
    public static final Map<String, ActiveMute> activeMutes = new ConcurrentHashMap<>();
    public static final Map<String, ActiveIPBan> activeIPBans = new ConcurrentHashMap<>();

    public static final Object discordInitLock = new Object();
    public static Map<Player, Integer> playerListPage = new HashMap<>();
    public static Map<Player, Integer> reasonPage = new HashMap<>();
    public static final Map<String, Long> discordRateLimits = new HashMap<>();

    // Other important stuff
    private static PunishmentGuiPlugin instance;
    private static Logger logger;
    public static SecretKey encryptionKey;
    public static Gson gson;
    public static HttpClient httpClient;
    public static ScheduledExecutorService discordScheduler;
    public static JDA jda;
    public static DiscordEventListener discordListener;
    public static PunishmentGuiPlugin getInstance() {
        return instance;
    }
    public static Logger getPluginLogger() {
        return logger;
    }
    public static void loadBanReasons() {
        instance.banReasons = getInstance().getConfig().getStringList("Punishment-Reasons");
    }
    public static final int DISCORD_RATE_LIMIT_SECONDS = 3;
    public static List<String> banReasons;

    public static String encryptData(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }
    public static String decryptData(String encryptedData) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance("AES");

            cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedData = cipher.doFinal(decodedData);
            return new String(decryptedData);
        } catch (BadPaddingException e) {
            logger.warning("Failed to decrypt data - possibly corrupted or wrong key");
            logger.warning("Please make sure you didn't copy any data from previous folders you had");
            return null;
        }

    }
    public static void initializeEncryption() throws Exception {
        if (keyFile.exists()) {
            byte[] keyBytes = Files.readAllBytes(keyFile.toPath());
            encryptionKey = new SecretKeySpec(keyBytes, "AES");
        } else {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            encryptionKey = keyGen.generateKey();

            Files.write(keyFile.toPath(), encryptionKey.getEncoded());

            keyFile.setReadOnly();
            keyFile.setExecutable(false, false);
            keyFile.setWritable(false, false);

            logger.info("Generated new encryption key for punishment data");
        }
    }

    // Classes

    public static class PunishmentRecord {
        public String playerName;
        public String playerUUID;
        public String punishmentType;
        public String reason;
        public String duration;
        public String staffMember;
        public String staffUUID;
        public String timestamp;
        public String playerIP;
        public boolean active;

        public PunishmentRecord(String playerName, String playerUUID, String punishmentType,
                                String reason, String duration, String staffMember, String staffUUID,
                                String playerIP) {
            this.playerName = playerName;
            this.playerUUID = playerUUID;
            this.punishmentType = punishmentType;
            this.reason = reason;
            this.duration = duration;
            this.staffMember = staffMember;
            this.staffUUID = staffUUID;
            this.playerIP = playerIP;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            this.active = !punishmentType.equals("UNBAN") && !punishmentType.equals("KICK");
        }
    }
    public static class DiscordEmbed {
        public String title;
        public String description;
        public int color;
        public String timestamp;
        public Footer footer;
        public Field[] fields;

        public static class Footer {
            public String text;
            public Footer(String text) { this.text = text; }
        }

        public static class Field {
            public String name;
            public String value;
            public boolean inline;
            public Field(String name, String value, boolean inline) {
                this.name = name;
                this.value = value;
                this.inline = inline;
            }
        }
    }
    public static class HistoryPageInfo {
        public String targetPlayer;
        public List<PunishmentRecord> records;
        public int currentPage;
        public int totalPages;

        public HistoryPageInfo(String targetPlayer, List<PunishmentRecord> records, int currentPage, int totalPages) {
            this.targetPlayer = targetPlayer;
            this.records = records;
            this.currentPage = currentPage;
            this.totalPages = totalPages;
        }
    }
    public static class BanSession {
        public String targetPlayer;
        public String punishmentType;
        public Set<String> selectedReasons = new HashSet<>();
        public String customReason;

        public BanSession(String targetPlayer) {
            this.targetPlayer = targetPlayer;
        }

        public String getCalculatedDuration() {
            if (selectedReasons.isEmpty() && (customReason == null || customReason.isEmpty())) {
                return punishmentType.equals("MUTE") ? "1d" : "3d";
            }

            Map<String, String> currentDurations = punishmentType.equals("MUTE") ? muteReasonDurations : banReasonDurations;

            long totalMinutes = 0;
            boolean hasCustomReason = customReason != null && !customReason.isEmpty();

            for (String reason : selectedReasons) {
                String duration = currentDurations.get(reason);
                if (duration != null) {
                    totalMinutes += parseDuration(duration);
                }
            }

            if (hasCustomReason) {
                String defaultDuration = punishmentType.equals("MUTE") ? "1d" : "3d";
                totalMinutes += parseDuration(defaultDuration);
            }

            if (totalMinutes == 0) {
                return punishmentType.equals("MUTE") ? "1d" : "3d";
            }

            return formatDuration(totalMinutes);
        }

        public String getCombinedReasons() {
            List<String> allReasons = new ArrayList<>(selectedReasons);
            if (customReason != null && !customReason.isEmpty()) {
                allReasons.add(customReason);
            }
            return allReasons.isEmpty() ? "No reason specified" : String.join(", ", allReasons);
        }
    }

    public static class ActiveBan {
        public String playerName;
        public String reason;
        public String staffMember;
        public long expiryTime;
        public boolean isPermanent;

        public ActiveBan(String playerName, String reason, String staffMember, long expiryTime) {
            this.playerName = playerName;
            this.reason = reason;
            this.staffMember = staffMember;
            this.expiryTime = expiryTime;
            this.isPermanent = expiryTime == 0;
        }

        public boolean isExpired() {
            return !isPermanent && System.currentTimeMillis() > expiryTime;
        }
    }

    public static class ActiveMute {
        public String playerName;
        public String reason;
        public String staffMember;
        public long expiryTime;
        public boolean isPermanent;

        public ActiveMute(String playerName, String reason, String staffMember, long expiryTime) {
            this.playerName = playerName;
            this.reason = reason;
            this.staffMember = staffMember;
            this.expiryTime = expiryTime;
            this.isPermanent = expiryTime == 0;
        }

        public boolean isExpired() {
            return !isPermanent && System.currentTimeMillis() > expiryTime;
        }
    }

    public static class ActiveIPBan {
        public String ipAddress;
        public String reason;
        public String staffMember;
        public long expiryTime;
        public boolean isPermanent;

        public ActiveIPBan(String ipAddress, String reason, String staffMember, long expiryTime) {
            this.ipAddress = ipAddress;
            this.reason = reason;
            this.staffMember = staffMember;
            this.expiryTime = expiryTime;
            this.isPermanent = expiryTime == 0;
        }

        public boolean isExpired() {
            return !isPermanent && System.currentTimeMillis() > expiryTime;
        }
    }


    // initialize
    private static void initializeDataSystem() {
        try {
            dataFolder = instance.getDataFolder();
            punishmentDataFolder = new File(dataFolder, "punishment_data");
            keyFile = new File(dataFolder, "security.key");

            if (!punishmentDataFolder.exists()) {
                punishmentDataFolder.mkdirs();
            }

            gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

            httpClient = HttpClient.newHttpClient();
            discordScheduler = Executors.newScheduledThreadPool(2);

            discordActive = getInstance().getConfig().getBoolean("discord.active", false);
            discordBotToken = getInstance().getConfig().getString("discord.bot-token", "");
            discordChannelId = getInstance().getConfig().getString("discord.channel-id", "");
            discordBotId = getInstance().getConfig().getString("discord.bot-id", "");

            if (!getInstance().getConfig().contains("discord.active")) {
                getInstance().getConfig().set("discord.active", false);
            }
            if (!getInstance().getConfig().contains("discord.bot-token")) {
                getInstance().getConfig().set("discord.bot-token", "YOUR_BOT_TOKEN_HERE");
            }
            if (!getInstance().getConfig().contains("discord.channel-id")) {
                getInstance().getConfig().set("discord.channel-id", "YOUR_CHANNEL_ID_HERE");
            }
            if (!getInstance().getConfig().contains("discord.bot-id")) {
                getInstance().getConfig().set("discord.bot-id", "YOUR_BOT_ID_HERE");
            }
            getInstance().saveConfig();

            if (discordActive) {
                if (discordBotToken.isEmpty() || discordBotToken.contains("YOUR_BOT_TOKEN_HERE") ||
                        discordChannelId.isEmpty() || discordChannelId.contains("YOUR_CHANNEL_ID_HERE")) {
                    logger.warning("Discord integration is enabled but bot token or channel ID is not configured!");
                    logger.warning("Please set valid bot token and channel ID in config.yml");
                    discordActive = false;
                } else {
                    logger.info("Discord integration is enabled and configured");
                    startDiscordMessagePolling();
                }
            } else {
                logger.info("Discord integration is disabled");
            }

            initializeEncryption();
            loadPunishmentData();
            loadPlayerIPs();


            logger.info("Punishment logging system initialized successfully!");

        } catch (Exception e) {
            logger.severe("Failed to initialize punishment logging system: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void initializePunishmentSystem() {
        experimentalPunishSystem = getConfig().getBoolean("BuiltinSystems.EnforcementModule", false);

        if (experimentalPunishSystem && !hasExistingPunishmentPlugins()) {
            builtInSystemActive = true;
            logger.info("Built-in punishment system activated!");

            loadActivePunishments();

            startPunishmentCleanupTask();

            registerBuiltInCommands();

            startPunishmentStatusValidation();

            getServer().getPluginManager().registerEvents(new MuteListener(), this);

        } else if (experimentalPunishSystem) {
            logger.info("Experimental punishment system is enabled but existing punishment plugins detected. Built-in system disabled.");
        }
    }

    private void startPunishmentCleanupTask() {
        FoliaUtils.runAsyncTimer(this, () -> {
            cleanupExpiredPunishments();
        }, 20L, 20L * 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void cleanupExpiredPunishments() {
        try {
            boolean hasChanges = false;

            int expiredBans = 0;
            for (String key : new ArrayList<>(activeBans.keySet())) {
                if (activeBans.get(key).isExpired()) {
                    activeBans.remove(key);
                    expiredBans++;
                    hasChanges = true;
                }
            }
            if (expiredBans > 0) {
                logger.info("Cleaned up " + expiredBans + " expired bans");
            }

            int expiredMutes = 0;
            for (String key : new ArrayList<>(activeMutes.keySet())) {
                if (activeMutes.get(key).isExpired()) {
                    activeMutes.remove(key);
                    expiredMutes++;
                    hasChanges = true;
                }
            }
            if (expiredMutes > 0) {
                logger.info("Cleaned up " + expiredMutes + " expired mutes");
            }

            int expiredIPBans = 0;
            for (String key : new ArrayList<>(activeIPBans.keySet())) {
                if (activeIPBans.get(key).isExpired()) {
                    activeIPBans.remove(key);
                    expiredIPBans++;
                    hasChanges = true;
                }
            }
            if (expiredIPBans > 0) {
                logger.info("Cleaned up " + expiredIPBans + " expired IP bans");
            }

            if (hasChanges) {
                saveActivePunishments();
            }

        } catch (Exception e) {
            logger.warning("Error cleaning up expired punishments: " + e.getMessage());
        }
    }


    private void registerBuiltInCommands() {
        if (!builtInSystemActive) return;
        BuiltInCommands commandHandler = new BuiltInCommands();

        getCommand("ban").setExecutor(commandHandler);
        getCommand("tempban").setExecutor(commandHandler);
        getCommand("unban").setExecutor(commandHandler);
        getCommand("pardon").setExecutor(commandHandler);
        getCommand("mute").setExecutor(commandHandler);
        getCommand("tempmute").setExecutor(commandHandler);
        getCommand("unmute").setExecutor(commandHandler);
        getCommand("kick").setExecutor(commandHandler);
        getCommand("banip").setExecutor(commandHandler);
        getCommand("ban-ip").setExecutor(commandHandler);
        getCommand("unbanip").setExecutor(commandHandler);
        getCommand("pardon-ip").setExecutor(commandHandler);
        getCommand("banlist").setExecutor(commandHandler);
        getCommand("mutelist").setExecutor(commandHandler);

        logger.info("Registered built-in punishment commands");
    }

    public static void saveActivePunishments() {
        if (!builtInSystemActive) return;

        try {
            File bansFile = new File(dataFolder, "active_bans.dat");
            File mutesFile = new File(dataFolder, "active_mutes.dat");
            File ipBansFile = new File(dataFolder, "active_ip_bans.dat");

            String bansJson = gson.toJson(activeBans);
            String encryptedBans = encryptData(bansJson);
            Files.writeString(bansFile.toPath(), encryptedBans);

            String mutesJson = gson.toJson(activeMutes);
            String encryptedMutes = encryptData(mutesJson);
            Files.writeString(mutesFile.toPath(), encryptedMutes);

            String ipBansJson = gson.toJson(activeIPBans);
            String encryptedIPBans = encryptData(ipBansJson);
            Files.writeString(ipBansFile.toPath(), encryptedIPBans);

        } catch (Exception e) {
            getPluginLogger().warning("Error saving active punishments: " + e.getMessage());
        }
    }

    private boolean hasExistingPunishmentPlugins() {
        return getServer().getPluginManager().getPlugin("EssentialsX") != null ||
                getServer().getPluginManager().getPlugin("Essentials") != null ||
                getServer().getPluginManager().getPlugin("LiteBans") != null ||
                getServer().getPluginManager().getPlugin("AdvancedBan") != null ||
                getServer().getPluginManager().getPlugin("BanManager") != null;
    }

    private void loadActivePunishments() {
        try {
            File bansFile = new File(dataFolder, "active_bans.dat");
            File mutesFile = new File(dataFolder, "active_mutes.dat");
            File ipBansFile = new File(dataFolder, "active_ip_bans.dat");

            if (bansFile.exists()) {
                String encryptedContent = Files.readString(bansFile.toPath());
                String decryptedContent = decryptData(encryptedContent);
                if (decryptedContent != null) {
                    TypeToken<Map<String, ActiveBan>> token = new TypeToken<Map<String, ActiveBan>>() {};
                    Map<String, ActiveBan> loadedBans = gson.fromJson(decryptedContent, token.getType());
                    if (loadedBans != null) {
                        activeBans.putAll(loadedBans);
                    }
                }
            }

            if (mutesFile.exists()) {
                String encryptedContent = Files.readString(mutesFile.toPath());
                String decryptedContent = decryptData(encryptedContent);
                if (decryptedContent != null) {
                    TypeToken<Map<String, ActiveMute>> token = new TypeToken<Map<String, ActiveMute>>() {};
                    Map<String, ActiveMute> loadedMutes = gson.fromJson(decryptedContent, token.getType());
                    if (loadedMutes != null) {
                        activeMutes.putAll(loadedMutes);
                    }
                }
            }

            if (ipBansFile.exists()) {
                String encryptedContent = Files.readString(ipBansFile.toPath());
                String decryptedContent = decryptData(encryptedContent);
                if (decryptedContent != null) {
                    TypeToken<Map<String, ActiveIPBan>> token = new TypeToken<Map<String, ActiveIPBan>>() {};
                    Map<String, ActiveIPBan> loadedIPBans = gson.fromJson(decryptedContent, token.getType());
                    if (loadedIPBans != null) {
                        activeIPBans.putAll(loadedIPBans);
                    }
                }
            }

            logger.info("Loaded " + activeBans.size() + " active bans, " +
                    activeMutes.size() + " active mutes, and " +
                    activeIPBans.size() + " active IP bans");

        } catch (Exception e) {
            logger.warning("Error loading active punishments: " + e.getMessage());
        }
    }

    // Loaders
    public static void loadPunishmentData() {
        try {
            if (!punishmentDataFolder.exists()) {
                return;
            }

            File[] playerFiles = punishmentDataFolder.listFiles((dir, name) -> name.endsWith(".dat"));
            if (playerFiles == null || playerFiles.length == 0) {
                return;
            }

            int successCount = 0;
            int failCount = 0;
            int corruptedCount = 0;

            for (File playerFile : playerFiles) {
                try {
                    if (!playerFile.exists() || !playerFile.canRead()) {
                        failCount++;
                        continue;
                    }

                    String encryptedContent = Files.readString(playerFile.toPath());
                    if (encryptedContent == null || encryptedContent.trim().isEmpty()) {
                        failCount++;
                        continue;
                    }

                    String decryptedContent = decryptData(encryptedContent);
                    if (decryptedContent == null || decryptedContent.trim().isEmpty()) {
                        failCount++;
                        continue;
                    }

                    String trimmedContent = decryptedContent.trim();
                    if (!trimmedContent.startsWith("[") && !trimmedContent.startsWith("{")) {
                        String playerName = playerFile.getName().replace(".dat", "");
                        logger.warning("Corrupted punishment file detected: " + playerFile.getName() +
                                " - contains player name '" + trimmedContent + "' instead of punishment data");

                        File backupFile = new File(playerFile.getParentFile(), playerFile.getName() + ".corrupted_backup");
                        try {
                            Files.copy(playerFile.toPath(), backupFile.toPath());
                            logger.info("Created backup of corrupted file: " + backupFile.getName());
                        } catch (Exception backupError) {
                            logger.warning("Failed to create backup of corrupted file: " + backupError.getMessage());
                        }

                        String playerKey = playerName.toLowerCase();
                        punishmentHistory.put(playerKey, new ArrayList<>());

                        try {
                            playerFile.delete();
                            savePunishmentRecord(playerKey, null);
                            logger.info("Recreated empty punishment file for player: " + playerName);
                            corruptedCount++;
                        } catch (Exception recreateError) {
                            logger.warning("Failed to recreate file for " + playerName + ": " + recreateError.getMessage());
                            failCount++;
                        }
                        continue;
                    }

                    if (!trimmedContent.startsWith("[")) {
                        logger.warning("Invalid JSON structure in file " + playerFile.getName() +
                                " - expected array but found: " + trimmedContent.substring(0, Math.min(50, trimmedContent.length())));
                        failCount++;
                        continue;
                    }

                    TypeToken<List<PunishmentRecord>> token = new TypeToken<List<PunishmentRecord>>() {};
                    List<PunishmentRecord> records = gson.fromJson(decryptedContent, token.getType());

                    if (records == null) {
                        logger.warning("Failed to parse JSON for file " + playerFile.getName());
                        failCount++;
                        continue;
                    }

                    String playerName = playerFile.getName().replace(".dat", "");
                    String playerKey = playerName.toLowerCase();

                    punishmentHistory.put(playerKey, records);
                    successCount++;

                } catch (com.google.gson.JsonSyntaxException e) {
                    String playerName = playerFile.getName().replace(".dat", "");
                    logger.warning("JSON syntax error in file " + playerFile.getName() + ": " + e.getMessage());

                    try {
                        String rawContent = Files.readString(playerFile.toPath());
                        String decrypted = decryptData(rawContent);
                        logger.warning("File content after decryption: '" + (decrypted != null ? decrypted : "null") + "'");

                        if (decrypted != null && !decrypted.trim().startsWith("[") && !decrypted.trim().startsWith("{")) {
                            File backupFile = new File(playerFile.getParentFile(), playerFile.getName() + ".corrupted_backup");
                            Files.copy(playerFile.toPath(), backupFile.toPath());

                            String playerKey = playerName.toLowerCase();
                            punishmentHistory.put(playerKey, new ArrayList<>());
                            playerFile.delete();
                            savePunishmentRecord(playerKey, null);

                            logger.info("Fixed corrupted file for player: " + playerName);
                            corruptedCount++;
                            continue;
                        }
                    } catch (Exception readError) {
                        logger.warning("Could not read corrupted file content: " + readError.getMessage());
                    }

                    failCount++;
                } catch (Exception e) {
                    logger.warning("Error loading punishment data from " + playerFile.getName() + ": " + e.getMessage());
                    failCount++;
                }
            }

            if (successCount > 0) {
            }
            if (corruptedCount > 0) {
                logger.info("Fixed " + corruptedCount + " corrupted files (recreated with empty punishment history)");
            }
            if (failCount > 0) {
                logger.warning("Failed to load data for " + failCount + " files (corrupted or invalid format)");
            }

            if (punishmentHistory.size() > 0) {
                punishmentHistory.keySet().stream().limit(10).forEach(player -> {
                    int recordCount = punishmentHistory.get(player).size();
                });
            }

        } catch (Exception e) {
            logger.severe("Critical error in loadPunishmentData: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void loadReasonDurations() {
        FileConfiguration config = getInstance().getConfig();

        for (String reason : banReasons) {
            String path = "ban-reason-durations." + reason.replace(" ", "_").replace("/", "_");
            if (!config.contains(path)) {
                config.set(path, "3d");
            }
            banReasonDurations.put(reason, config.getString(path));
        }

        Map<String, String> muteDefaults = new HashMap<>();
        muteDefaults.put("Fly Hacks", "3d");
        muteDefaults.put("Speed Hacks", "2d");
        muteDefaults.put("Speed Bridge", "1d");
        muteDefaults.put("No Fall Damage", "1d");
        muteDefaults.put("Kill Aura/Mob Aura", "5d");
        muteDefaults.put("Nuking", "7d");
        muteDefaults.put("Duping", "5d");
        muteDefaults.put("TP Abusing", "3d");
        muteDefaults.put("Xray", "5d");
        muteDefaults.put("Path Finding", "3d");
        muteDefaults.put("BOT", "7d");
        muteDefaults.put("Auto Bucket", "2d");
        muteDefaults.put("Delay Package", "1d");
        muteDefaults.put("Griefing", "3d");
        muteDefaults.put("Inappropriate Behavior", "6h");
        muteDefaults.put("Spam", "30m");
        muteDefaults.put("Advertising", "2d");

        for (String reason : banReasons) {
            String path = "mute-reason-durations." + reason.replace(" ", "_").replace("/", "_");
            if (!config.contains(path)) {
                config.set(path, muteDefaults.getOrDefault(reason, "1d"));
            }
            muteReasonDurations.put(reason, config.getString(path));
        }

        getInstance().saveConfig();
    }
    public static void loadPlayerIPs() {

        try {
            File ipFile = new File(dataFolder, "player_ips.dat");
            if (!ipFile.exists()) {
                return;
            }

            String encryptedContent = Files.readString(ipFile.toPath());
            if (encryptedContent == null || encryptedContent.trim().isEmpty()) {
                return;
            }

            String decryptedContent = decryptData(encryptedContent);
            if (decryptedContent == null || decryptedContent.trim().isEmpty()) {
                return;
            }

            TypeToken<Map<String, String>> token = new TypeToken<Map<String, String>>() {};
            Map<String, String> loadedIPs = gson.fromJson(decryptedContent, token.getType());

            if (loadedIPs != null) {
                for (Map.Entry<String, String> entry : loadedIPs.entrySet()) {
                    playerIPs.put(entry.getKey().toLowerCase(), entry.getValue());
                }
            } else {
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void validateAndUpdatePunishmentStatus() {
        try {
            boolean hasChanges = false;
            int updatedRecords = 0;

            for (Map.Entry<String, List<PunishmentRecord>> entry : punishmentHistory.entrySet()) {
                String playerKey = entry.getKey();
                List<PunishmentRecord> records = entry.getValue();
                boolean playerRecordsChanged = false;

                for (PunishmentRecord record : records) {
                    if (!record.active) continue;

                    boolean shouldStillBeActive = false;

                    switch (record.punishmentType) {
                        case "BAN":
                        case "TEMPBAN":
                            shouldStillBeActive = isPlayerCurrentlyBannedAnySystem(record.playerName);
                            break;
                        case "MUTE":
                            shouldStillBeActive = isPlayerCurrentlyMutedAnySystem(record.playerName);
                            break;
                        case "KICK":
                            shouldStillBeActive = false;
                            break;
                        case "UNBAN":
                        case "UNMUTE":
                            shouldStillBeActive = false;
                            break;
                    }

                    if (!shouldStillBeActive && record.active) {
                        record.active = false;
                        playerRecordsChanged = true;
                        updatedRecords++;

                        logger.info("Marked punishment as expired: " + record.punishmentType +
                                " for " + record.playerName + " (Reason: " + record.reason + ")");
                    }
                }

                if (playerRecordsChanged) {
                    try {
                        String sanitizedKey = sanitizeFileName(playerKey);
                        String jsonContent = gson.toJson(records);
                        String encryptedContent = encryptData(jsonContent);

                        File playerFile = new File(punishmentDataFolder, sanitizedKey + ".dat");
                        Files.writeString(playerFile.toPath(), encryptedContent);
                        hasChanges = true;
                    } catch (Exception e) {
                        logger.warning("Failed to save updated punishment records for " + playerKey + ": " + e.getMessage());
                    }
                }
            }

            if (hasChanges) {
                logger.info("Updated " + updatedRecords + " expired punishment records to inactive status");
            }

        } catch (Exception e) {
            logger.warning("Error validating punishment status: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startPunishmentStatusValidation() {
        FoliaUtils.runAsyncTimer(this, () -> {
            validateAndUpdatePunishmentStatus();
        }, 20L * 60 * 5, 20L * 60 * 5, java.util.concurrent.TimeUnit.SECONDS);

        logger.info("Started punishment status validation task (runs every 5 minutes)");
    }

    public static void savePunishmentRecord(String playerKey, PunishmentRecord record) {
        try {

            if (playerKey == null || playerKey.trim().isEmpty()) {
                getPluginLogger().warning("Cannot save punishment record - playerKey is null or empty");
                return;
            }

            String sanitizedKey = sanitizeFileName(playerKey);
            String normalizedPlayerKey = playerKey.toLowerCase();

            List<PunishmentRecord> records = punishmentHistory.get(normalizedPlayerKey);
            if (records == null) {
                records = new ArrayList<>();
                punishmentHistory.put(normalizedPlayerKey, records);
                getPluginLogger().info("Created new punishment history for player: " + playerKey);
            }

            if (record != null) {
                records.add(record);
                getPluginLogger().info("Added " + record.punishmentType + " record for " + playerKey);
            }

            if (records == null) {
                getPluginLogger().severe("CRITICAL ERROR: Records list is null for " + playerKey + " - creating empty list");
                records = new ArrayList<>();
                punishmentHistory.put(normalizedPlayerKey, records);
            }

            String jsonContent;
            try {
                jsonContent = gson.toJson(records);

                if (jsonContent == null || jsonContent.trim().isEmpty()) {
                    getPluginLogger().severe("ERROR: Gson produced null/empty JSON for " + playerKey);
                    return;
                }

                if (!jsonContent.trim().startsWith("[")) {
                    getPluginLogger().severe("ERROR: Gson produced invalid JSON structure for " + playerKey);
                    getPluginLogger().severe("Expected JSON array but got: " + jsonContent.substring(0, Math.min(100, jsonContent.length())));
                    getPluginLogger().severe("Records object: " + records.toString());
                    return;
                }

            } catch (Exception jsonError) {
                getPluginLogger().severe("ERROR: Failed to serialize records to JSON for " + playerKey + ": " + jsonError.getMessage());
                jsonError.printStackTrace();
                return;
            }

            String encryptedContent;
            try {
                encryptedContent = encryptData(jsonContent);

                if (encryptedContent == null || encryptedContent.trim().isEmpty()) {
                    getPluginLogger().severe("ERROR: Encryption failed for " + playerKey);
                    return;
                }

            } catch (Exception encryptError) {
                getPluginLogger().severe("ERROR: Failed to encrypt data for " + playerKey + ": " + encryptError.getMessage());
                encryptError.printStackTrace();
                return;
            }

            File playerFile = new File(punishmentDataFolder, sanitizedKey + ".dat");
            File tempFile = new File(punishmentDataFolder, sanitizedKey + ".tmp");

            try {
                Files.writeString(tempFile.toPath(), encryptedContent);

                String verifyContent = Files.readString(tempFile.toPath());
                String verifyDecrypted = decryptData(verifyContent);

                if (verifyDecrypted == null || !verifyDecrypted.trim().startsWith("[")) {
                    getPluginLogger().severe("ERROR: Verification failed for temp file " + tempFile.getName());
                    tempFile.delete();
                    return;
                }

                if (playerFile.exists()) {
                    File backupFile = new File(punishmentDataFolder, sanitizedKey + ".bak");
                    Files.move(playerFile.toPath(), backupFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }

                Files.move(tempFile.toPath(), playerFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                if (record != null) {
                    getPluginLogger().info("Successfully saved punishment record for " + playerKey + " to " + playerFile.getName());
                }

            } catch (Exception writeError) {
                getPluginLogger().severe("ERROR: Failed to write file for " + playerKey + ": " + writeError.getMessage());
                writeError.printStackTrace();

                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }

        } catch (Exception e) {
            getPluginLogger().severe("CRITICAL ERROR in savePunishmentRecord for " + playerKey + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    public static void recoverFromBackups() {
        try {
            File[] backupFiles = punishmentDataFolder.listFiles((dir, name) -> name.endsWith(".bak"));
            if (backupFiles == null || backupFiles.length == 0) {
                return;
            }

            int recoveredCount = 0;

            for (File backupFile : backupFiles) {
                String playerKey = backupFile.getName().replace(".bak", "");
                File originalFile = new File(punishmentDataFolder, playerKey + ".dat");

                if (!originalFile.exists() || originalFile.length() == 0) {
                    try {
                        Files.copy(backupFile.toPath(), originalFile.toPath());
                        logger.info("Recovered data from backup for: " + playerKey);
                        recoveredCount++;
                    } catch (Exception recoverError) {
                        logger.warning("Failed to recover from backup " + backupFile.getName() + ": " + recoverError.getMessage());
                    }
                }
            }

            if (recoveredCount > 0) {
                logger.info("Recovered " + recoveredCount + " files from backups");
            }

        } catch (Exception e) {
            logger.warning("Error during backup recovery: " + e.getMessage());
        }
    }

    public static String sanitizeFileName(String fileName) {
        if (fileName == null) return "unknown";

        return fileName.toLowerCase()
                .replaceAll("[^a-z0-9._-]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
    }

    public static void savePlayerIPs() {
        try {
            File ipFile = new File(dataFolder, "player_ips.dat");
            String jsonData = gson.toJson(playerIPs);
            String encryptedData = encryptData(jsonData);
            Files.writeString(ipFile.toPath(), encryptedData);
        } catch (Exception e) {
        }
    }

    public static void reloadPunishmentData(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Reloading punishment data...");

        punishmentHistory.clear();
        playerIPs.clear();

        loadPunishmentData();
        loadPlayerIPs();

        player.sendMessage(ChatColor.GREEN + "Punishment data reloaded!");
        player.sendMessage(ChatColor.GRAY + "Players in history: " + punishmentHistory.size());
        player.sendMessage(ChatColor.GRAY + "Player IPs: " + playerIPs.size());

        if (punishmentHistory.size() > 0) {
            String examples = punishmentHistory.keySet().stream()
                    .limit(5)
                    .collect(Collectors.joining(", "));
            player.sendMessage(ChatColor.GRAY + "Example players: " + examples);
        }
    }

    public static String cleanIP(String ip) {
        if (ip == null) return "";

        int colonIndex = ip.indexOf(":");
        if (colonIndex != -1) {
            return ip.substring(0, colonIndex);
        }

        return ip;
    }

    public static boolean isValidPlayer(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }

        String bedrockPrefix = getInstance().getConfig().getString("bedrock-prefix", ".");
        String coreName = playerName;

        if (bedrockPrefix != null && !bedrockPrefix.isEmpty() && playerName.startsWith(bedrockPrefix)) {
            coreName = playerName.substring(bedrockPrefix.length());
        }

        if (coreName.length() < 3 || coreName.length() > 16) {
            return false;
        }

        if (!coreName.matches("^[a-zA-Z0-9_]+$")) {
            return false;
        }

        String lower = coreName.toLowerCase();
        if (lower.equals("test") || lower.equals("player") || lower.equals("admin") ||
                lower.equals("user") || lower.equals("example")) {
            return false;
        }

        return true;
    }

    // Enable, Disable, Join
    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);

        // Enable other Scripts
        CommandHandler commandHandler = new CommandHandler();
        Log logger2 = new Log();
        FoliaUtils foliautils = new FoliaUtils();
        Debug debug = new Debug();
        Gui gui = new Gui();
        Punishments punishments = new Punishments();
        Discord discord = new Discord();
        Bancheckers bancheckers = new Bancheckers();
        Handler handler = new Handler();
        HistoryGui historyGui = new HistoryGui();
        logger = getLogger();

        CommandsEvents commandEvents = new CommandsEvents();
        getServer().getPluginManager().registerEvents(commandEvents, this);
        getServer().getPluginManager().registerEvents(new Gui(), this);

        getCommand("punish").setExecutor(commandEvents);

        saveDefaultConfig();
        loadBanReasons();
        loadReasonDurations();

        if (!getConfig().contains("BuiltinSystems.EnforcementModule")) {
            getConfig().set("BuiltinSystems.EnforcementModule", false);
            saveConfig();
        }

        initializeDataSystem();

        initializeDiscordAsync();

        initializePunishmentSystem();


        logger.info("Punishment GUI Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        shutdownDiscordIntegration();
        chatInputWaiting.clear();
        activeSessions.clear();
        historyPages.clear();
        lastDiscordMessageTime.clear();
        if (discordScheduler != null && !discordScheduler.isShutdown()) {
            discordScheduler.shutdown();
            try {
                if (!discordScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    discordScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                discordScheduler.shutdownNow();
            }
        }

        shutdownDiscordSystem();

        if (builtInSystemActive) {
            saveActivePunishments();
            logger.info("Saved active punishments data");
        }
        logger.info("System Stopped. Discord bot offline");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String rawIP = player.getAddress().getAddress().getHostAddress();
        String currentIP = cleanIP(rawIP);


        String playerKey = playerName.toLowerCase();
        playerIPs.put(playerKey, currentIP);

        getPluginLogger().info("PLAYER JOIN: Stored IP mapping - '" + playerKey + "' -> '" + currentIP + "'");

        savePlayerIPs();

        checkBanEvasion(player, currentIP);
    }
    // Alerters
    public static void alertStaffBanEvasion(String playerName, String ip, List<String> suspiciousPlayers) {
        if (Bukkit.getPlayer(playerName).hasPermission("punishmentsystem.warnbypass")) { return; }

        String alertMessage = ChatColor.RED + "⚠  POSSIBLE BAN EVASION DETECTED ⚠ \n" +
                ChatColor.YELLOW + "Player: " + ChatColor.WHITE + playerName + "\n" +
                ChatColor.YELLOW + "Matches banned players: " + ChatColor.RED +
                String.join(", ", suspiciousPlayers);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("punishmentsystem.warn")) {
                staff.sendMessage(alertMessage);
            }
        }
        String reasonString = "Ban Evader. Data Matches "+suspiciousPlayers+" user.";
        logPunishment(playerName, "BanEvading", reasonString, "View " + suspiciousPlayers +" For the length", "Server");

        logger.warning("BAN EVASION ALERT: " + playerName + " matches banned players: " +
                String.join(", ", suspiciousPlayers));
    }

    // Formats
    public static long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) return 0;

        try {
            String unit = duration.substring(duration.length() - 1).toLowerCase();
            long value = Long.parseLong(duration.substring(0, duration.length() - 1));

            switch (unit) {
                case "s": return value / 60;
                case "mins": return value;
                case "h": return value * 60;
                case "d": return value * 60 * 24;
                case "w": return value * 60  * 24 * 7;
                case "months": return value * 60 * 24 * 30;
                case "y": return value * 60 * 24 * 365;
                default: return value;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    public static String formatDuration(long totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + "m";
        } else if (totalMinutes < 60 * 24) {
            long hours = totalMinutes / 60;
            long remainingMinutes = totalMinutes % 60;
            return remainingMinutes > 0 ? hours + "h" + remainingMinutes + "m" : hours + "h";
        } else {
            long days = totalMinutes / (60 * 24);
            long remainingHours = (totalMinutes % (60 * 24)) / 60;
            long remainingMinutes = totalMinutes % 60;

            StringBuilder result = new StringBuilder();
            result.append(days).append("d");
            if (remainingHours > 0) result.append(remainingHours).append("h");
            if (remainingMinutes > 0) result.append(remainingMinutes).append("m");

            return result.toString();
        }
    }
}

