package net.doodcraft.cozmyc.vinemanipulation.display;

import com.projectkorra.projectkorra.event.AbilityEndEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

public class DisplayManagerListener implements Listener {

    private final DisplayManager displayManager;

    public DisplayManagerListener(DisplayManager displayManager) {
        this.displayManager = displayManager;
    }

    @EventHandler
    public void onAbilityEnd(AbilityEndEvent event) {
        displayManager.removeDisplaysForAbility(event.getAbility());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("ProjectKorra")) {
            DisplayManager.shutdown();
        }
    }
}