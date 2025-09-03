package me.kanuunankuulaspluginsadmingui.punishmentgui.Checkers;

import me.kanuunankuulaspluginsadmingui.punishmentgui.executers.BuiltInCommands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.FoliaUtils.*;
import static org.bukkit.Bukkit.getLogger;

public class Bancheckers {
    public static void checkBanEvasion(Player player, String currentIP) {
        runAsync(getInstance(), () -> {
            try {
                String playerName = player.getName();
                List<String> suspiciousPlayers = new ArrayList<>();


                for (Map.Entry<String, String> entry : playerIPs.entrySet()) {
                    String storedPlayer = entry.getKey();
                    String storedIP = entry.getValue();


                    if (storedPlayer.equalsIgnoreCase(playerName)) {
                        continue;
                    }

                    if (!storedIP.equals(currentIP)) {
                        continue;
                    }


                    boolean isBanned = false;

                    if (builtInSystemActive) {
                        isBanned = BuiltInCommands.isPlayerBanned(storedPlayer);
                    }

                    if (!isBanned) {
                        if (Bukkit.getBanList(org.bukkit.BanList.Type.NAME).isBanned(storedPlayer)) {
                            isBanned = true;
                        }

                        if (!isBanned && (checkEssentialsBan(storedPlayer) || checkLiteBan(storedPlayer) || checkAdvancedBan(storedPlayer))) {
                            isBanned = true;
                        }
                    }


                    if (isBanned) {
                        suspiciousPlayers.add(storedPlayer);
                    }
                }

                if (!suspiciousPlayers.isEmpty()) {
                    runGlobalTask(getInstance(), () -> {
                        alertStaffBanEvasion(playerName, currentIP, suspiciousPlayers);
                    });
                } else {
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    public static String getPunishmentStatusText(PunishmentRecord record) {
        if (record.active) {
            return "Active";
        } else {
            switch (record.punishmentType) {
                case "BAN":
                case "TEMPBAN":
                case "MUTE":
                    return "Expired";
                case "KICK":
                    return "Completed";
                case "UNBAN":
                case "UNMUTE":
                    return "Completed";
                default:
                    return "Inactive";
            }
        }
    }


    // Booleans
        // Bans
    public static boolean hasBanBypass(String playerName) {
        try {
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerName);
            if (player == null) return false;

            return player.hasPermission("essentials.ban.exempt") ||
                    player.hasPermission("litebans.bypass") ||
                    player.hasPermission("advancedban.bypass") ||
                    player.hasPermission("ban.bypass") ||
                    player.hasPermission("punishment.bypass") ||
                    player.isOp();
        } catch (Exception e) {
        }
        return false;
    }
    public static boolean isPlayerBanned(String playerName) {
        if (builtInSystemActive) {
            return BuiltInCommands.isPlayerBanned(playerName);
        }

        return checkEssentialsBan(playerName) || checkLiteBan(playerName) || checkAdvancedBan(playerName);
    }

    public static boolean checkEssentialsBan(String playerName) {
        try {
            Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
            if (essentials != null && essentials.isEnabled()) {
                Object essentialsPlugin = essentials;
                Object user = essentialsPlugin.getClass().getMethod("getUser", String.class).invoke(essentialsPlugin, playerName);
                if (user != null) {
                    Boolean isBanned = (Boolean) user.getClass().getMethod("isBanned").invoke(user);
                    return isBanned != null && isBanned;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }
    public static boolean checkLiteBan(String playerName) {
        try {
            Plugin liteBans = Bukkit.getPluginManager().getPlugin("LiteBans");
            if (liteBans != null && liteBans.isEnabled()) {
                Class<?> liteBansClass = Class.forName("litebans.api.Database");
                Object database = liteBansClass.getMethod("get").invoke(null);
                Boolean isBanned = (Boolean) database.getClass().getMethod("isPlayerBanned", String.class, String.class)
                        .invoke(database, playerName, null);
                return isBanned != null && isBanned;
            }
        } catch (Exception e) {
        }
        return false;
    }
    public static boolean checkAdvancedBan(String playerName) {
        try {
            Plugin advancedBan = Bukkit.getPluginManager().getPlugin("AdvancedBan");
            if (advancedBan != null && advancedBan.isEnabled()) {
                Class<?> punishmentManagerClass = Class.forName("me.leoko.advancedban.manager.PunishmentManager");
                Object punishmentManager = punishmentManagerClass.getMethod("get").invoke(null);
                Boolean isBanned = (Boolean) punishmentManager.getClass().getMethod("isBanned", String.class)
                        .invoke(punishmentManager, playerName);
                return isBanned != null && isBanned;
            }
        } catch (Exception e) {
        }
        return false;
    }

        // Mutes
    public static boolean checkEssentialsMute(String playerName) {
        try {
            Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
            if (essentials != null && essentials.isEnabled()) {
                Object essentialsPlugin = essentials;
                Object user = essentialsPlugin.getClass().getMethod("getUser", String.class).invoke(essentialsPlugin, playerName);
                if (user != null) {
                    Boolean isMuted = (Boolean) user.getClass().getMethod("isMuted").invoke(user);
                    return isMuted != null && isMuted;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }
    public static boolean checkLiteMute(String playerName) {
        try {
            Plugin liteBans = Bukkit.getPluginManager().getPlugin("LiteBans");
            if (liteBans != null && liteBans.isEnabled()) {
                Class<?> liteBansClass = Class.forName("litebans.api.Database");
                Object database = liteBansClass.getMethod("get").invoke(null);
                Boolean isMuted = (Boolean) database.getClass().getMethod("isPlayerMuted", String.class, String.class)
                        .invoke(database, playerName, null);
                return isMuted != null && isMuted;
            }
        } catch (Exception e) {
        }
        return false;
    }
    public static boolean checkAdvancedMute(String playerName) {
        try {
            Plugin advancedBan = Bukkit.getPluginManager().getPlugin("AdvancedBan");
            if (advancedBan != null && advancedBan.isEnabled()) {
                Class<?> punishmentManagerClass = Class.forName("me.leoko.advancedban.manager.PunishmentManager");
                Object punishmentManager = punishmentManagerClass.getMethod("get").invoke(null);
                Boolean isMuted = (Boolean) punishmentManager.getClass().getMethod("isMuted", String.class)
                        .invoke(punishmentManager, playerName);
                return isMuted != null && isMuted;
            }
        } catch (Exception e) {
        }
        return false;
    }
    public static boolean hasMuteBypass(String playerName) {
        try {
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerName);
            if (player == null) return false;

            return player.hasPermission("essentials.mute.exempt") ||
                    player.hasPermission("litebans.bypass") ||
                    player.hasPermission("advancedban.bypass") ||
                    player.hasPermission("mute.bypass") ||
                    player.hasPermission("punishment.bypass") ||
                    player.isOp();
        } catch (Exception e) {
        }
        return false;
    }
    public static boolean isPlayerMuted(String playerName) {
        if (builtInSystemActive) {
            return BuiltInCommands.isPlayerMuted(playerName);
        }

        return checkEssentialsMute(playerName) || checkLiteMute(playerName) || checkAdvancedMute(playerName);
    }

    // Status checkers
    public static boolean isPlayerCurrentlyBannedAnySystem(String playerName) {
        try {
            if (builtInSystemActive && BuiltInCommands.isPlayerBanned(playerName)) {
                return true;
            }

            if (Bukkit.getBanList(org.bukkit.BanList.Type.NAME).isBanned(playerName)) {
                return true;
            }

            if (checkEssentialsBan(playerName) || checkLiteBan(playerName) || checkAdvancedBan(playerName)) {
                return true;
            }

        } catch (Exception e) {
            getLogger().warning("Error checking ban status for " + playerName + ": " + e.getMessage());
        }
        return false;
    }
    public static boolean isPlayerCurrentlyMutedAnySystem(String playerName) {
        try {
            if (builtInSystemActive && BuiltInCommands.isPlayerMuted(playerName)) {
                return true;
            }

            if (checkEssentialsMute(playerName) || checkLiteMute(playerName) || checkAdvancedMute(playerName)) {
                return true;
            }

        } catch (Exception e) {
            getLogger().warning("Error checking mute status for " + playerName + ": " + e.getMessage());
        }
        return false;
    }


}
