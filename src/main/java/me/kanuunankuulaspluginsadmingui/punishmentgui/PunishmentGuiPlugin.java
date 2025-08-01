package me.kanuunankuulaspluginsadmingui.punishmentgui;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import me.kanuunankuulaspluginsadmingui.punishmentgui.Checkers.*;
import me.kanuunankuulaspluginsadmingui.punishmentgui.EventListeners.*;
import me.kanuunankuulaspluginsadmingui.punishmentgui.Updater.UpdateChecker;
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
    public static boolean isPlayerCurrentlyBanned(String playerName) {
        try {
            BanList banList = Bukkit.getBanList(BanList.Type.NAME);
            if (banList.isBanned(playerName)) {
                return true;
            }

            if (checkEssentialsBan(playerName)) {
                return true;
            }

            if (checkLiteBan(playerName)) {
                return true;
            }

            if (checkAdvancedBan(playerName)) {
                return true;
            }


        } catch (Exception e) {
            logger.warning("Error checking ban status for " + playerName + ": " + e.getMessage());
            return false;
        }
        return false;
    }
    public static boolean discordEnabled = false;
    public static volatile boolean discordInitializing = false;
    public static volatile boolean discordPollingActive = false;

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
    public static Map<String, String> pendingInteractions = new ConcurrentHashMap<>();
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
    public static String discordPublicKey;
    public static JDA jda;
    public static DiscordEventListener discordListener;
    public static Thread discordPollingThread;
    public static final Object discordLock = new Object();
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

    // Encryption parts, LEAVE THESE IN HERE IF YOU MODIFY THE CODE.
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

                    TypeToken<List<PunishmentRecord>> token = new TypeToken<List<PunishmentRecord>>() {};
                    List<PunishmentRecord> records = gson.fromJson(decryptedContent, token.getType());

                    if (records == null) {
                        failCount++;
                        continue;
                    }

                    String playerName = playerFile.getName().replace(".dat", "");
                    String playerKey = playerName.toLowerCase();

                    punishmentHistory.put(playerKey, records);
                    successCount++;

                } catch (Exception e) {
                    e.printStackTrace();
                    failCount++;
                }
            }

            if (punishmentHistory.size() > 0) {
                punishmentHistory.keySet().stream().limit(10).forEach(player -> {
                    int recordCount = punishmentHistory.get(player).size();
                });
            }

        } catch (Exception e) {
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
    public static void loadDiscordConfig() {

        getInstance().saveConfig();
    }

    // Savers
    public static void savePunishmentRecord(String playerKey, PunishmentRecord record) {
        try {
            List<PunishmentRecord> records = punishmentHistory.get(playerKey);
            if (records == null) {
                records = new ArrayList<>();
                punishmentHistory.put(playerKey, records);
            }

            String jsonContent = gson.toJson(records);
            String encryptedContent = encryptData(jsonContent);

            File playerFile = new File(punishmentDataFolder, playerKey + ".dat");
            Files.writeString(playerFile.toPath(), encryptedContent);


        } catch (Exception e) {
            e.printStackTrace();
        }
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

    // Reloaders
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

    // Ip checkers
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
        logger = instance.getLogger();
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

        CommandsEvents commandEvents = new CommandsEvents();
        getServer().getPluginManager().registerEvents(commandEvents, this);
        getServer().getPluginManager().registerEvents(new Gui(), this);

        getCommand("punish").setExecutor(commandEvents);

        // Alright the rest now
        saveDefaultConfig();
        loadBanReasons();
        loadReasonDurations();
        initializeDataSystem();

        String serverName = getInstance().getConfig().getString("Server-name", "Server");
        initializeDiscordAsync();

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

        if (jda != null) {
            jda.shutdown();
        }

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String currentIP = cleanIP(player.getAddress().getAddress().getHostAddress());

        playerIPs.put(playerName.toLowerCase(), currentIP);
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
        String reasonString = "Ban evader. Data Matches "+suspiciousPlayers+" user.";
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
                case "mins": return value;
                case "h": return value * 60;
                case "d": return value * 60 * 24;
                case "w": return value * 60 * 24 * 7;
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

