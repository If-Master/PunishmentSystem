package me.kanuunankuulaspluginsadmingui.punishmentgui.EventListeners;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Updater.UpdateChecker.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.CommandHandler.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.Debug.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.FoliaUtils.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.discord.Discord.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.gui.Gui.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.gui.HistoryGui.*;

public class CommandsEvents implements Listener, CommandExecutor {
    @EventHandler(priority = EventPriority.MONITOR)
    public static void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase();
        String[] args = command.split(" ");

        if (args.length < 2) return;

        String cmd = args[0];
        Player staff = event.getPlayer();
        String staffName = staff.getName();

        try {
            switch (cmd) {
                case "/ban":
                    executeWithPlayer(getInstance(), staff, player -> {
                        handleBanCommand(args, staffName);
                    });
                    break;
                case "/tempban":
                    if (!isValidDuration(args[2])) {break;}
                    executeWithPlayer(getInstance(), staff, player -> {
                        handleTempBanCommand(args, staffName);
                    });
                    break;
                case "/mute":
                    if (!isValidDuration(args[2])) {break;}
                    executeWithPlayer(getInstance(), staff, player -> {
                        handleMuteCommand(args, staffName);
                    });
                    break;
                case "/unban":
                    executeWithPlayer(getInstance(), staff, player -> {
                        handleUnBanCommand(args, staffName);
                    });
                    break;
                case "/pardon":
                    executeWithPlayer(getInstance(), staff, player -> {
                        handleUnBanCommand(args, staffName);
                    });
                    break;

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (!player.hasPermission("punishmentsystem.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "reload":
                    if (!player.hasPermission("punishmentsystem.reload")) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to reload the config!");
                        return true;
                    }
                    runAsync(getInstance(), () -> {
                        try {
                            shutdownDiscordIntegration();

                            executeWithPlayer(getInstance(), player, p -> {
                                getInstance().reloadConfig();
                                loadBanReasons();
                                loadReasonDurations();
                                reloadDiscordConfig();

                                p.sendMessage(ChatColor.YELLOW + "Configuration reloaded, initializing Discord...");
                            });

                            initializeDiscordAsync();

                            runAsyncLater(getInstance(), () -> {
                                sendMessageSafely(getInstance(), player,
                                        ChatColor.GREEN + "PunishmentSystem configuration reloaded successfully!");
                            }, 2, java.util.concurrent.TimeUnit.SECONDS);

                        } catch (Exception e) {
                            getPluginLogger().warning("Error during reload: " + e.getMessage());
                            sendMessageSafely(getInstance(), player,
                                    ChatColor.RED + "Error occurred during reload: " + e.getMessage());
                        }
                    });
                    return true;

                case "reloaddata":
                    if (!player.hasPermission("punishmentsystem.reload")) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to reload data!");
                        return true;
                    }
                    runAsync(getInstance(), () -> {
                        reloadPunishmentData(player);
                    });
                    return true;

                case "history":
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /punish history <player>");
                        return true;
                    }
                    executeWithPlayer(getInstance(), player, p -> {
                        onHistoryCommand(p, args);
                    });
                    return true;

                case "discord":
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /punish discord <test|status|debug>");
                        return true;
                    }

                    String discordCmd = args[1].toLowerCase();
                    switch (discordCmd) {
                        case "test":
                            runAsync(getInstance(), () -> {
                                testDiscordConnection(player);
                            });
                            break;
                        case "status":
                            executeWithPlayer(getInstance(), player, p -> {
                                checkDiscordStatus(p);
                            });
                            break;
                        case "debug":
                            runAsync(getInstance(), () -> {
                                debugDiscordHistory(player, args.length > 2 ? args[2] : null);
                            });
                            break;
                        default:
                            player.sendMessage(ChatColor.RED + "Unknown discord command: " + discordCmd);
                    }
                    return true;

