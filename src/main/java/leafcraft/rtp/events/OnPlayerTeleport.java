package leafcraft.rtp.events;

import leafcraft.rtp.RTP;
import leafcraft.rtp.tasks.CancellationCleanup;
import leafcraft.rtp.tools.Cache;
import leafcraft.rtp.tools.Config;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class OnPlayerTeleport implements Listener {
    private RTP plugin;
    private Config config;
    private Cache cache;

    public OnPlayerTeleport(RTP plugin, Config config, Cache cache) {
        this.config = config;
        this.cache = cache;
    }

    @EventHandler
    public void OnPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if(!this.cache.todoTP.containsKey(player.getName())) return;
        Location location = this.cache.playerFromLocations.getOrDefault(player.getName(),player.getLocation());
        if(location.distance(event.getTo()) < (Integer)this.config.getConfigValue("cancelDistance",2)) return;

        if(cache.loadChunks.containsKey(player.getName())) {
            cache.loadChunks.get(player.getName()).cancel();
            cache.loadChunks.remove(player.getName());
        }
        if(cache.doTeleports.containsKey(player.getName())) {
            cache.doTeleports.get(player.getName()).cancel();
            cache.doTeleports.remove(player.getName());
        }

        Location randomLocation = cache.todoTP.get(player.getName());
        if(cache.locAssChunks.containsKey(randomLocation)) {
            for(CompletableFuture<Chunk> cfChunk : cache.locAssChunks.get(randomLocation)) {
                cfChunk.cancel(true);
            }
        }
        cache.todoTP.remove(player.getName());
        cache.playerFromLocations.remove(player.getName());

        player.sendMessage(this.config.getLog("teleportCancel"));
    }
}