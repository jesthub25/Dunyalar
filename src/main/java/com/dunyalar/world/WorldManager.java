package com.dunyalar.world;

import com.dunyalar.Dunyalar;
import com.dunyalar.config.Settings;
import com.dunyalar.data.SpawnPoint;
import com.dunyalar.data.WorldData;
import com.dunyalar.data.WorldKind;
import com.dunyalar.data.WorldRegistry;
import com.dunyalar.data.WorldTheme;
import com.dunyalar.generators.FlatGenerator;
import com.dunyalar.generators.VoidGenerator;
import com.dunyalar.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dünya yükleme, oluşturma, ayar uygulama, güvenli konum, ışınlanma, spawn ve silme işlemleri.
 * Tüm Bukkit çağrıları ana thread'de yapılır; sadece klasör silme asenkrondur.
 */
public final class WorldManager {

    /** Sunucu kök klasöründe dünya adı olarak kullanılamayacak isimler. */
    private static final Set<String> RESERVED = new HashSet<String>(Arrays.asList(
            "plugins", "logs", "libraries", "cache", "versions", "config", "crash-reports",
            "debug", "bundler", "world", "world_nether", "world_the_end"));

    /** Üstünde/içinde durulması tehlikeli bloklar (isimle, sürüm bağımsız). */
    private static final Set<String> DANGER = new HashSet<String>(Arrays.asList(
            "LAVA", "FIRE", "SOUL_FIRE", "MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE", "CACTUS",
            "SWEET_BERRY_BUSH", "WITHER_ROSE", "POWDER_SNOW", "POINTED_DRIPSTONE", "END_PORTAL",
            "NETHER_PORTAL", "END_GATEWAY", "COBWEB"));

    /** Ejderha savaşı kapalı End dünyalarında "portal zaten var" işaretinin konumu (chunk 7,7). */
    private static final int DRAGON_MARK_X = 120;
    private static final int DRAGON_MARK_Z = 120;

    private final Dunyalar plugin;
    private final Logger log;
    private final Set<String> deleting = new HashSet<String>();
    private CreationTask current;

