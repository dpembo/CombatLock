package uk.co.pembo.combatlock.listener;

import uk.co.pembo.combatlock.CombatLockPlugin;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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

        // Always force off while in combat, whether starting or already gliding.
        // Cancelling the event is optional and unreliable; setGliding(false) is what works.
        event.setCancelled(true);
        if (player.isGliding() || event.isGliding()) {
            player.setGliding(false);
            sendBlockedThrottled(player, "elytra-glide-blocked");
            plugin.debug(player.getName() + " force-stopped glide (toggle event)");
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

        player.setGliding(false);
        sendBlockedThrottled(player, "elytra-glide-blocked");
        plugin.debug(player.getName() + " force-stopped glide (move event)");
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
        player.setGliding(false);
        sendBlockedThrottled(player, "elytra-boost-blocked");
        plugin.debug(player.getName() + " blocked elytra firework boost");
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

        if (plugin.getConfig().getBoolean("elytra.force-stop-on-combat", true)) {
            if (player.isGliding()) {
                player.setGliding(false);
                plugin.debug(player.getName() + " force-stopped glide on combat entry");
            }
        }

        if (plugin.getConfig().getBoolean("elytra.unequip-on-combat", true)) {
            unequipElytra(player);
        }
    }

    private void unequipElytra(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack chest = inv.getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) {
            return;
        }

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
        plugin.debug(player.getName() + " unequipped elytra on combat entry");
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
