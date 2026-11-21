package com.dunyalar.listeners;

import com.dunyalar.Dunyalar;
import com.dunyalar.data.WorldData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Eklentinin bir dünyasında ölen oyuncuyu o dünyanın spawn noktasında (yoksa güvenli merkezde) doğurur.
 * Ölüm dünyası ölüm anında kaydedilir; böylece her sürümde doğru dünya bilinir.
 */
public final class RespawnListener implements Listener {

    private final Dunyalar plugin;
    private final Map<UUID, String> deathWorld = new HashMap<UUID, String>();

    public RespawnListener(Dunyalar plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        deathWorld.put(p.getUniqueId(), p.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        String worldName = deathWorld.remove(p.getUniqueId());
        if (!plugin.settings().respawnEnabled()) {
            return;
        }
        if (worldName == null) {
            worldName = p.getWorld().getName();
        }
        WorldData d = plugin.registry().get(worldName);
        if (d == null) {
            return;
        }
        if (plugin.settings().bedPriority() && (e.isBedSpawn() || isAnchor(e))) {
            return;
        }
        World w = Bukkit.getWorld(d.name());
        if (w == null) {
            return;
        }
        Location loc = plugin.worlds().respawnLocation(w, d);
        if (loc != null) {
            e.setRespawnLocation(loc);
        }
    }

    private static boolean isAnchor(PlayerRespawnEvent e) {
        try {
            return e.isAnchorSpawn();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Ölüp yeniden doğmadan çıkan oyuncu geri girdiğinde ölüm ekranından doğar; kaydı bu yüzden tutulur.
     * Sadece sunucuda olmayan ve hâlâ ölü olmayan oyuncuların kayıtları temizlenir.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!p.isDead()) {
            deathWorld.remove(p.getUniqueId());
        }
    }
}
