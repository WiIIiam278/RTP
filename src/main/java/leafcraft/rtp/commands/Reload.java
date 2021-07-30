package leafcraft.rtp.commands;

import leafcraft.rtp.tools.Config;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Reload implements CommandExecutor {
    private Config config;

    public Reload(Config config) {
        this.config = config;
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(!sender.hasPermission("rtp.reload")) return false;

        String str = this.config.getLog("reloading");
        Bukkit.getConsoleSender().sendMessage(str);
        if(sender instanceof Player) {
            if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) str = PlaceholderAPI.setPlaceholders((Player)sender, str);
            sender.sendMessage(str);
        }

        this.config.refreshConfigs();

        str = this.config.getLog("reloaded");
        Bukkit.getConsoleSender().sendMessage(str);
        if(sender instanceof Player) {
            if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) str = PlaceholderAPI.setPlaceholders((Player)sender, str);
            sender.sendMessage(str);
        }
        return true;
    }
}
