package me.kanuunankuulaspluginsadmingui.punishmentgui.EventListeners;

import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.executers.BuiltInCommands.*;

public class MuteListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!builtInSystemActive) return;

        String playerName = event.getName();

        PunishmentGuiPlugin.ActiveBan ban = activeBans.get(playerName.toLowerCase());
        if (ban != null) {
            if (!ban.isPermanent && ban.isExpired()) {
                getPluginLogger().info("Removing expired ban for " + playerName);
                activeBans.remove(playerName.toLowerCase());
                saveActivePunishments();
                return;
            }

            String kickMessage = ChatColor.RED + "You are banned from this server!\n";

            if (ban.isPermanent) {
                kickMessage += ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + "Permanent\n";
            } else {
                long remainingTime = ban.expiryTime - System.currentTimeMillis();
                String remainingDuration = formatRemainingTime(remainingTime);
                kickMessage += ChatColor.YELLOW + "Expires: " + ChatColor.WHITE +
                        new java.util.Date(ban.expiryTime) + "\n";
                kickMessage += ChatColor.YELLOW + "Time remaining: " + ChatColor.WHITE + remainingDuration + "\n";
            }

            kickMessage += ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + ban.reason + "\n";
            kickMessage += ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + ban.staffMember;

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
            return;
        }

        String playerIP = cleanIP(event.getAddress().getHostAddress());
        PunishmentGuiPlugin.ActiveIPBan ipBan = activeIPBans.get(playerIP);
        if (ipBan != null) {
            if (!ipBan.isPermanent && ipBan.isExpired()) {
                getPluginLogger().info("Removing expired IP ban for " + playerIP);
                activeIPBans.remove(playerIP);
                saveActivePunishments();
                return;
            }

            String kickMessage = ChatColor.RED + "Your IP address is banned from this server!\n";
            if (ipBan.isPermanent) {
                kickMessage += ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + "Permanent\n";
            } else {
                long remainingTime = ipBan.expiryTime - System.currentTimeMillis();
                String remainingDuration = formatRemainingTime(remainingTime);
                kickMessage += ChatColor.YELLOW + "Expires: " + ChatColor.WHITE +
                        new java.util.Date(ipBan.expiryTime) + "\n";
                kickMessage += ChatColor.YELLOW + "Time remaining: " + ChatColor.WHITE + remainingDuration + "\n";
            }
            kickMessage += ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + ipBan.reason + "\n";
            kickMessage += ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + ipBan.staffMember;

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
        }

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!builtInSystemActive) return;

        String playerName = event.getPlayer().getName();

        if (isPlayerMuted(playerName)) {
            PunishmentGuiPlugin.ActiveMute mute = activeMutes.get(playerName.toLowerCase());
            if (mute != null) {
                if (mute.isExpired()) {
                    activeMutes.remove(playerName.toLowerCase());
                    saveActivePunishments();
                } else {
                    String message = ChatColor.RED + "You are currently muted!\n";
                    if (mute.isPermanent) {
                        message += ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + "Permanent\n";
                    } else {
                        message += ChatColor.YELLOW + "Expires: " + ChatColor.WHITE + new java.util.Date(mute.expiryTime) + "\n";
                    }
                    message += ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + mute.reason;

                    event.getPlayer().sendMessage(message);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!builtInSystemActive) return;

        String playerName = event.getPlayer().getName();

        if (isPlayerMuted(playerName)) {
            PunishmentGuiPlugin.ActiveMute mute = activeMutes.get(playerName.toLowerCase());
            if (mute != null) {
                if (mute.isExpired()) {
                    activeMutes.remove(playerName.toLowerCase());
                    saveActivePunishments();
                    return;
                }

                event.setCancelled(true);

                String message = ChatColor.RED + "You are muted and cannot chat!\n";
                if (mute.isPermanent) {
                    message += ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + "Permanent\n";
                } else {
                    long remainingTime = mute.expiryTime - System.currentTimeMillis();
                    String timeLeft = formatRemainingTime(remainingTime);
                    message += ChatColor.YELLOW + "Time remaining: " + ChatColor.WHITE + timeLeft + "\n";
                }
                message += ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + mute.reason;

                event.getPlayer().sendMessage(message);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!builtInSystemActive) return;

        String playerName = event.getPlayer().getName();
        String command = event.getMessage().toLowerCase();

        String[] mutedCommands = {"/msg", "/tell", "/whisper", "/m", "/w", "/r", "/reply", "/me", "/say"};

        for (String mutedCmd : mutedCommands) {
            if (command.startsWith(mutedCmd + " ")) {
                if (isPlayerMuted(playerName)) {
                    PunishmentGuiPlugin.ActiveMute mute = activeMutes.get(playerName.toLowerCase());
                    if (mute != null && !mute.isExpired()) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage(ChatColor.RED + "You cannot use this command while muted!");
                        return;
                    }
                }
            }
        }
    }

    private String formatRemainingTime(long milliseconds) {
        if (milliseconds <= 0) return "Expired";

        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h " + (minutes % 60) + "m";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
}