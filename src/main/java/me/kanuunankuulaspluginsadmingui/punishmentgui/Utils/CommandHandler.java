package me.kanuunankuulaspluginsadmingui.punishmentgui.Utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.Checkers.Bancheckers.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.builtInSystemActive;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.Log.*;

public class CommandHandler {
    public static boolean hasActualBanPermission(String playerName) {
        Player staffPlayer = Bukkit.getPlayer(playerName);
        if (staffPlayer == null) return false;

        if (staffPlayer.hasPermission("punishmentsystem.ban")) {
            return true;
        }

        if (staffPlayer.hasPermission("essentials.ban") ||
                staffPlayer.hasPermission("essentials.tempban")) {
            return true;
        }

        if (staffPlayer.hasPermission("litebans.ban") ||
                staffPlayer.hasPermission("litebans.tempban")) {
            return true;
        }

        if (staffPlayer.hasPermission("advancedban.ban") ||
                staffPlayer.hasPermission("advancedban.tempban")) {
            return true;
        }

        if (staffPlayer.hasPermission("minecraft.command.ban") ||
                staffPlayer.hasPermission("minecraft.command.ban-ip")) {
            return true;
        }

        if (staffPlayer.isOp()) {
            return true;
        }

        return false;
    }

    public static boolean hasActualMutePermission(String playerName) {
        Player staffPlayer = Bukkit.getPlayer(playerName);
        if (staffPlayer == null) return false;

        if (staffPlayer.hasPermission("punishmentsystem.mute")) {
            return true;
        }

        if (staffPlayer.hasPermission("essentials.mute")) {
            return true;
        }

        if (staffPlayer.hasPermission("litebans.mute")) {
            return true;
        }

        if (staffPlayer.hasPermission("advancedban.mute")) {
            return true;
        }

        if (staffPlayer.isOp()) {
            return true;
        }

        return false;
    }

    public static boolean hasActualKickPermission(String playerName) {
        Player staffPlayer = Bukkit.getPlayer(playerName);
        if (staffPlayer == null) return false;

        if (staffPlayer.hasPermission("punishmentsystem.kick")) {
            return true;
        }

        if (staffPlayer.hasPermission("essentials.kick")) {
            return true;
        }

        if (staffPlayer.hasPermission("litebans.kick")) {
            return true;
        }

        if (staffPlayer.hasPermission("advancedban.kick")) {
            return true;
        }

        if (staffPlayer.hasPermission("minecraft.command.kick")) {
            return true;
        }

        if (staffPlayer.isOp()) {
            return true;
        }

        return false;
    }

    public static boolean hasActualUnbanPermission(String playerName) {
        Player staffPlayer = Bukkit.getPlayer(playerName);
        if (staffPlayer == null) return false;

        if (staffPlayer.hasPermission("punishmentsystem.unban")) {
            return true;
        }

        if (staffPlayer.hasPermission("essentials.unban")) {
            return true;
        }

        if (staffPlayer.hasPermission("litebans.unban")) {
            return true;
        }

        if (staffPlayer.hasPermission("advancedban.unban")) {
            return true;
        }

        if (staffPlayer.hasPermission("minecraft.command.pardon")) {
            return true;
        }

        return staffPlayer.isOp();
    }

    public static void handleBanCommand(String[] args, String staffName) {
        if (args.length < 2) return;
        if (!hasActualBanPermission(staffName)) {
            Player staffPlayer = Bukkit.getPlayer(staffName);
            if (staffPlayer != null) {
                staffPlayer.sendMessage("§cYou don't have permission to execute ban commands!");
            }
            return;
        }

        String playerName = args[1];
        String reason = "No reason specified";

        if (args.length > 2) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }
        if (isPlayerBanned(playerName)) {
            Player staffPlayer = Bukkit.getPlayer(staffName);
            if (staffPlayer != null) {
                staffPlayer.sendMessage("§cPlayer " + playerName + " is already banned!");
            }
            return;
        }

