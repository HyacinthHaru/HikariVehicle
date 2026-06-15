package com.hikaricraft.vehicle.manager;

import com.hikaricraft.vehicle.HikariVehicle;
import com.hikaricraft.vehicle.config.ConfigManager;
import com.hikaricraft.vehicle.model.VehicleData;
import com.hikaricraft.vehicle.util.RailUtils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Core vehicle tick logic: movement, steering, fuel, durability,
 * hazards, collision, and visual effects.
 */
public class VehicleManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    // ===== Physics / timing constants =====
    private static final int TICKS_PER_SECOND = 20;
    /** Action-bar throttle for fuel/durability warnings. */
    private static final int ACTION_BAR_INTERVAL_TICKS = 40;
    /** Periodic PDC autosave for active vehicles (defends against crashes). */
    private static final int AUTOSAVE_INTERVAL_TICKS = 100;
    /** Durability low-warning threshold (fraction of max). */
    private static final double DURABILITY_LOW_RATIO = 0.1;
    /** Vertical step-up curve. */
    private static final double STEP_UP_VERTICAL_MULTIPLIER = 0.6;
    private static final double STEP_UP_GROUND_BOOST = 0.05;
    /** Sink speed (blocks/tick) while submerged. */
    private static final double WATER_SINK_VELOCITY_Y = -0.3;
    /** Vertical impulse applied to entities hit by the cart. */
    private static final double COLLISION_KNOCKBACK_Y = 0.2;
    /** Exhaust particle placement relative to the cart. */
    private static final double EXHAUST_BEHIND_DISTANCE = 0.5;
    private static final double EXHAUST_Y_OFFSET = 0.3;
    private static final double EXHAUST_PARTICLE_SPREAD = 0.05;
    private static final double EXHAUST_PARTICLE_SPEED = 0.01;
    /** Speed damping applied to A/D key-turn rate. */
    private static final double KEY_TURN_SPEED_DAMPING = 0.1;
    /** Minecart entity tweaks while in drive mode. */
    private static final double DRIVE_MAX_SPEED = 1.0;
    private static final double DRIVE_DERAILED_VELOCITY_MOD = 1.0;
    /** Vanilla defaults restored on exit. */
    private static final double VANILLA_MAX_SPEED = 0.4;
    private static final double VANILLA_DERAILED_VELOCITY_MOD = 0.5;
    /** Collision search box half-extents. */
    private static final double COLLISION_CHECK_RADIUS = 1.0;
    /** Squared distance below which a knockback direction is considered degenerate. */
    private static final double KNOCKBACK_MIN_DISTANCE_SQ = 1.0e-4;

    private final HikariVehicle plugin;
    private final ConfigManager config;
    private final Map<UUID, VehicleData> activeVehicles = new ConcurrentHashMap<>();
    /** Tracks collision cooldowns: victim UUID -> last collision tick. */
    private final Map<UUID, Long> collisionCooldowns = new ConcurrentHashMap<>();
    /** Tracks collision victims for death detection: victim UUID -> collision record. */
    private final Map<UUID, CollisionRecord> collisionVictims = new ConcurrentHashMap<>();
    private BukkitRunnable tickTask;

    /** Record for tracking collision victims for death messages. */
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

    private enum HazardResult {
        NONE,
        SKIP_TICK,
        REMOVE
    }

    private boolean isBedrockPlayer(Player player) {
        // Floodgate assigns UUID version 0 to Bedrock players.
        return player.getUniqueId().version() == 0;
    }

    private VehicleInput readInput(Player player) {
        if (isBedrockPlayer(player)) {
            // Bedrock fallback: auto-forward when not sneaking
            // (getCurrentInput() is unreliable via Geyser).
            boolean sneak = player.isSneaking();
            return new VehicleInput(!sneak, false, false, false, sneak);
        }
        org.bukkit.Input input = player.getCurrentInput();
        if (input == null) {
            // Input packets have not arrived yet (join / respawn / dimension change).
            // Treat as idle to avoid NPE on isForward()/isSneak().
            return new VehicleInput(false, false, false, false, player.isSneaking());
        }
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
            tickTask = null;
        }
        for (var entry : activeVehicles.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof Minecart minecart) {
                entry.getValue().saveToEntity(minecart);
                resetMinecartState(minecart);
            }
        }
        activeVehicles.clear();
        collisionCooldowns.clear();
        collisionVictims.clear();
    }

    /**
     * Persist the in-memory state of every active vehicle into its PDC,
     * without touching the active set. Called before /hv reload so
     * configuration reloads cannot lose driver progress.
     */
    public void saveActiveVehicles() {
        for (var entry : activeVehicles.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof Minecart minecart) {
                entry.getValue().saveToEntity(minecart);
            }
        }
    }

    /**
     * Activate driving mode for a minecart.
     * @return true if successfully entered, false if on rails or already active.
     */
    public boolean enterVehicle(Player player, Minecart minecart) {
        UUID minecartId = minecart.getUniqueId();

        if (activeVehicles.containsKey(minecartId)) {
            return false;
        }

        // Don't activate on rails — let vanilla/RailTransit handle that.
        if (RailUtils.isRail(minecart.getLocation().getBlock().getType())) {
            return false;
        }

        // Configure minecart for ground driving.
        minecart.setMaxSpeed(DRIVE_MAX_SPEED);
        minecart.setSlowWhenEmpty(false);
        minecart.setDerailedVelocityMod(new Vector(
                DRIVE_DERAILED_VELOCITY_MOD, DRIVE_DERAILED_VELOCITY_MOD, DRIVE_DERAILED_VELOCITY_MOD));

        VehicleData data = new VehicleData(config.getMaxDurability());
        data.loadFromEntity(minecart);
        data.setDriverId(player.getUniqueId());
        // Seed heading from the minecart's current yaw so the first tick does not
        // snap the cart toward the player's look direction.
        data.setHeading(minecart.getLocation().getYaw());

        activeVehicles.put(minecartId, data);
        return true;
    }

    /**
     * Deactivate driving mode, save data, reset minecart.
     */
    public void exitVehicle(UUID minecartId) {
        cleanupVehicle(minecartId);
    }

    public boolean isActiveVehicle(UUID minecartId) {
        return activeVehicles.containsKey(minecartId);
    }

    /**
     * Only block manual dismounts from the active driver while the vehicle is still moving
     * or stuck in water. All other exits should be allowed so the minecart can recover.
     */
    public boolean shouldCancelExit(UUID minecartId, Entity exited) {
        VehicleData data = activeVehicles.get(minecartId);
        if (data == null) {
            return false;
        }
        if (!(exited instanceof Player player)) {
            return false;
        }
        if (!player.getUniqueId().equals(data.getDriverId())) {
            return false;
        }
        if (!player.isOnline() || player.isDead()) {
            return false;
        }
        return data.getSpeed() >= config.getMinSpeed() || data.getWaterTicks() > 0;
    }

    // ===== Tick loop =====

    private void tickAll() {
        // Snapshot the active set so that synchronous event callbacks
        // (VehicleExitEvent, etc.) cannot mutate the iterator mid-flight.
        List<Map.Entry<UUID, VehicleData>> snapshot = new ArrayList<>(activeVehicles.entrySet());
        Set<UUID> toRemove = new HashSet<>();

        for (var entry : snapshot) {
            UUID minecartId = entry.getKey();
            VehicleData data = entry.getValue();

            // Re-check membership: an entry may already have been evicted via an event callback.
            if (!activeVehicles.containsKey(minecartId) || toRemove.contains(minecartId)) {
                continue;
            }

            try {
                Entity entity = Bukkit.getEntity(minecartId);
                if (!(entity instanceof Minecart minecart) || !entity.isValid()) {
                    toRemove.add(minecartId);
                    continue;
                }

                if (minecart.getPassengers().isEmpty() || data.getDriverId() == null) {
                    toRemove.add(minecartId);
                    continue;
                }

                Entity passenger = minecart.getPassengers().get(0);
                if (!passenger.isValid()) {
                    toRemove.add(minecartId);
                    continue;
                }
                if (!(passenger instanceof Player player)
                        || !player.isOnline()
                        || player.isDead()
                        || !player.getUniqueId().equals(data.getDriverId())) {
                    toRemove.add(minecartId);
                    continue;
                }

                // tickVehicle returns true when the cart has been retired.
                if (tickVehicle(minecart, player, data)) {
                    toRemove.add(minecartId);
                }
            } catch (RuntimeException e) {
                // Never let a single vehicle's failure break the global tick task.
                plugin.getLogger().log(Level.SEVERE,
                        "Error ticking vehicle " + minecartId + "; evicting.", e);
                toRemove.add(minecartId);
            }
        }

        for (UUID minecartId : toRemove) {
            cleanupVehicle(minecartId);
        }

        cleanupCollisionCooldowns();
    }

    private void cleanupVehicle(UUID minecartId) {
        VehicleData data = activeVehicles.remove(minecartId);
        if (data == null) return;
        Entity entity = Bukkit.getEntity(minecartId);
        if (entity instanceof Minecart minecart) {
            data.saveToEntity(minecart);
            resetMinecartState(minecart);
        }
        refreshPlayerPosition(data.getDriverId());
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
        // 0. Rail check — let vanilla / rail-transit handle carts on rails.
        if (RailUtils.isRail(minecart.getLocation().getBlock().getType())) {
            minecart.removePassenger(player);
            return true;
        }

        // 1. Read player input (Bedrock-compatible).
        VehicleInput input = readInput(player);

        // 2. Exit: sneak while stopped and not in water.
        if (input.sneak() && data.getSpeed() < config.getMinSpeed() && data.getWaterTicks() == 0) {
            minecart.removePassenger(player);
            return true;
        }

        // 3. Hazards (lava / water) — may destroy the cart or pause this tick.
        HazardResult hazardResult = checkHazards(minecart, player, data);
        if (hazardResult == HazardResult.REMOVE) {
            return true;
        }
        if (hazardResult == HazardResult.SKIP_TICK) {
            return false;
        }

        // 4. Fuel.
        boolean hasFuel = true;
        if (config.isFuelEnabled()) {
            if (data.getFuelTicks() <= 0) {
                hasFuel = tryConsumeFuel(player, data);
                if (!hasFuel && input.forward()
                        && minecart.getTicksLived() % ACTION_BAR_INTERVAL_TICKS == 0) {
                    player.sendActionBar(LEGACY.deserialize(config.getMessage("no-fuel")));
                }
            } else {
                data.setFuelTicks(data.getFuelTicks() - 1);
            }
        }

        // 5. Speed.
        double speed = data.getSpeed();
        double accelPerTick = config.getAcceleration() / TICKS_PER_SECOND;

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

        // 6. Heading (yaw-following + A/D key assist).
        if (speed > 0) {
            double playerYaw = player.getLocation().getYaw();
            double heading = data.getHeading();

            // Yaw-following (speed-dependent).
            double yawRate = config.getMaxTurnRate() / (1.0 + speed * config.getTurnDamping());
            double diff = normalizeAngle(playerYaw - heading);
            if (Math.abs(diff) > yawRate) {
                diff = Math.signum(diff) * yawRate;
            }

            // A/D key turning boost (less speed-dependent for quick maneuvers).
            double keyRate = config.getKeyTurnRate() / (1.0 + speed * KEY_TURN_SPEED_DAMPING);
            if (input.left()) diff -= keyRate;
            if (input.right()) diff += keyRate;

            data.setHeading(normalizeAngle(heading + diff));
        }

        // 7. Apply velocity.
        if (speed > 0) {
            double rad = Math.toRadians(data.getHeading());
            double vx = -Math.sin(rad) * speed / TICKS_PER_SECOND;
            double vz = Math.cos(rad) * speed / TICKS_PER_SECOND;
            double vy = minecart.getVelocity().getY();

            // Step-up check.
            Location nextPos = minecart.getLocation().clone().add(vx, 0, vz);
            vy = handleStepUp(minecart, nextPos, vy, data);

            minecart.setVelocity(new Vector(vx, vy, vz));
            minecart.setRotation((float) data.getHeading(), 0f);
        } else {
            Vector v = minecart.getVelocity();
            minecart.setVelocity(new Vector(0, v.getY(), 0));
        }

        // 8. Collision.
        if (config.isCollisionEnabled() && speed >= config.getCollisionMinSpeed()) {
            handleCollision(minecart, player);
        }

        // 9. Durability — true means the vehicle was destroyed.
        if (config.isDurabilityEnabled() && speed > 0) {
            if (handleDurability(minecart, player, data)) {
                return true;
            }
        }

        // 10. Exhaust particles.
        if (config.isExhaustEnabled() && speed > 0) {
            if (!config.isExhaustOnlyWhenAccelerating() || input.forward()) {
                spawnExhaust(minecart, data);
            }
        }

        // 11. Periodic PDC autosave — bounds the data loss window if the
        // server crashes or the chunk unloads with us still active.
        if (minecart.getTicksLived() % AUTOSAVE_INTERVAL_TICKS == 0) {
            data.saveToEntity(minecart);
        }

        return false;
    }

    // ===== Hazards =====

    private HazardResult checkHazards(Minecart minecart, Player player, VehicleData data) {
        Material type = minecart.getLocation().getBlock().getType();

        // Lava: instant destroy.
        if (type == Material.LAVA && config.isLavaInstantDestroy()) {
            player.sendMessage(LEGACY.deserialize(config.getMessage("lava-destroy")));
            destroyCart(minecart, player, false, null);
            return HazardResult.REMOVE;
        }

        // Water: sink, then eject after delay.
        if (type == Material.WATER) {
            data.setWaterTicks(data.getWaterTicks() + 1);
            data.setSpeed(0);
            minecart.setVelocity(new Vector(0, WATER_SINK_VELOCITY_Y, 0));

            if (data.getWaterTicks() >= config.getWaterEjectDelay()) {
                player.sendMessage(LEGACY.deserialize(config.getMessage("water-eject")));
                Location dropAt = minecart.getLocation().clone();
                destroyCart(minecart, player, true, dropAt);
                return HazardResult.REMOVE;
            }
            return HazardResult.SKIP_TICK;
        }

        data.setWaterTicks(0);
        return HazardResult.NONE;
    }

    /**
     * Common destruction path for hazards / durability-zero.
     * Evicts the cart from the active set first so the synchronous
     * VehicleExitEvent triggered by removePassenger does not re-enter the cleanup path.
     */
    private void destroyCart(Minecart minecart, Player driver, boolean dropMinecartItem, Location dropAt) {
        UUID minecartId = minecart.getUniqueId();
        UUID driverId = driver.getUniqueId();
        activeVehicles.remove(minecartId);

        minecart.removePassenger(driver);
        minecart.remove();

        if (dropMinecartItem && dropAt != null && dropAt.getWorld() != null) {
            dropAt.getWorld().dropItemNaturally(dropAt, new ItemStack(Material.MINECART));
        }
        refreshPlayerPosition(driverId);
    }

    // ===== Fuel =====

    private boolean tryConsumeFuel(Player player, VehicleData data) {
        PlayerInventory inventory = player.getInventory();

        // Check configured fuel items first.
        for (var entry : config.getFuelItems().entrySet()) {
            int slot = inventory.first(entry.getKey());
            if (slot >= 0) {
                ItemStack stack = inventory.getItem(slot);
                if (stack == null) continue;
                // Promote to long arithmetic to avoid overflow on absurdly large config values.
                int burnTicks = (int) Math.min(
                        Integer.MAX_VALUE,
                        (long) entry.getValue() * TICKS_PER_SECOND);
                consumeFuel(player, slot, stack, burnTicks, data);
                return true;
            }
        }

        // Fallback: any burnable item not in the configured list.
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType().isFuel()
                    && !config.getFuelItems().containsKey(item.getType())) {
                int burnTicks = (int) Math.min(
                        Integer.MAX_VALUE,
                        (long) config.getDefaultBurnTime() * TICKS_PER_SECOND);
                consumeFuel(player, i, item, burnTicks, data);
                return true;
            }
        }

        return false;
    }

    private void consumeFuel(Player player, int slot, ItemStack stack, int fuelTicks, VehicleData data) {
        String name = stack.getType().name();
        if (stack.getType() == Material.LAVA_BUCKET) {
            consumeLavaBucket(player, slot, stack);
        } else {
            decrementStack(player, slot, stack);
        }

        data.setFuelTicks(fuelTicks);
        player.sendActionBar(LEGACY.deserialize(
                config.getMessage("fuel-consumed").replace("{item}", name)));
    }

    private void consumeLavaBucket(Player player, int slot, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        if (stack.getAmount() <= 1) {
            inventory.setItem(slot, new ItemStack(Material.BUCKET));
            return;
        }

        stack.setAmount(stack.getAmount() - 1);
        Map<Integer, ItemStack> leftovers = inventory.addItem(new ItemStack(Material.BUCKET));
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void decrementStack(Player player, int slot, ItemStack stack) {
        if (stack.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
    }

    // ===== Step-up =====

    private double handleStepUp(Minecart minecart, Location nextPos, double currentVy,
                                VehicleData data) {
        Location checkLoc = nextPos.clone();
        checkLoc.setY(minecart.getLocation().getY());
        Block block = checkLoc.getBlock();

        if (block.getType().isSolid()) {
            BoundingBox bb = block.getBoundingBox();
            double heightDiff = bb.getMaxY() - minecart.getLocation().getY();

            if (heightDiff > 0 && heightDiff <= config.getStepHeight()) {
                // Climbable — boost upward.
                return Math.max(currentVy, heightDiff * STEP_UP_VERTICAL_MULTIPLIER + STEP_UP_GROUND_BOOST);
            } else if (heightDiff > config.getStepHeight()) {
                // Wall — stop.
                data.setSpeed(0);
                return currentVy;
            }
        }

        return currentVy;
    }

    // ===== Collision =====

    private void handleCollision(Minecart minecart, Player driver) {
        long now = Bukkit.getCurrentTick();

        for (Entity entity : minecart.getNearbyEntities(
                COLLISION_CHECK_RADIUS, COLLISION_CHECK_RADIUS, COLLISION_CHECK_RADIUS)) {
            if (entity.equals(driver) || entity.equals(minecart)) continue;
            if (!(entity instanceof LivingEntity living)) continue;

            // Per-victim damage cooldown.
            Long lastCollision = collisionCooldowns.get(entity.getUniqueId());
            if (lastCollision != null && now - lastCollision < config.getCollisionDamageCooldown()) {
                continue;
            }

            collisionCooldowns.put(entity.getUniqueId(), now);
            living.damage(config.getCollisionDamage(), minecart);

            // Record victim for death-message attribution.
            collisionVictims.put(entity.getUniqueId(),
                    new CollisionRecord(driver.getUniqueId(), driver.getName(), now));

            // Compute horizontal knockback, defending against coincident positions
            // (zero-length vector -> normalize() returns NaN, which would corrupt entity state).
            Vector delta = entity.getLocation().toVector().subtract(minecart.getLocation().toVector());
            Vector knockback;
            if (delta.lengthSquared() > KNOCKBACK_MIN_DISTANCE_SQ) {
                knockback = delta.normalize().multiply(config.getCollisionKnockback());
            } else {
                knockback = new Vector(0, 0, 0);
            }
            knockback.setY(COLLISION_KNOCKBACK_Y);
            entity.setVelocity(knockback);
        }
    }

    // ===== Durability =====

    /** @return true if the vehicle was destroyed */
    private boolean handleDurability(Minecart minecart, Player player, VehicleData data) {
        double dist = data.getDistanceSinceLastDurability() + data.getSpeed() / TICKS_PER_SECOND;

        if (dist >= config.getDistancePerDurability()) {
            int points = (int) (dist / config.getDistancePerDurability());
            dist -= points * config.getDistancePerDurability();
            data.setDurability(data.getDurability() - points);

            if (data.getDurability() <= 0) {
                player.sendMessage(LEGACY.deserialize(config.getMessage("vehicle-destroyed")));
                destroyCart(minecart, player, false, null);
                return true;
            }

            if (data.getDurability() <= data.getMaxDurability() * DURABILITY_LOW_RATIO
                    && minecart.getTicksLived() % ACTION_BAR_INTERVAL_TICKS == 0) {
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
        double offsetX = Math.sin(rad) * EXHAUST_BEHIND_DISTANCE;   // behind the vehicle
        double offsetZ = -Math.cos(rad) * EXHAUST_BEHIND_DISTANCE;
        Location loc = minecart.getLocation();
        if (loc.getWorld() == null) return;

        loc.getWorld().spawnParticle(
                config.getExhaustType(),
                loc.getX() + offsetX,
                loc.getY() + EXHAUST_Y_OFFSET,
                loc.getZ() + offsetZ,
                config.getExhaustCount(),
                EXHAUST_PARTICLE_SPREAD, EXHAUST_PARTICLE_SPREAD, EXHAUST_PARTICLE_SPREAD,
                EXHAUST_PARTICLE_SPEED
        );
    }

    // ===== Utilities =====

    /**
     * Wrap an angle into (-180, 180]. Guards against NaN / infinity so that
     * a single bad input cannot lock the tick thread in an infinite loop.
     */
    private double normalizeAngle(double angle) {
        if (!Double.isFinite(angle)) return 0.0;
        angle = angle % 360.0;
        if (angle > 180.0) angle -= 360.0;
        else if (angle <= -180.0) angle += 360.0;
        return angle;
    }

    /**
     * Force-clear any leftover passengers before resetting vanilla minecart behavior.
     * This prevents fake-player or cancelled-exit residue from making the cart unridable.
     */
    private void resetMinecartState(Minecart minecart) {
        for (Entity passenger : List.copyOf(minecart.getPassengers())) {
            minecart.removePassenger(passenger);
        }
        minecart.eject();
        minecart.setVelocity(new Vector(0, 0, 0));
        minecart.setMaxSpeed(VANILLA_MAX_SPEED);
        minecart.setSlowWhenEmpty(true);
        minecart.setDerailedVelocityMod(new Vector(
                VANILLA_DERAILED_VELOCITY_MOD, VANILLA_DERAILED_VELOCITY_MOD, VANILLA_DERAILED_VELOCITY_MOD));
    }

    /**
     * Schedule a 1-tick delayed self-teleport for the driver.
     * Forces the server to re-validate the player's position,
     * preventing "moved wrongly" rubber-banding after dismount.
     */
    private void refreshPlayerPosition(UUID driverId) {
        if (driverId == null) return;
        if (!plugin.isEnabled()) return;
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
