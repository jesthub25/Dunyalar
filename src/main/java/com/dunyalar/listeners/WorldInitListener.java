package com.dunyalar.listeners;

import com.dunyalar.Dunyalar;
import com.dunyalar.util.Compat;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

/**
 * Dünya başlatılırken (spawn bölgesi hazırlanmadan hemen önce) çalışır.
 *
 * Vanilla, her yeni/yüklenen dünyada spawn çevresindeki yüzlerce chunk'ı ana thread'de senkron üretir.
 * Bu hem sunucuyu dondurur hem de Spigot 1.16.5'te özel End dünyalarında iç chunk hatasına yol açar.
 * Eklentinin dünyalarında bu adım atlanır; arazi eklentinin TPS dostu ön yüklemesiyle üretilir.
 */
public final class WorldInitListener implements Listener {

    private final Dunyalar plugin;

    public WorldInitListener(Dunyalar plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInit(WorldInitEvent e) {
        World w = e.getWorld();
        String name = w.getName();
        boolean creating = plugin.worlds() != null && plugin.worlds().isPreparing(name);
        boolean registered = plugin.registry() != null && plugin.registry().get(name) != null;
        if (!creating && !registered) {
            return;
        }
        // Oluşturulan dünyada ayar, kurulum bitince config'e göre uygulanır
        Compat.setKeepSpawnInMemory(w, !creating && plugin.settings().keepSpawn());
    }
}
