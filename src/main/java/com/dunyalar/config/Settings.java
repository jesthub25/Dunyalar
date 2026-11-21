package com.dunyalar.config;

import com.dunyalar.data.WorldKind;
import com.dunyalar.data.WorldTheme;
import com.dunyalar.util.Text;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * config.yml'nin doğrulanmış, tip-güvenli anlık görüntüsü.
 * Eksik anahtarlar jar içindeki varsayılan config'ten gelir; hatalı değerler loglanıp varsayılana döner.
 */
public final class Settings {

    public static final int ABSOLUTE_MAX_RADIUS = 29_999_984;
    private static final Pattern DEFAULT_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private final JavaPlugin plugin;
    private final Logger log;
    private FileConfiguration c;

    private String prefix;
    private boolean radiusMode;
    private int minSize;
    private int maxSize;
    private Pattern namePattern;
    private String seed;
    private boolean structures;
    private int progressInterval;
    private int barLength;
    private String doneSound;

    private boolean pregen;
    private int pregenMaxRadius;
    private int tickBudgetMs;
    private int parallelChunks;
    private boolean asyncChunks;

    private double borderDamage;
    private double borderBuffer;
    private int warnDistance;
    private int warnTime;
    private boolean actionBar;
    private boolean blockThrowables;

    private boolean voidPlatform;
    private Material voidPlatformBlock;
    private int voidPlatformRadius;
    private int voidY;

    private final Map<WorldTheme, List<Material>> flatLayers = new EnumMap<WorldTheme, List<Material>>(WorldTheme.class);
    private final Map<WorldTheme, String> biomes = new EnumMap<WorldTheme, String>(WorldTheme.class);
    private final Map<WorldKind, Boolean> dragon = new EnumMap<WorldKind, Boolean>(WorldKind.class);
    private int endCenterOffset;

    private boolean pvp;
    private Difficulty difficulty;
    private boolean monsters;
    private boolean animals;
    private boolean keepSpawn;

    private boolean tpTitle;
    private String tpSound;
    private int safeSearchRadius;
    private boolean safePlatform;
    private Material safePlatformBlock;

