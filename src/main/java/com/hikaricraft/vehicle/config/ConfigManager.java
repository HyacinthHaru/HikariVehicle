package com.hikaricraft.vehicle.config;

import com.hikaricraft.vehicle.HikariVehicle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages all plugin configuration with i18n support.
 *
 * <p>All values loaded from disk are clamped to safe ranges so an
 * accidentally bad config cannot trigger divide-by-zero, runaway speed,
 * negative cool-downs, or other physics breakage.
 */
public class ConfigManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private static final String DEFAULT_PREFIX = "&7[&bHikariVehicle&7]&r ";

    // Default Chinese messages (fallback when neither config.yml nor selected language provides them).
    private static final Map<String, String> DEFAULT_MESSAGES_ZH_CN = Map.ofEntries(
            Map.entry("no-permission", "&c你没有权限执行此操作"),
            Map.entry("no-fuel", "&c燃料耗尽！请补充燃料"),
            Map.entry("fuel-consumed", "&a已消耗燃料: &f{item}"),
            Map.entry("vehicle-destroyed", "&c车辆已损坏报废"),
            Map.entry("water-eject", "&e车辆落水！已强制弹出"),
            Map.entry("lava-destroy", "&c车辆被岩浆销毁！"),
            Map.entry("reload-success", "&a配置已重新加载"),
            Map.entry("durability-low", "&e车辆耐久度不足: &f{durability}/{max}"),
            Map.entry("death-by-vehicle", "&c{victim} &f被 &c{driver} &f开车撞死了")
    );

    // Default English messages.
    private static final Map<String, String> DEFAULT_MESSAGES_EN_US = Map.ofEntries(
            Map.entry("no-permission", "&cYou don't have permission to do this"),
            Map.entry("no-fuel", "&cOut of fuel! Please refuel"),
            Map.entry("fuel-consumed", "&aConsumed fuel: &f{item}"),
            Map.entry("vehicle-destroyed", "&cVehicle has been destroyed"),
            Map.entry("water-eject", "&eVehicle fell into water! Ejected"),
            Map.entry("lava-destroy", "&cVehicle destroyed by lava!"),
            Map.entry("reload-success", "&aConfiguration reloaded"),
            Map.entry("durability-low", "&eVehicle durability low: &f{durability}/{max}"),
            Map.entry("death-by-vehicle", "&c{victim} &fwas run over by &c{driver}")
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
    private double collisionMaxDamage;
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
        // getString may return null if the YAML key is present but explicitly null;
        // fall back to the default to avoid a NPE on toLowerCase().
        String raw = config.getString("language", "zh_cn");
        if (raw == null) raw = "zh_cn";
        language = raw.toLowerCase();
    }

    private void loadMovement() {
        maxSpeed = clampPositive(config.getDouble("movement.max-speed", 10.0), 10.0, "movement.max-speed");
        acceleration = clampNonNegative(config.getDouble("movement.acceleration", 2.0), 2.0, "movement.acceleration");
        coastFriction = clampUnit(config.getDouble("movement.coast-friction", 0.92), 0.92, "movement.coast-friction");
        brakeFriction = clampUnit(config.getDouble("movement.brake-friction", 0.80), 0.80, "movement.brake-friction");
        minSpeed = clampNonNegative(config.getDouble("movement.min-speed", 0.1), 0.1, "movement.min-speed");
        if (minSpeed >= maxSpeed) {
            plugin.getLogger().warning("movement.min-speed (" + minSpeed
                    + ") must be < movement.max-speed (" + maxSpeed + "); resetting to default 0.1.");
            minSpeed = Math.min(0.1, maxSpeed * 0.5);
        }
    }

    private void loadSteering() {
        maxTurnRate = clampNonNegative(config.getDouble("steering.max-turn-rate", 15.0), 15.0, "steering.max-turn-rate");
        turnDamping = clampNonNegative(config.getDouble("steering.turn-damping", 0.3), 0.3, "steering.turn-damping");
        keyTurnRate = clampNonNegative(config.getDouble("steering.key-turn-rate", 5.0), 5.0, "steering.key-turn-rate");
    }

    private void loadTerrain() {
        double v = config.getDouble("terrain.step-height", 0.5);
        if (!Double.isFinite(v) || v < 0.0 || v > 2.0) {
            plugin.getLogger().warning("terrain.step-height (" + v + ") out of range [0, 2]; using default 0.5.");
            v = 0.5;
        }
        stepHeight = v;
    }

    private void loadFuel() {
        fuelEnabled = config.getBoolean("fuel.enabled", true);
        defaultBurnTime = clampPositiveInt(
                config.getInt("fuel.default-burn-time", 60), 60, "fuel.default-burn-time");

        fuelItems.clear();
        ConfigurationSection section = config.getConfigurationSection("fuel.items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    int seconds = section.getInt(key);
                    if (seconds <= 0) {
                        plugin.getLogger().warning("fuel.items." + key + " must be > 0; skipping.");
                        continue;
                    }
                    fuelItems.put(material, seconds);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid fuel material: " + key);
                }
            }
        }
    }

    private void loadDurability() {
        durabilityEnabled = config.getBoolean("durability.enabled", true);
        maxDurability = clampPositiveInt(
                config.getInt("durability.max-durability", 1000), 1000, "durability.max-durability");
        distancePerDurability = clampStrictlyPositive(
                config.getDouble("durability.distance-per-durability", 10.0),
                10.0, "durability.distance-per-durability");
    }

    private void loadCollision() {
        collisionEnabled = config.getBoolean("collision.enabled", true);
        collisionDamage = clampNonNegative(
                config.getDouble("collision.damage", 1.0), 1.0, "collision.damage");
        collisionMaxDamage = clampNonNegative(
                config.getDouble("collision.max-damage", 4.0), 4.0, "collision.max-damage");
        if (collisionMaxDamage < collisionDamage) {
            plugin.getLogger().warning("collision.max-damage (" + collisionMaxDamage
                    + ") must be >= collision.damage (" + collisionDamage + "); raising to base.");
            collisionMaxDamage = collisionDamage;
        }
        collisionMinSpeed = clampNonNegative(
                config.getDouble("collision.min-speed", 3.0), 3.0, "collision.min-speed");
        collisionKnockback = clampNonNegative(
                config.getDouble("collision.knockback", 0.5), 0.5, "collision.knockback");
        collisionDamageCooldown = clampPositiveInt(
                config.getInt("collision.damage-cooldown", 20), 20, "collision.damage-cooldown");
        collisionDeathTrackWindow = clampPositiveInt(
                config.getInt("collision.death-track-window", 100), 100, "collision.death-track-window");
    }

    private void loadHazards() {
        waterEjectDelay = clampPositiveInt(
                config.getInt("hazards.water.eject-delay", 20), 20, "hazards.water.eject-delay");
        lavaInstantDestroy = config.getBoolean("hazards.lava.instant-destroy", true);
    }

    private void loadEffects() {
        exhaustEnabled = config.getBoolean("effects.exhaust.enabled", true);

        String particleName = config.getString("effects.exhaust.type", "CAMPFIRE_SIGNAL_SMOKE");
        if (particleName == null) particleName = "CAMPFIRE_SIGNAL_SMOKE";
        try {
            exhaustType = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            exhaustType = Particle.CAMPFIRE_SIGNAL_SMOKE;
            plugin.getLogger().warning("Invalid particle type: " + particleName);
        }

        exhaustCount = clampPositiveInt(
                config.getInt("effects.exhaust.count", 1), 1, "effects.exhaust.count");
        exhaustOnlyWhenAccelerating = config.getBoolean("effects.exhaust.only-when-accelerating", true);
    }

    private void loadMessages() {
        // Pick the message section matching the configured language.
        String messageSection = "messages_" + language;
        ConfigurationSection section = config.getConfigurationSection(messageSection);

        // Fallback strategy: prefer the *other* shipped locale rather than the same one.
        if (section == null) {
            if (language.equals("en_us")) {
                section = config.getConfigurationSection("messages_zh_cn");
            } else {
                section = config.getConfigurationSection("messages_en_us");
            }
        }

        messages.clear();
        if (section != null) {
            String rawPrefix = section.getString("prefix", DEFAULT_PREFIX);
            messagePrefix = (rawPrefix != null) ? rawPrefix : DEFAULT_PREFIX;
            for (String key : section.getKeys(false)) {
                if (key.equals("prefix")) continue;
                String value = section.getString(key);
                if (value != null) {
                    messages.put(key, value);
                }
            }
        } else {
            messagePrefix = DEFAULT_PREFIX;
        }
    }

    /**
     * Get a formatted message string with § color codes, prefixed.
     */
    public String getMessage(String key) {
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
     * Get a raw message without prefix (for death messages etc.).
     * Always returns a non-null string (falls back to the key name).
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
        if ("en_us".equals(language)) {
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
        return LEGACY.deserialize(legacyText != null ? legacyText : "");
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }

    // ===== Clamp helpers =====

    private double clampPositive(double v, double fallback, String key) {
        if (!Double.isFinite(v) || v <= 0.0) {
            plugin.getLogger().warning(key + " (" + v + ") must be > 0; using default " + fallback + ".");
            return fallback;
        }
        return v;
    }

    private double clampStrictlyPositive(double v, double fallback, String key) {
        return clampPositive(v, fallback, key);
    }

    private double clampNonNegative(double v, double fallback, String key) {
        if (!Double.isFinite(v) || v < 0.0) {
            plugin.getLogger().warning(key + " (" + v + ") must be >= 0; using default " + fallback + ".");
            return fallback;
        }
        return v;
    }

    /** Clamp a friction-style multiplier into (0, 1]. */
    private double clampUnit(double v, double fallback, String key) {
        if (!Double.isFinite(v) || v <= 0.0 || v > 1.0) {
            plugin.getLogger().warning(key + " (" + v + ") must be in (0, 1]; using default " + fallback + ".");
            return fallback;
        }
        return v;
    }

    private int clampPositiveInt(int v, int fallback, String key) {
        if (v <= 0) {
            plugin.getLogger().warning(key + " (" + v + ") must be > 0; using default " + fallback + ".");
            return fallback;
        }
        return v;
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
    /** Returned map is unmodifiable; iteration order matches config.yml order. */
    public Map<Material, Integer> getFuelItems() { return Collections.unmodifiableMap(fuelItems); }
    public int getDefaultBurnTime() { return defaultBurnTime; }

    public boolean isDurabilityEnabled() { return durabilityEnabled; }
    public int getMaxDurability() { return maxDurability; }
    public double getDistancePerDurability() { return distancePerDurability; }

    public boolean isCollisionEnabled() { return collisionEnabled; }
    public double getCollisionDamage() { return collisionDamage; }
    public double getCollisionMaxDamage() { return collisionMaxDamage; }
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
