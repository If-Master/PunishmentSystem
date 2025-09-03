package me.kanuunankuulaspluginsadmingui.punishmentgui.gui;

import me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin;
import me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.FoliaUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.executers.Punishments.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.gui.Handler.*;

public class Gui implements Listener {
    public static ItemStack createPunishmentRecordItem(PunishmentRecord record) {
        Material material;
        ChatColor nameColor;

        switch (record.punishmentType) {
            case "BAN":
                material = Material.BARRIER;
                nameColor = ChatColor.DARK_RED;
                break;
            case "TEMPBAN":
                material = Material.CLOCK;
                nameColor = ChatColor.RED;
                break;
            case "MUTE":
                material = Material.ORANGE_DYE;
                nameColor = ChatColor.GOLD;
                break;
            case "KICK":
                material = Material.IRON_BOOTS;
                nameColor = ChatColor.YELLOW;
                break;
            case "UNBAN":
                material = Material.LIME_DYE;
                nameColor = ChatColor.GREEN;
                break;
            case "BanEvading":
                material = Material.REDSTONE;
                nameColor = ChatColor.DARK_RED;
                break;
            default:
                material = Material.PAPER;
                nameColor = ChatColor.WHITE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String status = record.active ? ChatColor.RED + " [ACTIVE]" : ChatColor.GRAY + " [INACTIVE]";
        meta.setDisplayName(nameColor + record.punishmentType + status);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Date: " + ChatColor.AQUA + record.timestamp);
        lore.add(ChatColor.WHITE + "Reason: " + ChatColor.GRAY + record.reason);

        if (record.duration != null && !record.duration.isEmpty() &&
                !record.punishmentType.equals("KICK") && !record.punishmentType.equals("UNBAN")) {
            lore.add(ChatColor.WHITE + "Duration: " + ChatColor.GREEN + record.duration);
        }

        lore.add(ChatColor.WHITE + "Staff: " + ChatColor.LIGHT_PURPLE + record.staffMember);

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;



        String title = event.getView().getTitle();

        boolean isCustomGUI = title.contains("Ban GUI - Select Player") ||
                title.startsWith(ChatColor.DARK_RED + "Punish: ") ||
                title.contains("Select Reason") ||
                title.startsWith(ChatColor.DARK_PURPLE + "History: ");

        if (!isCustomGUI) {
            return;
        }

        if (!Handler.validateGUIAuthenticity(event)) {
            Player player2 = (Player) event.getWhoClicked();
            player2.sendMessage(ChatColor.RED + "Security Warning: Invalid GUI detected!");
            getPluginLogger().warning("Player " + player2.getName() + " attempted to interact with fake punishment GUI!");
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        event.setCancelled(true);


            if (event.getAction().toString().contains("DRAG")) {
                return;
            }

            if (title.contains("Ban GUI - Select Player")) {
                handlePlayerSelectionNavigation(player, event);
            } else if (title.startsWith(ChatColor.DARK_RED + "Punish: ")) {
                handlePunishmentTypeClick(player, event);
            } else if (title.contains("Select Reason")) {
                handleReasonNavigation(player, event);
            } else if (title.startsWith(ChatColor.DARK_PURPLE + "History: ")) {
                handleHistoryClick(player, event);
            }
    }

    @EventHandler
    public static void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String inputType = chatInputWaiting.get(player);

        if (inputType != null) {
            event.setCancelled(true);
            String message = event.getMessage();

            chatInputWaiting.remove(player);

            FoliaUtils.runEntityTask(getInstance(), player, () -> {
                player.sendMessage(ChatColor.GRAY + "Input received: " + ChatColor.WHITE + message);

                switch (inputType) {
                    case "PLAYER_NAME":
                        openPunishmentTypeGUI(player, message);
                        break;
                    case "REASON":
                        PunishmentGuiPlugin.BanSession reasonSession = activeSessions.get(player);
                        if (reasonSession != null) {
                            reasonSession.customReason = message;
                            openReasonGUI(player);
                        }
                        break;
                }
            });
        }
    }


    public static void secureInventory(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.hasItemMeta()) {
                Handler.addSecurityMetadata(item);
            }
        }
    }

    public static void handlePlayerSelectionNavigation(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();

        if (displayName.equals(ChatColor.YELLOW + "◀ Previous Page")) {
            handlePlayerListPageNavigation(player, false);
            return;
        } else if (displayName.equals(ChatColor.YELLOW + "Next Page ▶")) {
            handlePlayerListPageNavigation(player, true);
            return;
        }

        handlePlayerSelectionClick(player, event);
    }

    public static void openPlayerListGUI(Player player) {
        int currentPage = playerListPage.getOrDefault(player, 0);
        int itemsPerPage = 44;

        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "Ban GUI - Select Player (Page " + (currentPage + 1) + ")");

        ItemStack customPlayer = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta customMeta = (SkullMeta) customPlayer.getItemMeta();
        customMeta.setDisplayName(ChatColor.YELLOW + "Custom Player Input");
        customMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Click to enter a custom",
                ChatColor.GRAY + "player name in chat"
        ));
        customPlayer.setItemMeta(customMeta);
        gui.setItem(0, customPlayer);

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        List<Player> sortedPlayers = new ArrayList<>();
        List<Player> bedrockPlayers = new ArrayList<>();

        for (Player onlinePlayer : onlinePlayers) {
            if (onlinePlayer.equals(player)) continue;

            if (onlinePlayer.getName().startsWith(".")) {
                bedrockPlayers.add(onlinePlayer);
            } else {
                sortedPlayers.add(onlinePlayer);
            }
        }

        sortedPlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        bedrockPlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        List<Player> allPlayers = new ArrayList<>(sortedPlayers);
        allPlayers.addAll(bedrockPlayers);

        int totalPages = (int) Math.ceil((double) allPlayers.size() / itemsPerPage);
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allPlayers.size());

        int slot = 1;
        for (int i = startIndex; i < endIndex; i++) {
            Player onlinePlayer = allPlayers.get(i);

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            skullMeta.setOwningPlayer(onlinePlayer);

            boolean hasBypass = onlinePlayer.hasPermission("punishmentsystem.bypass");
            boolean isBedrock = onlinePlayer.getName().startsWith(".");

            if (hasBypass) {
                skullMeta.setDisplayName(ChatColor.GOLD + onlinePlayer.getName() + ChatColor.RED + " [PROTECTED]");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.RED + "This player cannot be punished");
                lore.add(ChatColor.RED + "Has bypass permission");
                lore.add(ChatColor.YELLOW + "Status: Online");
                if (isBedrock) {
                    lore.add(ChatColor.AQUA + "Platform: Bedrock");
                }
                skullMeta.setLore(lore);
            } else {
                skullMeta.setDisplayName(ChatColor.GREEN + onlinePlayer.getName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Click to punish this player");
                lore.add(ChatColor.YELLOW + "Status: Online");
                if (isBedrock) {
                    lore.add(ChatColor.AQUA + "Platform: Bedrock");
                }
                skullMeta.setLore(lore);
            }

            playerHead.setItemMeta(skullMeta);
            gui.setItem(slot, playerHead);
            slot++;
        }

        if (currentPage > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "◀ Previous Page");
            prevMeta.setLore(List.of(ChatColor.GRAY + "Go to page " + currentPage));
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage);
        }

        if (currentPage < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page ▶");
            nextMeta.setLore(List.of(ChatColor.GRAY + "Go to page " + (currentPage + 2)));
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage);
        }

        ItemStack pageInfo = new ItemStack(Material.BOOK);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        pageInfoMeta.setDisplayName(ChatColor.AQUA + "Page " + (currentPage + 1) + "/" + totalPages);
        pageInfoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Total players: " + allPlayers.size(),
                ChatColor.GRAY + "Showing: " + (startIndex + 1) + "-" + endIndex
        ));
        pageInfo.setItemMeta(pageInfoMeta);
        gui.setItem(49, pageInfo);

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
        cancel.setItemMeta(cancelMeta);
        gui.setItem(46, cancel);

        secureInventory(gui);

        player.openInventory(gui);
    }

    public static void handlePlayerListPageNavigation(Player player, boolean nextPage) {
        int currentPage = playerListPage.getOrDefault(player, 0);
        if (nextPage) {
            playerListPage.put(player, currentPage + 1);
        } else {
            playerListPage.put(player, Math.max(0, currentPage - 1));
        }
        openPlayerListGUI(player);
    }

    public static void openPunishmentTypeGUI(Player player, String targetPlayer) {
        Player targetPlayerObj = Bukkit.getPlayer(targetPlayer);
        if (targetPlayerObj != null && targetPlayerObj.isOnline() && targetPlayerObj.hasPermission("punishmentsystem.bypass")) {
            player.sendMessage(ChatColor.RED + "Cannot punish " + targetPlayer + " - they have bypass permission!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Punish: " + targetPlayer);

        ItemStack mute = new ItemStack(Material.ORANGE_WOOL);
        ItemMeta muteMeta = mute.getItemMeta();
        if (player.hasPermission("punishmentsystem.mute")) {
            muteMeta.setDisplayName(ChatColor.GOLD + "Mute Player");
            muteMeta.setLore(List.of(ChatColor.GRAY + "Prevent player from chatting"));
        } else {
            muteMeta.setDisplayName(ChatColor.GOLD + "Mute Player");
            muteMeta.setLore(List.of(ChatColor.GRAY + "Prevent player from chatting - Lacking permissions "));
        }
        mute.setItemMeta(muteMeta);
        gui.setItem(10, mute);

        ItemStack tempban = new ItemStack(Material.RED_WOOL);
        ItemMeta tempbanMeta = tempban.getItemMeta();
        if (player.hasPermission("punishmentsystem.tempban")) {
            tempbanMeta.setDisplayName(ChatColor.RED + "Temporary Ban");
            tempbanMeta.setLore(List.of(ChatColor.GRAY + "Ban player for configured duration"));
        } else {
            tempbanMeta.setDisplayName(ChatColor.RED + "Temporary Ban");
            tempbanMeta.setLore(List.of(ChatColor.GRAY + "Ban player for configured duration  - Lacking permissions "));
        }
        tempban.setItemMeta(tempbanMeta);
        gui.setItem(12, tempban);

        ItemStack ban = new ItemStack(Material.BLACK_WOOL);
        ItemMeta banMeta = ban.getItemMeta();
        if (!player.hasPermission("punishmentsystem.ban")) {
            banMeta.setDisplayName(ChatColor.DARK_RED + "Permanent Ban");
            banMeta.setLore(List.of(ChatColor.GRAY + "Ban player permanently - Lacking permissions "));
        } else {
            banMeta.setDisplayName(ChatColor.DARK_RED + "Permanent Ban");
            banMeta.setLore(List.of(ChatColor.GRAY + "Ban player permanently"));
        }
        ban.setItemMeta(banMeta);
        gui.setItem(14, ban);

        ItemStack kick = new ItemStack(Material.YELLOW_WOOL);
        ItemMeta kickMeta = kick.getItemMeta();
        if (!player.hasPermission("punishmentsystem.kick")) {
            kickMeta.setDisplayName(ChatColor.YELLOW + "Kick Player");
            kickMeta.setLore(List.of(ChatColor.GRAY + "Remove player from server - Lacking permissions"));
        } else {
            kickMeta.setDisplayName(ChatColor.YELLOW + "Kick Player");
            kickMeta.setLore(List.of(ChatColor.GRAY + "Remove player from server"));
        }
        kick.setItemMeta(kickMeta);
        gui.setItem(16, kick);

        ItemStack unban = new ItemStack(Material.LIME_WOOL);
        ItemMeta unbanMeta = unban.getItemMeta();
        if (player.hasPermission("punishmentsystem.unban")) {
            unbanMeta.setDisplayName(ChatColor.GREEN + "Unban Player");
            unbanMeta.setLore(List.of(ChatColor.GRAY + "Remove ban from player"));
        } else {
            unbanMeta.setDisplayName(ChatColor.GREEN + "Unban Player");
            unbanMeta.setLore(List.of(ChatColor.GRAY + "Remove ban from player - Lacking permissions"));
        }
        unban.setItemMeta(unbanMeta);
        gui.setItem(22, unban);

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
        cancel.setItemMeta(cancelMeta);
        gui.setItem(26, cancel);

        PunishmentGuiPlugin.BanSession session = new PunishmentGuiPlugin.BanSession(targetPlayer);
        activeSessions.put(player, session);

        secureInventory(gui);

        player.openInventory(gui);
    }

    public static void handlePunishmentTypeClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();

        if (displayName.equals(ChatColor.RED + "Cancel")) {
            player.closeInventory();
            cleanupPlayerPageData(player);

            return;
        }

        PunishmentGuiPlugin.BanSession session = activeSessions.get(player);
        if (session == null) return;

        if (displayName.equals(ChatColor.GOLD + "Mute Player")) {
            if (player.hasPermission("punishmentsystem.ban")) {
                session.punishmentType = "MUTE";
                openReasonGUI(player);
            } else {
                player.closeInventory();
                openPlayerListGUI(player);
            }
        } else if (displayName.equals(ChatColor.RED + "Temporary Ban")) {
            if (!player.hasPermission("punishmentsystem.tempban")) {
                player.closeInventory();
                openPlayerListGUI(player);
            } else {
                session.punishmentType = "TEMPBAN";
                openReasonGUI(player);
            }
        } else if (displayName.equals(ChatColor.DARK_RED + "Permanent Ban")) {
            if (!player.hasPermission("punishmentsystem.ban")) {
                player.closeInventory();
                openPlayerListGUI(player);
            } else {
                session.punishmentType = "BAN";
                openReasonGUI(player);
            }
        } else if (displayName.equals(ChatColor.YELLOW + "Kick Player")) {
            if (!player.hasPermission("punishmentsystem.kick")) {
                player.closeInventory();
                openPlayerListGUI(player);
            } else {
                session.punishmentType = "KICK";
                openReasonGUI(player);
            }
        } else if (displayName.equals(ChatColor.GREEN + "Unban Player")) {
            if (!player.hasPermission("punishmentsystem.unban")) {
                player.closeInventory();
                openPlayerListGUI(player);
            } else {
                session.punishmentType = "UNBAN";
                executePunishment(player);
            }
        }
    }

    public static void openReasonGUI(Player player) {
        PunishmentGuiPlugin.BanSession session = activeSessions.get(player);
        if (session == null) return;

        int currentPage = reasonPage.getOrDefault(player, 0);
        int itemsPerPage = 45;

        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "Select Reason (Page " + (currentPage + 1) + ")");

        Map<String, String> currentDurations = session.punishmentType.equals("MUTE") ? muteReasonDurations : banReasonDurations;
        String durationType = session.punishmentType.equals("MUTE") ? "Mute" : "Ban";

        List<String> sortedReasons = new ArrayList<>(banReasons);
        sortedReasons.sort(String.CASE_INSENSITIVE_ORDER);

        int totalPages = (int) Math.ceil((double) sortedReasons.size() / itemsPerPage);
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, sortedReasons.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            String reason = sortedReasons.get(i);
            String duration = currentDurations.get(reason);
            boolean isSelected = session.selectedReasons.contains(reason);

            ItemStack reasonItem = new ItemStack(Material.PAPER);
            ItemMeta reasonMeta = reasonItem.getItemMeta();

            if (isSelected) {
                reasonMeta.setDisplayName(ChatColor.GREEN + reason + " ✓");
                reasonMeta.setLore(Arrays.asList(
                        ChatColor.GREEN + "SELECTED",
                        ChatColor.AQUA + durationType + " Duration: " + duration,
                        ChatColor.GRAY + "Click to deselect this reason"
                ));
            } else {
                reasonMeta.setDisplayName(ChatColor.WHITE + reason);
                reasonMeta.setLore(Arrays.asList(
                        ChatColor.AQUA + durationType + " Duration: " + duration,
                        ChatColor.GRAY + "Click to select this reason"
                ));
            }

            reasonItem.setItemMeta(reasonMeta);
            gui.setItem(slot, reasonItem);
            slot++;

        }

        if (currentPage > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "◀ Previous Page");
            prevMeta.setLore(List.of(ChatColor.GRAY + "Go to page " + currentPage));
            prevPage.setItemMeta(prevMeta);
            gui.setItem(46, prevPage);
        }

        if (currentPage < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page ▶");
            nextMeta.setLore(List.of(ChatColor.GRAY + "Go to page " + (currentPage + 2)));
            nextPage.setItemMeta(nextMeta);
            gui.setItem(47, nextPage);
        }

        ItemStack pageInfo = new ItemStack(Material.COMPASS);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        pageInfoMeta.setDisplayName(ChatColor.AQUA + "Reasons Page " + (currentPage + 1) + "/" + totalPages);
        pageInfoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Total reasons: " + sortedReasons.size(),
                ChatColor.GRAY + "Showing: " + (startIndex + 1) + "-" + endIndex
        ));
        pageInfo.setItemMeta(pageInfoMeta);
        gui.setItem(48, pageInfo);

        ItemStack customReason = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta customMeta = customReason.getItemMeta();
        customMeta.setDisplayName(ChatColor.YELLOW + "Custom Reason");
        String defaultDuration = session.punishmentType.equals("MUTE") ? "1d" : "3d";
        customMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Click to enter custom reason",
                ChatColor.GRAY + "Will use default duration: " + defaultDuration
        ));
        customReason.setItemMeta(customMeta);
        gui.setItem(49, customReason);

        ItemStack clearAll = new ItemStack(Material.RED_CONCRETE);
        ItemMeta clearMeta = clearAll.getItemMeta();
        clearMeta.setDisplayName(ChatColor.RED + "Clear All Reasons");
        clearMeta.setLore(List.of(ChatColor.GRAY + "Remove all selected reasons"));
        clearAll.setItemMeta(clearMeta);
        gui.setItem(50, clearAll);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Selected Reasons (" + session.selectedReasons.size() + ")");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Currently selected:");
        if (session.selectedReasons.isEmpty()) {
            lore.add(ChatColor.RED + "None selected");
        } else {
            for (String selectedReason : session.selectedReasons) {
                String dur = currentDurations.get(selectedReason);
                lore.add(ChatColor.GREEN + "• " + selectedReason + " (" + dur + ")");
            }
        }
        if (session.customReason != null && !session.customReason.isEmpty()) {
            String customDur = session.punishmentType.equals("MUTE") ? "1d" : "3d";
            lore.add(ChatColor.YELLOW + "• " + session.customReason + " (" + customDur + " - custom)");
        }

        if (session.punishmentType.equals("TEMPBAN") || session.punishmentType.equals("MUTE")) {
            lore.add(ChatColor.GOLD + "Total Duration: " + session.getCalculatedDuration());
        }

        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        gui.setItem(51, info);

        ItemStack execute = new ItemStack(Material.DIAMOND);
        ItemMeta executeMeta = execute.getItemMeta();
        executeMeta.setDisplayName(ChatColor.GREEN + "Execute Punishment");
        List<String> executeLore = new ArrayList<>();
        executeLore.add(ChatColor.GRAY + "Target: " + session.targetPlayer);
        executeLore.add(ChatColor.GRAY + "Type: " + session.punishmentType);
        if (session.punishmentType.equals("TEMPBAN") || session.punishmentType.equals("MUTE")) {
            executeLore.add(ChatColor.GRAY + "Duration: " + session.getCalculatedDuration());
        }
        executeLore.add(ChatColor.GRAY + "Reasons: " + session.getCombinedReasons());
        executeMeta.setLore(executeLore);
        execute.setItemMeta(executeMeta);
        gui.setItem(52, execute);

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
        cancel.setItemMeta(cancelMeta);
        gui.setItem(53, cancel);

        secureInventory(gui);

        player.openInventory(gui);
    }

    public static void handleReasonNavigation(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();

        if (displayName.equals(ChatColor.YELLOW + "◀ Previous Page")) {
            handleReasonPageNavigation(player, false);
            return;
        } else if (displayName.equals(ChatColor.YELLOW + "Next Page ▶")) {
            handleReasonPageNavigation(player, true);
            return;
        }

        handleReasonClick(player, event);
    }

    public static void handleReasonClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();
        PunishmentGuiPlugin.BanSession session = activeSessions.get(player);
        if (session == null) return;

        if (displayName.equals(ChatColor.RED + "Cancel")) {
            player.closeInventory();
            cleanupPlayerPageData(player);
        } else if (displayName.equals(ChatColor.YELLOW + "Custom Reason")) {
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Enter custom reason:");
            chatInputWaiting.put(player, "REASON");
        } else if (displayName.equals(ChatColor.RED + "Clear All Reasons")) {
            session.selectedReasons.clear();
            session.customReason = null;
            openReasonGUI(player);
        } else if (displayName.equals(ChatColor.GREEN + "Execute Punishment")) {
            if (session.selectedReasons.isEmpty() && (session.customReason == null || session.customReason.isEmpty())) {
                player.sendMessage(ChatColor.RED + "Please select at least one reason!");
                return;
            }
            executePunishment(player);
        } else if (clicked.getType() == Material.PAPER) {
            String cleanReason = ChatColor.stripColor(displayName).replace(" ✓", "");

            if (session.selectedReasons.contains(cleanReason)) {
                session.selectedReasons.remove(cleanReason);
            } else {
                session.selectedReasons.add(cleanReason);
            }

            openReasonGUI(player);
        }
    }

    public static void cleanupPlayerPageData(Player player) {
        FoliaUtils.runAsyncLater(getInstance(), () -> {
            validateAndUpdatePunishmentStatus();
        }, 20L, java.util.concurrent.TimeUnit.SECONDS);
        activeSessions.remove(player);
        playerListPage.remove(player);
        reasonPage.remove(player);
    }

}
