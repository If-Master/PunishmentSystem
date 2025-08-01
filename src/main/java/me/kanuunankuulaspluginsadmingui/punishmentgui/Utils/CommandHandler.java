package me.kanuunankuulaspluginsadmingui.punishmentgui.Utils;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.Checkers.Bancheckers.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.Log.*;

public class CommandHandler {
    public static void handleBanCommand(String[] args, String staffName) {
        if (args.length < 2) return;

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
            return;
        }

        if (hasBanBypass(playerName)) {
            return;
        }
        logPunishment(playerName, "BAN", reason, "Permanent", staffName);
    }
    public static void handleUnBanCommand(String[] args, String staffName) {
        if (args.length < 2) return;

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

        logPunishment(playerName, "MUTE", reason, duration, staffName);
    }

}
