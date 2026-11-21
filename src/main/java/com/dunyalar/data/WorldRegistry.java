package com.dunyalar.data;

import com.dunyalar.util.Text;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Oluşturulan dünyaların kalıcı kaydı: plugins/Dunyalar/worlds.yml
 * Dosya atomik yazılır (önce .tmp, sonra taşıma) — çökme anında bozulmaz.
 */
public final class WorldRegistry {

    private static final String ROOT = "dunyalar";

    private final Logger log;
    private final File file;
    private final Map<String, WorldData> byKey = new LinkedHashMap<String, WorldData>();
    private final Map<String, WorldData> byExact = new HashMap<String, WorldData>();

    public WorldRegistry(JavaPlugin plugin) {
        this.log = plugin.getLogger();
        this.file = new File(plugin.getDataFolder(), "worlds.yml");
    }

    public static String key(String name) {
        return Text.lower(name);
    }

    public void load() {
        byKey.clear();
        byExact.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection(ROOT);
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            try {
                WorldData d = parse(name, s);
                if (d == null) {
                    continue;
                }
                if (byKey.containsKey(key(name))) {
                    log.warning("worlds.yml: aynı isimde ikinci kayıt yok sayıldı: " + name);
                    continue;
                }
                put(d);
            } catch (Exception e) {
                log.log(Level.WARNING, "worlds.yml: '" + name + "' okunamadı, atlanıyor.", e);
            }
        }
    }

    private WorldData parse(String name, ConfigurationSection s) {
        if (!isSafeName(name)) {
            log.warning("worlds.yml: geçersiz dünya adı atlandı: " + name);
            return null;
        }
        WorldTheme theme = WorldTheme.parse(s.getString("tema"));
        WorldKind kind = WorldKind.parse(s.getString("tip"));
        if (theme == null || kind == null) {
            log.warning("worlds.yml: '" + name + "' için tema/tip geçersiz, atlanıyor.");
            return null;
        }
        int rx = s.getInt("yaricap-x", 0);
        int rz = s.getInt("yaricap-z", 0);
        if (rx < 1 || rz < 1) {
            log.warning("worlds.yml: '" + name + "' için yaricap-x / yaricap-z en az 1 olmalı, atlanıyor.");
            return null;
        }
        rx = Math.min(rx, com.dunyalar.config.Settings.ABSOLUTE_MAX_RADIUS);
        rz = Math.min(rz, com.dunyalar.config.Settings.ABSOLUTE_MAX_RADIUS);

        SpawnPoint spawn = null;
        ConfigurationSection sp = s.getConfigurationSection("spawn");
        if (sp != null) {
            spawn = new SpawnPoint(sp.getDouble("x"), sp.getDouble("y"), sp.getDouble("z"),
                    (float) sp.getDouble("yaw"), (float) sp.getDouble("pitch"));
        }
        return new WorldData(
                name, theme, kind, rx, rz,
                s.getLong("tohum", 0L),
                s.getLong("olusturulma", System.currentTimeMillis()),
                s.getBoolean("yapilar", true),
                s.getStringList("katmanlar"),
                s.getString("biyom", ""),
                s.getInt("platform-y", 64),
                s.getBoolean("ejderha", kind == WorldKind.NORMAL),
                spawn);
    }

    /** Tüm kayıtları diske yazar. Başarılıysa true. */
    public boolean save() {
        YamlConfiguration y = new YamlConfiguration();
        for (WorldData d : byKey.values()) {
            String p = ROOT + "." + d.name() + ".";
            y.set(p + "tema", d.theme().key());
            y.set(p + "tip", d.kind().key());
            y.set(p + "yaricap-x", d.radiusX());
            y.set(p + "yaricap-z", d.radiusZ());
            y.set(p + "tohum", d.seed());
            y.set(p + "olusturulma", d.createdAt());
            y.set(p + "yapilar", d.structures());
            if (!d.layers().isEmpty()) {
                y.set(p + "katmanlar", new ArrayList<String>(d.layers()));
            }
            if (!d.biome().isEmpty()) {
                y.set(p + "biyom", d.biome());
            }
            y.set(p + "platform-y", d.platformY());
            y.set(p + "ejderha", d.dragon());
            SpawnPoint sp = d.spawn();
            if (sp != null) {
                y.set(p + "spawn.x", sp.x());
                y.set(p + "spawn.y", sp.y());
                y.set(p + "spawn.z", sp.z());
                y.set(p + "spawn.yaw", (double) sp.yaw());
                y.set(p + "spawn.pitch", (double) sp.pitch());
            }
        }
        if (byKey.isEmpty()) {
            y.createSection(ROOT);
        }
        String text = "# Dunyalar - oluşturulan dünyaların kaydı.\n"
                + "# Elle düzenleyebilirsin (ör. yaricap-x/yaricap-z), ardından: /dünya reload\n"
                + y.saveToString();
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                throw new IOException("Klasör oluşturulamadı: " + dir);
            }
            File tmp = new File(dir, file.getName() + ".tmp");
            Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "worlds.yml kaydedilemedi!", e);
            return false;
        }
    }

    public void put(WorldData d) {
        WorldData old = byKey.put(key(d.name()), d);
        if (old != null) {
            byExact.remove(old.name());
        }
        byExact.put(d.name(), d);
    }

    public void remove(WorldData d) {
        WorldData old = byKey.remove(key(d.name()));
        if (old != null) {
            byExact.remove(old.name());
        }
    }

    public WorldData get(String name) {
        return name == null ? null : byKey.get(key(name));
    }

    /** Hızlı arama (hareket olayları için): önce tam isim, sonra küçük harf. */
    public WorldData get(World world) {
        if (world == null) {
            return null;
        }
        WorldData d = byExact.get(world.getName());
        return d != null ? d : byKey.get(key(world.getName()));
    }

    public Collection<WorldData> all() {
        return Collections.unmodifiableCollection(byKey.values());
    }

    public List<String> names() {
        List<String> out = new ArrayList<String>(byKey.size());
        for (WorldData d : byKey.values()) {
            out.add(d.name());
        }
        return out;
    }

    public int size() {
        return byKey.size();
    }

    /** Dosya/YAML açısından güvenli isim (desenden bağımsız sert kural). */
    public static boolean isSafeName(String name) {
        if (name == null || name.isEmpty() || name.length() > 64) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == '_' || ch == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
