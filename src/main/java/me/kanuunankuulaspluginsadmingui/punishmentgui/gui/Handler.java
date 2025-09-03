package me.kanuunankuulaspluginsadmingui.punishmentgui.gui;

import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.gui.Gui.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.gui.HistoryGui.*;


public class Handler {
    public static final String GUI_SECURITY_KEY = "§k§r§a§b§c§d§e§f§0§1§2§3§4§5§6§7§8§9§l§m§n§o§r";
    public static final String GUI_VALIDATION_LORE = "§8[PunishmentGUI-Verified]";
    public static ItemStack addSecurityMetadata(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();

        if (lore == null) {
            lore = new java.util.ArrayList<>();
        }

        if (!lore.contains(GUI_VALIDATION_LORE)) {
            lore.add(GUI_VALIDATION_LORE);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean validateGUIAuthenticity(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        boolean isSupposedCustomGUI = title.contains("Ban GUI - Select Player") ||
                title.startsWith(ChatColor.DARK_RED + "Punish: ") ||
                title.contains("Select Reason") ||
                title.startsWith(ChatColor.DARK_PURPLE + "History: ");

        if (!isSupposedCustomGUI) {
            return true;
        }

        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasLore()) {
                    List<String> lore = meta.getLore();
                    for (String loreLine : lore) {
                        if (loreLine.contains(GUI_VALIDATION_LORE)) {
                            return true;
                        }
                    }
                }

                if (meta.hasDisplayName() && meta.getDisplayName().contains(GUI_SECURITY_KEY)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void handlePlayerSelectionClick(Player player, InventoryClickEvent event) {
        if (!validateGUIAuthenticity(event)) {
            player.sendMessage(ChatColor.RED + "Security Warning: Invalid GUI detected. Please use official punishment commands.");
            getPluginLogger().warning("Player " + player.getName() + " attempted to interact with fake punishment GUI!");
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();

        if (displayName.equals(ChatColor.YELLOW + "Custom Player Input")) {
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Please enter the player name in chat:");
            chatInputWaiting.put(player, "PLAYER_NAME");
        } else if (displayName.equals(ChatColor.RED + "Cancel")) {
            player.closeInventory();
            cleanupPlayerPageData(player);
        } else if (displayName.startsWith(String.valueOf(ChatColor.GREEN))) {
            String playerName = ChatColor.stripColor(displayName);
            openPunishmentTypeGUI(player, playerName);
        } else if (displayName.contains("[PROTECTED]")) {
            player.sendMessage(ChatColor.RED + "This player cannot be punished - they have bypass permission!");
        }
    }

    public static void handleReasonPageNavigation(Player player, boolean nextPage) {
        int currentPage = reasonPage.getOrDefault(player, 0);
        if (nextPage) {
            reasonPage.put(player, currentPage + 1);
        } else {
            reasonPage.put(player, Math.max(0, currentPage - 1));
        }
        openReasonGUI(player);
    }

    public static void handleHistoryClick(Player player, InventoryClickEvent event) {
        if (!validateGUIAuthenticity(event)) {
            player.sendMessage(ChatColor.RED + "Security Warning: Invalid GUI detected. Please use official punishment commands.");
            getPluginLogger().warning("Player " + player.getName() + " attempted to interact with fake punishment GUI!");
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();
        PunishmentGuiPlugin.HistoryPageInfo pageInfo = historyPages.get(player);

        if (pageInfo == null) {
            player.closeInventory();
            return;
        }

        if (displayName.equals(ChatColor.RED + "Close")) {
            player.closeInventory();
            historyPages.remove(player);
        } else if (displayName.equals(ChatColor.YELLOW + "← Previous Page")) {
            if (pageInfo.currentPage > 1) {
                openPunishmentHistoryPage(player, pageInfo.targetPlayer, pageInfo.records,
                        pageInfo.currentPage - 1, pageInfo.totalPages);
            }
        } else if (displayName.equals(ChatColor.YELLOW + "Next Page →")) {
            if (pageInfo.currentPage < pageInfo.totalPages) {
                openPunishmentHistoryPage(player, pageInfo.targetPlayer, pageInfo.records,
                        pageInfo.currentPage + 1, pageInfo.totalPages);
            }
        }
    }

}
