package uk.co.pembo.combatlock.listener;

import uk.co.pembo.combatlock.CombatLockPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

/**
 * Registered at LOWEST priority so this runs before any other plugin
 * (Towny, AxPlayerWarps, CommandAlias, etc.) sees the command at all.
 */
public class CommandBlockListener implements Listener {

    private final CombatLockPlugin plugin;

    public CommandBlockListener(CombatLockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission(plugin.getBypassPermission())) {
            return;
        }
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            return;
        }

        // Strip leading slash, lower-case, for matching against config.
        String typed = event.getMessage().substring(1).toLowerCase();

        List<String> blocked = plugin.getConfig().getStringList("blocked-commands");
        for (String pattern : blocked) {
            String lowerPattern = pattern.toLowerCase();
            if (typed.equals(lowerPattern) || typed.startsWith(lowerPattern + " ")) {
                event.setCancelled(true);
                long remaining = plugin.getCombatManager().getRemainingSeconds(player.getUniqueId());
                String message = plugin.getConfig().getString("messages.command-blocked", "")
                        .replace("%time%", String.valueOf(remaining));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                plugin.debug(player.getName() + " blocked from running \"" + typed + "\" (" + remaining + "s remaining)");
                return;
            }
        }
    }
}
