package uk.co.pembo.combatlock.listener;

import uk.co.pembo.combatlock.CombatLockPlugin;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Blocks elytra while combat-locked.
 * <p>
 * Approach matches plugins known to work on Paper 26.2:
 * do <b>not</b> rely on cancelling {@link EntityToggleGlideEvent} alone
 * (client desync). Instead force {@code setGliding(false)} on toggle and
 * on every {@link PlayerMoveEvent} while tagged, cancel firework boosts,
 * block equip, and unequip on combat entry.
 */
public class ElytraListener implements Listener {

    private final CombatLockPlugin plugin;

    public ElytraListener(CombatLockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * When the player tries to start (or is forced into) glide while tagged:
     * force glide off. Do not depend on event cancellation — Paper clients
     * often ignore it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!shouldBlockGlide(player)) {
            return;
        }

        // Cancel + hard stop. Client often stays in "lying down" pose if we only
        // setGliding(false); reset pose/swimming and (throttled) resync location.
        event.setCancelled(true);
        if (player.isGliding() || event.isGliding()) {
            stopGlideHard(player, false);
            sendBlockedThrottled(player, "elytra-glide-blocked");
            debugThrottled(player, "force-stopped glide (toggle event)");
        }
    }

    /**
     * Continuous enforcement: every movement while tagged and gliding,
     * force glide off. This is the approach used by working 26.2 plugins.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only act on actual position changes to limit work (ignore look-only).
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isGliding()) {
            return;
        }
        if (!shouldBlockGlide(player)) {
            return;
        }

        stopGlideHard(player, false);
        sendBlockedThrottled(player, "elytra-glide-blocked");
        debugThrottled(player, "force-stopped glide (move event)");
    }

    /** Cancel firework rocket boosts while in combat. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        Player player = event.getPlayer();
        if (!shouldBlock(player)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("elytra.block-boost", true)) {
            return;
        }

        event.setCancelled(true);
        stopGlideHard(player, false);
        sendBlockedThrottled(player, "elytra-boost-blocked");
        debugThrottled(player, "blocked elytra firework boost");
    }

    /** Block right-click equip of an elytra. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEquip(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("elytra.block-equip", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!shouldBlock(player)) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ELYTRA) {
            return;
        }

        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                event.setCancelled(true);
                sendBlockedThrottled(player, "elytra-equip-blocked");
                plugin.debug(player.getName() + " blocked elytra equip (interact)");
            }
            default -> { /* ignore */ }
        }
    }

    /** Block inventory clicks that put elytra into the chest slot. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("elytra.block-equip", true)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!shouldBlock(player)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        int rawSlot = event.getRawSlot();
        ClickType click = event.getClick();
        boolean targetingChestSlot = isPlayerChestSlot(event, rawSlot);

        if (targetingChestSlot && cursor != null && cursor.getType() == Material.ELYTRA) {
            event.setCancelled(true);
            sendBlockedThrottled(player, "elytra-equip-blocked");
            return;
        }

        if (click.isShiftClick() && current != null && current.getType() == Material.ELYTRA) {
            PlayerInventory inv = player.getInventory();
            ItemStack chest = inv.getChestplate();
            if (chest == null || chest.getType().isAir() || chest.getType() == Material.ELYTRA) {
                event.setCancelled(true);
                sendBlockedThrottled(player, "elytra-equip-blocked");
            }
            return;
        }

        if (targetingChestSlot && click == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbar);
                if (hotbarItem != null && hotbarItem.getType() == Material.ELYTRA) {
                    event.setCancelled(true);
                    sendBlockedThrottled(player, "elytra-equip-blocked");
                }
            }
        }
    }

    /** Block dragging elytra onto the chest slot. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!plugin.getConfig().getBoolean("elytra.block-equip", true)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!shouldBlock(player)) {
            return;
        }

        ItemStack old = event.getOldCursor();
        if (old == null || old.getType() != Material.ELYTRA) {
            return;
        }
        if (event.getRawSlots().contains(6)) {
            event.setCancelled(true);
            sendBlockedThrottled(player, "elytra-equip-blocked");
        }
    }

    // -------------------------------------------------------------------------
    // Called from CombatManager on combat entry
    // -------------------------------------------------------------------------

    public void onCombatEnter(Player player) {
        if (player.hasPermission(plugin.getBypassPermission())) {
            return;
        }

        ItemStack chest = player.getInventory().getChestplate();
        String chestType = (chest == null || chest.getType().isAir()) ? "EMPTY" : chest.getType().name();
        plugin.debug(player.getName() + " combat-enter state: gliding=" + player.isGliding()
                + " chest=" + chestType
                + " block-glide=" + plugin.getConfig().getBoolean("elytra.block-glide", true)
                + " unequip=" + plugin.getConfig().getBoolean("elytra.unequip-on-combat", true));

        if (plugin.getConfig().getBoolean("elytra.force-stop-on-combat", true)) {
            if (player.isGliding()) {
                // Full resync on combat entry (teleport) to clear stuck glide pose.
                stopGlideHard(player, true);
                plugin.debug(player.getName() + " force-stopped glide on combat entry");
            }
        }

        if (plugin.getConfig().getBoolean("elytra.unequip-on-combat", true)) {
            unequipElytra(player);
        }
    }

    /** Cancel trident riptide propulsion while in combat (common escape tool). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        if (!plugin.getConfig().getBoolean("elytra.block-riptide", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!shouldBlock(player)) {
            return;
        }
        event.setCancelled(true);
        // Kill residual velocity from client-side riptide.
        player.setVelocity(player.getVelocity().multiply(0));
        sendBlockedThrottled(player, "riptide-blocked");
        plugin.debug(player.getName() + " blocked trident riptide while in combat");
    }

    private void unequipElytra(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack chest = inv.getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) {
            plugin.debug(player.getName() + " unequip skipped (chest is not ELYTRA)");
            return;
        }

        inv.setChestplate(null);
        var leftover = inv.addItem(chest);
        if (!leftover.isEmpty()) {
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
        stopGlideHard(player, true);
        plugin.debug(player.getName() + " unequipped elytra on combat entry");
    }


    /**
     * Force the player out of glide state and clear the client "lying down" pose.
     * @param resync if true, teleport the player to their current location to
     *               force a full client entity refresh (use on combat entry /
     *               unequip; avoid on every move/toggle to prevent jank).
     */
    private void stopGlideHard(Player player, boolean resync) {
        player.setGliding(false);
        player.setSwimming(false);
        // Clear fall-flying pose that often sticks after cancel/unequip on Paper.
        try {
            player.setPose(Pose.STANDING);
        } catch (Throwable ignored) {
            // Pose API should exist on 1.14+; ignore if something odd happens.
        }
        if (resync) {
            Location loc = player.getLocation();
            // Same-location teleport forces the client to refresh entity metadata.
            player.teleport(loc);
            player.setGliding(false);
            player.setSwimming(false);
            try {
                player.setPose(Pose.STANDING);
            } catch (Throwable ignored) {
            }
        }
    }

    private final java.util.Map<java.util.UUID, Long> lastDebugMillis = new java.util.concurrent.ConcurrentHashMap<>();

    private void debugThrottled(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = lastDebugMillis.get(player.getUniqueId());
        if (last != null && now - last < 1000L) {
            return;
        }
        lastDebugMillis.put(player.getUniqueId(), now);
        plugin.debug(player.getName() + " " + message);
    }

    private boolean shouldBlock(Player player) {
        if (player.hasPermission(plugin.getBypassPermission())) {
            return false;
        }
        return plugin.getCombatManager().isInCombat(player.getUniqueId());
    }

    private boolean shouldBlockGlide(Player player) {
        if (!plugin.getConfig().getBoolean("elytra.block-glide", true)) {
            return false;
        }
        return shouldBlock(player);
    }

    /** Throttle denial messages so move-event spam does not flood chat. */
    private final java.util.Map<java.util.UUID, Long> lastMessageMillis = new java.util.concurrent.ConcurrentHashMap<>();

    private void sendBlockedThrottled(Player player, String messageKey) {
        long now = System.currentTimeMillis();
        Long last = lastMessageMillis.get(player.getUniqueId());
        if (last != null && now - last < 2000L) {
            return; // at most one message every 2 seconds
        }
        lastMessageMillis.put(player.getUniqueId(), now);

        long remaining = plugin.getCombatManager().getRemainingSeconds(player.getUniqueId());
        String raw = plugin.getConfig().getString("messages." + messageKey, "");
        if (raw == null || raw.isEmpty()) {
            return;
        }
        raw = raw.replace("%time%", String.valueOf(remaining));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', raw));
    }

    private boolean isPlayerChestSlot(InventoryClickEvent event, int rawSlot) {
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType == InventoryType.CRAFTING || topType == InventoryType.PLAYER) {
            return rawSlot == 6;
        }
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
                && event.getClickedInventory() instanceof PlayerInventory) {
            return event.getSlot() == 38;
        }
        return false;
    }
}
