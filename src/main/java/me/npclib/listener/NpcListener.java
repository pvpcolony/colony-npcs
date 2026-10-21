package me.npclib.listener;

import me.npclib.NpcLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Keeps the client side NPC entities in sync with the players of the server.
 */
public final class NpcListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // Give the client a moment to finish the join sequence before entity packets arrive.
        Bukkit.getScheduler().runTaskLater(NpcLib.getPlugin(), new Runnable() {
            @Override
            public void run() {
                NpcLib.showAll(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        NpcLib.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        NpcLib.showAll(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        // The client clears its world when respawning, so every NPC has to be sent again.
        Bukkit.getScheduler().runTaskLater(NpcLib.getPlugin(), new Runnable() {
            @Override
            public void run() {
                NpcLib.respawnAll(player);
            }
        }, 2L);
    }
}
