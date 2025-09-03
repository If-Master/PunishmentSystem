package me.kanuunankuulaspluginsadmingui.punishmentgui.executers;

import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.FoliaUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.Debug.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.Log.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.gui.Gui.*;

public class Punishments {
    public static void executePunishment(Player player) {
        PunishmentGuiPlugin.BanSession session = activeSessions.get(player);
        if (session == null) return;

        Player targetPlayerObj = Bukkit.getPlayer(session.targetPlayer);
        if (targetPlayerObj != null && targetPlayerObj.isOnline() && targetPlayerObj.hasPermission("punishmentsystem.bypass")) {
            player.sendMessage(ChatColor.RED + "Cannot execute punishment - target player has bypass permission!");
            player.closeInventory();
            cleanupPlayerPageData(player);
            return;
        }

        if (session.targetPlayer.equalsIgnoreCase(player.getName())) {
            player.sendMessage(ChatColor.RED + "You cannot punish yourself!");
            player.closeInventory();
            cleanupPlayerPageData(player);
            return;
        }

        if (!isValidPlayer(session.targetPlayer)) {
            player.sendMessage(ChatColor.RED + "Invalid username: " + session.targetPlayer);
            player.closeInventory();
            cleanupPlayerPageData(player);
            return;
        }

        debugPunishmentExecution(player, session);

        player.closeInventory();

        String command = "";
        String reasonString = session.getCombinedReasons();

        switch (session.punishmentType) {
            case "KICK":
                if (!player.hasPermission("punishmentsystem.kick")) {
                    player.sendMessage("Lacking permissions for this command");
                    break;
                }
                command = "kick " + session.targetPlayer + " " + reasonString;
                break;
            case "BAN":
                if (!player.hasPermission("punishmentsystem.ban")) {
                    player.sendMessage("Lacking permissions for this command");
                    break;
                }
                command = "ban " + session.targetPlayer + " " + reasonString;
                break;
            case "TEMPBAN":
                if (!player.hasPermission("punishmentsystem.tempban")) {
                    player.sendMessage("Lacking permissions for this command");
                    break;
                }
                command = "tempban " + session.targetPlayer + " " + session.getCalculatedDuration() + " " + reasonString;
                break;
            case "MUTE":
                if (!player.hasPermission("punishmentsystem.mute")) {
                    player.sendMessage("Lacking permissions for this command");
                    break;
                }
                command = "tempmute " + session.targetPlayer + " " + session.getCalculatedDuration() + " " + reasonString;
                break;
            case "UNBAN":
                if (!player.hasPermission("punishmentsystem.unban")) {
                    player.sendMessage("Lacking permissions for this command");
                    break;
                }
                command = "pardon " + session.targetPlayer;
                break;

        }

        if (!command.isEmpty()) {
            String finalCommand = command;
            FoliaUtils.runGlobalTask(getInstance(), () -> {
                try {
                    player.sendMessage(ChatColor.YELLOW + "Executing: " + finalCommand);

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                    player.sendMessage(ChatColor.GREEN + "Punishment executed!");
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Error executing punishment: " + e.getMessage());
                }
            });
        }

        
        // reminder for me in the future: Uncommenting this will casue the plugin to add to the punishment record twice the thing. :)
        String duration = "";
        if (session.punishmentType.equals("TEMPBAN") || session.punishmentType.equals("MUTE")) {
            duration = session.getCalculatedDuration();
//            logPunishment(session.targetPlayer, session.punishmentType, reasonString, duration, player.getName());
        } else if (session.punishmentType.equals("BAN")) {
            duration = "Permanent";
//            logPunishment(session.targetPlayer, session.punishmentType, reasonString, duration, player.getName());
        } else if (session.punishmentType.equals("KICK")) {
            duration = "No Duration";
//            logPunishment(session.targetPlayer, session.punishmentType, reasonString, duration, player.getName());
        } else if (session.punishmentType.equals("UNBAN")) {
            duration = "No Duration";
            String reasonString2 = "Unbanned; No reason required";
//            logPunishment(session.targetPlayer, session.punishmentType, reasonString2, duration, player.getName());
        }




        cleanupPlayerPageData(player);

    }

}
