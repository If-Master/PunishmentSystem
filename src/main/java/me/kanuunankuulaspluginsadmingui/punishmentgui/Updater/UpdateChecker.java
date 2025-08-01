package me.kanuunankuulaspluginsadmingui.punishmentgui.Updater;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import static me.kanuunankuulaspluginsadmingui.punishmentgui.PunishmentGuiPlugin.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.CompletableFuture.*;
import static me.kanuunankuulaspluginsadmingui.punishmentgui.Utils.FoliaUtils.*;
import static org.bukkit.Bukkit.getLogger;

public class UpdateChecker {
    private static final String GITHUB_REPO = "If-Master/PunishmentSystem";
    private static String apiUrl = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static String assetUrl = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/assets/";

    public static void checkForUpdates(CommandSender player) {
        runAsync(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "PunishmentSystem-UpdateChecker");

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    getLogger().warning("Failed to check updates. Code: " + responseCode);
                    runTask(getInstance(), null, () ->
                            player.sendMessage("§cFailed to check for updates. HTTP Code: " + responseCode));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String responseBody = response.toString();
                int tagStart = responseBody.indexOf("\"tag_name\":\"") + 12;
                int tagEnd = responseBody.indexOf("\"", tagStart);
                String latest = responseBody.substring(tagStart, tagEnd);
                String current = getInstance().getDescription().getVersion();

                if (!current.equals(latest)) {
                    runTask(getInstance(), null, () -> {
                        player.sendMessage("§3Update available! Current: " + current + ", Latest: " + latest);
                        player.sendMessage("§eUse '/punish download' to download the update automatically.");
                    });
                    getLogger().info("Update available! Current: " + current + ", Latest: " + latest);
                } else {
                    runTask(getInstance(), null, () ->
                            player.sendMessage("§aYou have the latest version! (" + current + ")"));
                    getLogger().info("No updates found. Current version: " + current);
                }
            } catch (Exception e) {
                getLogger().warning("Failed to check for updates: " + e.getMessage());
                runTask(getInstance(), null, () ->
                        player.sendMessage("§cFailed to check for updates: " + e.getMessage()));
                e.printStackTrace();
            }
        });
    }

    public static void updatePluginFromGitHub(CommandSender sender, Plugin plugin) {
        sender.sendMessage("§6Starting plugin update download...");

        runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestProperty("User-Agent", "PunishmentSystem-UpdateChecker");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String json = response.toString();

                String pluginName = plugin.getName();
                String assetInfo = findBestAsset(json, pluginName);

                if (assetInfo == null) {
                    runTask(getInstance(), null, () -> {
                        sender.sendMessage("§cNo suitable plugin JAR found in the latest release!");
                        sender.sendMessage("§cLooked for files matching: " + pluginName + "-*-shaded.jar or " + pluginName + ".jar");
                    });
                    return;
                }

                String[] parts = assetInfo.split("\\|");
                String assetId = parts[0];
                String assetName = parts[1];

                runTask(getInstance(), null, () ->
                        sender.sendMessage("§aFound asset: " + assetName + ". Starting download..."));

                downloadPluginUpdate(sender, assetId, assetName, plugin);

            } catch (Exception e) {
                runTask(getInstance(), null, () ->
                        sender.sendMessage("§cFailed to update plugin: " + e.getMessage()));
                getLogger().warning("Update failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static String findBestAsset(String json, String pluginName) {
        try {
            String assetsSection = json.substring(json.indexOf("\"assets\":[") + 10);
            assetsSection = assetsSection.substring(0, assetsSection.indexOf("]") + 1);

            String[] patterns = {
                    pluginName + "-.*-shaded\\.jar",
                    pluginName + "-shaded\\.jar",
                    pluginName + "\\.jar",
                    ".*-shaded\\.jar",
                    ".*\\.jar"
            };

            for (String patternStr : patterns) {
                Pattern pattern = Pattern.compile("\"name\":\"(" + patternStr + ")\"", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(assetsSection);

                if (matcher.find()) {
                    String assetName = matcher.group(1);

                    int nameIndex = assetsSection.indexOf("\"name\":\"" + assetName + "\"");
                    if (nameIndex != -1) {
                        String beforeName = assetsSection.substring(0, nameIndex);
                        int idStart = beforeName.lastIndexOf("\"id\":") + 5;
                        int idEnd = beforeName.indexOf(",", idStart);
                        if (idEnd == -1) idEnd = beforeName.indexOf("}", idStart);

                        String assetId = beforeName.substring(idStart, idEnd).trim();
                        return assetId + "|" + assetName;
                    }
                }
            }

            getLogger().warning("No suitable JAR asset found in release");
            return null;

        } catch (Exception e) {
            getLogger().warning("Error parsing assets: " + e.getMessage());
            return null;
        }
    }

    public static void downloadPluginUpdate(CommandSender sender, String assetId, String assetName, Plugin plugin) {
        runAsync(() -> {
            try {
                String downloadUrl = assetUrl + assetId;

                HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/octet-stream");
                conn.setRequestProperty("User-Agent", "PunishmentSystem-UpdateChecker");

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    runTask(getInstance(), null, () -> {
                        sender.sendMessage("§cDownload failed. HTTP Code: " + responseCode);
                        getLogger().warning("Asset download failed with code: " + responseCode);
                    });
                    return;
                }

                String pluginFileName = plugin.getName() + ".jar";
                File pluginFile = new File(plugin.getDataFolder().getParentFile(), pluginFileName);

                if (pluginFile.exists()) {
                    try {
                        pluginFile.delete();
                    } catch (Exception e) {
                        getLogger().warning("Failed to delete the file error: " + e.getMessage());
                    }
                }

                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, pluginFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                runTask(getInstance(), null, () -> {
                    sender.sendMessage("§a§lPlugin updated successfully!");
                    sender.sendMessage("§eDownloaded: " + assetName);
                    sender.sendMessage("§eInstalled as: " + pluginFileName);
                    sender.sendMessage("§6§lRestart the server to complete the update.");
                });

                getLogger().info("Plugin updated successfully: " + assetName + " -> " + pluginFileName);

            } catch (Exception e) {
                runTask(getInstance(), null, () -> {
                    sender.sendMessage("§cFailed to download plugin: " + e.getMessage());
                    sender.sendMessage("§eIf you have a backup, it's still available.");
                });
                getLogger().warning("Download error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Get information about available updates without downloading
     */
    public static void getUpdateInfo(CommandSender sender) {
        runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestProperty("User-Agent", "PunishmentSystem-UpdateChecker");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String json = response.toString();

                int tagStart = json.indexOf("\"tag_name\":\"") + 12;
                int tagEnd = json.indexOf("\"", tagStart);
                String latestVersion = json.substring(tagStart, tagEnd);

                int bodyStart = json.indexOf("\"body\":\"") + 8;
                int bodyEnd = json.indexOf("\"", bodyStart);
                String releaseNotes = json.substring(bodyStart, bodyEnd);
                if (releaseNotes.length() > 100) {
                    releaseNotes = releaseNotes.substring(0, 97) + "...";
                }

                String currentVersion = getInstance().getDescription().getVersion();
                String pluginName = getInstance().getName();
                String assetInfo = findBestAsset(json, pluginName);

                String finalReleaseNotes = releaseNotes;
                runTask(getInstance(), null, () -> {
                    sender.sendMessage("§6=== Plugin Update Information ===");
                    sender.sendMessage("§eCurrent Version: §f" + currentVersion);
                    sender.sendMessage("§eLatest Version: §f" + latestVersion);

                    if (!currentVersion.equals(latestVersion)) {
                        sender.sendMessage("§a✓ Update available!");
                        if (assetInfo != null) {
                            String assetName = assetInfo.split("\\|")[1];
                            sender.sendMessage("§eAvailable download: §f" + assetName);
                            sender.sendMessage("§eUse §a/punish download §eto download automatically");
                        }
                    } else {
                        sender.sendMessage("§a✓ You have the latest version!");
                    }

                    if (!finalReleaseNotes.isEmpty() && !finalReleaseNotes.equals("null")) {
                        sender.sendMessage("§eRelease Notes: §f" + finalReleaseNotes.replace("\\n", " "));
                    }

                    sender.sendMessage("§6================================");
                });

            } catch (Exception e) {
                runTask(getInstance(), null, () ->
                        sender.sendMessage("§cFailed to get update info: " + e.getMessage()));
                e.printStackTrace();
            }
        });
    }
}