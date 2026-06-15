package com.hikaricraft.vehicle.model;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Minecart;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Stores runtime and persistent state for a driven vehicle.
 *
 * <p>Persistent fields (saved into the minecart's PersistentDataContainer):
 * fuel ticks, current durability, max durability, distance-since-last-durability.
 *
 * <p>Volatile fields (recomputed each session): driverId, speed, heading, waterTicks.
 */
public class VehicleData {

    private static NamespacedKey KEY_FUEL;
    private static NamespacedKey KEY_DURABILITY;
    private static NamespacedKey KEY_MAX_DURABILITY;
    private static NamespacedKey KEY_DISTANCE;

    private UUID driverId;
    private double speed;        // m/s
    private double heading;      // degrees (yaw)
    private int fuelTicks;       // remaining fuel in ticks
    private int durability;
    private int maxDurability;
    private double distanceSinceLastDurability;
    private int waterTicks;      // ticks spent in water

    public static void initKeys(Plugin plugin) {
        // Idempotent: re-initialisation on plugin reload is harmless but unnecessary.
        if (KEY_FUEL != null) return;
        KEY_FUEL = new NamespacedKey(plugin, "vehicle_fuel");
        KEY_DURABILITY = new NamespacedKey(plugin, "vehicle_durability");
        KEY_MAX_DURABILITY = new NamespacedKey(plugin, "vehicle_max_durability");
        KEY_DISTANCE = new NamespacedKey(plugin, "vehicle_distance");
    }

    public VehicleData(int maxDurability) {
        this.speed = 0;
        this.heading = 0;
        this.fuelTicks = 0;
        this.maxDurability = Math.max(1, maxDurability);
        this.durability = this.maxDurability;
        this.distanceSinceLastDurability = 0;
        this.waterTicks = 0;
    }

    /**
     * Load persistent fields from the minecart's PDC.
     * All values are defensively clamped to sane ranges in case the NBT
     * was edited externally or written by an older/buggy version.
     */
    public void loadFromEntity(Minecart minecart) {
        PersistentDataContainer pdc = minecart.getPersistentDataContainer();

        int loadedMax = pdc.getOrDefault(KEY_MAX_DURABILITY, PersistentDataType.INTEGER, maxDurability);
        int loadedDur = pdc.getOrDefault(KEY_DURABILITY, PersistentDataType.INTEGER, maxDurability);
        int loadedFuel = pdc.getOrDefault(KEY_FUEL, PersistentDataType.INTEGER, 0);
        double loadedDist = pdc.getOrDefault(KEY_DISTANCE, PersistentDataType.DOUBLE, 0.0);

        this.maxDurability = Math.max(1, loadedMax);
        this.durability = Math.max(0, Math.min(loadedDur, this.maxDurability));
        this.fuelTicks = Math.max(0, loadedFuel);
        this.distanceSinceLastDurability =
                (Double.isFinite(loadedDist) && loadedDist >= 0.0) ? loadedDist : 0.0;
    }

    public void saveToEntity(Minecart minecart) {
        PersistentDataContainer pdc = minecart.getPersistentDataContainer();
        pdc.set(KEY_FUEL, PersistentDataType.INTEGER, fuelTicks);
        pdc.set(KEY_DURABILITY, PersistentDataType.INTEGER, durability);
        pdc.set(KEY_MAX_DURABILITY, PersistentDataType.INTEGER, maxDurability);
        pdc.set(KEY_DISTANCE, PersistentDataType.DOUBLE, distanceSinceLastDurability);
    }

    // Getters and setters
    public UUID getDriverId() { return driverId; }
    public void setDriverId(UUID driverId) { this.driverId = driverId; }
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    public double getHeading() { return heading; }
    public void setHeading(double heading) { this.heading = heading; }
    public int getFuelTicks() { return fuelTicks; }
    public void setFuelTicks(int fuelTicks) { this.fuelTicks = fuelTicks; }
    public int getDurability() { return durability; }
    public void setDurability(int durability) { this.durability = durability; }
    public int getMaxDurability() { return maxDurability; }
    public double getDistanceSinceLastDurability() { return distanceSinceLastDurability; }
    public void setDistanceSinceLastDurability(double d) { this.distanceSinceLastDurability = d; }
    public int getWaterTicks() { return waterTicks; }
    public void setWaterTicks(int waterTicks) { this.waterTicks = waterTicks; }
}
