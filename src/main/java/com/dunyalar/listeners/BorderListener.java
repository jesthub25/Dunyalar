package com.dunyalar.listeners;

import com.dunyalar.Dunyalar;
import com.dunyalar.data.WorldData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Oyuncuların dünya sınırını geçmesini engeller.
 * Kare dünyalarda vanilla mavi sınır zaten fiziksel duvardır; dikdörtgen dünyalarda (ör. 800x200)
 * kısa kenarı bu dinleyici korur. Ayrıca ender incisi, koro meyvesi, elytra, araç ve
 * başka eklentilerin ışınlamalarıyla sınır dışına çıkmayı engeller.
 */
public final class BorderListener implements Listener {

    private static final long NOTIFY_COOLDOWN_MS = 1500L;

    private final Dunyalar plugin;
    private final Map<UUID, Long> lastNotify = new HashMap<UUID, Long>();

    public BorderListener(Dunyalar plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        Location from = e.getFrom();
        World tw = to.getWorld();
        // Performans: sadece blok değiştiğinde kontrol et
        if (tw == from.getWorld() && from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        WorldData d = plugin.registry().get(tw);
        if (d == null || d.isInside(to)) {
            return;
        }
        if (tw == from.getWorld() && d.isInside(from)) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
        } else {
            e.setTo(plugin.worlds().clampInside(to, d));
        }
        notifyBorder(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        WorldData d = plugin.registry().get(to.getWorld());
        if (d == null || d.isInside(to)) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (plugin.settings().blockThrowables()
                && (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT)) {
            e.setCancelled(true);
        } else {
            e.setTo(plugin.worlds().clampInside(to, d));
        }
        notifyBorder(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleMove(VehicleMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        WorldData d = plugin.registry().get(to.getWorld());
        if (d == null || d.isInside(to) || !d.isInside(from)) {
            return;
        }
        Vehicle v = e.getVehicle();
        List<Entity> passengers = new ArrayList<Entity>(v.getPassengers());
        v.eject();
        v.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        v.teleport(from);
        for (Entity p : passengers) {
            if (p instanceof Player) {
                notifyBorder((Player) p);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        lastNotify.remove(e.getPlayer().getUniqueId());
    }

    private void notifyBorder(Player p) {
        if (!plugin.settings().actionBar()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastNotify.get(p.getUniqueId());
        if (last != null && now - last < NOTIFY_COOLDOWN_MS) {
            return;
        }
        lastNotify.put(p.getUniqueId(), now);
        String msg = plugin.settings().plain("sinir-uyari");
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
        } catch (Throwable t) {
            p.sendMessage(msg);
        }
    }
}