                case "debug":
                    if (!player.hasPermission("punishmentsystem.debug")) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to use debug commands!");
                        return true;
                    }
                    runAsync(getInstance(), () -> {
                        debugPlugin(player);
                    });
                    return true;
                case "version":
                    if (!player.hasPermission("punishmentsystem.admin")) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to check for updates!");
                        return true;
                    }
                    checkForUpdates(player);
                    return true;

                case "download":
                    if (!player.hasPermission("punishmentsystem.admin")) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to download updates!");
                        return true;
                    }
                    updatePluginFromGitHub(sender, getInstance());
                    return true;

                case "updateinfo":
                    if (!player.hasPermission("punishmentsystem.admin")) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to view update info!");
                        return true;
                    }
                    getUpdateInfo(player);
                    return true;

                case "help":
                    showHelpMenu(player);
                    return true;
            }

        }

        executeWithPlayer(getInstance(), player, p -> {
            openPlayerListGUI(p);
        });
        return true;
    }
    private void checkPluginVersion(Player player) {
        runAsync(getInstance(), () -> {
            sendMessageSafely(getInstance(), player,
                    ChatColor.YELLOW + "Checking for plugin updates...");

            String currentVersion = getInstance().getDescription().getVersion();

            runAsyncLater(getInstance(), () -> {
                sendMessageSafely(getInstance(), player,
                        ChatColor.GREEN + "Current version: " + ChatColor.WHITE + currentVersion);
                sendMessageSafely(getInstance(), player,
                        ChatColor.YELLOW + "Visit GitHub or Spigot for the latest version.");
            }, 1, java.util.concurrent.TimeUnit.SECONDS);
        });
    }

    private void showHelpMenu(Player player) {
        executeWithPlayer(getInstance(), player, p -> {
            p.sendMessage(ChatColor.GOLD + "=== PunishmentSystem Help ===");
            p.sendMessage(ChatColor.YELLOW + "/punish" + ChatColor.WHITE + " - Open punishment GUI");
            p.sendMessage(ChatColor.YELLOW + "/punish history <player>" + ChatColor.WHITE + " - View punishment history");

            if (p.hasPermission("punishmentsystem.reload")) {
                p.sendMessage(ChatColor.YELLOW + "/punish reload" + ChatColor.WHITE + " - Reload configuration");
                p.sendMessage(ChatColor.YELLOW + "/punish reloaddata" + ChatColor.WHITE + " - Reload punishment data");
            }

            if (p.hasPermission("punishmentsystem.debug")) {
                p.sendMessage(ChatColor.YELLOW + "/punish debug" + ChatColor.WHITE + " - Show debug information");
                p.sendMessage(ChatColor.YELLOW + "/punish discord <test|status|debug>" + ChatColor.WHITE + " - Discord commands");
            }

            if (p.hasPermission("punishmentsystem.admin")) {
                p.sendMessage(ChatColor.YELLOW + "/punish version" + ChatColor.WHITE + " - Check plugin version");
                p.sendMessage(ChatColor.YELLOW + "/punish download" + ChatColor.WHITE + " - Updates the plugin");
                p.sendMessage(ChatColor.YELLOW + "/punish updateinfo" + ChatColor.WHITE + " - shows update info");
            }

            p.sendMessage(ChatColor.GOLD + "========================");
        });
    }

    public static void handleCommandError(Player player, String command, Exception error) {
        getPluginLogger().severe("Command error in " + command + " by " + player.getName() + ": " + error.getMessage());
        error.printStackTrace();

        sendMessageSafely(getInstance(), player,
                ChatColor.RED + "An error occurred while executing the command. Please contact an administrator.");
    }

    public static boolean validateCommandArgs(Player player, String[] args, int minLength, String usage) {
        if (args.length < minLength) {
            sendMessageSafely(getInstance(), player, ChatColor.RED + "Usage: " + usage);
            return false;
        }
        return true;
    }

    public static boolean checkPermissionWithFeedback(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            sendMessageSafely(getInstance(), player,
                    ChatColor.RED + "You don't have permission to use this command! Required: " + permission);
            return false;
        }
        return true;
    }

}
