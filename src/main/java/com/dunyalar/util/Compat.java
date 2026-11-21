package com.dunyalar.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * 1.16.5 - 1.21.x arası sürüm farklarını güvenli şekilde (reflection + yakalama) yöneten yardımcı.
 * Bu sınıf, eski sürümde bulunmayan veya yeni sürümde kaldırılan API'lere doğrudan bağlanmaz.
 */
public final class Compat {

    private static final Method MIN_HEIGHT = find(World.class, "getMinHeight");
    private static final Method KEEP_SPAWN = find(World.class, "setKeepSpawnInMemory", boolean.class);
    private static final Method CHUNK_ASYNC = find(World.class, "getChunkAtAsync", int.class, int.class, boolean.class);

    private Compat() {
    }

    private static Method find(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Dünyanın en düşük blok yüksekliği (1.16: 0, 1.18+: -64 olabilir). */
    public static int minHeight(World world) {
        if (MIN_HEIGHT != null && world != null) {
            try {
                Object v = MIN_HEIGHT.invoke(world);
                if (v instanceof Integer) {
                    return (Integer) v;
                }
            } catch (Throwable ignored) {
                // eski sürüm
            }
        }
        return 0;
    }

    public static void setKeepSpawnInMemory(World world, boolean keep) {
        if (KEEP_SPAWN == null || world == null) {
            return;
        }
        try {
            KEEP_SPAWN.invoke(world, keep);
        } catch (Throwable ignored) {
            // Yeni sürümlerde kaldırılmış/desteklenmeyen olabilir
        }
    }

    /** Paper'ın asenkron chunk yükleme API'si var mı? */
    public static boolean hasAsyncChunks() {
        return CHUNK_ASYNC != null;
    }

    /** Paper: asenkron chunk üretir. Desteklenmiyorsa null döner. */
    public static CompletableFuture<?> loadChunkAsync(World world, int x, int z) {
        if (CHUNK_ASYNC == null) {
            return null;
        }
        try {
            Object result = CHUNK_ASYNC.invoke(world, x, z, true);
            if (result instanceof CompletableFuture) {
                return (CompletableFuture<?>) result;
            }
        } catch (Throwable ignored) {
            // Desteklenmiyor
        }
        return null;
    }

    /**
     * Biyomu anahtarla bulur (ör. "plains", "minecraft:the_end").
     * Biome 1.21.3+ sürümünde enum olmaktan çıktığı için enum metotları kullanılmaz.
     */
    public static Biome biome(String key, Logger logger) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        String k = Text.lower(key.trim());
        String path = k;
        if (k.contains(":")) {
            String ns = k.substring(0, k.indexOf(':'));
            path = k.substring(k.indexOf(':') + 1);
            if (!"minecraft".equals(ns)) {
                if (logger != null) {
                    logger.warning("Sadece minecraft: biyomları desteklenir, yok sayıldı: " + key);
                }
                return null;
            }
        }
        try {
            Biome biome = Registry.BIOME.get(NamespacedKey.minecraft(path));
            if (biome == null && logger != null) {
                logger.warning("Biyom bulunamadı: " + key + " (varsayılan biyom kullanılacak)");
            }
            return biome;
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("Biyom çözümlenemedi: " + key + " (" + t.getClass().getSimpleName() + ")");
            }
            return null;
        }
    }

    /** Sürüm bağımsız ses çalma (String anahtar her sürümde vardır). */
    public static void playSound(Player player, String sound) {
        if (player == null || sound == null || sound.trim().isEmpty()) {
            return;
        }
        try {
            Location loc = player.getLocation();
            player.playSound(loc, sound.trim(), 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // Geçersiz ses anahtarı
        }
    }
}
