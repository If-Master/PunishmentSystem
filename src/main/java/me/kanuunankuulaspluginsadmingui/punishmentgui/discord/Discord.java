package me.kanuunankuulaspluginsadmingui.punishmentgui.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.kanuunankuulaspluginsadmingui.punishmentgui.Checkers.Bancheckers;
import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.FoliaUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.FoliaUtils.*;

public class Discord {
    public static void reloadDiscordConfig() {
        discordActive = getInstance().getConfig().getBoolean("discord.active", false);
        discordBotToken = getInstance().getConfig().getString("discord.bot-token", "");
        discordChannelId = getInstance().getConfig().getString("discord.channel-id", "");
        discordBotId = getInstance().getConfig().getString("discord.bot-id", "");

        if (discordActive) {
            if (discordBotToken.isEmpty() || discordBotToken.contains("YOUR_BOT_TOKEN_HERE") ||
                    discordChannelId.isEmpty() || discordChannelId.contains("YOUR_CHANNEL_ID_HERE")) {
                getPluginLogger().warning("Discord integration is enabled but bot token or channel ID is not configured!");
                discordActive = false;
            } else {
                getPluginLogger().info("Discord integration reloaded and active");
                if (discordScheduler == null || discordScheduler.isShutdown()) {
                    discordScheduler = Executors.newScheduledThreadPool(2);
                }
                startDiscordMessagePolling();
            }
        } else {
            getPluginLogger().info("Discord integration is disabled");
            if (discordScheduler != null && !discordScheduler.isShutdown()) {
                discordScheduler.shutdown();
            }
        }
    }
    public static void testDiscordConnection(Player player) {
        if (!discordActive) {
            player.sendMessage(ChatColor.RED + "Discord integration is not enabled!");
            return;
        }

        if (discordBotToken.isEmpty() || discordBotToken.contains("YOUR_BOT_TOKEN_HERE") ||
                discordChannelId.isEmpty() || discordChannelId.contains("YOUR_CHANNEL_ID_HERE")) {
            player.sendMessage(ChatColor.RED + "Discord integration is enabled but not properly configured!");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Testing Discord connection...");

        runAsync(getInstance(), () -> {
            try {
                HttpResponse<String> response = sendDiscordRequest("GET", "channels/" + discordChannelId, null);
                if (response != null && response.statusCode() == 200) {
                    executeWithPlayer(getInstance(), player, p ->
                            p.sendMessage(ChatColor.GREEN + "Discord connection successful!")
                    );
                } else {
                    executeWithPlayer(getInstance(), player, p ->
                            p.sendMessage(ChatColor.RED + "Discord connection failed! Status: " +
                                    (response != null ? response.statusCode() : "No response"))
                    );
                }
            } catch (Exception e) {
                executeWithPlayer(getInstance(), player, p ->
                        p.sendMessage(ChatColor.RED + "Discord connection test failed: " + e.getMessage())
                );
            }
        });
    }

    public static class DiscordComponent {
        public int type;
        public String custom_id;
        public String label;
        public int style;
        public boolean disabled = false;
        public String url;

        public DiscordComponent(int type, String custom_id, String label, int style) {
            this.type = type;
            this.custom_id = custom_id;
            this.label = label;
            this.style = style;
        }

        public DiscordComponent(int type, String custom_id, String label, int style, boolean disabled) {
            this.type = type;
            this.custom_id = custom_id;
            this.label = label;
            this.style = style;
            this.disabled = disabled;
        }
    }
    public static class DiscordActionRow {
        public int type = 1;
        public DiscordComponent[] components;

        public DiscordActionRow(DiscordComponent[] components) {
            this.components = components;
        }
    }
    public static class DiscordMessageWithComponents {
        public String content;
        public PunishmentGuiPlugin.DiscordEmbed[] embeds;
        public DiscordActionRow[] components;

        public DiscordMessageWithComponents(String content, PunishmentGuiPlugin.DiscordEmbed[] embeds, DiscordActionRow[] components) {
            this.content = content;
            this.embeds = embeds;
            this.components = components;
        }
    }
    public static class DiscordInteraction {
        public String id;
        public String token;
        public int type;
        public DiscordInteractionData data;
        public DiscordUser user;
        public String channel_id;
        public String guild_id;
        public DiscordMessage message;
    }
    public static class DiscordInteractionData {
        public String custom_id;
        public int component_type;
    }
    public static class DiscordUser {
        public String id;
        public String username;
        public String discriminator;
        public boolean bot;
    }
    public static class DiscordMessage {
        public String id;
        public String channel_id;
        public DiscordUser author;
        public String content;
        public String timestamp;
        public PunishmentGuiPlugin.DiscordEmbed[] embeds;
        public DiscordActionRow[] components;
    }
    public static class DiscordInteractionResponse {
        public int type;
        public DiscordInteractionCallbackData data;

        public DiscordInteractionResponse(int type, DiscordInteractionCallbackData data) {
            this.type = type;
            this.data = data;
        }
    }
    public static class DiscordInteractionCallbackData {
        public String content;
        public PunishmentGuiPlugin.DiscordEmbed[] embeds;
        public DiscordActionRow[] components;
        public int flags;

        public DiscordInteractionCallbackData(String content, PunishmentGuiPlugin.DiscordEmbed[] embeds, DiscordActionRow[] components) {
            this.content = content;
            this.embeds = embeds;
            this.components = components;
        }
    }
    public static class DiscordPresence {
        public int since;
        public DiscordActivity[] activities;
        public String status;
        public boolean afk;

        public DiscordPresence(DiscordActivity[] activities, String status) {
            this.since = Integer.parseInt(null);
            this.activities = activities;
            this.status = status;
            this.afk = false;
        }
    }
    public static class DiscordActivity {
        public String name;
        public int type;
        public String state;

        public DiscordActivity(String name, int type, String state) {
            this.name = name;
            this.type = type;
            this.state = state;
        }
    }
    public static class HistorySession {
        public String playerName;
        public String authorId;
        public String messageId;
        public String category;
        public int page;
        public List<PunishmentGuiPlugin.PunishmentRecord> allRecords;
        public long createdAt;
        public String sessionId;
        public String interactionToken;

        public HistorySession(String playerName, String authorId, String category, List<PunishmentGuiPlugin.PunishmentRecord> records, String sessionId) {
            this.playerName = playerName;
            this.authorId = authorId;
            this.category = category;
            this.allRecords = records;
            this.page = 0;
            this.createdAt = System.currentTimeMillis();
            this.sessionId = sessionId;
        }
    }

    public static boolean botStatusSet = false;

    public static void initializeDiscordIntegration() {
        if (!discordActive) {
            getPluginLogger().info("Discord integration is disabled");
            return;
        }

        if (discordBotToken.isEmpty() || discordBotToken.contains("YOUR_BOT_TOKEN_HERE") ||
                discordChannelId.isEmpty() || discordChannelId.contains("YOUR_CHANNEL_ID_HERE")) {
            getPluginLogger().warning("Discord integration is enabled but bot token or channel ID is not properly configured!");
            return;
        }

        setBotStatus();

        startDiscordMessagePolling();



        getPluginLogger().info("Discord integration initialized successfully");
    }

    public static void shutdownDiscordIntegration() {
        if (jda != null) {
            try {
                getPluginLogger().info("Shutting down existing Discord integration...");

                if (discordListener != null) {
                    jda.removeEventListener(discordListener);
                }

                jda.shutdown();

                if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                    getPluginLogger().warning("JDA shutdown timed out, forcing shutdown...");
                    jda.shutdownNow();
                }

                getPluginLogger().info("Discord integration shut down successfully");

            } catch (InterruptedException e) {
                getPluginLogger().warning("Interrupted while shutting down Discord integration");
                jda.shutdownNow();
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                getPluginLogger().warning("Error during Discord shutdown: " + e.getMessage());
                jda.shutdownNow();
            } finally {
                jda = null;
                discordListener = null;
                discordEnabled = false;
            }
        }
    }


    public static void setBotStatus() {
        if (!discordActive || botStatusSet) return;

        runAsync(getInstance(), () -> {
            try {                 String serverName = getInstance().getConfig().getString("Server-name", "Server");

                    DiscordActivity activity = new DiscordActivity(
                            "Watching Over: " + serverName + " Punishment Logger",
                            3,
                            "Watch ya looking for? We got every punishment logged"
                    );

                    DiscordPresence presence = new DiscordPresence(
                            new DiscordActivity[]{activity},
                            "dnd"
                    );

                    String gatewayUrl = getDiscordGatewayUrl();
                    if (gatewayUrl != null) {
                        getPluginLogger().info("Bot status set: Watching Over " + serverName);
                        botStatusSet = true;
                    }

                } catch (Exception e) {
                    getPluginLogger().warning("Failed to set bot status: " + e.getMessage());
                }
        });
    }

