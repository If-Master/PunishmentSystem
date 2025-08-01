package me.kanuunankuulaspluginsadmingui.punishmentgui.Utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;


/* Complete Folia utility class for handling the most Folia-specific functionality  */
public class FoliaUtils {
    private static boolean  isFolia = false;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
    }

    // Detections

    public static boolean IsFolia() {
        return isFolia;
    }

    public static boolean IsPaper() {
        return !isFolia;
    }



    // Schedulers
    public static ScheduledTask runTask(Plugin plugin, Location location, Runnable task) {
        if (isFolia) {
            return Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runTaskLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (isFolia) {
            return Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }
    public static ScheduledTask runTaskTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            return Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, scheduledTask -> task.run(), delayTicks, periodTicks);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (isFolia) {
            return entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (isFolia) {
            return entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runEntityTaskTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            return entity.getScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), null, delayTicks, periodTicks);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runGlobalTask(Plugin plugin, Runnable task) {
        if (isFolia) {
            return Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runGlobalTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (isFolia) {
            return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runGlobalTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks, periodTicks);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runAsync(Plugin plugin, Runnable task) {
        if (isFolia) {
            return Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        if (isFolia) {
            return Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay, unit);
        } else {
            long ticks = unit.toMillis(delay) / 50;
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, ticks);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    public static ScheduledTask runAsyncTimer(Plugin plugin, Runnable task, long delay, long period, TimeUnit unit) {
        if (isFolia) {
            return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delay, period, unit);
        } else {
            long delayTicks = unit.toMillis(delay) / 50;
            long periodTicks = unit.toMillis(period) / 50;
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
            return new ScheduledTaskWrapper(bukkitTask);
        }
    }

    // ==================== ENTITY ACCESS ====================
    public static void executeWithEntity(Plugin plugin, Entity entity, Consumer<Entity> action) {
        if (isFolia) {
            entity.getScheduler().run(plugin, scheduledTask -> action.accept(entity), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> action.accept(entity));
        }
    }

    public static void getEntitySafely(Plugin plugin, Entity entity, Consumer<Entity> callback) {
        executeWithEntity(plugin, entity, callback);
    }

    public static void teleportSafely(Plugin plugin, Entity entity, Location location) {
        if (isFolia) {
            entity.getScheduler().run(plugin, scheduledTask -> {
                entity.teleport(location);
            }, null);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> entity.teleport(location));
        }
    }

    // ==================== WORLD ACCESS ====================

    public static void executeInRegion(Plugin plugin, World world, int chunkX, int chunkZ, Runnable action) {
        Location loc = new Location(world, chunkX << 4, 64, chunkZ << 4);
        runTask(plugin, loc, action);
    }

    public static void loadChunkSafely(Plugin plugin, World world, int x, int z, Consumer<Boolean> callback) {
        if (isFolia) {
            Location loc = new Location(world, x << 4, 64, z << 4);
            runTask(plugin, loc, () -> {
                boolean loaded = world.loadChunk(x, z, true);
                callback.accept(loaded);
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean loaded = world.loadChunk(x, z, true);
                callback.accept(loaded);
            });
        }
    }

    // ==================== PLAYER UTILITIES ====================

    public static void executeWithPlayer(Plugin plugin, Player player, Consumer<Player> action) {
        executeWithEntity(plugin, player, entity -> action.accept((Player) entity));
    }

    public static void sendMessageSafely(Plugin plugin, Player player, String message) {
        executeWithPlayer(plugin, player, p -> p.sendMessage(message));
    }

    public static void getOnlinePlayersSafely(Plugin plugin, Consumer<List<Player>> callback) {
        if (isFolia) {
            runGlobalTask(plugin, () -> {
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                callback.accept(players);
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                callback.accept(players);
            });
        }
    }

    // ==================== THREAD CHECKING ====================

    public static boolean isMainThread() {
        if (isFolia) {
            return true;
        } else {
            return Bukkit.isPrimaryThread();
        }
    }

    public static void ensureMainThread(Plugin plugin, Location location, Runnable action) {
        if (isFolia) {
            runTask(plugin, location, action);
        } else {
            if (Bukkit.isPrimaryThread()) {
                action.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, action);
            }
        }
    }

    // ==================== BUKKIT RUNNABLE REPLACEMENT ====================

    public static ScheduledTask runBukkitRunnableAsync(Plugin plugin, Runnable task) {
        return runAsync(plugin, task);
    }

    public static ScheduledTask runBukkitRunnableSync(Plugin plugin, Runnable task) {
        return runGlobalTask(plugin, task);
    }

    public static ScheduledTask runBukkitRunnableLater(Plugin plugin, Runnable task, long delayTicks) {
        return runGlobalTaskLater(plugin, task, delayTicks);
    }

    // ==================== COMMAND EXECUTION ====================

    public static void executeConsoleCommand(Plugin plugin, String command) {
        if (isFolia) {
            runGlobalTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            });
        }
    }

    public static void executeConsoleCommandWithCallback(Plugin plugin, String command, Runnable callback) {
        if (isFolia) {
            runGlobalTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                if (callback != null) callback.run();
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                if (callback != null) callback.run();
            });
        }
    }

    // ==================== COMPLETABLE FUTURE UTILITIES ====================

    public static <T> CompletableFuture<T> supplyAsync(Plugin plugin, Location location, java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runTask(plugin, location, () -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static <T> CompletableFuture<T> supplyAsyncEntity(Plugin plugin, Entity entity, java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executeWithEntity(plugin, entity, e -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    public static void cancelTask(ScheduledTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    public static void cancelAllTasks(Plugin plugin) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }
    private static class ScheduledTaskWrapper implements ScheduledTask {
        private final BukkitTask bukkitTask;

        public ScheduledTaskWrapper(BukkitTask bukkitTask) {
            this.bukkitTask = bukkitTask;
        }

        @Override
        public Plugin getOwningPlugin() {
            return bukkitTask.getOwner();
        }

        @Override
        public boolean isRepeatingTask() {
            return false;
        }

        @Override
        public @NotNull CancelledState cancel() {
            bukkitTask.cancel();
            return null;
        }

        @Override
        public boolean isCancelled() {
            return bukkitTask.isCancelled();
        }

        @Override
        public ExecutionState getExecutionState() {
            if (bukkitTask.isCancelled()) {
                return ExecutionState.CANCELLED;
            }
            return ExecutionState.RUNNING;
        }
    }

    // ==================== UTILITY METHODS ====================


    public static String getSchedulerType() {
        return isFolia ? "Folia" : "Paper/Spigot";
    }

    public static void logSchedulerInfo(Plugin plugin) {
        plugin.getLogger().info("Running on " + getSchedulerType() + " scheduler");
        if (isFolia) {
            plugin.getLogger().info("Using threaded region scheduling");
        } else {
            plugin.getLogger().info("Using traditional Bukkit scheduling");
        }
    }

}
