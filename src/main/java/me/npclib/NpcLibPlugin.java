package me.npclib;

import me.npclib.command.NpcCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bootstrap plugin. When the library is shaded into another plugin you do not need this
 * class, just call {@link NpcLib#init(org.bukkit.plugin.Plugin)} yourself.
 */
public final class NpcLibPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        NpcLib.init(this);

        PluginCommand command = getCommand("npclib");
        if (command != null) {
            NpcCommand executor = new NpcCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        NpcLib.shutdown();
    }
}
