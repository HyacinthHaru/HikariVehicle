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
            configManager.load();
            sender.sendMessage(configManager.getComponent("reload-success"));
            return true;
        }

        return false;
    }
}
