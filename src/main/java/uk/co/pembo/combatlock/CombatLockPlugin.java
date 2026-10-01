package uk.co.pembo.combatlock;

import uk.co.pembo.combatlock.listener.CombatDamageListener;
import uk.co.pembo.combatlock.listener.CommandBlockListener;
import uk.co.pembo.combatlock.listener.ElytraListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CombatLockPlugin extends JavaPlugin implements Listener {

    private CombatManager combatManager;
    private ElytraListener elytraListener;
    private String bypassPermission;
    private boolean debug;
    private BukkitTask elytraEnforceTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info(Globeworks.logo("CombatLock", getDescription().getVersion()));
        this.combatManager = new CombatManager(this);
        this.bypassPermission = getConfig().getString("bypass-permission", "combatlock.bypass");
        this.debug = getConfig().getBoolean("debug", false);

        this.elytraListener = new ElytraListener(this);
        getServer().getPluginManager().registerEvents(new CombatDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(this), this);
        getServer().getPluginManager().registerEvents(elytraListener, this);
        getServer().getPluginManager().registerEvents(this, this);

        // Periodic safety net: every 5 ticks, force-stop glide / unequip for anyone still tagged.
        // Event-based blocking alone is unreliable on Paper 26.x (client desync).
        this.elytraEnforceTask = Bukkit.getScheduler().runTaskTimer(this, this::enforceElytraRestrictions, 5L, 5L);
        getLogger().info("Elytra combat restrictions active (events + 5-tick enforcement).");
    }

    @Override
    public void onDisable() {
        if (elytraEnforceTask != null) {
            elytraEnforceTask.cancel();
            elytraEnforceTask = null;
        }
        if (combatManager != null) {
            combatManager.shutdown();
        }
    }

    /**
     * Runs every 5 ticks. For every combat-tagged player: stop gliding and
     * optionally strip chest elytra. This is the reliable path on Paper 26.2.
     */
    private void enforceElytraRestrictions() {
        if (!getConfig().getBoolean("elytra.block-glide", true)
                && !getConfig().getBoolean("elytra.unequip-on-combat", true)) {
            return;
        }

        for (UUID uuid : combatManager.getCombatUuids()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (player.hasPermission(bypassPermission)) {
                continue;
            }

            if (getConfig().getBoolean("elytra.block-glide", true) && player.isGliding()) {
                player.setGliding(false);
                debug(player.getName() + " force-stopped glide (tick enforce)");
            }

            if (getConfig().getBoolean("elytra.unequip-on-combat", true)) {
                PlayerInventory inv = player.getInventory();
                ItemStack chest = inv.getChestplate();
                if (chest != null && chest.getType() == Material.ELYTRA) {
                    inv.setChestplate(null);
                    var leftover = inv.addItem(chest);
                    if (!leftover.isEmpty()) {
                        for (ItemStack stack : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), stack);
                        }
                    }
                    if (player.isGliding()) {
                        player.setGliding(false);
                    }
                    debug(player.getName() + " unequipped elytra (tick enforce)");
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Cancel the lock silently on quit rather than firing on-combat-end
        // commands / messages at a player who's no longer online.
        combatManager.endCombat(event.getPlayer(), false);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("combatlock.reload")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("messages.no-permission", "&cYou don't have permission to do that.")));
                return true;
            }
            reloadConfig();
            this.bypassPermission = getConfig().getString("bypass-permission", "combatlock.bypass");
            this.debug = getConfig().getBoolean("debug", false);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getConfig().getString("messages.reload-success", "&aCombatLock configuration reloaded.")));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("combatlock.reload")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("messages.no-permission", "&cYou don't have permission to do that.")));
                return true;
            }
            if (args.length == 2) {
                if (args[1].equalsIgnoreCase("on")) {
                    this.debug = true;
                } else if (args[1].equalsIgnoreCase("off")) {
                    this.debug = false;
                } else {
                    sender.sendMessage("Usage: /combatlock debug [on|off]");
                    return true;
                }
            } else {
                this.debug = !this.debug;
            }
            getConfig().set("debug", this.debug);
            saveConfig();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aCombatLock debug logging " + (this.debug ? "&2enabled" : "&4disabled") + "&a."));
            return true;
        }
        sender.sendMessage("Usage: /combatlock <reload|debug [on|off]>");
        return true;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public ElytraListener getElytraListener() {
        return elytraListener;
    }

    public String getBypassPermission() {
        return bypassPermission;
    }

    public boolean isDebug() {
        return debug;
    }

    /** Logs a debug message to console when debug mode is enabled via config/command. */
    public void debug(String message) {
        if (debug) {
            getLogger().info("[debug] " + message);
        }
    }
}
