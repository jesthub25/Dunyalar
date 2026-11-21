package com.dunyalar.world;

import com.dunyalar.Dunyalar;
import com.dunyalar.config.Settings;
import com.dunyalar.data.WorldData;
import com.dunyalar.util.Compat;
import com.dunyalar.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Dünya oluşturma görevi:
 *  1/3 Dünya dosyalarını oluştur (ana thread, tek tick)
 *  2/3 Sınır, spawn, platform ve ayarlar
 *  3/3 Arazinin merkezden dışa doğru önceden üretilmesi (TPS bütçeli; Paper'da asenkron)
 * İlerleme, ayarlanan aralıklarla komutu kullanan kişiye chat'ten bildirilir.
 */
final class CreationTask implements Runnable {

    private static final int STEPS = 3;

    private final Dunyalar plugin;
    private final WorldManager manager;
    private final WorldData data;
    private final UUID playerId;
    private final String sizeText;

    private final AtomicInteger done = new AtomicInteger();
    private final AtomicInteger inflight = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    private World world;
    private BukkitTask timer;
    private long startNanos;
    private long pregenStartNanos;
    private long lastReport;
    private int[] chunkX = new int[0];
    private int[] chunkZ = new int[0];
    private int next;
    private boolean asyncMode;
    private boolean finished;

    CreationTask(Dunyalar plugin, WorldManager manager, WorldData data, UUID playerId, String sizeText) {
        this.plugin = plugin;
        this.manager = manager;
        this.data = data;
        this.playerId = playerId;
        this.sizeText = sizeText;
    }

    WorldData data() {
        return data;
    }

    private Settings s() {
        return plugin.settings();
    }

    void start() {
        startNanos = System.nanoTime();
        send(s().msg("olusturma-basladi", "dunya", data.name(), "tema", data.theme().key(),
                "tip", data.kind().key(), "boyut", sizeText));
        send(step(1, s().rawText("adim-dosya")));
        plugin.getLogger().info("Dünya oluşturuluyor: " + data.name() + " (" + data.theme().key() + "/"
                + data.kind().key() + ", alan " + data.areaText() + ")");
        // Bir sonraki tick: mesajlar oyuncuya ulaştıktan sonra ağır işlem başlasın
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                createStep();
            }
        });
    }

    private void createStep() {
        try {
            world = manager.createBukkitWorld(data);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Dünya oluşturulamadı: " + data.name(), t);
            world = Bukkit.getWorld(data.name());
            fail();
            return;
        }
        if (world == null) {
            plugin.getLogger().severe("Dünya oluşturulamadı (sunucu null döndürdü): " + data.name());
            fail();
            return;
        }

        send(step(2, s().rawText("adim-ayar")));
        try {
            manager.setupNewWorld(world, data);
        } catch (Throwable t) {
            // Dünya kullanılabilir; kurulumdaki hata loglanır, işlem devam eder
            plugin.getLogger().log(Level.WARNING, "Dünya kurulumunda hata: " + data.name(), t);
        }
        plugin.registry().put(data);
        plugin.registry().save();

        preparePregen();
        if (chunkX.length == 0) {
            finish();
            return;
        }
        send(step(3, s().rawText("adim-arazi", "toplam", String.valueOf(chunkX.length))));
        pregenStartNanos = System.nanoTime();
        lastReport = pregenStartNanos;
        asyncMode = Compat.hasAsyncChunks();
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    /** Merkezden dışa doğru sıralı chunk listesi (önce spawn çevresi hazır olur). */
    private void preparePregen() {
        Settings s = s();
        if (!s.pregen()) {
            return;
        }
        int rx = Math.min(data.radiusX(), s.pregenMaxRadius());
        int rz = Math.min(data.radiusZ(), s.pregenMaxRadius());
        if (rx <= 0 || rz <= 0) {
            return;
        }
        int minCX = Math.floorDiv(-rx, 16);
        int maxCX = Math.floorDiv(rx - 1, 16);
        int minCZ = Math.floorDiv(-rz, 16);
        int maxCZ = Math.floorDiv(rz - 1, 16);
        int w = maxCX - minCX + 1;
        int h = maxCZ - minCZ + 1;
        long count = (long) w * h;
        if (count <= 0 || count > 5_000_000L) {
            return;
        }
        long[] keys = new long[(int) count];
        int i = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                long dist = (long) cx * cx + (long) cz * cz;
                keys[i++] = (dist << 40) | ((long) (cx - minCX) << 20) | (long) (cz - minCZ);
            }
        }
        Arrays.sort(keys);
        chunkX = new int[keys.length];
        chunkZ = new int[keys.length];
        for (int j = 0; j < keys.length; j++) {
            chunkX[j] = (int) ((keys[j] >>> 20) & 0xFFFFF) + minCX;
            chunkZ[j] = (int) (keys[j] & 0xFFFFF) + minCZ;
        }
    }

    /** Her tick çalışır (ön yükleme). */
    @Override
    public void run() {
        if (finished) {
            return;
        }
        World current = Bukkit.getWorld(data.name());
        if (current == null || !current.getUID().equals(world.getUID())) {
            // Dünya dışarıdan kaldırıldı
            send(s().msg("olusturma-iptal", "dunya", data.name()));
            end();
            return;
        }
        int total = chunkX.length;
        if (asyncMode) {
            int limit = s().parallelChunks();
            while (inflight.get() < limit && next < total) {
                final int idx = next++;
                CompletableFuture<?> f = Compat.loadChunkAsync(world, chunkX[idx], chunkZ[idx]);
                if (f == null) {
                    asyncMode = false;
                    loadSync(idx);
                    break;
                }
                inflight.incrementAndGet();
                f.whenComplete(new BiConsumer<Object, Throwable>() {
                    @Override
                    public void accept(Object chunk, Throwable error) {
                        if (error != null) {
                            failed.incrementAndGet();
                        }
                        inflight.decrementAndGet();
                        done.incrementAndGet();
                    }
                });
            }
        } else {
            long deadline = System.nanoTime() + s().tickBudgetMs() * 1_000_000L;
            while (next < total) {
                loadSync(next++);
                if (System.nanoTime() >= deadline) {
                    break;
                }
            }
        }

        long now = System.nanoTime();
        if (now - lastReport >= s().progressInterval() * 1_000_000_000L) {
            lastReport = now;
            report(now);
        }
        if (done.get() >= total && inflight.get() <= 0) {
            finish();
        }
    }

    private void loadSync(int idx) {
        int cx = chunkX[idx];
        int cz = chunkZ[idx];
        try {
            world.loadChunk(cx, cz, true);
            // Eklenti bileti kaldırılır; chunk kaydedilip bellekten normal şekilde boşalır
            world.unloadChunkRequest(cx, cz);
        } catch (Throwable t) {
            failed.incrementAndGet();
        }
        done.incrementAndGet();
    }

    private void report(long now) {
        Settings s = s();
        int total = chunkX.length;
        int d = Math.min(done.get(), total);
        double ratio = total == 0 ? 1.0 : (double) d / total;
        int len = s.barLength();
        int filled = (int) Math.round(ratio * len);
        String bar = "&a" + Text.repeat('|', filled) + "&7" + Text.repeat('|', len - filled);
        double elapsed = (now - pregenStartNanos) / 1_000_000_000.0;
        String eta = d == 0 ? "?" : String.valueOf(Math.max(0L, Math.round(elapsed * (total - d) / d)));
        send(s.msg("olusturma-ilerleme", "bar", bar, "yuzde", Text.oneDecimal(ratio * 100.0),
                "yapilan", String.valueOf(d), "toplam", String.valueOf(total), "kalan", eta, "dunya", data.name()));
    }

    private void finish() {
        if (finished) {
            return;
        }
        double secs = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        if (chunkX.length > 0) {
            report(System.nanoTime());
        }
        end();
        send(s().msg("olusturma-tamamlandi", "dunya", data.name(), "sure", Text.oneDecimal(secs),
                "alan", data.areaText(), "tema", data.theme().key(), "tip", data.kind().key()));
        if (failed.get() > 0) {
            plugin.getLogger().warning(data.name() + ": " + failed.get() + " chunk ön yüklenemedi (oyuncu girince üretilecek).");
        }
        plugin.getLogger().info("Dünya hazır: " + data.name() + " (" + Text.oneDecimal(secs) + " sn)");
        if (playerId != null) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                Compat.playSound(p, s().doneSound());
            }
        }
    }

    private void fail() {
        send(s().msg("olusturma-hata", "dunya", data.name()));
        end();
        manager.cleanupFailed(world, data.name());
    }

    private void end() {
        finished = true;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        manager.creationEnded(this);
    }

    /** Sunucu kapanırken çağrılır. */
    void cancel() {
        finished = true;
        if (timer != null) {
            try {
                timer.cancel();
            } catch (Throwable ignored) {
                // zaten iptal
            }
            timer = null;
        }
    }

    private String step(int index, String description) {
        return s().msg("olusturma-adim", "adim", String.valueOf(index), "toplam", String.valueOf(STEPS),
                "aciklama", description);
    }

    /** Oyuncu çevrimdışı olduysa mesaj konsola düşer. */
    private void send(String message) {
        manager.tell(playerId, message);
    }
}
