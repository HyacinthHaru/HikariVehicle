package com.hikaricraft.vehicle.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Minecart;
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click on block with minecart
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || !isMinecart(item.getType())) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        BlockFace face = event.getBlockFace();
        Block targetBlock = clickedBlock.getRelative(face);

        // If placing on rail, let vanilla handle it
        if (isRail(clickedBlock.getType()) || isRail(targetBlock.getType())) {
            return;
        }

        // Check if target location is valid (solid ground or valid surface)
        if (!targetBlock.getType().isAir() && !targetBlock.isLiquid()) {
            return;
        }

        // Check if there's solid ground below
        Block below = targetBlock.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid()) {
            return;
        }

        // Cancel vanilla behavior and place minecart manually
        event.setCancelled(true);

        Player player = event.getPlayer();
        org.bukkit.Location loc = targetBlock.getLocation().add(0.5, 0.0, 0.5);

        // Spawn minecart
        Minecart minecart = (Minecart) player.getWorld().spawn(loc, org.bukkit.entity.EntityType.MINECART.getEntityClass());

        // Consume item (respect creative mode)
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    private boolean isMinecart(Material material) {
        return material == Material.MINECART
                || material == Material.CHEST_MINECART
                || material == Material.FURNACE_MINECART
                || material == Material.HOPPER_MINECART
                || material == Material.TNT_MINECART;
    }

    private boolean isRail(Material material) {
        return material == Material.RAIL
                || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL
                || material == Material.ACTIVATOR_RAIL;
    }
}
