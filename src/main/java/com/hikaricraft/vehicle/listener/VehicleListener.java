package com.hikaricraft.vehicle.listener;

import com.hikaricraft.vehicle.config.ConfigManager;
import com.hikaricraft.vehicle.manager.VehicleManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/**
 * Handles vehicle enter/exit/destroy events and player quit cleanup.
 */
public class VehicleListener implements Listener {

    private final VehicleManager vehicleManager;

    public VehicleListener(VehicleManager vehicleManager) {
        this.vehicleManager = vehicleManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;
        if (!(event.getEntered() instanceof Player player)) return;
        if (!player.hasPermission("hikarivehicle.drive")) return;

        // enterVehicle checks whether the minecart is on rails;
        // if it is, returns false and vanilla/RailTransit takes over.
        vehicleManager.enterVehicle(player, minecart);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;

        if (vehicleManager.isActiveVehicle(minecart.getUniqueId())) {
            if (vehicleManager.shouldCancelExit(minecart.getUniqueId(), event.getExited())) {
                // Block manual dismount while actively driving.
                // Safe/abnormal exits are allowed so the cart can be cleaned up.
                event.setCancelled(true);
                return;
            }
            vehicleManager.exitVehicle(minecart.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;

        if (vehicleManager.isActiveVehicle(minecart.getUniqueId())) {
            vehicleManager.exitVehicle(minecart.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getVehicle() instanceof Minecart minecart
                && vehicleManager.isActiveVehicle(minecart.getUniqueId())) {
            vehicleManager.exitVehicle(minecart.getUniqueId());
        }
        // Drop any pending collision record for this player so it doesn't
        // sit in memory until the death-track window expires.
        vehicleManager.removeCollisionRecord(player.getUniqueId());
    }

    /**
     * Show a custom death message when the player was killed by a vehicle collision.
     * Runs at MONITOR priority so other death-message plugins get to set their
     * message first, and the vehicle attribution wins only if applicable.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        VehicleManager.CollisionRecord record = vehicleManager.getCollisionRecord(victim.getUniqueId());
        if (record == null) return;

        // Only claim the death if the *killing blow* actually came from the recorded
        // driver. Without this, a harmless earlier bump would hijack any unrelated
        // death (fall, lava, mob) that happens within the tracking window.
        if (!wasKilledByDriver(victim, record)) {
            vehicleManager.removeCollisionRecord(victim.getUniqueId());
            return;
        }

        String template = vehicleManager.getDeathMessageTemplate();
        if (template == null || template.isEmpty()) {
            vehicleManager.removeCollisionRecord(victim.getUniqueId());
            return;
        }

        String victimName = victim.getName();
        String driverName = record.driverName() != null ? record.driverName() : "";
        String message = template
                .replace("{victim}", victimName != null ? victimName : "")
                .replace("{driver}", driverName);

        event.deathMessage(ConfigManager.toComponent(message));
        vehicleManager.removeCollisionRecord(victim.getUniqueId());
    }

    /**
     * True only when the victim's last damage cause was the recorded driver.
     * Collision damage is now dealt with the driver as the source, so the
     * killing blow's damager is the player who ran them over.
     */
    private boolean wasKilledByDriver(Player victim, VehicleManager.CollisionRecord record) {
        EntityDamageEvent cause = victim.getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        Entity damager = byEntity.getDamager();
        return damager instanceof Player p && p.getUniqueId().equals(record.driverId());
    }
}