        if (hasBanBypass(playerName)) {
            Player staffPlayer = Bukkit.getPlayer(staffName);
            if (staffPlayer != null) {
                staffPlayer.sendMessage("§cCannot ban " + playerName + " - they have bypass permission!");
            }
            return;
        }

        logPunishment(playerName, "BAN", reason, "Permanent", staffName);
    }

    public static void handleUnBanCommand(String[] args, String staffName) {
        if (args.length < 2) return;

        if (!hasActualUnbanPermission(staffName)) {
            Player staffPlayer = Bukkit.getPlayer(staffName);
            if (staffPlayer != null) {
                staffPlayer.sendMessage("§cYou don't have permission to execute unban commands!");
            }
            return;
        }

        String playerName = args[1];
        String reason = "No reason specified";

        if (args.length > 2) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }
        if (!isPlayerBanned(playerName)) {
            return;
        }

        logPunishment(playerName, "UNBAN", reason, "No Duration", staffName);
    }

    public static void handleTempBanCommand(String[] args, String staffName) {
        if (args.length < 3) return;

        if (!hasActualBanPermission(staffName)) {
            Player staffPlayer = Bukkit.getPlayer(staffName);
            if (staffPlayer != null) {
                staffPlayer.sendMessage("§cYou don't have permission to execute tempban commands!");
            }
            return;
        }

        String playerName = args[1];
        String duration = args[2];
        String reason = "No reason specified";

        if (args.length > 3) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }
        if (isPlayerBanned(playerName)) {
            return;
        }
        if (hasBanBypass(playerName)) {
            return;
        }

        logPunishment(playerName, "TEMPBAN", reason, duration, staffName);
    }

    public static void handleMuteCommand(String[] args, String staffName) {
        if (args.length < 2) return;

        if (!hasActualMutePermission(staffName)) {
            Player staffPlayer = Bukkit.getPlayer(staffName);
            if (staffPlayer != null) {
                staffPlayer.sendMessage("§cYou don't have permission to execute mute commands!");
            }
            return;
        }

        String playerName = args[1];
        String duration = "Permanent";
        String reason = "No reason specified";

        if (args.length > 2 && args[2].matches(".*\\d+.*[dhms].*")) {
            duration = args[2];
            if (args.length > 3) {
                StringBuilder reasonBuilder = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    reasonBuilder.append(args[i]).append(" ");
                }
                reason = reasonBuilder.toString().trim();
            }
        } else if (args.length > 2) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        if (hasMuteBypass(playerName)) { return; }

        if ((builtInSystemActive) && (duration.toLowerCase().toString().equals("permanent"))) {
            logPunishment(playerName, "MUTE", reason, duration, staffName);
        } else if (!(duration.toLowerCase().equals("permanent"))) {
            logPunishment(playerName, "TEMPMUTE", reason, duration, staffName);
        }

    }

    public static void handleKickCommand(String[] args, String staffName) {
        if (args.length < 2) return;

        if (!hasActualKickPermission(staffName)) {
            Player staffPlayer = Bukkit.getPlayer(staffName);
            if (staffPlayer != null) {
                staffPlayer.sendMessage("§cYou don't have permission to execute kick commands!");
            }
            return;
        }

        String playerName = args[1];
        String reason = "No reason specified";

        if (args.length > 2) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            Player staffPlayer = Bukkit.getPlayer(staffName);
            if (staffPlayer != null) {
                staffPlayer.sendMessage("§cPlayer " + playerName + " is not online!");
            }
            return;
        }

        if (targetPlayer.hasPermission("punishmentsystem.bypass")) {
            Player staffPlayer = Bukkit.getPlayer(staffName);
            if (staffPlayer != null) {
                staffPlayer.sendMessage("§cCannot kick " + playerName + " - they have bypass permission!");
            }
            return;
        }

        logPunishment(playerName, "KICK", reason, "Instant", staffName);
    }

}
