package com.hikaricraft.vehicle;

import com.hikaricraft.vehicle.config.ConfigManager;
import com.hikaricraft.vehicle.listener.MinecartPlacementListener;
import com.hikaricraft.vehicle.listener.VehicleListener;
import com.hikaricraft.vehicle.manager.VehicleManager;
import com.hikaricraft.vehicle.model.VehicleData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class HikariVehicle extends JavaPlugin {

    private ConfigManager configManager;
    private VehicleManager vehicleManager;

    @Override
    public void onEnable() {
        try {
            VehicleData.initKeys(this);

            configManager = new ConfigManager(this);
            configManager.load();

            vehicleManager = new VehicleManager(this, configManager);
            vehicleManager.start();

            getServer().getPluginManager().registerEvents(
                    new VehicleListener(vehicleManager), this);
            getServer().getPluginManager().registerEvents(
                    new MinecartPlacementListener(), this);

            getLogger().info("HikariVehicle enabled!");
        } catch (RuntimeException e) {
            getLogger().severe("Failed to enable HikariVehicle: " + e.getMessage());
            // Best-effort rollback so a tick task started before the failure is not orphaned.
            if (vehicleManager != null) {
                try {
                    vehicleManager.stop();
                } catch (RuntimeException ignored) {
                    // Already failing; just continue tearing down.
                }
            }
            throw e;
        }
    }

    @Override
    public void onDisable() {
        if (vehicleManager != null) {
            vehicleManager.stop();
        }
        getLogger().info("HikariVehicle disabled!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("hv")) return false;

        if (args.length == 0) {
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                    configManager.getPrefix()
                            + "HikariVehicle v" + getPluginMeta().getVersion()));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("hikarivehicle.admin")) {
                sender.sendMessage(configManager.getComponent("no-permission"));
                return true;
            }
            // Persist current in-memory state of every active vehicle before
            // reloading, so the reload itself cannot lose driver progress.
            if (vehicleManager != null) {
                vehicleManager.saveActiveVehicles();
            }
            configManager.load();
            sender.sendMessage(configManager.getComponent("reload-success"));
            return true;
        }

        return false;
    }
}