    private boolean respawnEnabled;
    private boolean bedPriority;

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void load() {
        plugin.reloadConfig();
        c = plugin.getConfig();
        InputStream in = plugin.getResource("config.yml");
        if (in != null) {
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                c.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (Exception e) {
                log.warning("Varsayılan config okunamadı: " + e.getMessage());
            }
        }

        prefix = c.getString("prefix", "&8[&bDünyalar&8] &r");

        String mode = Text.norm(c.getString("olusturma.boyut-modu", "yaricap"));
        radiusMode = !"toplam".equals(mode);
        minSize = clamp(c.getInt("olusturma.min-boyut", 5), 1, ABSOLUTE_MAX_RADIUS);
        maxSize = clamp(c.getInt("olusturma.maks-boyut", 50000), minSize, ABSOLUTE_MAX_RADIUS);
        String regex = c.getString("olusturma.isim-deseni", DEFAULT_NAME.pattern());
        try {
            namePattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            log.warning("Geçersiz isim-deseni, varsayılan kullanılıyor: " + e.getDescription());
            namePattern = DEFAULT_NAME;
        }
        seed = c.getString("olusturma.tohum", "");
        structures = c.getBoolean("olusturma.yapilar", true);
        progressInterval = clamp(c.getInt("olusturma.ilerleme-araligi", 2), 1, 60);
        barLength = clamp(c.getInt("olusturma.ilerleme-cubugu-uzunlugu", 20), 5, 60);
        doneSound = c.getString("olusturma.tamamlandi-sesi", "");

        pregen = c.getBoolean("on-yukleme.aktif", true);
        pregenMaxRadius = clamp(c.getInt("on-yukleme.maks-yaricap", 512), 0, 10000);
        tickBudgetMs = clamp(c.getInt("on-yukleme.tick-butcesi-ms", 15), 1, 45);
        parallelChunks = clamp(c.getInt("on-yukleme.paralel-chunk", 16), 1, 128);
        asyncChunks = c.getBoolean("on-yukleme.asenkron", true);

        borderDamage = Math.max(0.0, c.getDouble("sinir.hasar", 1.0));
        borderBuffer = Math.max(0.0, c.getDouble("sinir.hasar-tamponu", 0.0));
        warnDistance = clamp(c.getInt("sinir.uyari-mesafesi", 5), 0, 1000);
        warnTime = clamp(c.getInt("sinir.uyari-suresi", 10), 0, 3600);
        actionBar = c.getBoolean("sinir.aksiyon-bar-mesaji", true);
        blockThrowables = c.getBoolean("sinir.firlatma-engelle", true);

        voidPlatform = c.getBoolean("void.platform.aktif", true);
        voidPlatformBlock = block(c.getString("void.platform.blok", "BEDROCK"), Material.BEDROCK, "void.platform.blok");
        voidPlatformRadius = clamp(c.getInt("void.platform.yaricap", 1), 0, 16);
        voidY = c.getInt("void.platform.y", 64);

        flatLayers.clear();
        flatLayers.put(WorldTheme.WORLD, layers("world",
                Arrays.asList(Material.BEDROCK, Material.DIRT, Material.DIRT, Material.DIRT, Material.GRASS_BLOCK)));
        flatLayers.put(WorldTheme.NETHER, layers("nether",
                Arrays.asList(Material.BEDROCK, Material.NETHERRACK, Material.NETHERRACK, Material.NETHERRACK, Material.NETHERRACK)));
        flatLayers.put(WorldTheme.END, layers("end",
                Arrays.asList(Material.BEDROCK, Material.END_STONE, Material.END_STONE, Material.END_STONE, Material.END_STONE)));

        biomes.clear();
        biomes.put(WorldTheme.WORLD, c.getString("biyomlar.world", "plains"));
        biomes.put(WorldTheme.NETHER, c.getString("biyomlar.nether", "nether_wastes"));
        biomes.put(WorldTheme.END, c.getString("biyomlar.end", "the_end"));

        dragon.clear();
        dragon.put(WorldKind.NORMAL, c.getBoolean("end.ejderha.normal", true));
        dragon.put(WorldKind.FLAT, c.getBoolean("end.ejderha.flat", false));
        dragon.put(WorldKind.VOID, c.getBoolean("end.ejderha.void", false));
        endCenterOffset = clamp(c.getInt("end.merkez-ofset", 12), 0, 1000);

        pvp = c.getBoolean("dunya-ayarlari.pvp", true);
        String diff = String.valueOf(c.getString("dunya-ayarlari.zorluk", "NORMAL")).trim().toUpperCase(java.util.Locale.ROOT);
        difficulty = Difficulty.NORMAL;
        for (Difficulty d : Difficulty.values()) {
            if (d.name().equals(diff)) {
                difficulty = d;
            }
        }
        monsters = c.getBoolean("dunya-ayarlari.canavar-spawn", true);
        animals = c.getBoolean("dunya-ayarlari.hayvan-spawn", true);
        keepSpawn = c.getBoolean("dunya-ayarlari.spawn-bellekte-tut", false);

        tpTitle = c.getBoolean("isinlanma.baslik", true);
        tpSound = c.getString("isinlanma.ses", "");
        safeSearchRadius = clamp(c.getInt("isinlanma.guvenli-arama-yaricapi", 8), 0, 32);
        safePlatform = c.getBoolean("isinlanma.zemin-yoksa-platform", true);
        safePlatformBlock = block(c.getString("isinlanma.platform-blok", "COBBLESTONE"), Material.COBBLESTONE, "isinlanma.platform-blok");

        respawnEnabled = c.getBoolean("yeniden-dogma.aktif", true);
        bedPriority = c.getBoolean("yeniden-dogma.yatak-oncelikli", false);
    }

    private Material block(String name, Material fallback, String path) {
        Material m = name == null ? null : Material.matchMaterial(name.trim());
        if (m == null || !m.isBlock() || isAir(m)) {
            log.warning("Geçersiz blok '" + name + "' (" + path + "), varsayılan kullanılıyor: " + fallback.name());
            return fallback;
        }
        return m;
    }

