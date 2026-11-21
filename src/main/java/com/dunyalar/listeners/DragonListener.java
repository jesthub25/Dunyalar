package com.dunyalar.listeners;

import com.dunyalar.Dunyalar;
import com.dunyalar.data.WorldData;
import com.dunyalar.data.WorldTheme;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/** Ejderha savaşı kapalı (config) End dünyalarında Ender Ejderhası doğmasını engeller (yedek koruma). */
public final class DragonListener implements Listener {

    private final Dunyalar plugin;

    public DragonListener(Dunyalar plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) {
        if (e.getEntityType() != EntityType.ENDER_DRAGON) {
            return;
        }
        WorldData d = plugin.registry().get(e.getLocation().getWorld());
        if (d != null && d.theme() == WorldTheme.END && !d.dragon()) {
            e.setCancelled(true);
        }
    }
}
