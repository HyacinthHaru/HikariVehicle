package com.hikaricraft.vehicle.manager;

import com.hikaricraft.vehicle.HikariVehicle;
import com.hikaricraft.vehicle.config.ConfigManager;
import com.hikaricraft.vehicle.model.VehicleData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core vehicle tick logic: movement, steering, fuel, durability,
 * hazards, collision, and visual effects.
 */
public class VehicleManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final HikariVehicle plugin;
    private final ConfigManager config;
    private final Map<UUID, VehicleData> activeVehicles = new ConcurrentHashMap<>();
    /** Tracks collision cooldowns: victim UUID -> last collision tick */
    private final Map<UUID, Long> collisionCooldowns = new ConcurrentHashMap<>();
    /** Tracks collision victims for death detection: victim UUID -> collision record */
    private final Map<UUID, CollisionRecord> collisionVictims = new ConcurrentHashMap<>();
    private BukkitRunnable tickTask;

    /** Record for tracking collision victims for death messages */
    public record CollisionRecord(UUID driverId, String driverName, long tick) {}

    public VehicleManager(HikariVehicle plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Abstracted player input — works for both Java and Bedrock.
     */
    private record VehicleInput(boolean forward, boolean backward,
                                boolean left, boolean right, boolean sneak) {}

    private boolean isBedrockPlayer(Player player) {
        // Floodgate assigns UUID version 0 to Bedrock players
        return player.getUniqueId().version() == 0;
    }

    private VehicleInput readInput(Player player) {
        if (isBedrockPlayer(player)) {
            // Bedrock fallback: auto-forward when not sneaking
            // getCurrentInput() is unreliable via Geyser
            boolean sneak = player.isSneaking();
            return new VehicleInput(!sneak, false, false, false, sneak);
        }
        org.bukkit.Input input = player.getCurrentInput();
        return new VehicleInput(
                input.isForward(), input.isBackward(),
                input.isLeft(), input.isRight(),
                input.isSneak());
    }

    public void start() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickAll();
            }
        };
        tickTask.runTaskTimer(plugin, 0L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (var entry : activeVehicles.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof Minecart minecart) {
                entry.getValue().saveToEntity(minecart);
                minecart.setVelocity(new Vector(0, 0, 0));
                minecart.setMaxSpeed(0.4);
                minecart.setSlowWhenEmpty(true);
                minecart.setDerailedVelocityMod(new Vector(0.5, 0.5, 0.5));
            }
        }
        activeVehicles.clear();
        collisionCooldowns.clear();
        collisionVictims.clear();
    }

    /**
     * Activate driving mode for a minecart.
     * Returns true if successfully entered, false if on rails or already active.
     */
    public boolean enterVehicle(Player player, Minecart minecart) {
        UUID cartId = minecart.getUniqueId();

        if (activeVehicles.containsKey(cartId)) {
            return false;
        }

        // Don't activate on rails — let vanilla/RailTransit handle that
        if (isRail(minecart.getLocation().getBlock().getType())) {
            return false;
        }

        // Configure minecart for ground driving
        minecart.setMaxSpeed(1.0);
        minecart.setSlowWhenEmpty(false);
        minecart.setDerailedVelocityMod(new Vector(1.0, 1.0, 1.0));

        VehicleData data = new VehicleData(cartId, config.getMaxDurability());
        data.loadFromEntity(minecart);
        data.setDriverId(player.getUniqueId());
        data.setHeading(player.getLocation().getYaw());
        data.setDriving(true);

        activeVehicles.put(cartId, data);
        return true;
    }

    /**
     * Deactivate driving mode, save data, reset minecart.
     */
    public void exitVehicle(UUID minecartId) {
        VehicleData data = activeVehicles.remove(minecartId);
        if (data != null) {
            Entity entity = Bukkit.getEntity(minecartId);
            if (entity instanceof Minecart minecart) {
                data.saveToEntity(minecart);
                minecart.setVelocity(new Vector(0, 0, 0));
                minecart.setMaxSpeed(0.4);
                minecart.setSlowWhenEmpty(true);
                minecart.setDerailedVelocityMod(new Vector(0.5, 0.5, 0.5));
            }
            refreshPlayerPosition(data.getDriverId());
        }
    }

    public boolean isActiveVehicle(UUID minecartId) {
        return activeVehicles.containsKey(minecartId);
    }

    public VehicleData getVehicleData(UUID minecartId) {
        return activeVehicles.get(minecartId);
    }

    // ===== Tick Loop =====

    private void tickAll() {
        // 收集需要移除的车辆，避免在迭代中多路径删除
        Set<UUID> toRemove = new HashSet<>();

        for (var entry : activeVehicles.entrySet()) {
            UUID cartId = entry.getKey();
            VehicleData data = entry.getValue();

            // 如果已经被标记移除，跳过
            if (toRemove.contains(cartId)) continue;

            Entity entity = Bukkit.getEntity(cartId);
            if (!(entity instanceof Minecart minecart) || !entity.isValid()) {
                toRemove.add(cartId);
                continue;
            }

            if (minecart.getPassengers().isEmpty() || data.getDriverId() == null) {
                toRemove.add(cartId);
                continue;
            }

            Entity passenger = minecart.getPassengers().get(0);
            if (!(passenger instanceof Player player)
                    || !player.getUniqueId().equals(data.getDriverId())) {
                toRemove.add(cartId);
                continue;
            }

            // tickVehicle 返回 true 表示车辆已被销毁
            if (tickVehicle(minecart, player, data)) {
                toRemove.add(cartId);
            }
        }

        // 批量清理
        for (UUID cartId : toRemove) {
            cleanupVehicle(cartId);
        }

        // 定期清理过期的碰撞冷却记录
        cleanupCollisionCooldowns();
    }

    private void cleanupVehicle(UUID minecartId) {
        VehicleData data = activeVehicles.remove(minecartId);
        if (data != null) {
            Entity entity = Bukkit.getEntity(minecartId);
            if (entity instanceof Minecart minecart) {
                data.saveToEntity(minecart);
                minecart.setVelocity(new Vector(0, 0, 0));
                minecart.setMaxSpeed(0.4);
                minecart.setSlowWhenEmpty(true);
                minecart.setDerailedVelocityMod(new Vector(0.5, 0.5, 0.5));
            }
            refreshPlayerPosition(data.getDriverId());
        }
    }

    private void cleanupCollisionCooldowns() {
        long now = Bukkit.getCurrentTick();
        int cooldownTicks = config.getCollisionDamageCooldown();
        int deathWindow = config.getCollisionDeathTrackWindow();
        collisionCooldowns.entrySet().removeIf(entry ->
                now - entry.getValue() > cooldownTicks * 2L);
        collisionVictims.entrySet().removeIf(entry ->
                now - entry.getValue().tick() > deathWindow);
    }

    /**
     * Check if a player was recently hit by a vehicle and return the collision record.
     */
    public CollisionRecord getCollisionRecord(UUID victimId) {
        return collisionVictims.get(victimId);
    }

    /**
     * Remove collision record after death message is sent.
     */
    public void removeCollisionRecord(UUID victimId) {
        collisionVictims.remove(victimId);
    }

    /**
     * Get the death message template from config.
     */
    public String getDeathMessageTemplate() {
        return config.getRawMessage("death-by-vehicle");
    }

    /**
     * @return true if the vehicle was destroyed and should be removed
     */
    private boolean tickVehicle(Minecart minecart, Player player, VehicleData data) {
        // 0. Rail check - exit vehicle mode if on rails (防呆设计)
        if (isRail(minecart.getLocation().getBlock().getType())) {
            minecart.removePassenger(player);
            return true;
        }

        // 1. Read player input (Bedrock-compatible)
        VehicleInput input = readInput(player);

        // 2. Exit: sneak while stopped and not in water
        if (input.sneak() && data.getSpeed() < config.getMinSpeed() && data.getWaterTicks() == 0) {
            minecart.removePassenger(player);
            return true;  // 正常退出，标记移除
        }

        // 3. Hazards (lava / water) - 返回 true 表示车辆已销毁
        if (checkHazards(minecart, player, data)) {
            return true;
        }

        // 4. Fuel
        boolean hasFuel = true;
        if (config.isFuelEnabled()) {
            if (data.getFuelTicks() <= 0) {
                hasFuel = tryConsumeFuel(player, data);
                if (!hasFuel && input.forward() && minecart.getTicksLived() % 40 == 0) {
                    player.sendActionBar(LEGACY.deserialize(config.getMessage("no-fuel")));
                }
            } else {
                data.setFuelTicks(data.getFuelTicks() - 1);
            }
        }

        // 5. Speed
        double speed = data.getSpeed();
        double accelPerTick = config.getAcceleration() / 20.0;

        if (input.forward() && hasFuel) {
            speed = Math.min(speed + accelPerTick, config.getMaxSpeed());
        } else if (input.backward() || input.sneak()) {
            speed *= config.getBrakeFriction();
        } else {
            speed *= config.getCoastFriction();
        }

        if (speed < config.getMinSpeed()) {
            speed = 0;
        }
        data.setSpeed(speed);

        // 6. Heading (yaw-following + A/D key assist)
        if (speed > 0) {
            double playerYaw = player.getLocation().getYaw();
            double heading = data.getHeading();

            // Yaw-following (speed-dependent)
            double yawRate = config.getMaxTurnRate() / (1.0 + speed * config.getTurnDamping());
            double diff = normalizeAngle(playerYaw - heading);
            if (Math.abs(diff) > yawRate) {
                diff = Math.signum(diff) * yawRate;
            }

            // A/D key turning boost (less speed-dependent for quick maneuvers)
            double keyRate = config.getKeyTurnRate() / (1.0 + speed * 0.1);
            if (input.left()) diff -= keyRate;
            if (input.right()) diff += keyRate;

            data.setHeading(normalizeAngle(heading + diff));
        }

        // 7. Apply velocity
        if (speed > 0) {
            double rad = Math.toRadians(data.getHeading());
            double vx = -Math.sin(rad) * speed / 20.0;
            double vz = Math.cos(rad) * speed / 20.0;
            double vy = minecart.getVelocity().getY();

            // Step-up check
            Location nextPos = minecart.getLocation().clone().add(vx, 0, vz);
            vy = handleStepUp(minecart, nextPos, vy, data);

            minecart.setVelocity(new Vector(vx, vy, vz));
            minecart.setRotation((float) data.getHeading(), 0f);
        } else {
            Vector v = minecart.getVelocity();
            minecart.setVelocity(new Vector(0, v.getY(), 0));
        }

        // 8. Collision
        if (config.isCollisionEnabled() && speed >= config.getCollisionMinSpeed()) {
            handleCollision(minecart, player);
        }

        // 9. Durability - 返回 true 表示车辆已销毁
        if (config.isDurabilityEnabled() && speed > 0) {
            if (handleDurability(minecart, player, data)) {
                return true;
            }
        }

        // 10. Exhaust particles
        if (config.isExhaustEnabled() && speed > 0) {
            if (!config.isExhaustOnlyWhenAccelerating() || input.forward()) {
                spawnExhaust(minecart, data);
            }
        }

        return false;
    }

    // ===== Hazards =====

    /**
     * @return true if the vehicle was destroyed and should be removed
     */
    private boolean checkHazards(Minecart minecart, Player player, VehicleData data) {
        Material type = minecart.getLocation().getBlock().getType();

        // Lava: instant destroy
        if (type == Material.LAVA && config.isLavaInstantDestroy()) {
            player.sendMessage(LEGACY.deserialize(config.getMessage("lava-destroy")));
            minecart.removePassenger(player);
            minecart.remove();
            refreshPlayerPosition(player.getUniqueId());
            return true;
        }

        // Water: sink, then eject after delay
        if (type == Material.WATER) {
            data.setWaterTicks(data.getWaterTicks() + 1);
            data.setSpeed(0);
            minecart.setVelocity(new Vector(0, -0.3, 0));

            if (data.getWaterTicks() >= config.getWaterEjectDelay()) {
                player.sendMessage(LEGACY.deserialize(config.getMessage("water-eject")));
                Location loc = minecart.getLocation();
                minecart.removePassenger(player);
                minecart.remove();
                loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.MINECART));
                refreshPlayerPosition(player.getUniqueId());
                return true;
            }
            return true; // skip driving while in water, but not destroyed yet
        }

        data.setWaterTicks(0);
        return false;
    }

    // ===== Fuel =====

    private boolean tryConsumeFuel(Player player, VehicleData data) {
        var inventory = player.getInventory();

        // Check configured fuel items first
        for (var entry : config.getFuelItems().entrySet()) {
            int slot = inventory.first(entry.getKey());
            if (slot >= 0) {
                ItemStack stack = inventory.getItem(slot);
                String name = stack.getType().name();
                stack.setAmount(stack.getAmount() - 1);
                data.setFuelTicks(entry.getValue() * 20);
                player.sendActionBar(LEGACY.deserialize(
                        config.getMessage("fuel-consumed").replace("{item}", name)));
                return true;
            }
        }

        // Fallback: any burnable item not in the configured list
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType().isFuel()
                    && !config.getFuelItems().containsKey(item.getType())) {
                String name = item.getType().name();
                item.setAmount(item.getAmount() - 1);
                data.setFuelTicks(config.getDefaultBurnTime() * 20);
                player.sendActionBar(LEGACY.deserialize(
                        config.getMessage("fuel-consumed").replace("{item}", name)));
                return true;
            }
        }

        return false;
    }

    // ===== Step-Up =====

    private double handleStepUp(Minecart minecart, Location nextPos, double currentVy,
                                VehicleData data) {
        Location checkLoc = nextPos.clone();
        checkLoc.setY(minecart.getLocation().getY());
        Block block = checkLoc.getBlock();

        if (block.getType().isSolid()) {
            BoundingBox bb = block.getBoundingBox();
            double heightDiff = bb.getMaxY() - minecart.getLocation().getY();

            if (heightDiff > 0 && heightDiff <= config.getStepHeight()) {
                // Climbable — boost upward
                return Math.max(currentVy, heightDiff * 0.6 + 0.05);
            } else if (heightDiff > config.getStepHeight()) {
                // Wall — stop
                data.setSpeed(0);
                return currentVy;
            }
        }

        return currentVy;
    }

    // ===== Collision =====

    private void handleCollision(Minecart minecart, Player driver) {
        long now = Bukkit.getCurrentTick();

        for (Entity entity : minecart.getNearbyEntities(1.0, 1.0, 1.0)) {
            if (entity.equals(driver) || entity.equals(minecart)) continue;
            if (!(entity instanceof LivingEntity living)) continue;

            // 检查碰撞冷却
            Long lastCollision = collisionCooldowns.get(entity.getUniqueId());
            if (lastCollision != null && now - lastCollision < config.getCollisionDamageCooldown()) {
                continue;
            }

            // 记录碰撞时间并造成伤害
            collisionCooldowns.put(entity.getUniqueId(), now);
            living.damage(config.getCollisionDamage(), minecart);

            // 记录碰撞受害者用于死亡检测
            collisionVictims.put(entity.getUniqueId(),
                    new CollisionRecord(driver.getUniqueId(), driver.getName(), now));

            Vector knockback = entity.getLocation().toVector()
                    .subtract(minecart.getLocation().toVector())
                    .normalize()
                    .multiply(config.getCollisionKnockback());
            knockback.setY(0.2);
            entity.setVelocity(knockback);
        }
    }

    // ===== Durability =====

    /** @return true if the vehicle was destroyed */
    private boolean handleDurability(Minecart minecart, Player player, VehicleData data) {
        double dist = data.getDistanceSinceLastDurability() + data.getSpeed() / 20.0;

        if (dist >= config.getDistancePerDurability()) {
            int points = (int) (dist / config.getDistancePerDurability());
            dist -= points * config.getDistancePerDurability();
            data.setDurability(data.getDurability() - points);

            if (data.getDurability() <= 0) {
                player.sendMessage(LEGACY.deserialize(config.getMessage("vehicle-destroyed")));
                minecart.removePassenger(player);
                minecart.remove();
                refreshPlayerPosition(player.getUniqueId());
                return true;
            }

            if (data.getDurability() <= data.getMaxDurability() * 0.1
                    && minecart.getTicksLived() % 40 == 0) {
                String msg = config.getMessage("durability-low")
                        .replace("{durability}", String.valueOf(data.getDurability()))
                        .replace("{max}", String.valueOf(data.getMaxDurability()));
                player.sendActionBar(LEGACY.deserialize(msg));
            }
        }

        data.setDistanceSinceLastDurability(dist);
        return false;
    }

    // ===== Effects =====

    private void spawnExhaust(Minecart minecart, VehicleData data) {
        double rad = Math.toRadians(data.getHeading());
        double offsetX = Math.sin(rad) * 0.5;   // behind the vehicle
        double offsetZ = -Math.cos(rad) * 0.5;
        Location loc = minecart.getLocation();

        loc.getWorld().spawnParticle(
                config.getExhaustType(),
                loc.getX() + offsetX,
                loc.getY() + 0.3,
                loc.getZ() + offsetZ,
                config.getExhaustCount(),
                0.05, 0.05, 0.05,
                0.01
        );
    }

    // ===== Utilities =====

    private double normalizeAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle <= -180) angle += 360;
        return angle;
    }

    private boolean isRail(Material material) {
        return material == Material.RAIL
                || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL
                || material == Material.ACTIVATOR_RAIL;
    }

    /**
     * Schedule a 1-tick delayed self-teleport for the driver.
     * Forces the server to re-validate the player's position,
     * preventing "moved wrongly" rubber-banding after dismount.
     */
    private void refreshPlayerPosition(UUID driverId) {
        if (driverId == null) return;
        Player driver = Bukkit.getPlayer(driverId);
        if (driver != null && driver.isOnline()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (driver.isOnline()) {
                    driver.teleport(driver.getLocation());
                }
            }, 1L);
        }
    }
}
