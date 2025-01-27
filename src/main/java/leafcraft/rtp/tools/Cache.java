package leafcraft.rtp.tools;

import leafcraft.rtp.RTP;
import leafcraft.rtp.tasks.DoTeleport;
import leafcraft.rtp.tasks.LoadChunks;
import leafcraft.rtp.tasks.QueueLocation;
import leafcraft.rtp.tasks.SetupTeleport;
import leafcraft.rtp.tools.Configuration.Configs;
import leafcraft.rtp.tools.selection.TeleportRegion;
import leafcraft.rtp.tools.selection.RandomSelectParams;
import leafcraft.rtp.tools.softdepends.PAPIChecker;
import leafcraft.rtp.tools.softdepends.VaultChecker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.*;

public class Cache {
    private final RTP plugin;
    private final Configs configs;

    public ConcurrentHashMap<RandomSelectParams,BukkitTask> queueTimers = new ConcurrentHashMap<>();

    public Cache(RTP plugin, Configs configs) {
        this.plugin = plugin;
        this.configs = configs;
        for(String region : configs.regions.getRegionNames()) {
            String worldName = (String) configs.regions.getRegionSetting(region,"world","world");
            World world = Bukkit.getWorld(worldName);
            if(world == null) world = Bukkit.getWorlds().get(0);
            Map<String,String> map = new HashMap<>();
            map.put("region",region);
            RandomSelectParams key = new RandomSelectParams(world,map,configs);
            TeleportRegion teleportRegion = new TeleportRegion(region,key.params,configs,this);
            permRegions.put(key, teleportRegion);
            teleportRegion.loadFile();
        }

        Double i = 0d;
        Integer period = configs.config.queuePeriod;
        if(period > 0) {
            Double increment = ((period.doubleValue()) / permRegions.size()) * 20;
            for (Map.Entry<RandomSelectParams, TeleportRegion> entry : permRegions.entrySet()) {
                queueTimers.put(entry.getKey(), Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                    double tps = TPS.getTPS();
                    double minTps = (Double) configs.config.getConfigValue("minTPS", 19.0);
                    if (tps < minTps) return;
                    QueueLocation queueLocation = new QueueLocation(entry.getValue(), this);
                    queueLocationTasks.put(queueLocation.idx, queueLocation);
                    queueLocation.runTaskAsynchronously(plugin);
                }, 200 + i.intValue(), period * 20));
                i += increment;
            }
        }
    }

    //if we needed to force load a chunk to prevent unload, undo that on teleport.
    public ConcurrentHashMap<HashableChunk,Long> forceLoadedChunks = new ConcurrentHashMap<>();

    //table of which players are teleporting to what location
    // key: player name
    // value: location they're going to, to be re-added to the queue on cancellation
    public ConcurrentHashMap<UUID,Location> todoTP = new ConcurrentHashMap<>();
    public ConcurrentHashMap<UUID,RandomSelectParams> regionKeys = new ConcurrentHashMap<>();

    //Bukkit task list in case of cancellation
    public ConcurrentHashMap<UUID, SetupTeleport> setupTeleports = new ConcurrentHashMap<>();
    public ConcurrentHashMap<UUID, LoadChunks> loadChunks = new ConcurrentHashMap<>();
    public ConcurrentHashMap<UUID, DoTeleport> doTeleports = new ConcurrentHashMap<>();
    public ConcurrentHashMap<Long, QueueLocation> queueLocationTasks = new ConcurrentHashMap<>();

    //pre-teleport location info for checking distance from command location
    public ConcurrentHashMap<UUID, Location> playerFromLocations = new ConcurrentHashMap<>();

    //info on number of attempts on last rtp command
    public ConcurrentHashMap<Location, Integer> numTeleportAttempts = new ConcurrentHashMap<>();

    //store teleport command cooldown
    public ConcurrentHashMap<UUID,Long> lastTeleportTime = new ConcurrentHashMap<>();

    public ConcurrentHashMap<RandomSelectParams, TeleportRegion> tempRegions = new ConcurrentHashMap<>();
    public ConcurrentHashMap<RandomSelectParams, TeleportRegion> permRegions = new ConcurrentHashMap<>();

    //Collection of chunks in each world and their coordinates, to reuse on reload
    public ConcurrentHashMap<UUID,ConcurrentHashMap<HashableChunk,Long>> allBadChunks = new ConcurrentHashMap<>();

    public void shutdown() {
        for(ConcurrentHashMap.Entry<UUID,SetupTeleport> entry : setupTeleports.entrySet()) {
            entry.getValue().cancel();
        }
        setupTeleports.clear();

        for(ConcurrentHashMap.Entry<UUID,LoadChunks> entry : loadChunks.entrySet()) {
            entry.getValue().cancel();
        }
        loadChunks.clear();

        for(ConcurrentHashMap.Entry<UUID,DoTeleport> entry : doTeleports.entrySet()) {
            entry.getValue().cancel();
        }
        doTeleports.clear();

        for(ConcurrentHashMap.Entry<Long,QueueLocation> entry : queueLocationTasks.entrySet()) {
            entry.getValue().cancel();
        }
        queueLocationTasks.clear();

        for(TeleportRegion region : tempRegions.values()) {
            region.shutdown();
        }

        for(TeleportRegion region : permRegions.values()) {
            region.shutdown();
            region.storeFile();
        }

        for(Map.Entry<HashableChunk,Long> entry : forceLoadedChunks.entrySet()) {
            entry.getKey().getChunk().setForceLoaded(false);
        }
        forceLoadedChunks.clear();

        for(Map.Entry<RandomSelectParams,BukkitTask> entry : queueTimers.entrySet()) {
            entry.getValue().cancel();
            queueTimers.remove(entry);
        }
    }


    public Location getRandomLocation(RandomSelectParams rsParams, boolean urgent, CommandSender sender, Player player) {
        TeleportRegion region;
        Double price = 0d;
        if(permRegions.containsKey(rsParams)) {
            region = permRegions.get(rsParams);
            if(!sender.hasPermission("rtp.free")) {
                price = (Double) configs.regions.getRegionSetting(region.name, "price", 0.0);
            }
        }
        else {
            region = new TeleportRegion("temp", rsParams.params, configs, this);
            if(!sender.hasPermission("rtp.free")) {
                price = configs.config.price;
            }
            tempRegions.put(rsParams,region);
        }

        Economy economy = VaultChecker.getEconomy();
        if(price > 0 && sender instanceof Player && economy!=null) {
            boolean canPay = economy.has((Player)sender,price);
            if(canPay) {
                economy.withdrawPlayer((Player)sender,price);
            }
            else {
                String msg = configs.lang.getLog("notEnoughMoney", price.toString());
                msg = PAPIChecker.fillPlaceholders((Player)sender,msg);
                sender.sendMessage(msg);
                if(!sender.getName().equals(player.getName())) {
                    player.sendMessage(msg);
                }
                return null;
            }
        }

        Location res = region.getLocation(urgent, sender, player);
        return res;
    }

    public void resetRegions() {
        for(TeleportRegion region : tempRegions.values()) {
            region.shutdown();
        }
        tempRegions.clear();

        for(TeleportRegion region : permRegions.values()) {
            region.storeFile();
            region.shutdown();
        }
        permRegions.clear();

        for(Map.Entry<HashableChunk,Long> entry : forceLoadedChunks.entrySet()) {
            entry.getKey().getChunk().setForceLoaded(false);
            entry.getKey().getChunk().unload(true);
        }
        forceLoadedChunks.clear();

        for(String region : configs.regions.getRegionNames()) {
            String worldName = (String) configs.regions.getRegionSetting(region,"world","world");
            World world = Bukkit.getWorld(worldName);
            if(world == null) world = Bukkit.getWorlds().get(0);
            Map<String,String> map = new HashMap<>();
            map.put("region",region);
            RandomSelectParams key = new RandomSelectParams(world,map,configs);
            TeleportRegion teleportRegion = new TeleportRegion(region, key.params,configs,this);
            permRegions.put(key, teleportRegion);
            teleportRegion.loadFile();
        }

        Double i = 0d;
        Integer period = configs.config.queuePeriod;
        if(period > 0) {
            Double increment = ((period.doubleValue()) / permRegions.size()) * 20;
            for (Map.Entry<RandomSelectParams, TeleportRegion> entry : permRegions.entrySet()) {
                queueTimers.put(entry.getKey(), Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                    double tps = TPS.getTPS();
                    double minTps = (Double) configs.config.getConfigValue("minTPS", 19.0);
                    if (tps < minTps) return;
                    QueueLocation queueLocation = new QueueLocation(entry.getValue(), this);
                    queueLocationTasks.put(queueLocation.idx, queueLocation);
                    queueLocation.runTaskAsynchronously(plugin);
                }, 40 + i.intValue(), period * 20));
                i += increment;
            }
        }
    }
}
