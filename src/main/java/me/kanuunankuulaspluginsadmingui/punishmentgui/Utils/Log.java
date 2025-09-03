package me.kanuunankuulaspluginsadmingui.punishmentgui.Utils;

import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.discord.Discord.*;

public class Log {
    public static void logPunishment(String playerName, String punishmentType, String reason, String duration, String staffMember) {
        try {

            String staffUUID = "Unknown";
            Player staffPlayer = Bukkit.getPlayer(staffMember);
            if (staffPlayer != null) {
                staffUUID = staffPlayer.getUniqueId().toString();
            }

            String playerUUID = "Unknown";
            String playerIP = "Unknown";

            Player targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer != null) {
                playerUUID = targetPlayer.getUniqueId().toString();
                playerIP = cleanIP(targetPlayer.getAddress().getAddress().getHostAddress());
            } else {
                playerIP = playerIPs.getOrDefault(playerName.toLowerCase(), "Unknown");
            }

            PunishmentGuiPlugin.PunishmentRecord record = new PunishmentGuiPlugin.PunishmentRecord(
                    playerName, playerUUID, punishmentType, reason, duration, staffMember, staffUUID, playerIP
            );

            String playerKey = playerName.toLowerCase();

            savePunishmentRecord(playerKey, record);

            sendDiscordNotification(record);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