    public WorldManager(Dunyalar plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    private Settings s() {
        return plugin.settings();
    }

    private WorldRegistry reg() {
        return plugin.registry();
    }

    // =================================================================== durum

    public boolean isBusy() {
        return current != null;
    }

    public String currentName() {
        return current == null ? null : current.data().name();
    }

    public boolean isPreparing(String name) {
        return current != null && current.data().name().equalsIgnoreCase(name);
    }

    void creationEnded(CreationTask task) {
        if (current == task) {
            current = null;
        }
    }

    public void shutdown() {
        if (current != null) {
            log.warning("Sunucu kapanırken '" + current.data().name() + "' dünyasının ön yüklemesi yarıda kaldı (dünya kullanılabilir).");
            current.cancel();
            current = null;
        }
    }

    public File folderOf(String name) {
        return new File(Bukkit.getWorldContainer(), name);
    }

    // =================================================================== üreticiler

    public ChunkGenerator generatorFor(WorldData d) {
        if (d.kind() == WorldKind.NORMAL) {
            return null;
        }
        String biomeKey = d.biome().isEmpty() ? s().biome(d.theme()) : d.biome();
        Biome biome = Compat.biome(biomeKey, log);
        if (d.kind() == WorldKind.VOID) {
            return new VoidGenerator(biome, d.platformY());
        }
        return new FlatGenerator(resolveLayers(d), biome);
    }

    private List<Material> resolveLayers(WorldData d) {
        List<Material> fallback = s().flatLayers(d.theme());
        if (d.layers().isEmpty()) {
            return fallback;
        }
        List<Material> out = new ArrayList<Material>(d.layers().size());
        for (String name : d.layers()) {
            Material m = name == null ? null : Material.matchMaterial(name);
            if (m == null || !m.isBlock()) {
                log.warning("'" + d.name() + "' dünyasında geçersiz katman '" + name + "', config katmanları kullanılıyor.");
                return fallback;
            }
            out.add(m);
        }
        return out;
    }

    public List<String> layerNames(WorldTheme theme) {
        List<String> out = new ArrayList<String>();
        for (Material m : s().flatLayers(theme)) {
            out.add(m.name());
        }
        return out;
    }

    // =================================================================== yükleme

    /** worlds.yml'deki tüm dünyaları yükler (yüklü olanlara ayarları yeniden uygular). */
    public int loadAll() {
        int ok = 0;
        for (WorldData d : new ArrayList<WorldData>(reg().all())) {
            if (isPreparing(d.name())) {
                continue;
            }
            World w = Bukkit.getWorld(d.name());
            if (w == null) {
                if (!folderOf(d.name()).isDirectory()) {
                    log.warning("'" + d.name() + "' dünyasının klasörü bulunamadı, yüklenmedi. (Silmek için: /dünya sil " + d.name() + ")");
                    continue;
                }
                try {
                    w = createBukkitWorld(d);
                } catch (Throwable t) {
                    log.log(Level.SEVERE, "'" + d.name() + "' dünyası yüklenemedi.", t);
                    continue;
                }
                if (w == null) {
                    log.severe("'" + d.name() + "' dünyası yüklenemedi (sunucu null döndürdü).");
                    continue;
                }
                log.info("Dünya yüklendi: " + d.name() + " (" + d.theme().key() + "/" + d.kind().key() + ")");
            }
            try {
                applySettings(w, d);
                ok++;
            } catch (Throwable t) {
                log.log(Level.WARNING, "'" + d.name() + "' ayarları uygulanamadı.", t);
            }
        }
        return ok;
    }

    World createBukkitWorld(WorldData d) {
        WorldCreator wc = new WorldCreator(d.name());
        wc.environment(d.theme().environment());
        wc.seed(d.seed());
        if (d.kind() == WorldKind.NORMAL) {
            wc.type(org.bukkit.WorldType.NORMAL);
            wc.generateStructures(d.structures());
        } else {
            wc.generator(generatorFor(d));
            wc.generateStructures(false);
        }
        return wc.createWorld();
    }

    // =================================================================== ayarlar

    /** Sınır ve dünya ayarlarını uygular (açılış ve reload'da). */
    public void applySettings(World w, WorldData d) {
        applySettings(w, d, true);
    }

    /**
     * @param keepSpawnSetting false ise spawn-bellekte-tut ayarı uygulanmaz. Yeni oluşturulan dünyada bu
     *                         ayar kurulum bitene kadar ertelenir; aksi halde spawn chunk'ları boşaltılırken
     *                         aynı tick'te yapılan chunk erişimleri (Paper 1.17/1.18) başarısız olur.
     */
    void applySettings(World w, WorldData d, boolean keepSpawnSetting) {
        Settings s = s();
        WorldBorder b = w.getWorldBorder();
        b.setCenter(0.0, 0.0);
        b.setSize(2.0 * Math.max(d.radiusX(), d.radiusZ()));
        b.setDamageAmount(s.borderDamage());
        b.setDamageBuffer(s.borderBuffer());
        b.setWarningDistance(s.warnDistance());
        b.setWarningTime(s.warnTime());

        w.setPVP(s.pvp());
        w.setDifficulty(s.difficulty());
        w.setSpawnFlags(s.monsters(), s.animals());
        if (keepSpawnSetting) {
            Compat.setKeepSpawnInMemory(w, s.keepSpawn());
        }

        Location sp = d.spawnLocation(w);
        if (sp != null) {
            w.setSpawnLocation(sp.getBlockX(), sp.getBlockY(), sp.getBlockZ());
        }
    }

    /** Kurulumun dokunacağı chunk'lar (platform, ejderha işareti, güvenli merkez araması). x,z çiftleri. */
    List<int[]> setupChunks(WorldData d) {
        List<int[]> out = new ArrayList<int[]>();
        int cx = centerX(d);
        int reach = Math.max(s().safeSearchRadius(), d.kind() == WorldKind.VOID ? s().voidPlatformRadius() : 0) + 1;
        for (int x = Math.floorDiv(cx - reach, 16); x <= Math.floorDiv(cx + reach, 16); x++) {
            for (int z = Math.floorDiv(-reach, 16); z <= Math.floorDiv(reach, 16); z++) {
                out.add(new int[]{x, z});
            }
        }
        if (d.theme() == WorldTheme.END && !d.dragon()) {
            out.add(new int[]{DRAGON_MARK_X >> 4, DRAGON_MARK_Z >> 4});
        }
        return out;
    }

    /**
     * Yeni dünyaya tek seferlik kurulum (ön yüklemeden sonra çağrılır). Gerekli chunk'lar eklenti
     * bileti ile sabitlenir; böylece kurulum sırasında boşaltılamazlar.
     */
    void finalizeNewWorld(World w, WorldData d) {
        List<int[]> chunks = setupChunks(d);
        List<int[]> held = new ArrayList<int[]>();
        try {
            for (int[] c : chunks) {
                w.addPluginChunkTicket(c[0], c[1], plugin);
                held.add(c);
                w.loadChunk(c[0], c[1], true);
            }
            if (d.kind() == WorldKind.VOID && s().voidPlatform()) {
                buildPlatform(w, 0, d.platformY(), 0, s().voidPlatformRadius(), s().voidPlatformBlock());
            }
            if (d.theme() == WorldTheme.END && !d.dragon()) {
                placeDragonMarker(w);
            }
            Location center = safeCenter(w, d);
            if (center != null && d.spawn() == null) {
                w.setSpawnLocation(center.getBlockX(), center.getBlockY(), center.getBlockZ());
            }
        } finally {
            for (int[] c : held) {
                try {
                    w.removePluginChunkTicket(c[0], c[1], plugin);
                } catch (Throwable ignored) {
                    // dünya kaldırılmış olabilir
                }
            }
        }
        Compat.setKeepSpawnInMemory(w, s().keepSpawn());
    }

    /**
     * Vanilla, End dünyasına ilk oyuncu girdiğinde merkezde aktif bir End portalı arar; bulamazsa
     * çıkış portalını inşa edip ejderhayı doğurur. Erişilemeyen bir yere bedrock ile kapatılmış tek bir
     * portal bloğu koyarak ejderha savaşının "zaten bitmiş" sayılmasını sağlarız (tüm sürümlerde geçerli).
     */
    private void placeDragonMarker(World w) {
        int y = Compat.minHeight(w);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(DRAGON_MARK_X + dx, y, DRAGON_MARK_Z + dz).setType(Material.BEDROCK, false);
                w.getBlockAt(DRAGON_MARK_X + dx, y + 1, DRAGON_MARK_Z + dz).setType(Material.BEDROCK, false);
            }
        }
        w.getBlockAt(DRAGON_MARK_X, y, DRAGON_MARK_Z).setType(Material.END_PORTAL, false);
    }

    private void buildPlatform(World w, int cx, int y, int cz, int radius, Material block) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                w.getBlockAt(cx + dx, y, cz + dz).setType(block, false);
            }
        }
    }

    /** Void platform yüksekliğini dünyanın sınırlarına sığdırır. */
    int clampPlatformY(int wanted, World.Environment env) {
        // Dünya henüz yokken sınırlar bilinmez; tüm sürümlerde geçerli güvenli aralık
        return Math.max(1, Math.min(250, wanted));
    }

    // =================================================================== güvenli konum

    /** Dünyanın merkezindeki (veya yakınındaki) güvenli konum. */
    public Location safeCenter(World w, WorldData d) {
        return safeAt(w, d, centerX(d), 0);
    }

    /** Ejderha savaşı olan End dünyalarında çıkış portalının üstüne düşmemek için merkez kaydırılır. */
    private int centerX(WorldData d) {
        if (d.theme() == WorldTheme.END && d.dragon()) {
            return Math.min(s().endCenterOffset(), Math.max(0, d.radiusX() - 2));
        }
        return 0;
    }

    public Location safeAt(World w, WorldData d, int x, int z) {
        x = Math.max(-d.radiusX(), Math.min(d.radiusX() - 1, x));
        z = Math.max(-d.radiusZ(), Math.min(d.radiusZ() - 1, z));
        int min = Compat.minHeight(w);
        int max = w.getMaxHeight();

        if (d.kind() == WorldKind.VOID) {
            int y = d.platformY() + 1;
            if (y > min && y + 1 < max && isSafe(w, x, y, z, min, max)) {
                return new Location(w, x + 0.5, y, z + 0.5);
            }
        }

        int radius = s().safeSearchRadius();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // sadece halkanın kenarı
                    }
                    int bx = x + dx;
                    int bz = z + dz;
                    if (!d.isBlockInside(bx, bz)) {
                        continue;
                    }
                    int y = findSafeY(w, bx, bz, min, max);
                    if (y != Integer.MIN_VALUE) {
                        return new Location(w, bx + 0.5, y, bz + 0.5);
                    }
                }
            }
        }

        // Güvenli yer yok: kontrollü yedek konum
        int y;
        if (d.kind() == WorldKind.VOID) {
            y = d.platformY() + 1;
        } else if (w.getEnvironment() == World.Environment.NETHER) {
            y = 64;
        } else {
            y = w.getHighestBlockYAt(x, z) + 1;
        }
        y = Math.max(min + 1, Math.min(max - 2, y));
        if (s().safePlatform()) {
            buildPlatform(w, x, y - 1, z, 1, s().safePlatformBlock());
            w.getBlockAt(x, y, z).setType(Material.AIR, false);
            w.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
        }
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    private int findSafeY(World w, int x, int z, int min, int max) {
        int top;
        if (w.getEnvironment() == World.Environment.NETHER) {
            top = Math.min(max - 2, 120); // bedrock tavanının altı
        } else {
            top = Math.min(max - 2, w.getHighestBlockYAt(x, z) + 1);
        }
        for (int y = top; y > min; y--) {
            if (isSafe(w, x, y, z, min, max)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean isSafe(World w, int x, int y, int z, int min, int max) {
        if (y - 1 < min || y + 1 >= max) {
            return false;
        }
        Material below = w.getBlockAt(x, y - 1, z).getType();
        if (!below.isSolid() || DANGER.contains(below.name())) {
            return false;
        }
        return passable(w.getBlockAt(x, y, z)) && passable(w.getBlockAt(x, y + 1, z));
    }

    private boolean passable(Block b) {
        if (b.isLiquid()) {
            return false;
        }
        Material t = b.getType();
        return !t.isSolid() && !DANGER.contains(t.name());
    }

    /** Konumu sınır içine çekip güvenli hale getirir (sınır dışına düşen oyuncular için). */
    public Location clampInside(Location loc, WorldData d) {
        World w = loc.getWorld();
        int bx = (int) Math.floor(Math.max(-d.radiusX() + 0.5, Math.min(d.radiusX() - 0.5, loc.getX())));
        int bz = (int) Math.floor(Math.max(-d.radiusZ() + 0.5, Math.min(d.radiusZ() - 0.5, loc.getZ())));
        Location safe = safeAt(w, d, bx, bz);
        safe.setYaw(loc.getYaw());
        safe.setPitch(loc.getPitch());
        return safe;
    }

    // =================================================================== oluşturma

    /** İsim kullanılabilir mi? Değilse mesaj anahtarı döner. */
    public String checkName(String name) {
        if (!WorldRegistry.isSafeName(name) || !s().namePattern().matcher(name).matches()) {
            return "gecersiz-isim";
        }
        String lower = WorldRegistry.key(name);
        if (RESERVED.contains(lower) || reg().get(name) != null || Bukkit.getWorld(name) != null
                || deleting.contains(lower) || isPreparing(name)) {
            return "isim-kullanimda";
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().equalsIgnoreCase(name)) {
                return "isim-kullanimda";
            }
        }
        File[] children = Bukkit.getWorldContainer().listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.getName().equalsIgnoreCase(name)) {
                    return "isim-kullanimda";
                }
            }
        }
        return null;
    }

    public void create(CommandSender sender, String name, WorldTheme theme, WorldKind kind, int radiusX, int radiusZ, String sizeText) {
        Settings s = s();
        long seed = parseSeed(s.seed());
        List<String> layers = kind == WorldKind.FLAT ? layerNames(theme) : Collections.<String>emptyList();
        String biome = kind == WorldKind.NORMAL ? "" : s.biome(theme);
        boolean dragon = theme == WorldTheme.END && s.dragon(kind);
        int platformY = clampPlatformY(s.voidY(), theme.environment());
        WorldData d = new WorldData(name, theme, kind, radiusX, radiusZ, seed, System.currentTimeMillis(),
                s.structures(), layers, biome, platformY, dragon, null);

        UUID id = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        current = new CreationTask(plugin, this, d, id, sizeText);
        current.start();
    }

    private long parseSeed(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new java.util.Random().nextLong();
        }
        String t = raw.trim();
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return t.hashCode(); // Vanilla ile aynı: metin tohum
        }
    }

    /** Başarısız oluşturmadan kalan dünyayı/klasörü temizler. */
    void cleanupFailed(World w, String name) {
        if (w != null) {
            try {
                Bukkit.unloadWorld(w, false);
            } catch (Throwable t) {
                log.log(Level.WARNING, "Yarım kalan dünya kaldırılamadı: " + name, t);
            }
        }
        final File folder = folderOf(name);
        if (folder.exists()) {
            deleteFolderAsync(folder, name, null, 1, false);
        }
    }

    // =================================================================== ışınlanma / spawn

    public void teleport(Player p, WorldData d) {
        Settings s = s();
        if (isPreparing(d.name())) {
            p.sendMessage(s.msg("dunya-hazirlaniyor", "dunya", d.name()));
            return;
        }
        World w = Bukkit.getWorld(d.name());
        if (w == null) {
            p.sendMessage(s.msg("dunya-yuklu-degil", "dunya", d.name()));
            return;
        }
        Location target = d.spawn() != null ? d.spawnLocation(w) : safeCenter(w, d);
        if (target == null) {
            p.sendMessage(s.msg("isinlanma-basarisiz", "dunya", d.name()));
            return;
        }
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        p.setFallDistance(0f);
        if (!p.teleport(target)) {
            p.sendMessage(s.msg("isinlanma-basarisiz", "dunya", d.name()));
            return;
        }
        p.sendMessage(s.msg("isinlandi", "dunya", d.name()));
        if (s.tpTitle()) {
            try {
                p.sendTitle(s.plain("baslik", "dunya", d.name()),
                        s.plain("alt-baslik", "dunya", d.name(), "tema", d.theme().key(), "tip", d.kind().key()),
                        10, 40, 15);
            } catch (Throwable ignored) {
                // başlık desteklenmiyor
            }
        }
        Compat.playSound(p, s.tpSound());
    }

    /** Ölüm sonrası doğma konumu. */
    public Location respawnLocation(World w, WorldData d) {
        Location sp = d.spawnLocation(w);
        return sp != null ? sp : safeCenter(w, d);
    }

    public void setSpawn(Player p, WorldData d) {
        Settings s = s();
        World w = Bukkit.getWorld(d.name());
        if (w == null) {
            p.sendMessage(s.msg("dunya-yuklu-degil", "dunya", d.name()));
            return;
        }
        if (!p.getWorld().getUID().equals(w.getUID())) {
            p.sendMessage(s.msg("spawn-yanlis-dunya", "dunya", d.name(), "aktif", p.getWorld().getName()));
            return;
        }
        Location l = p.getLocation();
        if (!d.isInside(l)) {
            p.sendMessage(s.msg("spawn-sinir-disi"));
            return;
        }
        d.setSpawn(SpawnPoint.of(l));
        reg().save();
        w.setSpawnLocation(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        p.sendMessage(s.msg("spawn-ayarlandi", "dunya", d.name(),
                "x", String.valueOf(l.getBlockX()), "y", String.valueOf(l.getBlockY()), "z", String.valueOf(l.getBlockZ())));
    }

    // =================================================================== silme

    public void delete(CommandSender sender, WorldData d) {
        Settings s = s();
        String key = WorldRegistry.key(d.name());
        if (isPreparing(d.name())) {
            sender.sendMessage(s.msg("dunya-hazirlaniyor", "dunya", d.name()));
            return;
        }
        if (deleting.contains(key)) {
            sender.sendMessage(s.msg("sil-basladi", "dunya", d.name()));
            return;
        }
        World w = Bukkit.getWorld(d.name());
        if (w != null) {
            int players = w.getPlayers().size();
            if (players > 0) {
                sender.sendMessage(s.msg("sil-oyuncu-var", "dunya", d.name(), "sayi", String.valueOf(players)));
                return;
            }
            List<World> worlds = Bukkit.getWorlds();
            if (!worlds.isEmpty() && worlds.get(0).getUID().equals(w.getUID())) {
                sender.sendMessage(s.msg("sil-ana-dunya"));
                return;
            }
            boolean unloaded;
            try {
                unloaded = Bukkit.unloadWorld(w, false);
            } catch (Throwable t) {
                log.log(Level.SEVERE, "Dünya kaldırılamadı: " + d.name(), t);
                unloaded = false;
            }
            if (!unloaded) {
                sender.sendMessage(s.msg("sil-hata", "dunya", d.name()));
                return;
            }
        }
        reg().remove(d);
        reg().save();
        deleting.add(key);
        sender.sendMessage(s.msg("sil-basladi", "dunya", d.name()));
        UUID id = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        deleteFolderAsync(folderOf(d.name()), d.name(), id, 1, true);
    }

    private void deleteFolderAsync(final File folder, final String name, final UUID notify,
                                   final int attempt, final boolean report) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final boolean ok = deleteRecursive(folder);
                if (!plugin.isEnabled()) {
                    return;
                }
                try {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (!ok && attempt < 3) {
                                // Bölge dosyaları bir an kilitli kalabilir: 2 sn sonra tekrar dene
                                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                                    @Override
                                    public void run() {
                                        deleteFolderAsync(folder, name, notify, attempt + 1, report);
                                    }
                                }, 40L);
                                return;
                            }
                            deleting.remove(WorldRegistry.key(name));
                            if (!report) {
                                return;
                            }
                            Settings s = s();
                            String msg = ok ? s.msg("sil-tamamlandi", "dunya", name)
                                    : s.msg("sil-klasor-kaldi", "dunya", name, "yol", folder.getAbsolutePath());
                            tell(notify, msg);
                            log.info(ok ? "Dünya silindi: " + name : "Dünya kaydı silindi, klasör kısmen kaldı: " + folder.getAbsolutePath());
                        }
                    });
                } catch (Throwable ignored) {
                    // eklenti kapanıyor
                }
            }
        });
    }

    private static boolean deleteRecursive(File folder) {
        if (!folder.exists()) {
            return true;
        }
        try {
            Files.walkFileTree(folder.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // aşağıda kontrol edilir
        }
        return !folder.exists();
    }

    void tell(UUID playerId, String message) {
        if (playerId != null) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
                return;
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
