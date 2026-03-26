package com.hikaricraft.vehicle.listener;

import com.hikaricraft.vehicle.manager.VehicleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;
        if (!(event.getEntered() instanceof Player player)) return;
        if (!player.hasPermission("hikarivehicle.drive")) return;

        // enterVehicle checks whether the minecart is on rails;
        // if it is, returns false and vanilla/RailTransit takes over.
        vehicleManager.enterVehicle(player, minecart);
    }

    @EventHandler(priority = EventPriority.HIGH)
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

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;

        if (vehicleManager.isActiveVehicle(minecart.getUniqueId())) {
            vehicleManager.exitVehicle(minecart.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getVehicle() instanceof Minecart minecart) {
            if (vehicleManager.isActiveVehicle(minecart.getUniqueId())) {
                vehicleManager.exitVehicle(minecart.getUniqueId());
            }
        }
    }

    /**
     * Handle player death - show custom death message if killed by vehicle collision.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        VehicleManager.CollisionRecord record = vehicleManager.getCollisionRecord(victim.getUniqueId());

        if (record != null) {
            // Use the death message from config
            String template = vehicleManager.getDeathMessageTemplate();
            String message = template
                    .replace("{victim}", victim.getName())
                    .replace("{driver}", record.driverName());

            event.deathMessage(Component.text(message));
            vehicleManager.removeCollisionRecord(victim.getUniqueId());
        }
    }
}
