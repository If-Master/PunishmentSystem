package me.kanuunankuulaspluginsadmingui.punishmentgui.gui;

import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.gui.Gui.*;

public class HistoryGui {
    public static void openPunishmentHistoryGUI(Player player, String targetPlayer, List<PunishmentGuiPlugin.PunishmentRecord> records) {
        int totalRecords = records.size();
        int recordsPerPage = 44;
        int totalPages = (int) Math.ceil((double) totalRecords / recordsPerPage);
        int currentPage = 1;

        openPunishmentHistoryPage(player, targetPlayer, records, currentPage, totalPages);
    }
    public static void openPunishmentHistoryPage(Player player, String targetPlayer, List<PunishmentGuiPlugin.PunishmentRecord> records, int currentPage, int totalPages) {
        Inventory gui = Bukkit.createInventory(null, 54,
                ChatColor.DARK_PURPLE + "History: " + targetPlayer + " (Page " + currentPage + "/" + totalPages + ")");

        int recordsPerPage = 44;
        int startIndex = (currentPage - 1) * recordsPerPage;
        int endIndex = Math.min(startIndex + recordsPerPage, records.size());

        List<PunishmentGuiPlugin.PunishmentRecord> reversedRecords = new ArrayList<>(records);
        Collections.reverse(reversedRecords);

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot >= 44) break;

            PunishmentGuiPlugin.PunishmentRecord record = reversedRecords.get(i);
            ItemStack recordItem = createPunishmentRecordItem(record);
            gui.setItem(slot, recordItem);
            slot++;
        }

        if (currentPage > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "← Previous Page");
            prevMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Go to page " + (currentPage - 1)
            ));
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage);
        }

        if (currentPage < totalPages) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page →");
            nextMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Go to page " + (currentPage + 1)
            ));
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Player Info");
        infoMeta.setLore(Arrays.asList(
                ChatColor.WHITE + "Player: " + ChatColor.YELLOW + targetPlayer,
                ChatColor.WHITE + "Total Records: " + ChatColor.GREEN + records.size(),
                ChatColor.WHITE + "Page: " + ChatColor.GOLD + currentPage + "/" + totalPages
        ));
        info.setItemMeta(infoMeta);
        gui.setItem(49, info);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Close");
        close.setItemMeta(closeMeta);
        gui.setItem(48, close);

        historyPages.put(player, new PunishmentGuiPlugin.HistoryPageInfo(targetPlayer, records, currentPage, totalPages));

        player.openInventory(gui);
    }
    public static void onHistoryCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return;
        }

        if (!player.hasPermission("punishmentsystem.history")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to view punishment history!");
            return;
        }

        String targetPlayer = args[1];
        List<PunishmentGuiPlugin.PunishmentRecord> records = punishmentHistory.get(targetPlayer.toLowerCase());

        if (records == null || records.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No punishment history found for " + targetPlayer);
            return;
        }

        openPunishmentHistoryGUI(player, targetPlayer, records);
    }

}
