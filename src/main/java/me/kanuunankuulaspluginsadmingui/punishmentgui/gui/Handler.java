package me.kanuunankuulaspluginsadmingui.punishmentgui.gui;

import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.gui.Gui.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.gui.HistoryGui.*;


public class Handler {
    public static void handlePlayerSelectionClick(Player player, InventoryClickEvent event) {
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
