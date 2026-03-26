package com.hikaricraft.vehicle.config;

import com.hikaricraft.vehicle.HikariVehicle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages all plugin configuration with i18n support.
 */
public class ConfigManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    // 默认中文消息
    private static final Map<String, String> DEFAULT_MESSAGES_ZH_CN = Map.of(
            "no-permission", "&c你没有权限执行此操作",
            "no-fuel", "&c燃料耗尽！请补充燃料",
            "fuel-consumed", "&a已消耗燃料: &f{item}",
            "vehicle-destroyed", "&c车辆已损坏报废",
            "water-eject", "&e车辆落水！已强制弹出",
            "lava-destroy", "&c车辆被岩浆销毁！",
            "reload-success", "&a配置已重新加载",
            "durability-low", "&e车辆耐久度不足: &f{durability}/{max}",
            "death-by-vehicle", "&c{victim} &f被 &c{driver} &f开车撞死了"
    );

    // Default English messages
    private static final Map<String, String> DEFAULT_MESSAGES_EN_US = Map.of(
            "no-permission", "&cYou don't have permission to do this",
            "no-fuel", "&cOut of fuel! Please refuel",
            "fuel-consumed", "&aConsumed fuel: &f{item}",
            "vehicle-destroyed", "&cVehicle has been destroyed",
            "water-eject", "&eVehicle fell into water! Ejected",
            "lava-destroy", "&cVehicle destroyed by lava!",
            "reload-success", "&aConfiguration reloaded",
            "durability-low", "&eVehicle durability low: &f{durability}/{max}",
            "death-by-vehicle", "&c{victim} &fwas run over by &c{driver}"
    );

    private final HikariVehicle plugin;
    private volatile FileConfiguration config;

    // Language
    private String language;

    // Movement
    private double maxSpeed;
    private double acceleration;
    private double coastFriction;
    private double brakeFriction;
    private double minSpeed;

    // Steering
    private double maxTurnRate;
    private double turnDamping;
    private double keyTurnRate;

    // Terrain
    private double stepHeight;

    // Fuel
    private boolean fuelEnabled;
    private Map<Material, Integer> fuelItems;
    private int defaultBurnTime;

    // Durability
    private boolean durabilityEnabled;
    private int maxDurability;
    private double distancePerDurability;

    // Collision
    private boolean collisionEnabled;
    private double collisionDamage;
    private double collisionMinSpeed;
    private double collisionKnockback;
    private int collisionDamageCooldown;
    private int collisionDeathTrackWindow;

    // Hazards
    private int waterEjectDelay;
    private boolean lavaInstantDestroy;

    // Effects
    private boolean exhaustEnabled;
    private Particle exhaustType;
    private int exhaustCount;
    private boolean exhaustOnlyWhenAccelerating;

    // Messages
    private String messagePrefix;
    private Map<String, String> messages;

    public ConfigManager(HikariVehicle plugin) {
        this.plugin = plugin;
        this.fuelItems = new LinkedHashMap<>();
        this.messages = new HashMap<>();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        loadLanguage();
        loadMovement();
        loadSteering();
        loadTerrain();
        loadFuel();
        loadDurability();
        loadCollision();
        loadHazards();
        loadEffects();
        loadMessages();
    }

    private void loadLanguage() {
        language = config.getString("language", "zh_cn").toLowerCase();
    }

    private void loadMovement() {
        maxSpeed = config.getDouble("movement.max-speed", 10.0);
        acceleration = config.getDouble("movement.acceleration", 2.0);
        coastFriction = config.getDouble("movement.coast-friction", 0.92);
        brakeFriction = config.getDouble("movement.brake-friction", 0.80);
        minSpeed = config.getDouble("movement.min-speed", 0.1);
    }

    private void loadSteering() {
        maxTurnRate = config.getDouble("steering.max-turn-rate", 15.0);
        turnDamping = config.getDouble("steering.turn-damping", 0.3);
        keyTurnRate = config.getDouble("steering.key-turn-rate", 5.0);
    }

    private void loadTerrain() {
        stepHeight = config.getDouble("terrain.step-height", 0.5);
    }

    private void loadFuel() {
        fuelEnabled = config.getBoolean("fuel.enabled", true);
        defaultBurnTime = config.getInt("fuel.default-burn-time", 60);

        fuelItems.clear();
        ConfigurationSection section = config.getConfigurationSection("fuel.items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    fuelItems.put(material, section.getInt(key));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid fuel material: " + key);
                }
            }
        }
    }

    private void loadDurability() {
        durabilityEnabled = config.getBoolean("durability.enabled", true);
        maxDurability = config.getInt("durability.max-durability", 1000);
        distancePerDurability = config.getDouble("durability.distance-per-durability", 10.0);
    }

    private void loadCollision() {
        collisionEnabled = config.getBoolean("collision.enabled", true);
        collisionDamage = config.getDouble("collision.damage", 1.0);
        collisionMinSpeed = config.getDouble("collision.min-speed", 3.0);
        collisionKnockback = config.getDouble("collision.knockback", 0.5);
        collisionDamageCooldown = config.getInt("collision.damage-cooldown", 20);
        collisionDeathTrackWindow = config.getInt("collision.death-track-window", 100);
    }

    private void loadHazards() {
        waterEjectDelay = config.getInt("hazards.water.eject-delay", 20);
        lavaInstantDestroy = config.getBoolean("hazards.lava.instant-destroy", true);
    }

    private void loadEffects() {
        exhaustEnabled = config.getBoolean("effects.exhaust.enabled", true);

        String particleName = config.getString("effects.exhaust.type", "CAMPFIRE_SIGNAL_SMOKE");
        try {
            exhaustType = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            exhaustType = Particle.CAMPFIRE_SIGNAL_SMOKE;
            plugin.getLogger().warning("Invalid particle type: " + particleName);
        }

        exhaustCount = config.getInt("effects.exhaust.count", 1);
        exhaustOnlyWhenAccelerating = config.getBoolean("effects.exhaust.only-when-accelerating", true);
    }

    private void loadMessages() {
        // 根据语言选择消息节点
        String messageSection = "messages_" + language;
        ConfigurationSection section = config.getConfigurationSection(messageSection);

        // 如果指定语言的节点不存在，尝试回退
        if (section == null) {
            if (language.equals("en_us")) {
                section = config.getConfigurationSection("messages_zh_cn");
            } else {
                section = config.getConfigurationSection("messages_en_us");
            }
        }

        messages.clear();
        if (section != null) {
            messagePrefix = section.getString("prefix", "&7[&bHikariVehicle&7]&r ");
            for (String key : section.getKeys(false)) {
                if (!key.equals("prefix")) {
                    messages.put(key, section.getString(key, ""));
                }
            }
        } else {
            messagePrefix = "&7[&bHikariVehicle&7]&r ";
        }
    }

    /**
     * Get a formatted message string with § color codes.
     */
    public String getMessage(String key) {
        // 优先使用配置文件中的消息，其次使用默认消息
        String msg = messages.get(key);
        if (msg == null || msg.isEmpty()) {
            msg = getDefaultMessage(key);
            if (msg == null) {
                plugin.getLogger().warning("Missing message key in config: " + key);
                msg = key;
            }
        }
        return colorize(messagePrefix + msg);
    }

    public String getPrefix() {
        return colorize(messagePrefix);
    }

    /**
     * Get a raw message without prefix (for death messages etc.)
     */
    public String getRawMessage(String key) {
        String msg = messages.get(key);
        if (msg == null || msg.isEmpty()) {
            msg = getDefaultMessage(key);
            if (msg == null) {
                plugin.getLogger().warning("Missing message key in config: " + key);
                msg = key;
            }
        }
        return colorize(msg);
    }

    private String getDefaultMessage(String key) {
        if (language.equals("en_us")) {
            return DEFAULT_MESSAGES_EN_US.get(key);
        }
        return DEFAULT_MESSAGES_ZH_CN.get(key);
    }

    /**
     * Get a message as an Adventure Component.
     */
    public Component getComponent(String key) {
        return LEGACY.deserialize(getMessage(key));
    }

    /**
     * Parse a legacy § string to an Adventure Component.
     */
    public static Component toComponent(String legacyText) {
        return LEGACY.deserialize(legacyText);
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }

    // Getters
    public double getMaxSpeed() { return maxSpeed; }
    public double getAcceleration() { return acceleration; }
    public double getCoastFriction() { return coastFriction; }
    public double getBrakeFriction() { return brakeFriction; }
    public double getMinSpeed() { return minSpeed; }

    public double getMaxTurnRate() { return maxTurnRate; }
    public double getTurnDamping() { return turnDamping; }
    public double getKeyTurnRate() { return keyTurnRate; }

    public double getStepHeight() { return stepHeight; }

    public boolean isFuelEnabled() { return fuelEnabled; }
    public Map<Material, Integer> getFuelItems() { return fuelItems; }
    public int getDefaultBurnTime() { return defaultBurnTime; }

    public boolean isDurabilityEnabled() { return durabilityEnabled; }
    public int getMaxDurability() { return maxDurability; }
    public double getDistancePerDurability() { return distancePerDurability; }

    public boolean isCollisionEnabled() { return collisionEnabled; }
    public double getCollisionDamage() { return collisionDamage; }
    public double getCollisionMinSpeed() { return collisionMinSpeed; }
    public double getCollisionKnockback() { return collisionKnockback; }
    public int getCollisionDamageCooldown() { return collisionDamageCooldown; }
    public int getCollisionDeathTrackWindow() { return collisionDeathTrackWindow; }

    public int getWaterEjectDelay() { return waterEjectDelay; }
    public boolean isLavaInstantDestroy() { return lavaInstantDestroy; }

    public boolean isExhaustEnabled() { return exhaustEnabled; }
    public Particle getExhaustType() { return exhaustType; }
    public int getExhaustCount() { return exhaustCount; }
    public boolean isExhaustOnlyWhenAccelerating() { return exhaustOnlyWhenAccelerating; }
}
