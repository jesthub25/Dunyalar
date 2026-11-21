package com.dunyalar.generators;

import com.dunyalar.util.Compat;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

/**
 * Void ve flat üreticilerin ortak tabanı.
 *
 * Eski (1.16.5) ChunkGenerator API'si kullanılır; bu API 1.21.x'te de çalışmaya devam eder.
 * Yeni sürümlerdeki shouldGenerate*() metotları @Override olmadan tanımlanmıştır:
 * 1.16.5'te sıradan metot olarak kalır, 1.17+ sürümlerde otomatik olarak üst sınıfı ezer.
 * Böylece vanilla arazi, mağara, yapı, süsleme ve mob üretimi her sürümde kapalı kalır.
 */
public abstract class BaseGenerator extends ChunkGenerator {

    private final Biome biome;
    private volatile boolean biomeFailed;
    private volatile int cachedMin = Integer.MIN_VALUE;

    protected BaseGenerator(Biome biome) {
        this.biome = biome;
    }

    protected final int minHeight(World world) {
        int m = cachedMin;
        if (m == Integer.MIN_VALUE) {
            m = Compat.minHeight(world);
            cachedMin = m;
        }
        return m;
    }

    /** Biyomu ayarlar (gökyüzü/sis/ses). Desteklenmeyen sürümde sessizce devre dışı kalır. */
    protected final void applyBiome(World world, BiomeGrid grid) {
        if (biome == null || grid == null || biomeFailed) {
            return;
        }
        try {
            int min = minHeight(world);
            int max = world.getMaxHeight();
            // Biyomlar 4x4x4 hücrelerde saklanır; her hücreye bir kez yazmak yeterli
            for (int x = 0; x < 16; x += 4) {
                for (int z = 0; z < 16; z += 4) {
                    for (int y = min; y < max; y += 4) {
                        grid.setBiome(x, y, z, biome);
                    }
                }
            }
        } catch (Throwable t) {
            biomeFailed = true;
        }
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    // Aşağıdaki metotlar bilinçli olarak @Override işaretsizdir: bazıları sadece eski, bazıları
    // sadece yeni sürümlerde vardır. Var olduğu her sürümde üst sınıfı ezer, olmadığında zararsızdır.

    public boolean canSpawn(World world, int x, int z) {
        return true;
    }

    public boolean shouldGenerateCaves() {
        return false;
    }

    public boolean shouldGenerateDecorations() {
        return false;
    }

    public boolean shouldGenerateMobs() {
        return false;
    }

    public boolean shouldGenerateStructures() {
        return false;
    }

    public boolean shouldGenerateNoise() {
        return false;
    }

    public boolean shouldGenerateSurface() {
        return false;
    }

    public boolean shouldGenerateBedrock() {
        return false;
    }
}