    private List<Material> layers(String theme, List<Material> fallback) {
        String path = "flat.katmanlar." + theme;
        List<String> raw = c.getStringList(path);
        if (raw == null || raw.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<Material>(fallback));
        }
        List<Material> out = new ArrayList<Material>();
        for (String s : raw) {
            Material m = s == null ? null : Material.matchMaterial(s.trim());
            if (m == null || !m.isBlock()) {
                log.warning("Geçersiz flat katmanı '" + s + "' (" + path + "), varsayılan katmanlar kullanılıyor.");
                return Collections.unmodifiableList(new ArrayList<Material>(fallback));
            }
            out.add(m);
        }
        if (out.size() > 128) {
            log.warning(path + " en fazla 128 katman olabilir, fazlası kesildi.");
            out = new ArrayList<Material>(out.subList(0, 128));
        }
        return Collections.unmodifiableList(out);
    }

    public static boolean isAir(Material m) {
        String n = m.name();
        return "AIR".equals(n) || "CAVE_AIR".equals(n) || "VOID_AIR".equals(n);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------------------------------------------------------- mesajlar

    /** Prefix'li, renklendirilmiş mesaj. */
    public String msg(String key, String... kv) {
        return Text.color(prefix + rawText(key, kv));
    }

    /** Prefix'siz, renklendirilmiş mesaj. */
    public String plain(String key, String... kv) {
        return Text.color(rawText(key, kv));
    }

    /** Renklendirilmemiş ham metin (iç içe kullanım için). */
    public String rawText(String key, String... kv) {
        String raw = c == null ? null : c.getString("mesajlar." + key);
        if (raw == null) {
            raw = key;
        }
        return Text.replace(raw, kv);
    }

    // ---------------------------------------------------------------- getters

    public boolean radiusMode() {
        return radiusMode;
    }

    public int minSize() {
        return minSize;
    }

    public int maxSize() {
        return maxSize;
    }

    public Pattern namePattern() {
        return namePattern;
    }

    public String seed() {
        return seed;
    }

    public boolean structures() {
        return structures;
    }

    public int progressInterval() {
        return progressInterval;
    }

    public int barLength() {
        return barLength;
    }

    public String doneSound() {
        return doneSound;
    }

    public boolean pregen() {
        return pregen;
    }

    public int pregenMaxRadius() {
        return pregenMaxRadius;
    }

    public int tickBudgetMs() {
        return tickBudgetMs;
    }

    public int parallelChunks() {
        return parallelChunks;
    }

    /** Paper'ın asenkron chunk API'si kullanılsın mı (config + sunucu desteği). */
    public boolean useAsyncChunks() {
        return asyncChunks && com.dunyalar.util.Compat.hasAsyncChunks();
    }

    public double borderDamage() {
        return borderDamage;
    }

    public double borderBuffer() {
        return borderBuffer;
    }

    public int warnDistance() {
        return warnDistance;
    }

    public int warnTime() {
        return warnTime;
    }

    public boolean actionBar() {
        return actionBar;
    }

    public boolean blockThrowables() {
        return blockThrowables;
    }

    public boolean voidPlatform() {
        return voidPlatform;
    }

    public Material voidPlatformBlock() {
        return voidPlatformBlock;
    }

    public int voidPlatformRadius() {
        return voidPlatformRadius;
    }

    public int voidY() {
        return voidY;
    }

    public List<Material> flatLayers(WorldTheme theme) {
        return flatLayers.get(theme);
    }

    public String biome(WorldTheme theme) {
        return biomes.get(theme);
    }

    public boolean dragon(WorldKind kind) {
        Boolean b = dragon.get(kind);
        return b != null && b;
    }

    public int endCenterOffset() {
        return endCenterOffset;
    }

    public boolean pvp() {
        return pvp;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public boolean monsters() {
        return monsters;
    }

    public boolean animals() {
        return animals;
    }

    public boolean keepSpawn() {
        return keepSpawn;
    }

    public boolean tpTitle() {
        return tpTitle;
    }

    public String tpSound() {
        return tpSound;
    }

    public int safeSearchRadius() {
        return safeSearchRadius;
    }

    public boolean safePlatform() {
        return safePlatform;
    }

    public Material safePlatformBlock() {
        return safePlatformBlock;
    }

    public boolean respawnEnabled() {
        return respawnEnabled;
    }

    public boolean bedPriority() {
        return bedPriority;
    }
}
