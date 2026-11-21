package com.dunyalar.generators;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.List;
import java.util.Random;

/**
 * Düz dünya. Katmanlar dünyanın en alt yüksekliğinden başlayarak alttan üste yerleştirilir.
 * Varsayılan: 1 bedrock + 4 tema taşı (world: toprak/çimen, nether: netherrack, end: end taşı).
 */
public final class FlatGenerator extends BaseGenerator {

    private final Material[] layers;

    public FlatGenerator(List<Material> layers, Biome biome) {
        super(biome);
        this.layers = layers.toArray(new Material[0]);
    }

    public int height() {
        return layers.length;
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData data = createChunkData(world);
        int min = minHeight(world);
        int max = world.getMaxHeight();
        for (int i = 0; i < layers.length; i++) {
            int y = min + i;
            if (y >= max) {
                break;
            }
            data.setRegion(0, y, 0, 16, y + 1, 16, layers[i]);
        }
        applyBiome(world, biome);
        return data;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, minHeight(world) + layers.length, 0.5);
    }
}
