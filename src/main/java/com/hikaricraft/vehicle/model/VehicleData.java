package com.hikaricraft.vehicle.model;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Minecart;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Stores runtime and persistent state for a driven vehicle.
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
        KEY_FUEL = new NamespacedKey(plugin, "vehicle_fuel");
        KEY_DURABILITY = new NamespacedKey(plugin, "vehicle_durability");
        KEY_MAX_DURABILITY = new NamespacedKey(plugin, "vehicle_max_durability");
        KEY_DISTANCE = new NamespacedKey(plugin, "vehicle_distance");
    }

    public VehicleData(int maxDurability) {
        this.speed = 0;
        this.heading = 0;
        this.fuelTicks = 0;
        this.durability = maxDurability;
        this.maxDurability = maxDurability;
        this.distanceSinceLastDurability = 0;
        this.waterTicks = 0;
    }

    public void loadFromEntity(Minecart minecart) {
        PersistentDataContainer pdc = minecart.getPersistentDataContainer();
        this.fuelTicks = pdc.getOrDefault(KEY_FUEL, PersistentDataType.INTEGER, 0);
        this.durability = pdc.getOrDefault(KEY_DURABILITY, PersistentDataType.INTEGER, maxDurability);
        this.maxDurability = pdc.getOrDefault(KEY_MAX_DURABILITY, PersistentDataType.INTEGER, maxDurability);
        this.distanceSinceLastDurability = pdc.getOrDefault(KEY_DISTANCE, PersistentDataType.DOUBLE, 0.0);
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
