package uk.co.pembo.combatlock;

import uk.co.pembo.combatlock.listener.CombatDamageListener;
import uk.co.pembo.combatlock.listener.CommandBlockListener;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CombatLockPlugin extends JavaPlugin implements Listener {

    private CombatManager combatManager;
    private String bypassPermission;
    private boolean debug;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info(Globeworks.logo("CombatLock", getDescription().getVersion()));
        this.combatManager = new CombatManager(this);
        this.bypassPermission = getConfig().getString("bypass-permission", "combatlock.bypass");
        this.debug = getConfig().getBoolean("debug", false);

        getServer().getPluginManager().registerEvents(new CombatDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (combatManager != null) {
            combatManager.shutdown();
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
