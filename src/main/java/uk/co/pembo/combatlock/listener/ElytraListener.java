package uk.co.pembo.combatlock.listener;

import uk.co.pembo.combatlock.CombatLockPlugin;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Blocks elytra glide and equip while a player is combat-locked.
 * Also used by CombatManager to force-stop an active glide on combat entry.
 */
public class ElytraListener implements Listener {

    private final CombatLockPlugin plugin;

    public ElytraListener(CombatLockPlugin plugin) {
        this.plugin = plugin;
    }

    /** Cancel starting a glide while in combat. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // Only care about *starting* a glide, not landing.
        if (!event.isGliding()) {
            return;
        }
        if (!shouldBlock(player)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("elytra.block-glide", true)) {
            return;
        }

        event.setCancelled(true);
        sendBlocked(player, "elytra-glide-blocked");
        plugin.debug(player.getName() + " blocked from starting elytra glide while in combat");
    }

    /**
     * Block right-click equip of an elytra (vanilla "equip from hand" behaviour)
     * while in combat.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

        // Right-click with elytra in hand equips it if chest slot is free/empty enough.
        // Cancel the interaction so it cannot equip.
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                event.setCancelled(true);
                sendBlocked(player, "elytra-equip-blocked");
                plugin.debug(player.getName() + " blocked from equipping elytra via interact while in combat");
            }
            default -> { /* ignore */ }
        }
    }

    /** Block inventory clicks that would place an elytra into the chestplate slot. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

        // Only care about the player's own inventory / crafting view.
        if (event.getClickedInventory() == null) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        int rawSlot = event.getRawSlot();
        ClickType click = event.getClick();

        // Chestplate slot in player inventory is raw slot 6 in the player view
        // (crafting slots 0-4, armor 5-8: helmet=5, chest=6, legs=7, boots=8).
        boolean targetingChestSlot = isPlayerChestSlot(event, rawSlot);

        // Cursor has elytra and click places it into chest slot
        if (targetingChestSlot && cursor != null && cursor.getType() == Material.ELYTRA) {
            event.setCancelled(true);
            sendBlocked(player, "elytra-equip-blocked");
            plugin.debug(player.getName() + " blocked from placing elytra into chest slot while in combat");
            return;
        }

        // Shift-click elytra from inventory into armor
        if (click.isShiftClick() && current != null && current.getType() == Material.ELYTRA) {
            // Shift-click of armor items auto-equips if possible
            PlayerInventory inv = player.getInventory();
            ItemStack chest = inv.getChestplate();
            if (chest == null || chest.getType() == Material.AIR || chest.getType() == Material.ELYTRA) {
                event.setCancelled(true);
                sendBlocked(player, "elytra-equip-blocked");
                plugin.debug(player.getName() + " blocked from shift-click equipping elytra while in combat");
            }
        }

        // Number-key hotbar swap into chest slot
        if (targetingChestSlot && click == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbar);
                if (hotbarItem != null && hotbarItem.getType() == Material.ELYTRA) {
                    event.setCancelled(true);
                    sendBlocked(player, "elytra-equip-blocked");
                    plugin.debug(player.getName() + " blocked from number-key equipping elytra while in combat");
                }
            }
        }
    }

    /** Block drag-and-drop that ends with elytra over the chest slot. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

        // Raw slot 6 is the chestplate slot in the standard player inventory view.
        if (event.getRawSlots().contains(6)) {
            event.setCancelled(true);
            sendBlocked(player, "elytra-equip-blocked");
            plugin.debug(player.getName() + " blocked from dragging elytra onto chest slot while in combat");
        }
    }

    private boolean shouldBlock(Player player) {
        if (player.hasPermission(plugin.getBypassPermission())) {
            return false;
        }
        return plugin.getCombatManager().isInCombat(player.getUniqueId());
    }

    private void sendBlocked(Player player, String messageKey) {
        long remaining = plugin.getCombatManager().getRemainingSeconds(player.getUniqueId());
        String raw = plugin.getConfig().getString("messages." + messageKey, "");
        if (raw == null || raw.isEmpty()) {
            return;
        }
        raw = raw.replace("%time%", String.valueOf(remaining));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', raw));
    }

    /**
     * Detects whether the clicked raw slot is the player's chestplate slot.
     * In the standard player inventory view, armor slots are raw 5–8
     * (helmet, chest, legs, boots). Chestplate is raw slot 6.
     * PlayerInventory storage index for chestplate is 38.
     */
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
