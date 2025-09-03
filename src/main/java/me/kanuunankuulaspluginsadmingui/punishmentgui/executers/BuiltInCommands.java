package me.kanuunankuulaspluginsadmingui.punishmentgui.executers;

import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.Log.*;

public class BuiltInCommands implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!builtInSystemActive) return false;

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "ban":
                return handleBanCommand(sender, args);
            case "tempban":
                return handleTempBanCommand(sender, args);
            case "unban":
            case "pardon":
                return handleUnbanCommand(sender, args);
            case "mute":
                return handleMuteCommand(sender, args);
            case "tempmute":
                return handleTempMuteCommand(sender, args);
            case "unmute":
                return handleUnmuteCommand(sender, args);
            case "kick":
                return handleKickCommand(sender, args);
            case "ban-ip":
            case "banip":
                return handleBanIPCommand(sender, args);
            case "unbanip":
            case "pardon-ip":
                return handleUnbanIPCommand(sender, args);
            case "banlist":
                return handleBanListCommand(sender, args);
            case "mutelist":
                return handleMuteListCommand(sender, args);
        }

        return false;
    }

    private boolean handleBanCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.ban")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to ban players!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /ban <player> [reason]");
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "No reason specified";

        if (isPlayerBanned(targetName)) {
            sender.sendMessage(ChatColor.RED + "Player " + targetName + " is already banned!");
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.hasPermission("punishmentsystem.bypass")) {
            sender.sendMessage(ChatColor.RED + "Cannot ban " + targetName + " - they have bypass permission!");
            return true;
        }

        PunishmentGuiPlugin.ActiveBan ban = new PunishmentGuiPlugin.ActiveBan(targetName, reason, sender.getName(), 0);
        activeBans.put(targetName.toLowerCase(), ban);

        if (target != null) {
            target.kickPlayer(ChatColor.RED + "You have been permanently banned!\n" +
                    ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + reason + "\n" +
                    ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + sender.getName());
        }

        saveActivePunishments();
        logPunishment(targetName, "BAN", reason, "Permanent", sender.getName());

        Bukkit.broadcastMessage(ChatColor.RED + targetName + " has been permanently banned by " + sender.getName() + " for: " + reason);

        return true;
    }

    private boolean handleTempBanCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.tempban")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to temporarily ban players!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tempban <player> <duration> [reason]");
            return true;
        }

        String targetName = args[0];
        String durationStr = args[1];
        String reason = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "No reason specified";

        if (isPlayerBanned(targetName)) {
            sender.sendMessage(ChatColor.RED + "Player " + targetName + " is already banned!");
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.hasPermission("punishmentsystem.bypass")) {
            sender.sendMessage(ChatColor.RED + "Cannot ban " + targetName + " - they have bypass permission!");
            return true;
        }

        long duration = parseDurationToMillis(durationStr);
        if (duration <= 0) {
            sender.sendMessage(ChatColor.RED + "Invalid duration format! Use format like: 1d, 2h, 30m, 45s");
            return true;
        }

        long expiryTime = System.currentTimeMillis() + duration;
        PunishmentGuiPlugin.ActiveBan ban = new PunishmentGuiPlugin.ActiveBan(targetName, reason, sender.getName(), expiryTime);
        activeBans.put(targetName.toLowerCase(), ban);

        if (target != null) {
            target.kickPlayer(ChatColor.RED + "You have been temporarily banned!\n" +
                    ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + durationStr + "\n" +
                    ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + reason + "\n" +
                    ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + sender.getName());
        }

        saveActivePunishments();
        logPunishment(targetName, "TEMPBAN", reason, durationStr, sender.getName());

        Bukkit.broadcastMessage(ChatColor.RED + targetName + " has been temporarily banned by " + sender.getName() + " for " + durationStr + ": " + reason);

        return true;
    }

    private boolean handleUnbanCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.unban")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to unban players!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unban <player>");
            return true;
        }

        String targetName = args[0];

        if (!isPlayerBanned(targetName)) {
            sender.sendMessage(ChatColor.RED + "Player " + targetName + " is not banned!");
            return true;
        }

        activeBans.remove(targetName.toLowerCase());
        saveActivePunishments();
        logPunishment(targetName, "UNBAN", "Unbanned", "No Duration", sender.getName());

        sender.sendMessage(ChatColor.GREEN + "Player " + targetName + " has been unbanned!");

        return true;
    }

    private boolean handleMuteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.mute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to mute players!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /mute <player> [reason]");
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "No reason specified";

        if (isPlayerMuted(targetName)) {
            sender.sendMessage(ChatColor.RED + "Player " + targetName + " is already muted!");
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.hasPermission("punishmentsystem.bypass")) {
            sender.sendMessage(ChatColor.RED + "Cannot mute " + targetName + " - they have bypass permission!");
            return true;
        }

        PunishmentGuiPlugin.ActiveMute mute = new PunishmentGuiPlugin.ActiveMute(targetName, reason, sender.getName(), 0);
        activeMutes.put(targetName.toLowerCase(), mute);

        if (target != null) {
            target.sendMessage(ChatColor.RED + "You have been permanently muted!\n" +
                    ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + reason + "\n" +
                    ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + sender.getName());
        }

        saveActivePunishments();
        logPunishment(targetName, "MUTE", reason, "Permanent", sender.getName());

        sender.sendMessage(ChatColor.GREEN + targetName + " has been permanently muted for: " + reason);

        return true;
    }

    private boolean handleTempMuteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.mute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to mute players!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tempmute <player> <duration> [reason]");
            return true;
        }

        String targetName = args[0];
        String durationStr = args[1];
        String reason = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "No reason specified";

        if (isPlayerMuted(targetName)) {
            sender.sendMessage(ChatColor.RED + "Player " + targetName + " is already muted!");
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target != null && target.hasPermission("punishmentsystem.bypass")) {
            sender.sendMessage(ChatColor.RED + "Cannot mute " + targetName + " - they have bypass permission!");
            return true;
        }

        long duration = parseDurationToMillis(durationStr);
        if (duration <= 0) {
            sender.sendMessage(ChatColor.RED + "Invalid duration format! Use format like: 1d, 2h, 30m, 45s");
            return true;
        }

        long expiryTime = System.currentTimeMillis() + duration;
        PunishmentGuiPlugin.ActiveMute mute = new PunishmentGuiPlugin.ActiveMute(targetName, reason, sender.getName(), expiryTime);
        activeMutes.put(targetName.toLowerCase(), mute);

        if (target != null) {
            target.sendMessage(ChatColor.RED + "You have been temporarily muted!\n" +
                    ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + durationStr + "\n" +
                    ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + reason + "\n" +
                    ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + sender.getName());
        }

        saveActivePunishments();
        logPunishment(targetName, "TEMPMUTE", reason, durationStr, sender.getName());

        sender.sendMessage(ChatColor.GREEN + targetName + " has been temporarily muted for " + durationStr + ": " + reason);

        return true;
    }

    private boolean handleUnmuteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.unmute")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to unmute players!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unmute <player>");
            return true;
        }

        String targetName = args[0];

        if (!isPlayerMuted(targetName)) {
            sender.sendMessage(ChatColor.RED + "Player " + targetName + " is not muted!");
            return true;
        }

        activeMutes.remove(targetName.toLowerCase());
        saveActivePunishments();
        logPunishment(targetName, "UNMUTE", "Unmuted", "No Duration", sender.getName());

        Player target = Bukkit.getPlayer(targetName);
        if (target != null) {
            target.sendMessage(ChatColor.GREEN + "You have been unmuted!");
        }

        sender.sendMessage(ChatColor.GREEN + "Player " + targetName + " has been unmuted!");

        return true;
    }

    private boolean handleKickCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.kick")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to kick players!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /kick <player> [reason]");
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "No reason specified";

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player " + targetName + " is not online!");
            return true;
        }

        if (target.hasPermission("punishmentsystem.bypass")) {
            sender.sendMessage(ChatColor.RED + "Cannot kick " + targetName + " - they have bypass permission!");
            return true;
        }

        target.kickPlayer(ChatColor.RED + "You have been kicked!\n" +
                ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + reason + "\n" +
                ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + sender.getName());

        logPunishment(targetName, "KICK", reason, "Instant", sender.getName());

        Bukkit.broadcastMessage(ChatColor.YELLOW + targetName + " has been kicked by " + sender.getName() + " for: " + reason);

        return true;
    }

    private boolean handleBanIPCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.banip")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to IP ban players!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /banip <player|ip> [reason]");
            return true;
        }

        String target = args[0];
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "No reason specified";

        String ipAddress;
        String targetPlayerName = null;

        if (target.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            ipAddress = target;
            targetPlayerName = "Unknown Player";
        } else {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer != null) {
                ipAddress = cleanIP(targetPlayer.getAddress().getAddress().getHostAddress());
                targetPlayerName = targetPlayer.getName();
            } else {
                ipAddress = getPlayerIP(target);
                if ("Unknown".equals(ipAddress)) {
                    sender.sendMessage(ChatColor.RED + "Could not determine IP address for player: " + target);
                    return true;
                }
                targetPlayerName = target;
            }
        }

        if (activeIPBans.containsKey(ipAddress)) {
            sender.sendMessage(ChatColor.RED + "IP address " + ipAddress + " is already banned!");
            return true;
        }

        PunishmentGuiPlugin.ActiveIPBan ipBan = new PunishmentGuiPlugin.ActiveIPBan(ipAddress, reason, sender.getName(), 0);
        activeIPBans.put(ipAddress, ipBan);

        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerIP = cleanIP(player.getAddress().getAddress().getHostAddress());
            if (playerIP.equals(ipAddress)) {
                player.kickPlayer(ChatColor.RED + "Your IP address has been banned!\n" +
                        ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + reason + "\n" +
                        ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + sender.getName());
            }
        }

        saveActivePunishments();

        logPunishment("IP of user:" + targetPlayerName, "IPBAN", reason, "Permanent", sender.getName());

        sender.sendMessage(ChatColor.GREEN + "IP address " + ipAddress + " has been permanently banned!");

        return true;
    }

    private boolean handleUnbanIPCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.unbanip")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to unban IP addresses!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unbanip <player|ip>");
            return true;
        }

        String target = args[0];
        String ipAddress;
        String targetPlayerName = null;

        if (target.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            ipAddress = target;
            targetPlayerName = "Unknown Player";
        } else {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer != null) {
                ipAddress = cleanIP(targetPlayer.getAddress().getAddress().getHostAddress());
                targetPlayerName = targetPlayer.getName();
            } else {
                ipAddress = getPlayerIP(target);
                if ("Unknown".equals(ipAddress)) {
                    sender.sendMessage(ChatColor.RED + "Could not determine IP address for player: " + target);
                    return true;
                }
                targetPlayerName = target;
            }
        }

        if (!activeIPBans.containsKey(ipAddress)) {
            sender.sendMessage(ChatColor.RED + "IP address " + ipAddress + " is not banned!");
            return true;
        }

        activeIPBans.remove(ipAddress);
        saveActivePunishments();

        logPunishment("IP of player:" + targetPlayerName, "IPUNBAN", "IP Unbanned", "No Duration", sender.getName());

        sender.sendMessage(ChatColor.GREEN + "IP address " + ipAddress + " has been unbanned!");

        return true;
    }

    private boolean handleBanListCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.banlist")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view the ban list!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Active Bans (" + activeBans.size() + ") ===");

        if (activeBans.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No active bans!");
            return true;
        }

        for (PunishmentGuiPlugin.ActiveBan ban : activeBans.values()) {
            String duration = ban.isPermanent ? "Permanent" : "Until " + new java.util.Date(ban.expiryTime);
            sender.sendMessage(ChatColor.YELLOW + ban.playerName + ChatColor.WHITE + " - " +
                    ChatColor.RED + ban.reason + ChatColor.WHITE + " (" + duration + ")");
        }

        return true;
    }

    private boolean handleMuteListCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punishmentsystem.mutelist")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view the mute list!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Active Mutes (" + activeMutes.size() + ") ===");

        if (activeMutes.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No active mutes!");
            return true;
        }

        for (PunishmentGuiPlugin.ActiveMute mute : activeMutes.values()) {
            String duration = mute.isPermanent ? "Permanent" : "Until " + new java.util.Date(mute.expiryTime);
            sender.sendMessage(ChatColor.YELLOW + mute.playerName + ChatColor.WHITE + " - " +
                    ChatColor.RED + mute.reason + ChatColor.WHITE + " (" + duration + ")");
        }

        return true;
    }

    public static boolean isPlayerBanned(String playerName) {
        if (!builtInSystemActive) return false;

        PunishmentGuiPlugin.ActiveBan ban = activeBans.get(playerName.toLowerCase());
        if (ban != null && ban.isExpired()) {
            activeBans.remove(playerName.toLowerCase());
            saveActivePunishments();
            return false;
        }
        return ban != null;
    }

    public static boolean isPlayerMuted(String playerName) {
        if (!builtInSystemActive) return false;

        PunishmentGuiPlugin.ActiveMute mute = activeMutes.get(playerName.toLowerCase());
        if (mute != null && mute.isExpired()) {
            activeMutes.remove(playerName.toLowerCase());
            saveActivePunishments();
            return false;
        }
        return mute != null;
    }

    public static boolean isIPBanned(String ipAddress) {
        if (!builtInSystemActive) return false;

        PunishmentGuiPlugin.ActiveIPBan ipBan = activeIPBans.get(ipAddress);
        if (ipBan != null && ipBan.isExpired()) {
            activeIPBans.remove(ipAddress);
            saveActivePunishments();
            return false;
        }
        return ipBan != null;
    }

    private long parseDurationToMillis(String duration) {
        try {
            if (duration == null || duration.trim().isEmpty()) {
                return -1;
            }

            duration = duration.toLowerCase().trim();
            long totalMillis = 0;

            String cleanDuration = duration.replaceAll("[^0-9dhms]", "");

            StringBuilder currentNumber = new StringBuilder();

            for (int i = 0; i < cleanDuration.length(); i++) {
                char c = cleanDuration.charAt(i);

                if (Character.isDigit(c)) {
                    currentNumber.append(c);
                } else if (c == 'd' || c == 'h' || c == 'm' || c == 's') {
                    if (currentNumber.length() > 0) {
                        long value = Long.parseLong(currentNumber.toString());

                        switch (c) {
                            case 'd':
                                totalMillis += value * 24 * 60 * 60 * 1000;
                                break;
                            case 'h':
                                totalMillis += value * 60 * 60 * 1000;
                                break;
                            case 'm':
                                totalMillis += value * 60 * 1000;
                                break;
                            case 's':
                                totalMillis += value * 1000;
                                break;
                        }

                        currentNumber = new StringBuilder();
                    }
                }
            }

            if (currentNumber.length() > 0) {
                long value = Long.parseLong(currentNumber.toString());
                totalMillis += value * 1000;
            }

            return totalMillis > 0 ? totalMillis : -1;

        } catch (Exception e) {
            return -1;
        }
    }
}