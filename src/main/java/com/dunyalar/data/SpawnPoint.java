package com.dunyalar.data;

import org.bukkit.Location;
import org.bukkit.World;

/** Bir dünyaya ait kalıcı spawn noktası. */
public final class SpawnPoint {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public SpawnPoint(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static SpawnPoint of(Location loc) {
        return new SpawnPoint(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }
}
