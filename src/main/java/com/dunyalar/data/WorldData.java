package com.dunyalar.data;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Eklentinin oluşturduğu bir dünyanın kalıcı bilgileri (worlds.yml).
 * Sınır: merkez (0,0); X ekseninde [-radiusX, radiusX], Z ekseninde [-radiusZ, radiusZ].
 */
public final class WorldData {

    private final String name;
    private final WorldTheme theme;
    private final WorldKind kind;
    private final int radiusX;
    private final int radiusZ;
    private final long seed;
    private final long createdAt;
    private final boolean structures;
    private final List<String> layers;
    private final String biome;
    private final int platformY;
    private final boolean dragon;
    private SpawnPoint spawn;

    public WorldData(String name, WorldTheme theme, WorldKind kind, int radiusX, int radiusZ, long seed,
                     long createdAt, boolean structures, List<String> layers, String biome, int platformY,
                     boolean dragon, SpawnPoint spawn) {
        this.name = name;
        this.theme = theme;
        this.kind = kind;
        this.radiusX = Math.max(1, radiusX);
        this.radiusZ = Math.max(1, radiusZ);
        this.seed = seed;
        this.createdAt = createdAt;
        this.structures = structures;
        this.layers = layers == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(layers));
        this.biome = biome == null ? "" : biome;
        this.platformY = platformY;
        this.dragon = dragon;
        this.spawn = spawn;
    }

    public String name() {
        return name;
    }

    public WorldTheme theme() {
        return theme;
    }

    public WorldKind kind() {
        return kind;
    }

    public int radiusX() {
        return radiusX;
    }

    public int radiusZ() {
        return radiusZ;
    }

    public long seed() {
        return seed;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean structures() {
        return structures;
    }

    public List<String> layers() {
        return layers;
    }

    public String biome() {
        return biome;
    }

    public int platformY() {
        return platformY;
    }

    /** End dünyasında ejderha savaşı açık mı? */
    public boolean dragon() {
        return dragon;
    }

    public SpawnPoint spawn() {
        return spawn;
    }

    public void setSpawn(SpawnPoint spawn) {
        this.spawn = spawn;
    }

    /** Alan gösterimi, ör. 2000x2000 */
    public String areaText() {
        return (radiusX * 2) + "x" + (radiusZ * 2);
    }

    public boolean isInside(double x, double z) {
        return x >= -radiusX && x <= radiusX && z >= -radiusZ && z <= radiusZ;
    }

    public boolean isInside(Location loc) {
        return loc != null && isInside(loc.getX(), loc.getZ());
    }

    /** Blok koordinatının tamamen sınır içinde olup olmadığı. */
    public boolean isBlockInside(int bx, int bz) {
        return bx >= -radiusX && bx <= radiusX - 1 && bz >= -radiusZ && bz <= radiusZ - 1;
    }

    public Location spawnLocation(World world) {
        return spawn == null ? null : spawn.toLocation(world);
    }
}
