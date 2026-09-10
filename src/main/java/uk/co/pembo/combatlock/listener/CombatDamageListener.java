package uk.co.pembo.combatlock.listener;

import uk.co.pembo.combatlock.CombatLockPlugin;
import uk.co.pembo.combatlock.CombatRole;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class CombatDamageListener implements Listener {

    private final CombatLockPlugin plugin;

    public CombatDamageListener(CombatLockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return; // no self-damage combat tagging
        }

        plugin.getCombatManager().enterCombat(attacker, CombatRole.AGGRESSOR);
        plugin.getCombatManager().enterCombat(victim, CombatRole.DEFENDER);
    }

    /** Resolves the attacking player, following projectiles back to their shooter. */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
