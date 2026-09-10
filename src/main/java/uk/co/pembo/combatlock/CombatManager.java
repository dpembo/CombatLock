package uk.co.pembo.combatlock;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are currently combat-locked, as what role, and for
 * how much longer. Every fresh hit on a player overwrites their previous
 * lock (role + expiry) and reschedules the expiry task - this is what gives
 * the "resets and starts counting down again" behaviour.
 */
public class CombatManager {

    private record Lock(CombatRole role, long expiryMillis, BukkitTask task) {}

    private final CombatLockPlugin plugin;
    private final Map<UUID, Lock> locks = new ConcurrentHashMap<>();

    public CombatManager(CombatLockPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInCombat(UUID uuid) {
        return locks.containsKey(uuid);
    }

    public CombatRole getRole(UUID uuid) {
        Lock lock = locks.get(uuid);
        return lock == null ? null : lock.role();
    }

    public long getRemainingSeconds(UUID uuid) {
        Lock lock = locks.get(uuid);
        if (lock == null) {
            return 0;
        }
        long remainingMillis = lock.expiryMillis() - System.currentTimeMillis();
        return Math.max(0, (remainingMillis + 999) / 1000); // round up
    }

    /**
     * Puts (or re-puts) a player into combat under the given role, resetting
     * their timer to the configured duration for that role and rescheduling
     * the automatic end-of-combat task.
     */
    public void enterCombat(Player player, CombatRole role) {
        if (player.hasPermission(plugin.getBypassPermission())) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        int seconds = config.getInt("combat." + role.configKey() + "-seconds",
                role == CombatRole.AGGRESSOR ? 15 : 10);

        UUID uuid = player.getUniqueId();

        // Cancel any existing expiry task for this player - we're resetting the clock.
        Lock existing = locks.get(uuid);
        if (existing != null) {
            existing.task().cancel();
        }

        long expiry = System.currentTimeMillis() + (seconds * 1000L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> endCombat(player, true), seconds * 20L);

        locks.put(uuid, new Lock(role, expiry, task));

        boolean freshEntry = existing == null || existing.role() != role;
        if (freshEntry) {
            runCommands(config.getStringList("on-combat-start." + role.configKey()), player);
            plugin.debug(player.getName() + " entered combat as " + role + " for " + seconds + "s");
        }

        String messageKey = "entered-combat-" + role.configKey();
        sendMessage(player, messageKey, "%time%", String.valueOf(seconds));
    }

    /**
     * Ends combat for a player. Called automatically on expiry, or manually
     * (e.g. on quit) with natural=false to skip end-of-combat commands/messages.
     */
    public void endCombat(Player player, boolean natural) {
        UUID uuid = player.getUniqueId();
        Lock lock = locks.remove(uuid);
        if (lock == null) {
            return;
        }
        lock.task().cancel();

        if (natural) {
            runCommands(plugin.getConfig().getStringList("on-combat-end." + lock.role().configKey()), player);
            sendMessage(player, "combat-ended", null, null);
        }

        plugin.debug(player.getName() + " left combat (" + lock.role() + ", natural=" + natural + ")");
    }

    /** Cancels all outstanding tasks without running end-of-combat commands, e.g. on plugin disable. */
    public void shutdown() {
        for (Lock lock : locks.values()) {
            lock.task().cancel();
        }
        locks.clear();
    }

    private void runCommands(List<String> commands, Player player) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String command : commands) {
            String resolved = command.replace("%player%", player.getName());
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved));
        }
    }

    private void sendMessage(Player player, String key, String placeholder, String value) {
        String raw = plugin.getConfig().getString("messages." + key, "");
        if (raw == null || raw.isEmpty()) {
            return;
        }
        if (placeholder != null) {
            raw = raw.replace(placeholder, value);
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', raw));
    }
}