    public static String getDiscordGatewayUrl() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/gateway/bot"))
                    .header("Authorization", "Bot " + discordBotToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
                return data.get("url").getAsString();
            }
        } catch (Exception e) {
            getPluginLogger().warning("Failed to get gateway URL: " + e.getMessage());
        }
        return null;
    }

    public static List<PunishmentGuiPlugin.PunishmentRecord> findPlayerRecords(String playerName) {
        String searchName = playerName.toLowerCase().trim();

        for (String key : punishmentHistory.keySet()) {
            if (key.toLowerCase().equals(searchName)) {
                return punishmentHistory.get(key);
            }
        }

        for (String key : punishmentHistory.keySet()) {
            if (key.toLowerCase().contains(searchName)) {
                return punishmentHistory.get(key);
            }
        }

        for (String key : punishmentHistory.keySet()) {
            if (key.toLowerCase().startsWith(searchName)) {
                return punishmentHistory.get(key);
            }
        }

        Player onlinePlayer = Bukkit.getPlayer(playerName);
        if (onlinePlayer != null) {
            String onlineName = onlinePlayer.getName();
            for (String key : punishmentHistory.keySet()) {
                if (key.equalsIgnoreCase(onlineName)) {
                    return punishmentHistory.get(key);
                }
            }
        }

        String bedrockPrefix = getInstance().getConfig().getString("bedrock-prefix", ".");
        String withPrefix = bedrockPrefix + searchName;
        for (String key : punishmentHistory.keySet()) {
            if (key.toLowerCase().equals(withPrefix)) {
                return punishmentHistory.get(key);
            }
        }

        return null;
    }

    public static String findPlayerKey(String playerName) {
        String searchName = playerName.toLowerCase().trim();

        for (String key : punishmentHistory.keySet()) {
            if (key.toLowerCase().equals(searchName)) {
                return key;
            }
        }

        for (String key : punishmentHistory.keySet()) {
            if (key.toLowerCase().contains(searchName)) {
                return key;
            }
        }

        for (String key : punishmentHistory.keySet()) {
            if (key.toLowerCase().startsWith(searchName)) {
                return key;
            }
        }

        Player onlinePlayer = Bukkit.getPlayer(playerName);
        if (onlinePlayer != null) {
            String onlineName = onlinePlayer.getName();
            for (String key : punishmentHistory.keySet()) {
                if (key.equalsIgnoreCase(onlineName)) {
                    return key;
                }
            }
        }

        String bedrockPrefix = getInstance().getConfig().getString("bedrock-prefix", ".");
        String withPrefix = bedrockPrefix + searchName;
        for (String key : punishmentHistory.keySet()) {
            if (key.toLowerCase().equals(withPrefix)) {
                return key;
            }
        }

        return playerName;
    }

    public static List<PunishmentGuiPlugin.PunishmentRecord> filterRecordsByCategory(List<PunishmentGuiPlugin.PunishmentRecord> records, String category) {
        switch (category.toLowerCase()) {
            case "bans":
                return records.stream()
                        .filter(r -> r.punishmentType.equals("BAN") || r.punishmentType.equals("TEMPBAN") || r.punishmentType.equals("UNBAN"))
                        .collect(Collectors.toList());
            case "mutes":
                return records.stream()
                        .filter(r -> r.punishmentType.equals("MUTE"))
                        .collect(Collectors.toList());
            case "kicks":
                return records.stream()
                        .filter(r -> r.punishmentType.equals("KICK"))
                        .collect(Collectors.toList());
            default:
                return new ArrayList<>(records);
        }
    }

    public static void sendDiscordNotification(PunishmentGuiPlugin.PunishmentRecord record) {
        if (!discordActive) return;

        if (discordBotToken.isEmpty() || discordBotToken.contains("YOUR_BOT_TOKEN_HERE") ||
                discordChannelId.isEmpty() || discordChannelId.contains("YOUR_CHANNEL_ID_HERE")) {
            getPluginLogger().warning("Discord integration is enabled but bot token or channel ID is not properly configured!");
            return;
        }

        runAsync(getInstance(), () -> {
            try {
                    PunishmentGuiPlugin.DiscordEmbed embed = new PunishmentGuiPlugin.DiscordEmbed();
                    embed.timestamp = record.timestamp;
                    String serverName = getInstance().getConfig().getString("Server-name", "Server");
                    embed.footer = new PunishmentGuiPlugin.DiscordEmbed.Footer(serverName + " Punishment System");

                    switch (record.punishmentType) {
                        case "BAN":
                            embed.color = 0xFF0000;
                            embed.title = "🔨 Player Banned";
                            break;
                        case "TEMPBAN":
                            embed.color = 0xFF8C00;
                            embed.title = "⏰ Player Temporarily Banned";
                            break;
                        case "MUTE":
                            embed.color = 0xFFFF00;
                            embed.title = "🔇 Player Muted";
                            break;
                        case "KICK":
                            embed.color = 0x00FF00;
                            embed.title = "👢 Player Kicked";
                            break;
                        case "UNBAN":
                            embed.color = 0x00FF00;
                            embed.title = "✅ Player Unbanned";
                            break;
                        case "BanEvading":
                            embed.color = 0xFF0000;
                            embed.title = "🔨 Player Ban Evading";
                            break;
                        default:
                            embed.color = 0x808080;
                            embed.title = "📋 Punishment Issued";
                    }

                    List<PunishmentGuiPlugin.DiscordEmbed.Field> fields = new ArrayList<>();
                    fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Player", record.playerName, true));
                    fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Staff Member", record.staffMember, true));
                    fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Action", record.punishmentType, true));

                    if (record.duration != null && !record.duration.isEmpty() && !record.punishmentType.equals("KICK")) {
                        if (isValidDuration(record.duration)) {
                            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Duration", record.duration, true));
                        } else { return;}
                    }

                    fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Reason", record.reason, false));
                    embed.fields = fields.toArray(new PunishmentGuiPlugin.DiscordEmbed.Field[0]);

                    DiscordMessage message = new DiscordMessage();
                    message.content = "";
                    message.embeds = new PunishmentGuiPlugin.DiscordEmbed[]{embed};
                    String jsonPayload = gson.toJson(message);

                    sendDiscordRequest("POST", "channels/" + discordChannelId + "/messages", jsonPayload);

                } catch (Exception e) {
                    getPluginLogger().warning("Failed to send Discord notification: " + e.getMessage());
                }
        });
    }

    public static void sendDiscordMessage(String content) {
        if (!discordActive || content == null || content.trim().isEmpty()) {
            return;
        }

        if (discordBotToken.isEmpty() || discordBotToken.contains("YOUR_BOT_TOKEN_HERE") ||
                discordChannelId.isEmpty() || discordChannelId.contains("YOUR_CHANNEL_ID_HERE")) {
            getPluginLogger().warning("Discord credentials not properly configured");
            return;
        }

        if (content.length() > 1900) {
            String[] parts = content.split("\n");
            StringBuilder currentMessage = new StringBuilder();

            for (String part : parts) {
                if (currentMessage.length() + part.length() + 1 > 1900) {
                    sendDiscordMessageInternal(currentMessage.toString());
                    currentMessage = new StringBuilder();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (currentMessage.length() > 0) {
                    currentMessage.append("\n");
                }
                currentMessage.append(part);
            }

            if (currentMessage.length() > 0) {
                sendDiscordMessageInternal(currentMessage.toString());
            }
        } else {
            sendDiscordMessageInternal(content);
        }
    }

    public static void sendDiscordMessageInternal(String content) {
        runAsync(getInstance(), () -> {
                try {
                    JsonObject message = new JsonObject();
                    message.addProperty("content", content);
                    String jsonPayload = gson.toJson(message);

                    sendDiscordRequest("POST", "channels/" + discordChannelId + "/messages", jsonPayload);

                } catch (Exception e) {
                    getPluginLogger().warning("Failed to send Discord message: " + e.getMessage());
                }
        });
    }

    public static void sendHistoryEmbedWithButtons(HistorySession session) {
        try {
            PunishmentGuiPlugin.DiscordEmbed embed = createHistoryEmbed(session);
            DiscordActionRow[] actionRows = createHistoryButtons(session);

            DiscordMessageWithComponents message = new DiscordMessageWithComponents(
                    "",
                    new PunishmentGuiPlugin.DiscordEmbed[]{embed},
                    actionRows
            );

            String jsonPayload = gson.toJson(message);

            CompletableFuture<HttpResponse<String>> future = sendDiscordRequestAsync("POST", "channels/" + discordChannelId + "/messages", jsonPayload);

            future.thenAccept(response -> {
                if (response.statusCode() == 200) {
                    try {
                        JsonObject responseObj = JsonParser.parseString(response.body()).getAsJsonObject();
                        String messageId = responseObj.get("id").getAsString();
                        session.messageId = messageId;
                        messageToSession.put(messageId, session.sessionId);
                    } catch (Exception e) {
                        getPluginLogger().warning("Could not parse message ID from Discord response: " + e.getMessage());
                    }
                } else {
                    getPluginLogger().warning("Discord API failed with status: " + response.statusCode() + " - " + response.body());
                }
            });

        } catch (Exception e) {
            getPluginLogger().warning("Failed to send history embed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void startDiscordMessagePolling() {
        return;
    }

    public static void checkDiscordMessages() {
        if (!discordActive) return;

        try {
            CompletableFuture<HttpResponse<String>> future = sendDiscordRequestAsync("GET", "channels/" + discordChannelId + "/messages?limit=5", null);

            future.thenAccept(response -> {
                if (response.statusCode() == 200) {
                    processDiscordMessages(response.body());
                } else {
                    getPluginLogger().warning("Discord API error: " + response.statusCode());
                }
            });

        } catch (Exception e) {
            getPluginLogger().warning("Error checking Discord messages: " + e.getMessage());
        }
    }

    public static void processDiscordMessages(String jsonResponse) {
        try {
            JsonArray messages = JsonParser.parseString(jsonResponse).getAsJsonArray();

            for (JsonElement messageElement : messages) {
                JsonObject message = messageElement.getAsJsonObject();
                String messageId = message.get("id").getAsString();

                if (lastDiscordMessageTime.containsKey(messageId)) continue;

                String content = "";
                if (message.has("content") && !message.get("content").isJsonNull()) {
                    content = message.get("content").getAsString().trim();
                }

                if (content.isEmpty()) continue;

                JsonObject author = message.getAsJsonObject("author");
                if (author == null) continue;

                String authorId = author.get("id").getAsString();
                long timestamp = Instant.parse(message.get("timestamp").getAsString()).toEpochMilli();

                JsonElement botElement = author.get("bot");
                boolean isBot = botElement != null && botElement.getAsBoolean();

                if (isBot) continue;

                long messageAge = System.currentTimeMillis() - timestamp;
                if (messageAge > 10000) continue;

                lastDiscordMessageTime.put(messageId, timestamp);

                getPluginLogger().info("Processing Discord command from " + authorId + ": " + content);

                processDiscordCommand(content, authorId);
                deleteDiscordMessage(messageId);
            }

        } catch (Exception e) {
            getPluginLogger().warning("Error processing Discord messages: " + e.getMessage());
        }
    }
    public static void processDiscordCommand(String content, String authorId) {
        if (content == null || content.trim().isEmpty()) return;

        String[] parts = content.trim().split("\\s+");
        if (parts.length < 1) return;

        String command = parts[0].toLowerCase();

        if (command.equals("debug")) {
            handleDiscordDebugCommand();
            return;
        }

        switch (command) {
            case "history":
            case "lookup":
            case "check":
                if (parts.length < 2) {
                    sendInvalidCommandMessage();
                    return;
                }
                String playerName = parts[1];

                switch (command) {
                    case "history":
                        handleDiscordHistoryCommand(playerName, authorId);
                        break;
                    case "lookup":
                        handleDiscordLookupCommand(playerName, authorId);
                        break;
                    case "check":
                        handleDiscordCheckCommand(playerName, authorId);
                        break;
                }
                break;

            case "tempban":
                if (parts.length < 4) {
                    sendDiscordMessage("❌ **Invalid tempban format!**\n\n" +
                            "**Usage:** `tempban <player> <duration> <reason>`\n" +
                            "**Example:** `tempban Notch 1d Griefing`");
                    return;
                }

                String playerToBan = parts[1];
                String duration = parts[2];
                String reason = String.join(" ", Arrays.copyOfRange(parts, 3, parts.length));
                handlediscordBanCommand(playerToBan, duration, reason, authorId);
                break;

            default:
                sendDiscordMessage("❌ **Unknown command:** `" + command + "`\n\n" +
                        "**Available commands:**\n" +
                        "• `history <player>` - View punishment history\n" +
                        "• `lookup <player>` - Quick player lookup\n" +
                        "• `check <player>` - Check if player is online\n" +
                        "• `tempban <player> <duration> <reason>` - Temporarily ban a player\n" +
                        "• `debug` - Show debug information");
                break;
        }
    }
    public static void sendInvalidCommandMessage() {
        sendDiscordMessage("❌ **Invalid command format!**\n\n" +
                "**Available commands:**\n" +
                "• `history <player>` - View punishment history\n" +
                "• `lookup <player>` - Quick player lookup\n" +
                "• `check <player>` - Check if player is online\n" +
                "• `tempban <player> <duration> <reason>` - Temporarily ban a player\n" +
                "• `debug` - Show debug information\n\n" +
                "**Examples:**\n" +
                "• `history Notch`\n" +
                "• `tempban Notch 1d Griefing`");
    }

    public static void handlediscordBanCommand(String playerName, String duration, String reason, String authorId) {
        try {
            if (playerName == null || playerName.trim().isEmpty()) {
                sendDiscordMessage("❌ Player name cannot be empty!");
                return;
            }
            playerName = playerName.trim();

            if (duration == null || duration.trim().isEmpty()) {
                sendDiscordMessage("❌ Duration is required!");
                return;
            }
            duration = duration.trim();

            if (!isValidDuration(duration)) {
                sendDiscordMessage("❌ Invalid duration format! Use formats like: 1d, 2h, 30m, 1w, etc.");
                return;
            }

            if (reason == null || reason.trim().isEmpty()) {
                reason = "No reason provided";
            }
            reason = reason.trim();

            if (Bancheckers.hasBanBypass(playerName)) {
                sendDiscordMessage("❌ Cannot ban " + playerName + " - Player has ban bypass permission!");
                return;
            }

            String banCommand = String.format("tempban %s %s %s", playerName, duration, reason);

            FoliaUtils.executeConsoleCommand(getInstance(), banCommand);

            PunishmentGuiPlugin.DiscordEmbed embed = new PunishmentGuiPlugin.DiscordEmbed();
            embed.color = 0xFF0000;
            embed.title = "🔨 Player Banned";
            embed.timestamp = Instant.now().toString();

            String serverName = getInstance().getConfig().getString("Server-name", "Server");
            embed.footer = new PunishmentGuiPlugin.DiscordEmbed.Footer(serverName + " Punishment System");

            List<PunishmentGuiPlugin.DiscordEmbed.Field> fields = new ArrayList<>();
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Player", playerName, true));
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Duration", duration, true));
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Banned by", "Discord User: " + "<@"+authorId+">", true));
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Reason", reason, false));

            embed.fields = fields.toArray(new PunishmentGuiPlugin.DiscordEmbed.Field[0]);

            sendDiscordEmbed(embed);

        } catch (Exception e) {
            sendDiscordMessage("❌ An error occurred while processing the ban command: " + e.getMessage());
            getPluginLogger().warning("Error in Discord ban command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void handleDiscordHistoryCommand(String playerName, String authorId) {
        if (punishmentHistory.isEmpty()) {
            sendDiscordMessage("❌ **No punishment data available!**");
            return;
        }

        List<PunishmentGuiPlugin.PunishmentRecord> records = findPlayerRecords(playerName);
        String foundKey = findPlayerKey(playerName);

        if (records == null || records.isEmpty()) {
            sendNoHistoryFoundMessage(playerName);
            return;
        }

        String sessionId = "hist_" + authorId + "_" + System.currentTimeMillis();
        HistorySession session = new HistorySession(foundKey, authorId, "all", records, sessionId);

        historySessions.put(sessionId, session);
        sessionExpiry.put(sessionId, System.currentTimeMillis() + 300000);

        sendHistoryEmbedWithButtons(session);
    }

    public static void handleDiscordLookupCommand(String playerName, String authorId) {
        try {
            List<PunishmentGuiPlugin.PunishmentRecord> records = findPlayerRecords(playerName);
            String playerIP = getPlayerIP(playerName);

            PunishmentGuiPlugin.DiscordEmbed embed = createLookupEmbed(playerName, records);

            sendDiscordEmbed(embed);

        } catch (Exception e) {
            getPluginLogger().warning("Error in lookup command: " + e.getMessage());
            sendDiscordMessage("❌ Error looking up player " + playerName);
        }
    }

    public static void handleDiscordCheckCommand(String playerName, String authorId) {
        try {
            Player onlinePlayer = Bukkit.getPlayer(playerName);

            PunishmentGuiPlugin.DiscordEmbed embed = createCheckEmbed(playerName, onlinePlayer);

            sendDiscordEmbed(embed);

        } catch (Exception e) {
            getPluginLogger().warning("Error in check command: " + e.getMessage());
            sendDiscordMessage("❌ Error checking player " + playerName);
        }
    }

    public static void handleDiscordDebugCommand() {
        try {
            PunishmentGuiPlugin.DiscordEmbed embed = createDebugEmbed();

            sendDiscordEmbed(embed);

        } catch (Exception e) {
            getPluginLogger().warning("Error in debug command: " + e.getMessage());
            sendDiscordMessage("❌ Error generating debug information");
        }
    }

    public static PunishmentGuiPlugin.DiscordEmbed createLookupEmbed(String playerName, List<PunishmentGuiPlugin.PunishmentRecord> records) {
        PunishmentGuiPlugin.DiscordEmbed embed = new PunishmentGuiPlugin.DiscordEmbed();
        embed.title = "🔍 Player Lookup: " + playerName;
        embed.color = 0x3498db;
        embed.timestamp = Instant.now().toString();

        String serverName = getInstance().getConfig().getString("Server-name", "Server");
        embed.footer = new PunishmentGuiPlugin.DiscordEmbed.Footer(serverName + " Punishment System");

        List<PunishmentGuiPlugin.DiscordEmbed.Field> fields = new ArrayList<>();

        if (records != null && !records.isEmpty()) {
            long activeBans = records.stream()
                    .filter(r -> (r.punishmentType.equals("BAN") || r.punishmentType.equals("TEMPBAN")) && r.active)
                    .count();

            long activeMutes = records.stream()
                    .filter(r -> r.punishmentType.equals("MUTE") && r.active)
                    .count();

            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Active Bans", String.valueOf(activeBans), true));
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Active Mutes", String.valueOf(activeMutes), true));
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Total Punishments", String.valueOf(records.size()), true));

            LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
            long recentCount = records.stream()
                    .filter(r -> {
                        try {
                            LocalDateTime recordTime = LocalDateTime.parse(r.timestamp);
                            return recordTime.isAfter(weekAgo);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .count();

            if (recentCount > 0) {
                fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Recent Activity (7 days)",
                        recentCount + " punishment(s)", false));
            }
        } else {
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Punishment History", "No punishment history found", false));
        }

        embed.fields = fields.toArray(new PunishmentGuiPlugin.DiscordEmbed.Field[0]);
        return embed;
    }

    public static PunishmentGuiPlugin.DiscordEmbed createCheckEmbed(String playerName, Player onlinePlayer) {
        PunishmentGuiPlugin.DiscordEmbed embed = new PunishmentGuiPlugin.DiscordEmbed();
        embed.title = "🔍 Player Check: " + playerName;
        embed.timestamp = Instant.now().toString();

        String serverName = getInstance().getConfig().getString("Server-name", "Server");
        embed.footer = new PunishmentGuiPlugin.DiscordEmbed.Footer(serverName + " Punishment System");

        List<PunishmentGuiPlugin.DiscordEmbed.Field> fields = new ArrayList<>();

        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            embed.color = 0x2ecc71;

            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Status", "🟢 Online", true));
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("World", onlinePlayer.getWorld().getName(), true));
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Location",
                    onlinePlayer.getLocation().getBlockX() + ", " +
                            onlinePlayer.getLocation().getBlockY() + ", " +
                            onlinePlayer.getLocation().getBlockZ(), true));

            if (onlinePlayer.hasPermission("punishmentsystem.bypass")) {
                fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("⚠️ Warning", "Has bypass permission", false));
            }

            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Gamemode", onlinePlayer.getGameMode().toString(), true));
            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Health",
                    String.format("%.1f", onlinePlayer.getHealth()) + "/20", true));
        } else {
            embed.color = 0xe74c3c;

            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Status", "🔴 Offline", true));

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                long lastPlayed = offlinePlayer.getLastPlayed();
                Date lastPlayedDate = new Date(lastPlayed);
                fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Last Seen", lastPlayedDate.toString(), false));

                long daysSince = (System.currentTimeMillis() - lastPlayed) / (1000 * 60 * 60 * 24);
                fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Days since last seen", String.valueOf(daysSince), true));
            } else {
                fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Status", "❌ Never joined the server", false));
            }
        }

        embed.fields = fields.toArray(new PunishmentGuiPlugin.DiscordEmbed.Field[0]);
        return embed;
    }

    public static PunishmentGuiPlugin.DiscordEmbed createDebugEmbed() {
        PunishmentGuiPlugin.DiscordEmbed embed = new PunishmentGuiPlugin.DiscordEmbed();
        embed.title = "🔧 Discord Debug Information";
        embed.color = 0x9b59b6;

        List<PunishmentGuiPlugin.DiscordEmbed.Field> fields = new ArrayList<>();

        fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Discord Active",
                discordActive ? "✅ YES" : "❌ NO", true));
        fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Bot Status Set",
                botStatusSet ? "✅ YES" : "❌ NO", true));
        fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Scheduler Running",
                discordScheduler != null && !discordScheduler.isShutdown() ? "✅ YES" : "❌ NO", true));

        fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Punishment History Size",
                String.valueOf(punishmentHistory.size()), true));
        fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Active Sessions",
                String.valueOf(historySessions.size()), true));

        if (!punishmentHistory.isEmpty()) {
            StringBuilder samplePlayers = new StringBuilder();
            punishmentHistory.keySet().stream()
                    .limit(5)
                    .forEach(player -> samplePlayers.append("• ").append(player).append("\n"));

            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Sample Players", samplePlayers.toString(), false));
        }

        fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field("Test Command", "Use: `history <playername>`", false));

        embed.fields = fields.toArray(new PunishmentGuiPlugin.DiscordEmbed.Field[0]);
        return embed;
    }

    public static void sendDiscordEmbed(PunishmentGuiPlugin.DiscordEmbed embed) {
        try {
            DiscordMessageWithComponents message = new DiscordMessageWithComponents(
                    "",
                    new PunishmentGuiPlugin.DiscordEmbed[]{embed},
                    new DiscordActionRow[0]
            );

            String jsonPayload = gson.toJson(message);

            CompletableFuture<HttpResponse<String>> future = sendDiscordRequestAsync("POST", "channels/" + discordChannelId + "/messages", jsonPayload);

            future.thenAccept(response -> {
                if (response.statusCode() != 200) {
                    getPluginLogger().warning("Discord API failed with status: " + response.statusCode() + " - " + response.body());
                }
            });

        } catch (Exception e) {
            getPluginLogger().warning("Failed to send Discord embed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void checkDiscordBanEvasion(String playerName, String playerIP, StringBuilder response) {
        if (playerIP.equals("Unknown")) {
            response.append("⚠️ Cannot check ban evasion - IP unknown\n");
            return;
        }

        List<String> suspiciousPlayers = new ArrayList<>();

        for (Map.Entry<String, String> entry : playerIPs.entrySet()) {
            String storedPlayer = entry.getKey();
            String storedIP = entry.getValue();

            if (storedPlayer.equalsIgnoreCase(playerName)) continue;
            if (!storedIP.equals(playerIP)) continue;

            List<PunishmentGuiPlugin.PunishmentRecord> records = punishmentHistory.get(storedPlayer);
            if (records != null) {
                for (PunishmentGuiPlugin.PunishmentRecord record : records) {
                    if ((record.punishmentType.equals("BAN") || record.punishmentType.equals("TEMPBAN")) && record.active) {
                        suspiciousPlayers.add(storedPlayer);
                        break;
                    }
                }
            }
        }

        if (!suspiciousPlayers.isEmpty()) {
            response.append("\n⚠️ **POSSIBLE BAN EVASION DETECTED**\n");
            response.append("Shares IP with banned player(s): ").append(String.join(", ", suspiciousPlayers)).append("\n");
        }
    }

    public static void deleteDiscordMessage(String messageId) {
        try {
            sendDiscordRequestAsync("DELETE", "channels/" + discordChannelId + "/messages/" + messageId, null);
        } catch (Exception e) {
            getPluginLogger().warning("Error deleting Discord message: " + e.getMessage());
        }
    }
    public static void sendNoHistoryFoundMessage(String playerName) {
        StringBuilder errorMsg = new StringBuilder();
        errorMsg.append("❌ **No punishment history found for:** `").append(playerName).append("`\n\n");

        List<String> similarNames = new ArrayList<>();
        String searchName = playerName.toLowerCase();
        for (String key : punishmentHistory.keySet()) {
            if (key.toLowerCase().contains(searchName.substring(0, Math.min(3, searchName.length())))) {
                similarNames.add(key);
            }
        }

        if (!similarNames.isEmpty()) {
            errorMsg.append("**Did you mean one of these?**\n");
            similarNames.stream().limit(10).forEach(name ->
                    errorMsg.append("• `").append(name).append("`\n"));
        } else {
            errorMsg.append("**Possible reasons:**\n");
            errorMsg.append("• Player has never been punished\n");
            errorMsg.append("• Player name is misspelled\n");
            errorMsg.append("• Player has never joined the server\n\n");

            String examples = punishmentHistory.keySet().stream()
                    .limit(10)
                    .collect(Collectors.joining("`, `"));
            if (!examples.isEmpty()) {
                errorMsg.append("**Players with history:**\n`").append(examples).append("`");
                if (punishmentHistory.size() > 10) {
                    errorMsg.append("\n*...and ").append(punishmentHistory.size() - 10).append(" more*");
                }
            }
        }

        sendDiscordMessage(errorMsg.toString());
    }

    public static PunishmentGuiPlugin.DiscordEmbed createHistoryEmbed(HistorySession session) {
        PunishmentGuiPlugin.DiscordEmbed embed = new PunishmentGuiPlugin.DiscordEmbed();
        embed.color = 0x3498db;
        embed.title = "📋 Punishment History - " + session.playerName;
        embed.timestamp = Instant.now().toString();

        String serverName = getInstance().getConfig().getString("Server-name", "Server");


        embed.footer = new PunishmentGuiPlugin.DiscordEmbed.Footer(serverName + " • Page " + (session.page + 1));

        List<PunishmentGuiPlugin.PunishmentRecord> filteredRecords = filterRecordsByCategory(session.allRecords, session.category);

        if (filteredRecords.isEmpty()) {
            embed.description = "No " + getCategoryDisplayName(session.category).toLowerCase() + " records found for this player.";
            return embed;
        }

        filteredRecords.sort((a, b) -> {
            try {
                return b.timestamp != null && a.timestamp != null ? b.timestamp.compareTo(a.timestamp) : 0;
            } catch (Exception e) {
                return 0;
            }
        });

        int recordsPerPage = 5;
        int startIndex = session.page * recordsPerPage;
        int endIndex = Math.min(startIndex + recordsPerPage, filteredRecords.size());

        List<PunishmentGuiPlugin.DiscordEmbed.Field> fields = new ArrayList<>();

        String categoryDisplay = getCategoryDisplayName(session.category);
        int totalPages = Math.max(1, (int) Math.ceil(filteredRecords.size() / (double) recordsPerPage));

        fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field(
                "📊 Summary",
                "**Category:** " + categoryDisplay + "\n" +
                        "**Total Records:** " + filteredRecords.size() + "\n" +
                        "**Showing:** " + (startIndex + 1) + "-" + endIndex + " of " + filteredRecords.size() + "\n" +
                        "**Page:** " + (session.page + 1) + "/" + totalPages + "\n"+
                        "**Requested by:** <@" + session.authorId.toString() + ">" ,
                false
        ));

        for (int i = startIndex; i < endIndex; i++) {
            PunishmentGuiPlugin.PunishmentRecord record = filteredRecords.get(i);

            String statusEmoji = getStatusEmoji(record);
            String fieldName = (i + 1) + ". " + record.punishmentType + " " + statusEmoji;

            StringBuilder fieldValue = new StringBuilder();
            fieldValue.append("**Reason:** ").append(record.reason != null ? record.reason : "No reason").append("\n");
            fieldValue.append("**Staff:** ").append(record.staffMember != null ? record.staffMember : "Unknown").append("\n");

            if (record.timestamp != null) {
                try {
                    String formattedTime = formatTimestamp(record.timestamp);
                    fieldValue.append("**Date:** ").append(formattedTime).append("\n");
                } catch (Exception e) {
                    fieldValue.append("**Date:** ").append(record.timestamp).append("\n");
                }
            }

            if (record.duration != null && !record.duration.isEmpty() &&
                    !record.duration.equals("0") && !record.duration.equals("permanent") &&
                    !record.punishmentType.equals("KICK") && !record.punishmentType.equals("UNBAN")) {
                fieldValue.append("**Duration:** ").append(record.duration).append("\n");
            }

            fieldValue.append("**Status:** ").append(record.active ? "Active" : "Inactive");

            fields.add(new PunishmentGuiPlugin.DiscordEmbed.Field(fieldName, fieldValue.toString(), true));
        }

        embed.fields = fields.toArray(new PunishmentGuiPlugin.DiscordEmbed.Field[0]);
        return embed;
    }

    public static DiscordActionRow[] createHistoryButtons(HistorySession session) {
        List<DiscordActionRow> rows = new ArrayList<>();

        try {
            String shortId = "s" + String.format("%05d", Math.abs(session.sessionId.hashCode() % 100000));

            sessionIdMapping.put(shortId, session.sessionId);


            DiscordComponent[] categoryButtons = {
                    new DiscordComponent(2, shortId + "_all", "All", session.category.equals("all") ? 1 : 2),
                    new DiscordComponent(2, shortId + "_bans", "Bans", session.category.equals("bans") ? 1 : 2),
                    new DiscordComponent(2, shortId + "_mutes", "Mutes", session.category.equals("mutes") ? 1 : 2),
                    new DiscordComponent(2, shortId + "_kicks", "Kicks", session.category.equals("kicks") ? 1 : 2)
            };
            rows.add(new DiscordActionRow(categoryButtons));

            List<PunishmentGuiPlugin.PunishmentRecord> filteredRecords = filterRecordsByCategory(session.allRecords, session.category);
            int totalPages = Math.max(1, (int) Math.ceil(filteredRecords.size() / 5.0));

            if (totalPages > 1) {
                List<DiscordComponent> navButtons = new ArrayList<>();

                DiscordComponent prevButton = new DiscordComponent(2, shortId + "_prev", "◀ Previous", 2);
                if (session.page <= 0) {
                    prevButton.disabled = true;
                }
                navButtons.add(prevButton);

                DiscordComponent pageButton = new DiscordComponent(2, shortId + "_page", "Page " + (session.page + 1) + "/" + totalPages, 2);
                pageButton.disabled = true;
                navButtons.add(pageButton);

                DiscordComponent nextButton = new DiscordComponent(2, shortId + "_next", "Next ▶", 2);
                if (session.page >= totalPages - 1) {
                    nextButton.disabled = true;
                }
                navButtons.add(nextButton);

                rows.add(new DiscordActionRow(navButtons.toArray(new DiscordComponent[0])));
            }

            return rows.toArray(new DiscordActionRow[0]);

        } catch (Exception e) {
            getPluginLogger().warning("Error creating history buttons: " + e.getMessage());
            e.printStackTrace();
            return new DiscordActionRow[0];
        }
    }

    public static String getCategoryDisplayName(String category) {
        switch (category.toLowerCase()) {
            case "bans": return "Bans & Unbans";
            case "mutes": return "Mutes";
            case "kicks": return "Kicks";
            default: return "All Punishments";
        }
    }

    public static String getStatusEmoji(PunishmentGuiPlugin.PunishmentRecord record) {
        if (record.active) {
            return record.punishmentType.equals("BAN") || record.punishmentType.equals("TEMPBAN") ? "🔴" : "🟡";
        } else {
            return "⚫";
        }
    }

    public static String formatTimestamp(String timestamp) {
        try {
            String formattedTime = timestamp.replace("T", " ");
            if (formattedTime.length() > 16) {
                formattedTime = formattedTime.substring(0, 16);
            }
            return formattedTime;
        } catch (Exception e) {
            return timestamp;
        }
    }

    public static void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();

        sessionExpiry.entrySet().removeIf(entry -> {
            if (currentTime > entry.getValue()) {
                String sessionId = entry.getKey();
                HistorySession session = historySessions.remove(sessionId);

                if (session != null) {
                    sessionIdMapping.entrySet().removeIf(mapping ->
                            mapping.getValue().equals(sessionId));

                    if (session.messageId != null) {
                        messageToSession.remove(session.messageId);
                    }

                    getPluginLogger().info("Cleaned up expired session: " + sessionId);
                }
                return true;
            }
            return false;
        });
    }

    public static void logDiscordActivity(String activity) {
        if (getInstance().getConfig().getBoolean("discord.debug-logging", false)) {
            getPluginLogger().info("[Discord] " + activity);
        }
    }

    public static boolean isDiscordConfigured() {
        return discordActive &&
                !discordBotToken.isEmpty() &&
                !discordBotToken.contains("YOUR_BOT_TOKEN_HERE") &&
                !discordChannelId.isEmpty() &&
                !discordChannelId.contains("YOUR_CHANNEL_ID_HERE");
    }

    public static void validateDiscordConfig() {
        List<String> issues = new ArrayList<>();

        if (!discordActive) {
            issues.add("Discord integration is disabled");
        }

        if (discordBotToken.isEmpty() || discordBotToken.contains("YOUR_BOT_TOKEN_HERE")) {
            issues.add("Bot token is not configured");
        }

        if (discordChannelId.isEmpty() || discordChannelId.contains("YOUR_CHANNEL_ID_HERE")) {
            issues.add("Channel ID is not configured");
        }

        if (discordPublicKey.isEmpty()) {
            issues.add("Public key is not configured (interactions will not work)");
        }

        if (!issues.isEmpty()) {
            getPluginLogger().warning("Discord configuration issues found:");
            issues.forEach(issue -> getPluginLogger().warning("  - " + issue));
        }
    }

    public static void sendDiscordStartupMessage() {
        if (!isDiscordConfigured()) return;

        String serverName = getInstance().getConfig().getString("Server-name", "Minecraft Server");
        String startupMessage = "🟢 **" + serverName + " Online**\n" +
                "Punishment system is now active and monitoring for violations.\n" +
                "Use `history <player>` to view punishment records.";

        sendDiscordMessage(startupMessage);
    }

    public static void sendDiscordShutdownMessage() {
        if (!isDiscordConfigured()) return;

        String serverName = getInstance().getConfig().getString("Server-name", "Minecraft Server");
        String shutdownMessage = "🔴 **" + serverName + " Offline**\n" +
                "Server is shutting down. Punishment system will be unavailable.";

        sendDiscordMessage(shutdownMessage);
    }

    public static boolean isDiscordRateLimited(String identifier) {
        long currentTime = System.currentTimeMillis();
        Long lastMessage = discordRateLimits.get(identifier);

        if (lastMessage == null || (currentTime - lastMessage) >= (DISCORD_RATE_LIMIT_SECONDS * 1000)) {
            discordRateLimits.put(identifier, currentTime);
            return false;
        }

        return true;
    }

    public static void clearDiscordRateLimits() {
        long currentTime = System.currentTimeMillis();
        discordRateLimits.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) >= (DISCORD_RATE_LIMIT_SECONDS * 1000));
    }

    public static HttpResponse sendDiscordRequest(String method, String endpoint, String jsonPayload) {
        try {
            String url = "https://discord.com/api/v10/" + endpoint;

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bot " + discordBotToken)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "PunishmentSystem-Discord-Bot/1.0");

            switch (method.toUpperCase()) {
                case "GET":
                    requestBuilder.GET();
                    break;
                case "POST":
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonPayload != null ? jsonPayload : ""));
                    break;
                case "PUT":
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(jsonPayload != null ? jsonPayload : ""));
                    break;
                case "PATCH":
                    requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonPayload != null ? jsonPayload : ""));
                    break;
                case "DELETE":
                    requestBuilder.DELETE();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (getInstance().getConfig().getBoolean("discord.debug-logging", false)) {
                getPluginLogger().info("Discord API " + method + " " + endpoint + " -> " + response.statusCode());
            }

            if (response.statusCode() == 429) {
                getPluginLogger().warning("Discord API rate limit hit for " + endpoint);
                getPluginLogger().warning("Discord API error: " + response.statusCode());

                String retryAfter = response.headers().firstValue("retry-after").orElse("5");
                try {
                    int retrySeconds = Integer.parseInt(retryAfter);
                    getPluginLogger().info("Rate limited, should retry after " + retrySeconds + " seconds");

                    return response;

                } catch (NumberFormatException e) {
                    getPluginLogger().warning("Error parsing retry-after header: " + e.getMessage());
                }
            }

            return response;

        } catch (Exception e) {
            getPluginLogger().warning("Error sending Discord request: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static CompletableFuture<HttpResponse<String>> sendDiscordRequestAsync(String method, String endpoint, String jsonPayload) {
        try {
            String url = "https://discord.com/api/v10/" + endpoint;

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bot " + discordBotToken)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "PunishmentSystem-Discord-Bot/1.0");

            switch (method.toUpperCase()) {
                case "GET":
                    requestBuilder.GET();
                    break;
                case "POST":
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonPayload != null ? jsonPayload : ""));
                    break;
                case "PUT":
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(jsonPayload != null ? jsonPayload : ""));
                    break;
                case "PATCH":
                    requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonPayload != null ? jsonPayload : ""));
                    break;
                case "DELETE":
                    requestBuilder.DELETE();
                    break;
                default:
                    CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalArgumentException("Unsupported HTTP method: " + method));
                    return future;
            }

            HttpRequest request = requestBuilder.build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (getInstance().getConfig().getBoolean("discord.debug-logging", false)) {
                            getPluginLogger().info("Discord API " + method + " " + endpoint + " -> " + response.statusCode());
                        }

                        if (response.statusCode() == 429) {
                            getPluginLogger().warning("Discord API rate limit hit for " + endpoint);

                            String retryAfter = response.headers().firstValue("retry-after").orElse("1");
                            getPluginLogger().info("Rate limited, should retry after " + retryAfter + " seconds");
                        }

                        return response;
                    })
                    .exceptionally(throwable -> {
                        getPluginLogger().warning("Error sending async Discord request: " + throwable.getMessage());
                        throwable.printStackTrace();
                        return null;
                    });

        } catch (Exception e) {
            getPluginLogger().warning("Error creating async Discord request: " + e.getMessage());
            e.printStackTrace();
            CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    public static CompletableFuture<HttpResponse<String>> sendDiscordRequestAsyncWithRetry(String method, String endpoint, String jsonPayload, int maxRetries) {
        return sendDiscordRequestAsync(method, endpoint, jsonPayload)
                .thenCompose(response -> {
                    if (response != null && response.statusCode() == 429 && maxRetries > 0) {
                        String retryAfter = response.headers().firstValue("retry-after").orElse("1");
                        try {
                            int retrySeconds = Integer.parseInt(retryAfter);
                            getPluginLogger().info("Rate limited, retrying after " + retrySeconds + " seconds (attempts left: " + maxRetries + ")");

                            CompletableFuture<Void> delay = new CompletableFuture<>();
                            CompletableFuture.delayedExecutor(retrySeconds, TimeUnit.SECONDS)
                                    .execute(() -> delay.complete(null));

                            return delay.thenCompose(v -> sendDiscordRequestAsyncWithRetry(method, endpoint, jsonPayload, maxRetries - 1));
                        } catch (NumberFormatException e) {
                            getPluginLogger().warning("Invalid retry-after header: " + retryAfter);
                        }
                    }
                    return CompletableFuture.completedFuture(response);
                });
    }

    public static boolean validateDiscordBotToken() {
        try {
            HttpResponse<String> response = sendDiscordRequest("GET", "users/@me", null);

            if (response != null && response.statusCode() == 200) {
                JsonObject botInfo = JsonParser.parseString(response.body()).getAsJsonObject();
                String botUsername = botInfo.get("username").getAsString();
                getPluginLogger().info("Discord bot authenticated as: " + botUsername);
                return true;
            } else if (response != null) {
                getPluginLogger().warning("Discord bot token validation failed. Status: " + response.statusCode());
                if (response.statusCode() == 401) {
                    getPluginLogger().warning("Invalid bot token provided!");
                }
            }

            return false;

        } catch (Exception e) {
            getPluginLogger().warning("Error validating Discord bot token: " + e.getMessage());
            return false;
        }
    }

    public static void initializeDiscordAsync() {
        synchronized (discordInitLock) {
            if (discordInitializing) {
                getPluginLogger().info("Discord integration is already initializing, skipping...");
                return;
            }
            discordInitializing = true;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String serverName = ServerName != null ? ServerName : "Unknown Server";
                getPluginLogger().info("Attempting to initialize Discord integration...");

                Class.forName("net.dv8tion.jda.api.JDABuilder");
                Class.forName("net.dv8tion.jda.api.hooks.ListenerAdapter");

                shutdownDiscordIntegration();

                discordListener = new DiscordEventListener();
                String Status = getInstance().getConfig().getString("discord.custom-status", "Punishment Logger 3000");

                getPluginLogger().info("Creating new JDA instance...");
                jda = JDABuilder.createDefault(discordBotToken)
                        .addEventListeners(discordListener)
                        .setStatus(OnlineStatus.DO_NOT_DISTURB)
                        .setActivity(Activity.customStatus(Status))
                        .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                        .build();

                getPluginLogger().info("Waiting for JDA to be ready...");
                jda.awaitReady();

                if (discordChannelId != null && !discordChannelId.isEmpty()) {
                    if (validateDiscordChannel()) {
                        discordEnabled = true;
                        getPluginLogger().info("Discord integration initialized successfully!");
                    } else {
                        getPluginLogger().warning("Discord channel validation failed. Integration disabled.");
                        discordEnabled = false;
                    }
                } else {
                    discordEnabled = true;
                    getPluginLogger().info("Discord integration initialized successfully!");
                }

            } catch (ClassNotFoundException e) {
                getPluginLogger().warning("JDA library not found. Discord integration disabled.");
                getPluginLogger().warning("Make sure JDA is properly shaded in your plugin JAR.");
                discordEnabled = false;
            } catch (Exception e) {
                getPluginLogger().severe("Failed to initialize Discord integration: " + e.getMessage());
                e.printStackTrace();
                discordEnabled = false;
            } finally {
                synchronized (discordInitLock) {
                    discordInitializing = false;
                }
            }
        });
    }

    public static boolean validateDiscordChannel() {
        try {
            if (jda == null || discordChannelId == null || discordChannelId.isEmpty()) {
                return false;
            }

            var channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                getPluginLogger().info("Discord channel validated: #" + channel.getName());
                return true;
            } else {
                getPluginLogger().warning("Discord channel not found or bot doesn't have access!");
                return false;
            }

        } catch (Exception e) {
            getPluginLogger().warning("Error validating Discord channel: " + e.getMessage());
            return false;
        }
    }

    public boolean isDiscordEnabled() {
        return discordEnabled && jda != null;
    }

    public static List<ActionRow> convertToJDAActionRows(DiscordActionRow[] rows) {
        List<ActionRow> actionRows = new ArrayList<>();

        for (DiscordActionRow row : rows) {
            if (row.components != null) {
                List<Button> buttons = new ArrayList<>();

                for (DiscordComponent component : row.components) {
                    if (component.type == 2) {
                        Button button;

                        switch (component.style) {
                            case 1:
                                button = Button.primary(component.custom_id, component.label);
                                break;
                            case 2:
                                button = Button.secondary(component.custom_id, component.label);
                                break;
                            case 3:
                                button = Button.success(component.custom_id, component.label);
                                break;
                            case 4:
                                button = Button.danger(component.custom_id, component.label);
                                break;
                            case 5:
                                if (component.url != null) {
                                    button = Button.link(component.url, component.label);
                                } else {
                                    continue;
                                }
                                break;
                            default:
                                button = Button.secondary(component.custom_id, component.label);
                                break;
                        }

                        if (component.disabled) {
                            button = button.asDisabled();
                        }

                        buttons.add(button);
                    }
                }

                if (!buttons.isEmpty()) {
                    actionRows.add(ActionRow.of(buttons));
                }
            }
        }

        return actionRows;
    }

    public static class DiscordEventListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            getPluginLogger().info("Discord message received from: " + event.getAuthor().getAsTag());
            getPluginLogger().info("Channel ID: " + event.getChannel().getId());
            getPluginLogger().info("Message content: '" + event.getMessage().getContentRaw() + "'");

            if (event.getAuthor().isBot()) {
                getPluginLogger().info("Ignoring message from bot");
                return;
            }

            if (discordChannelId != null && !discordChannelId.isEmpty()) {
                if (!event.getChannel().getId().equals(discordChannelId)) {
                    getPluginLogger().info("Message not from configured channel. Expected: " + discordChannelId + ", Got: " + event.getChannel().getId());
                    return;
                }
            }

            String content = event.getMessage().getContentRaw().trim();
            if (content.isEmpty()) {
                getPluginLogger().info("Empty message content");
                return;
            }

            String userId = event.getAuthor().getId();
            if (isDiscordRateLimited(userId)) {
                event.getChannel().sendMessage("⏰ Please wait before using another command.").queue();
                return;
            }

            getPluginLogger().info("Processing Discord command: " + content);

            deleteDiscordMessage(event.getMessageId());
            processDiscordCommandViaJDA(content, event.getAuthor().getId(), event.getChannel());
        }
        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            String commandName = event.getName();
            String userId = event.getUser().getId();

            event.reply("Event Recorded: " + commandName);
            if (isDiscordRateLimited(userId)) {
                event.reply("⏰ Please wait before using another command.").setEphemeral(true).queue();
                return;
            }

            switch (commandName) {
                case "history":
                    handleJDAHistoryCommand(event);
                    break;
                case "lookup":
                    handleJDALookupCommand(event);
                    break;
                case "check":
                    handleJDACheckCommand(event);
                    break;
                case "debug":
                    handleJDADebugCommand(event);
                    break;
                case "tempban":
                    handleJDABanCommand(event);
                    break;
                default:
                    event.reply("❌ Unknown command").setEphemeral(true).queue();
            }
        }
        public static void processDiscordCommandViaJDA(String content, String authorId, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
            if (content == null || content.trim().isEmpty()) return;

            String[] parts = content.trim().split("\\s+");
            if (parts.length < 1) return;

            String command = parts[0].toLowerCase();

            if (command.equals("debug")) {
                handleJDADebugCommand(null, channel, authorId);
                return;
            }

            switch (command) {
                case "history":
                case "lookup":
                case "check":
                    if (parts.length < 2) {
                        channel.sendMessage("❌ **Invalid command format!**\n\n" +
                                "**Available commands:**\n" +
                                "• `history <player>` - View punishment history\n" +
                                "• `lookup <player>` - Quick player lookup\n" +
                                "• `check <player>` - Check if player is online\n" +
                                "• `tempban <player> <duration> <reason>` - Temporarily ban a player\n" +
                                "• `debug` - Show debug information\n\n" +
                                "**Examples:**\n" +
                                "• `history Notch`\n" +
                                "• `tempban Notch 1d Griefing`").queue();
                        return;
                    }
                    String playerName = parts[1];

                    switch (command) {
                        case "history":
                            handleJDAHistoryCommandDirect(playerName, authorId, channel);
                            break;
                        case "lookup":
                            handleJDALookupCommandDirect(playerName, authorId, channel);
                            break;
                        case "check":
                            handleJDACheckCommandDirect(playerName, authorId, channel);
                            break;
                    }
                    break;

                case "tempban":
                    if (parts.length < 4) {
                        channel.sendMessage("❌ **Invalid tempban format!**\n\n" +
                                "**Usage:** `tempban <player> <duration> <reason>`\n" +
                                "**Example:** `tempban Notch 1d Griefing`").queue();
                        return;
                    }

                    String playerToBan = parts[1];
                    String duration = parts[2];
                    String reason = String.join(" ", Arrays.copyOfRange(parts, 3, parts.length));
                    handleJDABanCommandDirect(playerToBan, duration, reason, authorId, channel);
                    break;

                default:
                    channel.sendMessage("❌ **Unknown command:** `" + command + "`\n\n" +
                            "**Available commands:**\n" +
                            "• `history <player>` - View punishment history\n" +
                            "• `lookup <player>` - Quick player lookup\n" +
                            "• `check <player>` - Check if player is online\n" +
                            "• `tempban <player> <duration> <reason>` - Temporarily ban a player\n" +
                            "• `debug` - Show debug information").queue();
                    break;
            }
        }
        public static void handleJDAHistoryCommandDirect(String playerName, String authorId, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
            CompletableFuture.runAsync(() -> {
                try {
                    List<PunishmentGuiPlugin.PunishmentRecord> records = findPlayerRecords(playerName);

                    if (records == null || records.isEmpty()) {
                        channel.sendMessage("❌ No punishment history found for: `" + playerName + "`").queue();
                        return;
                    }

                    String sessionId = UUID.randomUUID().toString();
                    HistorySession session = new HistorySession(
                            playerName,
                            authorId,
                            "all",
                            records,
                            sessionId
                    );

                    historySessions.put(sessionId, session);
                    sessionExpiry.put(sessionId, System.currentTimeMillis() + SESSION_TIMEOUT);

                    PunishmentGuiPlugin.DiscordEmbed embed = createHistoryEmbed(session);
                    List<ActionRow> actionRows = createJDAActionRows(session);

                    channel.sendMessageEmbeds(convertToJDAEmbed(embed))
                            .setComponents(actionRows)
                            .queue(message -> {
                                session.messageId = message.getId();
                                messageToSession.put(message.getId(), sessionId);
                            });

                } catch (Exception e) {
                    getPluginLogger().warning("Error in JDA history command: " + e.getMessage());
                    channel.sendMessage("❌ Error retrieving punishment history").queue();
                }
            });
        }

        public static void handleJDALookupCommandDirect(String playerName, String authorId, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
            CompletableFuture.runAsync(() -> {
                try {
                    List<PunishmentGuiPlugin.PunishmentRecord> records = findPlayerRecords(playerName);
                    String playerIP = getPlayerIP(playerName);

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("🔍 Player Lookup: " + playerName);
                    embed.setColor(0x3498db);
                    embed.setTimestamp(java.time.Instant.now());

                    if (records != null && !records.isEmpty()) {
                        long activeBans = records.stream()
                                .filter(r -> (r.punishmentType.equals("BAN") || r.punishmentType.equals("TEMPBAN")) && r.active)
                                .count();

                        long activeMutes = records.stream()
                                .filter(r -> r.punishmentType.equals("MUTE") && r.active)
                                .count();

                        embed.addField("Active Bans", String.valueOf(activeBans), true);
                        embed.addField("Active Mutes", String.valueOf(activeMutes), true);
                        embed.addField("Total Punishments", String.valueOf(records.size()), true);

                        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
                        long recentCount = records.stream()
                                .filter(r -> {
                                    try {
                                        LocalDateTime recordTime = LocalDateTime.parse(r.timestamp);
                                        return recordTime.isAfter(weekAgo);
                                    } catch (Exception e) {
                                        return false;
                                    }
                                })
                                .count();

                        if (recentCount > 0) {
                            embed.addField("Recent Activity (7 days)", recentCount + " punishment(s)", false);
                        }
                    } else {
                        embed.addField("Punishment History", "No punishment history found", false);
                    }

                    StringBuilder banEvasionInfo = new StringBuilder();
                    checkDiscordBanEvasion(playerName, playerIP, banEvasionInfo);
                    if (banEvasionInfo.length() > 0) {
                        embed.addField("Ban Evasion Check", banEvasionInfo.toString(), false);
                    }
                    embed.addField("Requested by", "<@" + authorId + ">", false);

                    channel.sendMessageEmbeds(embed.build()).queue();

                } catch (Exception e) {
                    getPluginLogger().warning("Error in JDA lookup command: " + e.getMessage());
                    channel.sendMessage("❌ Error looking up player " + playerName).queue();
                }

            });
        }

        public static void handleJDACheckCommandDirect(String playerName, String authorId, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
            CompletableFuture.runAsync(() -> {
                try {
                    Player onlinePlayer = Bukkit.getPlayer(playerName);

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("🔍 Player Check: " + playerName);
                    embed.setTimestamp(java.time.Instant.now());

                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        embed.setColor(0x2ecc71);
                        embed.addField("Status", "🟢 Online", true);
                        embed.addField("World", onlinePlayer.getWorld().getName(), true);
                        embed.addField("Location",
                                onlinePlayer.getLocation().getBlockX() + ", " +
                                        onlinePlayer.getLocation().getBlockY() + ", " +
                                        onlinePlayer.getLocation().getBlockZ(), true);

                        if (onlinePlayer.hasPermission("punishmentsystem.bypass")) {
                            embed.addField("⚠️ Permissions", "Has bypass permission", false);
                        }

                        embed.addField("Gamemode", onlinePlayer.getGameMode().toString(), true);
                        embed.addField("Health", String.format("%.1f", onlinePlayer.getHealth()) + "/20", true);

                        embed.addField("Requested by", "<@" + authorId + ">", false);
                    } else {
                        embed.setColor(0xe74c3c);
                        embed.addField("Status", "🔴 Offline", true);

                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                        if (offlinePlayer.hasPlayedBefore()) {
                            long lastPlayed = offlinePlayer.getLastPlayed();
                            Date lastPlayedDate = new Date(lastPlayed);
                            embed.addField("Last Seen", lastPlayedDate.toString(), false);

                            long daysSince = (System.currentTimeMillis() - lastPlayed) / (1000 * 60 * 60 * 24);
                            embed.addField("Days Since Last Seen", String.valueOf(daysSince), true);
                        } else {
                            embed.addField("Player Status", "❌ Never joined the server", false);
                        }
                        embed.addBlankField( true);
                        embed.addField("Requested by", "<@" + authorId + ">", false);

                    }

                    channel.sendMessageEmbeds(embed.build()).queue();

                } catch (Exception e) {
                    getPluginLogger().warning("Error in JDA check command: " + e.getMessage());
                    channel.sendMessage("❌ Error checking player " + playerName).queue();
                }
            });
        }

        public static void handleJDABanCommandDirect(String playerName, String duration, String reason, String authorId, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
            try {
                if (playerName == null || playerName.trim().isEmpty()) {
                    channel.sendMessage("❌ Player name cannot be empty!").queue();
                    return;
                }
                playerName = playerName.trim();

                if (duration == null || duration.trim().isEmpty()) {
                    channel.sendMessage("❌ Duration is required!").queue();
                    return;
                }
                duration = duration.trim();

                if (!isValidDuration(duration)) {
                    channel.sendMessage("❌ Invalid duration format! Use formats like: 1d, 2h, 30m, 1w, etc.").queue();
                    return;
                }

                if (reason == null || reason.trim().isEmpty()) {
                    reason = "No reason provided";
                }
                reason = reason.trim();

                if (Bancheckers.hasBanBypass(playerName)) {
                    channel.sendMessage("❌ Cannot ban " + playerName + " - Player has ban bypass permission!").queue();
                    return;
                }

                String banCommand = String.format("tempban %s %s %s", playerName, duration, reason);
                FoliaUtils.executeConsoleCommand(getInstance(), banCommand);

                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(java.awt.Color.RED)
                        .setTitle("🔨 Player Banned")
                        .addField("Player", playerName, true)
                        .addField("Duration", duration, true)
                        .addField("Banned by", "<@" + authorId + ">", true)
                        .addField("Reason", reason, false)
                        .setTimestamp(java.time.Instant.now());

                channel.sendMessageEmbeds(embed.build()).queue();

            } catch (Exception e) {
                channel.sendMessage("❌ An error occurred while processing the ban command: " + e.getMessage()).queue();
                getPluginLogger().warning("Error in Discord ban command: " + e.getMessage());
                e.printStackTrace();
            }
        }
        public static void handleJDADebugCommand(SlashCommandInteractionEvent event, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel, String authorId) {
            CompletableFuture.runAsync(() -> {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("🔧 Discord Debug Information");
                embed.setColor(0x9b59b6);

                embed.setTimestamp(java.time.Instant.now());

                embed.addField("Discord Active", discordActive ? "✅ YES" : "❌ NO", true);
                embed.addField("JDA Status",
                        jda != null && jda.getStatus().name().equals("CONNECTED") ? "✅ CONNECTED" : "❌ DISCONNECTED", true);
                embed.addField("Scheduler Running",
                        discordScheduler != null && !discordScheduler.isShutdown() ? "✅ YES" : "❌ NO", true);

                embed.addField("Punishment History Size", String.valueOf(punishmentHistory.size()), true);
                embed.addField("Active Sessions", String.valueOf(historySessions.size()), true);

                if (!punishmentHistory.isEmpty()) {
                    StringBuilder samplePlayers = new StringBuilder();
                    punishmentHistory.keySet().stream()
                            .limit(5)
                            .forEach(player -> samplePlayers.append("• ").append(player).append("\n"));
                    embed.addField("Sample Players", samplePlayers.toString(), false);
                }

                embed.addField("Usage", "Test with: `history <playername>`", false);

                embed.addBlankField( true);
                embed.addField("Requested by", "<@" + authorId + ">", false);

                if (event != null) {
                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                } else {
                    channel.sendMessageEmbeds(embed.build()).queue();
                }
            });
        }

        @Override
        public void onButtonInteraction(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
            String buttonId = event.getComponentId();
            String userId = event.getUser().getId();

            if (isDiscordRateLimited(userId)) {
                event.reply("⏰ Please wait before using another button.").setEphemeral(true).queue();
                return;
            }

            handleJDAButtonInteraction(event, buttonId);
        }
        public static void handleJDABanCommand(SlashCommandInteractionEvent event) {
            try {
                OptionMapping playerOption = event.getOption("player");
            if (playerOption == null) {
                event.reply("❌ Player name is required!").setEphemeral(true).queue();
                return;
            }
            String playerName = playerOption.getAsString().trim();

            if (playerName.isEmpty()) {
                event.reply("❌ Player name cannot be empty!").setEphemeral(true).queue();
                return;
            }

            OptionMapping durationOption = event.getOption("duration");
            if (durationOption == null) {
                event.reply("❌ Duration is required!").setEphemeral(true).queue();
                return;
            }
            String duration = durationOption.getAsString().trim();

            if (!isValidDuration(duration)) {
                event.reply("❌ Invalid duration format! Use formats like: 1d, 2h, 30m, 1w, etc.").setEphemeral(true).queue();
                return;
            }

            OptionMapping reasonOption = event.getOption("reason");
            String reason = "No reason provided";
            if (reasonOption != null && !reasonOption.getAsString().trim().isEmpty()) {
                reason = reasonOption.getAsString().trim();
            }

            if (Bancheckers.hasBanBypass(playerName)) {
                event.reply("❌ Cannot ban " + playerName + " - Player has ban bypass permission!").setEphemeral(true).queue();
                return;
            }

            String discordUser = event.getUser().getAsTag();
            String banCommand = String.format("tempban %s %s %s", playerName, duration, reason);

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), banCommand);

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(java.awt.Color.RED)
                    .setTitle("🔨 Player Banned")
                    .addField("Player", playerName, true)
                    .addField("Duration", duration, true)
                    .addField("Banned by", discordUser, true)
                    .addField("Reason", reason, false)
                    .setTimestamp(Instant.now());

            event.replyEmbeds(embed.build()).queue();

            } catch (Exception e) {
                event.reply("❌ An error occurred while processing the ban command: " + e.getMessage()).setEphemeral(true).queue();
             e.printStackTrace();
            }
        }

        private static boolean isValidDuration(String duration) {
            if (duration == null || duration.isEmpty()) {
                return false;
            }

            String pattern = "^\\d+[smhdw]$";
            return duration.toLowerCase().matches(pattern);
        }

        public static void handleJDAButtonInteraction(ButtonInteractionEvent event, String buttonId) {
            try {
                String[] parts = buttonId.split("_");
                if (parts.length < 2) {
                    event.reply("❌ Invalid button interaction").setEphemeral(true).queue();
                    return;
                }

                String shortSessionId = parts[0];
                String action = parts[1];

                String fullSessionId = sessionIdMapping.get(shortSessionId);
                if (fullSessionId == null) {
                    event.reply("❌ Session expired. Please run the command again.").setEphemeral(true).queue();
                    return;
                }

                HistorySession session = historySessions.get(fullSessionId);
                if (session == null) {
                    event.reply("❌ Session not found. Please run the command again.").setEphemeral(true).queue();
                    return;
                }

                sessionExpiry.put(fullSessionId, System.currentTimeMillis() + SESSION_TIMEOUT);

                switch (action) {
                    case "all":
                    case "bans":
                    case "mutes":
                    case "kicks":
                        session.category = action;
                        session.page = 0;
                        break;
                    case "prev":
                        if (session.page > 0) {
                            session.page--;
                        }
                        break;
                    case "next":
                        List<PunishmentGuiPlugin.PunishmentRecord> filteredRecords = filterRecordsByCategory(session.allRecords, session.category);
                        int totalPages = Math.max(1, (int) Math.ceil(filteredRecords.size() / 5.0));
                        if (session.page < totalPages - 1) {
                            session.page++;
                        }
                        break;
                    case "page":
                        event.deferEdit().queue();
                        return;
                    default:
                        event.reply("❌ Unknown action").setEphemeral(true).queue();
                        return;
                }

                PunishmentGuiPlugin.DiscordEmbed embed = createHistoryEmbed(session);
                List<ActionRow> actionRows = createJDAActionRows(session);

                event.deferEdit().queue();
                event.getHook().editOriginalEmbeds(convertToJDAEmbed(embed))
                        .setComponents(actionRows)
                        .queue();

            } catch (Exception e) {
                getPluginLogger().warning("Error handling JDA button interaction: " + e.getMessage());
                event.reply("❌ Error processing button interaction").setEphemeral(true).queue();
            }
        }
        public static List<ActionRow> createJDAActionRows(HistorySession session) {
            List<ActionRow> actionRows = new ArrayList<>();

            try {
                String shortId = "s" + String.format("%05d", Math.abs(session.sessionId.hashCode() % 100000));
                sessionIdMapping.put(shortId, session.sessionId);

                List<Button> categoryButtons = new ArrayList<>();
                categoryButtons.add(createCategoryButton(shortId + "_all", "All", session.category.equals("all")));
                categoryButtons.add(createCategoryButton(shortId + "_bans", "Bans", session.category.equals("bans")));
                categoryButtons.add(createCategoryButton(shortId + "_mutes", "Mutes", session.category.equals("mutes")));
                categoryButtons.add(createCategoryButton(shortId + "_kicks", "Kicks", session.category.equals("kicks")));

                actionRows.add(ActionRow.of(categoryButtons));

                List<PunishmentGuiPlugin.PunishmentRecord> filteredRecords = filterRecordsByCategory(session.allRecords, session.category);
                int totalPages = Math.max(1, (int) Math.ceil(filteredRecords.size() / 5.0));

                if (totalPages > 1) {
                    List<Button> navButtons = new ArrayList<>();

                    Button prevButton = Button.secondary(shortId + "_prev", "◀ Previous");
                    if (session.page <= 0) {
                        prevButton = prevButton.asDisabled();
                    }
                    navButtons.add(prevButton);

                    Button pageButton = Button.secondary(shortId + "_page", "Page " + (session.page + 1) + "/" + totalPages)
                            .asDisabled();
                    navButtons.add(pageButton);

                    Button nextButton = Button.secondary(shortId + "_next", "Next ▶");
                    if (session.page >= totalPages - 1) {
                        nextButton = nextButton.asDisabled();
                    }
                    navButtons.add(nextButton);

                    actionRows.add(ActionRow.of(navButtons));
                }

                return actionRows;

            } catch (Exception e) {
                getPluginLogger().warning("Error creating JDA action rows: " + e.getMessage());
                return new ArrayList<>();
            }
        }
        public static Button createCategoryButton(String customId, String label, boolean isActive) {
            if (isActive) {
                return Button.primary(customId, label);
            } else {
                return Button.secondary(customId, label);
            }
        }

        public static final long SESSION_TIMEOUT = 30 * 60 * 1000;

        public static MessageEmbed convertToJDAEmbed(PunishmentGuiPlugin.DiscordEmbed embed) {
            EmbedBuilder builder = new EmbedBuilder();

            if (embed.title != null) builder.setTitle(embed.title);
            if (embed.description != null) builder.setDescription(embed.description);
            if (embed.color != 0) builder.setColor(embed.color);
            if (embed.timestamp != null) builder.setTimestamp(java.time.Instant.parse(embed.timestamp));
            if (embed.footer != null) builder.setFooter(embed.footer.text);

            if (embed.fields != null) {
                for (PunishmentGuiPlugin.DiscordEmbed.Field field : embed.fields) {
                    builder.addField(field.name, field.value, field.inline);
                }
            }

            return builder.build();
        }

        public static List<Button> convertToJDAButtons(DiscordActionRow[] rows) {
            List<Button> buttons = new ArrayList<>();

            for (DiscordActionRow row : rows) {
                if (row.components != null) {
                    for (DiscordComponent component : row.components) {
                        if (component.type == 2) {
                            Button button = createButton(component);
                            if (button != null) {
                                buttons.add(button);
                            }
                        }
                    }
                }
            }

            return buttons;
        }
        public static Button createButton(DiscordComponent component) {
            Button button = null;

            switch (component.style) {
                case 1:
                    button = Button.primary(component.custom_id, component.label);
                    break;
                case 2:
                    button = Button.secondary(component.custom_id, component.label);
                    break;
                case 3:
                    button = Button.success(component.custom_id, component.label);
                    break;
                case 4:
                    button = Button.danger(component.custom_id, component.label);
                    break;
                case 5:
                    if (component.url != null) {
                        button = Button.link(component.url, component.label);
                    }
                    break;
                default:
                    button = Button.secondary(component.custom_id, component.label);
                    break;
            }

            if (button != null && component.disabled) {
                button = button.asDisabled();
            }

            return button;
        }

        public static void shutdownDiscordSystem() {
            try {
                if (discordScheduler != null && !discordScheduler.isShutdown()) {
                    discordScheduler.shutdown();
                    try {
                        if (!discordScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                            discordScheduler.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        discordScheduler.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }

                if (jda != null) {
                    jda.shutdown();
                    try {
                        if (!jda.awaitShutdown(5, TimeUnit.SECONDS)) {
                            jda.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        jda.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                    getPluginLogger().info("JDA Discord bot shutdown");
                }

                if (httpClient != null) {
                    httpClient = null;
                }

                historySessions.clear();
                sessionExpiry.clear();
                sessionIdMapping.clear();
                messageToSession.clear();
                lastDiscordMessageTime.clear();

                getPluginLogger().info("Discord system shutdown complete");

            } catch (Exception e) {
                getPluginLogger().warning("Error during Discord system shutdown: " + e.getMessage());
                e.printStackTrace();
            }
        }
        public static void handleJDAHistoryCommand(SlashCommandInteractionEvent event) {
            String playerName = event.getOption("player").getAsString();

            event.deferReply().queue();

            CompletableFuture.runAsync(() -> {
                try {
                    List<PunishmentGuiPlugin.PunishmentRecord> records = findPlayerRecords(playerName);

                    if (records == null || records.isEmpty()) {
                        event.getHook().sendMessage("❌ No punishment history found for: `" + playerName + "`").queue();
                        return;
                    }

                    String sessionId = UUID.randomUUID().toString();
                    HistorySession session = new HistorySession(
                            playerName,
                            event.getUser().getId(),
                            "all",
                            records,
                            sessionId
                    );

                    historySessions.put(sessionId, session);
                    sessionExpiry.put(sessionId, System.currentTimeMillis() + SESSION_TIMEOUT);

                    PunishmentGuiPlugin.DiscordEmbed embed = createHistoryEmbed(session);
                    List<ActionRow> actionRows = createJDAActionRows(session);

                    event.getHook().sendMessageEmbeds(convertToJDAEmbed(embed))
                            .setComponents(actionRows)
                            .queue(message -> {
                                session.messageId = message.getId();
                                messageToSession.put(message.getId(), sessionId);
                            });

                } catch (Exception e) {
                    getPluginLogger().warning("Error in JDA history command: " + e.getMessage());
                    event.getHook().sendMessage("❌ Error retrieving punishment history").queue();
                }
            });
        }
        public static void handleJDALookupCommand(SlashCommandInteractionEvent event) {
            String playerName = event.getOption("player").getAsString();

            event.deferReply().queue();

            CompletableFuture.runAsync(() -> {
                try {
                    List<PunishmentGuiPlugin.PunishmentRecord> records = findPlayerRecords(playerName);
                    String playerIP = getPlayerIP(playerName);

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("🔍 Player Lookup: " + playerName);
                    embed.setColor(0x3498db);
                    embed.setTimestamp(java.time.Instant.now());

                    if (records != null && !records.isEmpty()) {
                        long activeBans = records.stream()
                                .filter(r -> (r.punishmentType.equals("BAN") || r.punishmentType.equals("TEMPBAN")) && r.active)
                                .count();

                        long activeMutes = records.stream()
                                .filter(r -> r.punishmentType.equals("MUTE") && r.active)
                                .count();

                        embed.addField("Active Bans", String.valueOf(activeBans), true);
                        embed.addField("Active Mutes", String.valueOf(activeMutes), true);
                        embed.addField("Total Punishments", String.valueOf(records.size()), true);

                        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
                        long recentCount = records.stream()
                                .filter(r -> {
                                    try {
                                        LocalDateTime recordTime = LocalDateTime.parse(r.timestamp);
                                        return recordTime.isAfter(weekAgo);
                                    } catch (Exception e) {
                                        return false;
                                    }
                                })
                                .count();

                        if (recentCount > 0) {
                            embed.addField("Recent Activity (7 days)", recentCount + " punishment(s)", false);
                        }
                    } else {
                        embed.addField("Punishment History", "No punishment history found", false);
                    }

                    StringBuilder banEvasionInfo = new StringBuilder();
                    checkDiscordBanEvasion(playerName, playerIP, banEvasionInfo);
                    if (banEvasionInfo.length() > 0) {
                        embed.addField("Ban Evasion Check", banEvasionInfo.toString(), false);
                    }

                    event.getHook().sendMessageEmbeds(embed.build()).queue();

                } catch (Exception e) {
                    getPluginLogger().warning("Error in JDA lookup command: " + e.getMessage());

                    EmbedBuilder errorEmbed = new EmbedBuilder();
                    errorEmbed.setTitle("❌ Error");
                    errorEmbed.setDescription("Error looking up player " + playerName);
                    errorEmbed.setColor(0xe74c3c);
                    errorEmbed.setTimestamp(java.time.Instant.now());

                    event.getHook().sendMessageEmbeds(errorEmbed.build()).queue();
                }
            });
        }
        public static void handleJDACheckCommand(SlashCommandInteractionEvent event) {
            String playerName = event.getOption("player").getAsString();

            event.deferReply().queue();

            CompletableFuture.runAsync(() -> {
                try {
                    Player onlinePlayer = Bukkit.getPlayer(playerName);

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("🔍 Player Check: " + playerName);
                    embed.setTimestamp(java.time.Instant.now());

                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        embed.setColor(0x2ecc71);
                        embed.addField("Status", "🟢 Online", true);
                        embed.addField("World", onlinePlayer.getWorld().getName(), true);
                        embed.addField("Location",
                                onlinePlayer.getLocation().getBlockX() + ", " +
                                        onlinePlayer.getLocation().getBlockY() + ", " +
                                        onlinePlayer.getLocation().getBlockZ(), true);

                        if (onlinePlayer.hasPermission("punishmentsystem.bypass")) {
                            embed.addField("⚠️ Permissions", "Has bypass permission", false);
                        }

                        embed.addField("Gamemode", onlinePlayer.getGameMode().toString(), true);
                        embed.addField("Health", String.format("%.1f", onlinePlayer.getHealth()) + "/20", true);
                    } else {
                        embed.setColor(0xe74c3c);
                        embed.addField("Status", "🔴 Offline", true);

                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                        if (offlinePlayer.hasPlayedBefore()) {
                            long lastPlayed = offlinePlayer.getLastPlayed();
                            Date lastPlayedDate = new Date(lastPlayed);
                            embed.addField("Last Seen", lastPlayedDate.toString(), false);

                            long daysSince = (System.currentTimeMillis() - lastPlayed) / (1000 * 60 * 60 * 24);
                            embed.addField("Days Since Last Seen", String.valueOf(daysSince), true);
                        } else {
                            embed.addField("Player Status", "❌ Never joined the server", false);
                        }
                    }

                    event.getHook().sendMessageEmbeds(embed.build()).queue();

                } catch (Exception e) {
                    getPluginLogger().warning("Error in JDA check command: " + e.getMessage());

                    EmbedBuilder errorEmbed = new EmbedBuilder();
                    errorEmbed.setTitle("❌ Error");
                    errorEmbed.setDescription("Error checking player " + playerName);
                    errorEmbed.setColor(0xe74c3c);
                    errorEmbed.setTimestamp(java.time.Instant.now());

                    event.getHook().sendMessageEmbeds(errorEmbed.build()).queue();
                }
            });
        }

        public static void handleJDADebugCommand(SlashCommandInteractionEvent event) {
            event.deferReply().queue();

            CompletableFuture.runAsync(() -> {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("🔧 Discord Debug Information");
                embed.setColor(0x9b59b6);
                embed.setTimestamp(java.time.Instant.now());

                embed.addField("Discord Active", discordActive ? "✅ YES" : "❌ NO", true);
                embed.addField("JDA Status",
                        jda != null && jda.getStatus().name().equals("CONNECTED") ? "✅ CONNECTED" : "❌ DISCONNECTED", true);
                embed.addField("Scheduler Running",
                        discordScheduler != null && !discordScheduler.isShutdown() ? "✅ YES" : "❌ NO", true);

                embed.addField("Punishment History Size", String.valueOf(punishmentHistory.size()), true);
                embed.addField("Active Sessions", String.valueOf(historySessions.size()), true);

                if (!punishmentHistory.isEmpty()) {
                    StringBuilder samplePlayers = new StringBuilder();
                    punishmentHistory.keySet().stream()
                            .limit(5)
                            .forEach(player -> samplePlayers.append("• ").append(player).append("\n"));
                    embed.addField("Sample Players", samplePlayers.toString(), false);
                }

                embed.addField("Usage", "Test with: `/history <playername>`", false);

                event.getHook().sendMessageEmbeds(embed.build()).queue();
            });
        }

        public boolean isDiscordReady() {
            return jda != null && jda.getStatus() == JDA.Status.CONNECTED && discordEnabled;
        }}

    public static void checkDiscordStatus(Player player) {
        player.sendMessage(ChatColor.YELLOW + "=== Discord Status ===");
        player.sendMessage(ChatColor.WHITE + "Active: " + (discordActive ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        player.sendMessage(ChatColor.WHITE + "Bot Token: " + (discordBotToken.isEmpty() || discordBotToken.contains("YOUR_BOT_TOKEN_HERE") ?
                ChatColor.RED + "Not configured" : ChatColor.GREEN + "Configured"));
        player.sendMessage(ChatColor.WHITE + "Channel ID: " + (discordChannelId.isEmpty() || discordChannelId.contains("YOUR_CHANNEL_ID_HERE") ?
                ChatColor.RED + "Not configured" : ChatColor.GREEN + discordChannelId));
    }
    public static boolean isValidDuration(String duration) {
        if (duration == null || duration.trim().isEmpty()) {
            return false;
        }

        String cleanDuration = duration.trim().toLowerCase();

        if (cleanDuration.equals("permanent") ||
                cleanDuration.equals("indefinite") ||
                cleanDuration.equals("forever") ||
                cleanDuration.equals("0")) {
            return true;
        }

        String timePattern = "^\\d+[smhdwMy](\\s*\\d+[smhdwMy])*$";
        if (cleanDuration.matches(timePattern)) {
            return true;
        }

        try {
            long number = Long.parseLong(cleanDuration);
            return number >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
