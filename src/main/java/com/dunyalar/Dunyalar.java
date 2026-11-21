package com.dunyalar;

import com.dunyalar.commands.DunyaCommand;
import com.dunyalar.config.Settings;
import com.dunyalar.data.WorldData;
import com.dunyalar.data.WorldKind;
import com.dunyalar.data.WorldRegistry;
import com.dunyalar.data.WorldTheme;
import com.dunyalar.generators.FlatGenerator;
import com.dunyalar.generators.VoidGenerator;
import com.dunyalar.listeners.BorderListener;
import com.dunyalar.listeners.DragonListener;
import com.dunyalar.listeners.RespawnListener;
import com.dunyalar.util.Compat;
import com.dunyalar.util.Text;
import com.dunyalar.world.WorldManager;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.logging.Level;

public final class Dunyalar extends JavaPlugin {

    private Settings settings;
    private WorldRegistry registry;
    private WorldManager worlds;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            settings = new Settings(this);
            settings.load();
            registry = new WorldRegistry(this);
            registry.load();
            worlds = new WorldManager(this);
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Dunyalar başlatılamadı, eklenti devre dışı bırakılıyor.", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginCommand cmd = getCommand("dünya");
        if (cmd == null) {
            getLogger().severe("plugin.yml içinde 'dünya' komutu bulunamadı! Jar bozuk olabilir.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        DunyaCommand executor = new DunyaCommand(this);
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BorderListener(this), this);
        pm.registerEvents(new RespawnListener(this), this);
        pm.registerEvents(new DragonListener(this), this);

        int loaded = worlds.loadAll();
        getLogger().info("Dunyalar v" + getDescription().getVersion() + " aktif. Yönetilen dünya: "
                + registry.size() + " (yüklü: " + loaded + ")"
                + (Compat.hasAsyncChunks() ? " [Paper asenkron chunk desteği aktif]" : ""));
    }

    @Override
    public void onDisable() {
        if (worlds != null) {
            worlds.shutdown();
        }
        if (registry != null) {
            registry.save();
        }
    }

    /** config.yml ve worlds.yml'yi yeniden okur, yeni dünyaları yükler, ayarları uygular. */
    public int reloadAll() {
        settings.load();
        registry.load();
        return worlds.loadAll();
    }

    /**
     * bukkit.yml veya başka bir dünya eklentisi bu dünyaları yüklerse doğru üretici kullanılsın.
     * Kimlik örnekleri: Dunyalar:void, Dunyalar:void-nether, Dunyalar:flat-end
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        try {
            if (registry != null && worlds != null) {
                WorldData d = registry.get(worldName);
                if (d != null) {
                    return worlds.generatorFor(d);
                }
            }
            String key = Text.norm(id == null ? "" : id.trim());
            if (key.isEmpty()) {
                key = "void";
            }
            String[] parts = key.split("[-:_]", 2);
            WorldKind kind = WorldKind.parse(parts[0]);
            if (kind == null || kind == WorldKind.NORMAL) {
                return null; // vanilla üretici
            }
            WorldTheme theme = parts.length > 1 ? WorldTheme.parse(parts[1]) : WorldTheme.WORLD;
            if (theme == null) {
                theme = WorldTheme.WORLD;
            }
            String biomeKey = theme == WorldTheme.NETHER ? "nether_wastes" : theme == WorldTheme.END ? "the_end" : "plains";
            if (kind == WorldKind.FLAT) {
                java.util.List<Material> layers = settings != null ? settings.flatLayers(theme)
                        : Arrays.asList(Material.BEDROCK, Material.DIRT, Material.DIRT, Material.DIRT, Material.GRASS_BLOCK);
                return new FlatGenerator(layers, Compat.biome(biomeKey, getLogger()));
            }
            return new VoidGenerator(Compat.biome(biomeKey, getLogger()), settings != null ? settings.voidY() : 64);
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Varsayılan üretici oluşturulamadı: " + worldName, t);
            return null;
        }
    }

    public Settings settings() {
        return settings;
    }

    public WorldRegistry registry() {
        return registry;
    }

    public WorldManager worlds() {
        return worlds;
    }
}
