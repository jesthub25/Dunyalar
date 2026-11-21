package com.dunyalar.generators;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.Random;

/** Tamamen boş dünya. Merkez platformu dünya oluşturulurken yerleştirilir. */
public final class VoidGenerator extends BaseGenerator {

    private final int spawnY;

    public VoidGenerator(Biome biome, int platformY) {
        super(biome);
        this.spawnY = platformY + 1;
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        applyBiome(world, biome);
        return createChunkData(world);
    }

    /** Sunucunun boş dünyada güvenli spawn araması yapıp donmasını önler. */
    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, spawnY, 0.5);
    }
}
