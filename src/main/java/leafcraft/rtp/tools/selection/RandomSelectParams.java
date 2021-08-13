package leafcraft.rtp.tools.selection;

import leafcraft.rtp.tools.Configuration.Configs;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;

public class RandomSelectParams {
    public TeleportRegion.Shapes shape;
    public UUID worldID;
    public int r, cr, cx, cz, minY, maxY;
    public boolean requireSkyLight,worldBorderOverride;

    public boolean modifiedRegionSpecs;
    public Map<String,String> params;

    public RandomSelectParams(World world, Map<String,String> params, Configs configs) {
        modifiedRegionSpecs = params.size()>0;
        if(params.size()<=2) { // where region specs are unmodified
            boolean hasWorld = params.containsKey("world");
            boolean hasPlayer = params.containsKey("player");
            if(params.size() == 1 && (hasWorld || hasPlayer)) {
                modifiedRegionSpecs = false;
            }
            else if(hasWorld && hasPlayer) {
                modifiedRegionSpecs = false;
            }
        }

        String worldName = params.getOrDefault("world",world.getName());
        worldName = configs.worlds.worldPlaceholder2Name(worldName);
        if(!configs.worlds.checkWorldExists(worldName)) worldName = world.getName();
        world = Bukkit.getWorld(worldName);

        String defaultRegion = (String)configs.worlds.getWorldSetting(world.getName(),"region","default");
        String regionName = params.getOrDefault("region",defaultRegion);

        worldName = (String)configs.regions.getRegionSetting(regionName,"world",world.getName());
        if(configs.worlds.checkWorldExists(worldName)) {
            world = Bukkit.getWorld(worldName);
        }
        else {
            worldName = world.getName();
        }

        this.params = params;

        worldBorderOverride = Boolean.getBoolean(this.params.getOrDefault("worldBorderOverride","false"));
        if(worldBorderOverride) {
            this.params.put("shape", "SQUARE");
            this.params.put("radius", String.valueOf((int)world.getWorldBorder().getSize()));
            this.params.put("centerX", String.valueOf(world.getWorldBorder().getCenter().getBlockX()));
            this.params.put("centerZ", String.valueOf(world.getWorldBorder().getCenter().getBlockZ()));
        }

        //ugh string parsing, but at least it's short and clean
        // fills in any missing values
        this.params.put("world",worldName);
        this.params.putIfAbsent("shape",(String)configs.regions.getRegionSetting(regionName,"shape","CIRCLE"));
        this.params.putIfAbsent("radius", (configs.regions.getRegionSetting(regionName,"radius",4096)).toString());
        this.params.putIfAbsent("centerRadius", (configs.regions.getRegionSetting(regionName,"centerRadius",1024)).toString());
        this.params.putIfAbsent("centerX", (configs.regions.getRegionSetting(regionName,"centerX",0)).toString());
        this.params.putIfAbsent("centerZ", (configs.regions.getRegionSetting(regionName,"centerZ",0)).toString());
        this.params.putIfAbsent("weight", (configs.regions.getRegionSetting(regionName,"weight",1.0)).toString());
        this.params.putIfAbsent("minY", (configs.regions.getRegionSetting(regionName,"minY",0)).toString());
        this.params.putIfAbsent("maxY", (configs.regions.getRegionSetting(regionName,"maxY",128)).toString());
        this.params.putIfAbsent("requireSkyLight", (configs.regions.getRegionSetting(regionName,"requireSkyLight",true)).toString());
        this.params.putIfAbsent("worldBorderOverride", (configs.regions.getRegionSetting(regionName,"worldBorderOverride",false)).toString());

        worldID = world.getUID();
        shape = TeleportRegion.Shapes.valueOf(this.params.getOrDefault("shape","CIRCLE"));
        r = Integer.valueOf(this.params.get("radius"));
        cr = Integer.valueOf(this.params.get("centerRadius"));
        cx = Integer.valueOf(this.params.get("centerX"));
        cz = Integer.valueOf(this.params.get("centerZ"));
        minY = Integer.valueOf(this.params.get("minY"));
        maxY = Integer.valueOf(this.params.get("maxY"));
        requireSkyLight = Boolean.valueOf(this.params.get("requireSkyLight"));
        worldBorderOverride = Boolean.valueOf(this.params.get("worldBorderOverride"));

//        System.out.println("creating RandomSelectParams with params:");
//        for(Map.Entry<String,String> entry : this.params.entrySet()) {
//            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
//        }
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null) return false;
        if(o instanceof RandomSelectParams) {
            RandomSelectParams that = (RandomSelectParams) o;
            if(this.worldID != that.worldID) return false;
            if(this.shape != that.shape) return false;
            if(this.r != that.r) return false;
            if(this.cr != that.cr) return false;
            if(this.cx != that.cx) return false;
            if(this.cz != that.cz) return false;
            if(this.maxY != that.maxY) return false;
            if(this.minY != that.minY) return false;
            if(this.requireSkyLight != that.requireSkyLight) return false;
            if(this.worldBorderOverride != that.worldBorderOverride) return false;
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int res = 0;
        res ^= worldID.hashCode();
        res ^= shape.hashCode();
        res ^= Integer.hashCode(r);
        res ^= Integer.hashCode(cr);
        res ^= Integer.hashCode(cx);
        res ^= Integer.hashCode(cz);
        res ^= Integer.hashCode(minY);
        res ^= Integer.hashCode(maxY);
        res ^= Boolean.hashCode(requireSkyLight);
        res ^= Boolean.hashCode(worldBorderOverride);
        return res;
    }
}