package com.hikaricraft.vehicle.listener;

import com.hikaricraft.vehicle.util.RailUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles minecart placement on non-rail surfaces.
 * Allows placing minecarts on any solid block, not just rails.
 */
public class MinecartPlacementListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click on block, main hand only (otherwise the event fires twice).
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || !isMinecart(item.getType())) return;
        if (!event.getPlayer().hasPermission("hikarivehicle.place")) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        BlockFace face = event.getBlockFace();
        if (face == null) return;
        Block targetBlock = clickedBlock.getRelative(face);

        // Defer to vanilla placement when interacting with rails.
        if (RailUtils.isRail(clickedBlock.getType()) || RailUtils.isRail(targetBlock.getType())) {
            return;
        }

        // Target location must be empty or liquid; below must be solid ground.
        if (!targetBlock.getType().isAir() && !targetBlock.isLiquid()) {
            return;
        }
        Block below = targetBlock.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid()) {
            return;
        }

        EntityType entityType = getEntityType(item.getType());
        if (entityType == null) return;

        Player player = event.getPlayer();
        Location loc = targetBlock.getLocation().add(0.5, 0.0, 0.5);

        // Cancel vanilla placement and spawn the matching variant manually.
        event.setCancelled(true);

        Entity spawned = player.getWorld().spawnEntity(loc, entityType);
        if (spawned == null) {
            // Spawn refused (e.g. by another plugin); do not consume the item.
            return;
        }

        // Consume item (respect creative mode).
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (item.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                item.setAmount(item.getAmount() - 1);
            }
        }
    }

    private boolean isMinecart(Material material) {
        return material == Material.MINECART
                || material == Material.CHEST_MINECART
                || material == Material.FURNACE_MINECART
                || material == Material.HOPPER_MINECART
                || material == Material.TNT_MINECART;
    }

    private EntityType getEntityType(Material material) {
        return switch (material) {
            case MINECART -> EntityType.MINECART;
            case CHEST_MINECART -> EntityType.CHEST_MINECART;
            case FURNACE_MINECART -> EntityType.FURNACE_MINECART;
            case HOPPER_MINECART -> EntityType.HOPPER_MINECART;
            case TNT_MINECART -> EntityType.TNT_MINECART;
            default -> null;
        };
    }
}
