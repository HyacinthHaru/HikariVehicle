package com.hikaricraft.vehicle.util;

import org.bukkit.Material;

/**
 * Shared rail-type predicate used by placement and tick logic.
 */
public final class RailUtils {

    private RailUtils() {}

    public static boolean isRail(Material material) {
        return material == Material.RAIL
                || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL
                || material == Material.ACTIVATOR_RAIL;
    }
}
