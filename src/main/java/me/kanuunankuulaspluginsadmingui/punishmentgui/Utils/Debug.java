package me.kanuunankuulaspluginsadmingui.punishmentgui.Utils;

import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static org.bukkit.Bukkit.getLogger;
import static org.bukkit.Bukkit.getServer;

public class Debug {
    public static void Debugger(String text, String version) {
        if (!debugActive) {return;}

        if (version.equals("warning")) {
            getLogger().warning(text);
        } else if (version.equals("logger")) {
            getLogger().info(text);
        }
    }

    public static void debugPunishmentExecution(Player player, PunishmentGuiPlugin.BanSession session) {
        debugActive = getInstance().getConfig().getBoolean("discord.active", false);
        if (!debugActive) { return; }

        getPluginLogger().info("=== DEBUG: Punishment Execution ===");
        getPluginLogger().info("Executor: " + player.getName());
        getPluginLogger().info("Target: " + session.targetPlayer);
        getPluginLogger().info("Punishment Type: " + session.punishmentType);
        getPluginLogger().info("Selected Reasons: " + session.selectedReasons);
        getPluginLogger().info("Custom Reason: " + session.customReason);
        getPluginLogger().info("Combined Reasons: " + session.getCombinedReasons());
        getPluginLogger().info("Calculated Duration: " + session.getCalculatedDuration());

        Player targetPlayer = Bukkit.getPlayer(session.targetPlayer);
        if (targetPlayer != null) {
            getPluginLogger().info("Target is online: " + targetPlayer.isOnline());
            getPluginLogger().info("Target UUID: " + targetPlayer.getUniqueId());
        } else {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(session.targetPlayer);
            getPluginLogger().info("Target offline UUID: " + offlineTarget.getUniqueId());
            getPluginLogger().info("Target has played before: " + offlineTarget.hasPlayedBefore());
        }

        if (getServer().getPluginManager().getPlugin("EssentialsX") != null) {
            getPluginLogger().info("EssentialsX is loaded");
        }
        if (getServer().getPluginManager().getPlugin("LiteBans") != null) {
            getPluginLogger().info("LiteBans is loaded");
        }
        if (getServer().getPluginManager().getPlugin("AdvancedBan") != null) {
            getPluginLogger().info("AdvancedBan is loaded");
        }

        getPluginLogger().info("=== END DEBUG ===");
    }
    public static void debugPlugin(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Plugin Debug Information ===");
        player.sendMessage(ChatColor.WHITE + "Plugin Version: " + ChatColor.GREEN + getInstance().getDescription().getVersion());
        player.sendMessage(ChatColor.WHITE + "Data Folder: " + ChatColor.GRAY + dataFolder.getPath());
        player.sendMessage(ChatColor.WHITE + "Punishment Data Folder: " + ChatColor.GRAY + punishmentDataFolder.getPath());
        player.sendMessage(ChatColor.WHITE + "Punishment Data Folder Exists: " + (punishmentDataFolder.exists() ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));

        if (punishmentDataFolder.exists()) {
            File[] files = punishmentDataFolder.listFiles((dir, name) -> name.endsWith(".dat"));
            player.sendMessage(ChatColor.WHITE + "Data Files Found: " + ChatColor.GREEN + (files != null ? files.length : 0));
        }

        player.sendMessage(ChatColor.WHITE + "Punishment History Size: " + ChatColor.GREEN + punishmentHistory.size());
        player.sendMessage(ChatColor.WHITE + "Player IPs Size: " + ChatColor.GREEN + playerIPs.size());
        player.sendMessage(ChatColor.WHITE + "Active Sessions: " + ChatColor.GREEN + activeSessions.size());
        player.sendMessage(ChatColor.WHITE + "Chat Input Waiting: " + ChatColor.GREEN + chatInputWaiting.size());

        player.sendMessage(ChatColor.WHITE + "Discord Active: " + (discordActive ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        player.sendMessage(ChatColor.WHITE + "Discord Bot Token Set: " + (discordBotToken != null && !discordBotToken.isEmpty() && !discordBotToken.contains("YOUR_BOT_TOKEN_HERE") ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        player.sendMessage(ChatColor.WHITE + "Discord Channel ID Set: " + (discordChannelId != null && !discordChannelId.isEmpty() && !discordChannelId.contains("YOUR_CHANNEL_ID_HERE") ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
    }

    public static void debugDiscordHistory(Player player, String testPlayer) {
        player.sendMessage(ChatColor.GOLD + "=== Discord History Debug ===");
        player.sendMessage(ChatColor.WHITE + "Punishment History Size: " + ChatColor.GREEN + punishmentHistory.size());
        player.sendMessage(ChatColor.WHITE + "Player IPs Size: " + ChatColor.GREEN + playerIPs.size());

        if (testPlayer != null) {
            player.sendMessage(ChatColor.WHITE + "Testing search for: " + ChatColor.YELLOW + testPlayer);

            String searchName = testPlayer.toLowerCase().trim();
            boolean found = false;

            for (String key : punishmentHistory.keySet()) {
                if (key.toLowerCase().equals(searchName)) {
                    player.sendMessage(ChatColor.GREEN + "✓ Found exact match: " + key);
                    player.sendMessage(ChatColor.GRAY + "  Records: " + punishmentHistory.get(key).size());
                    found = true;
                    break;
                }
            }

            if (!found) {
                player.sendMessage(ChatColor.RED + "✗ No exact match found");

                List<String> partialMatches = new ArrayList<>();
                for (String key : punishmentHistory.keySet()) {
                    if (key.toLowerCase().contains(searchName)) {
                        partialMatches.add(key);
                    }
                }

                if (!partialMatches.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Partial matches: " + String.join(", ", partialMatches));
                }
            }
        }

        if (punishmentHistory.size() > 0) {
            player.sendMessage(ChatColor.WHITE + "Example players in history:");
            punishmentHistory.keySet().stream().limit(10).forEach(playerName -> {
                int recordCount = punishmentHistory.get(playerName).size();
                player.sendMessage(ChatColor.GRAY + "  - " + playerName + " (" + recordCount + " records)");
            });
        } else {
            player.sendMessage(ChatColor.RED + "No players in punishment history!");
        }
    }

}
