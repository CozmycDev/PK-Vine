package net.doodcraft.cozmyc.vinemanipulation.display.managed;

import com.projectkorra.projectkorra.ability.Ability;
import net.doodcraft.cozmyc.vinemanipulation.display.DisplayManager;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ManagedItemDisplay extends ManagedDisplay {

    public ManagedItemDisplay(UUID entityUUID, Ability owner, DisplayManager displayManager) {
        super(entityUUID, owner, displayManager);
    }

    public ItemDisplay getBukkitEntity() {
        return displayManager.getValidBukkitEntity(entityUUID, ItemDisplay.class);
    }

    public void setItemStack(ItemStack itemStack) {
        displayManager.verifyMainThread();
        ItemDisplay entity = getBukkitEntity();
        if (entity != null) {
            entity.setItemStack(itemStack);
        }
    }

    public ItemStack getItemStack() {
        displayManager.verifyMainThread();
        ItemDisplay entity = getBukkitEntity();
        return (entity != null) ? entity.getItemStack() : null;
    }

    public void setItemDisplayTransform(ItemDisplayTransform transform) {
        displayManager.verifyMainThread();
        ItemDisplay entity = getBukkitEntity();
        if (entity != null) {
            entity.setItemDisplayTransform(transform);
        }
    }

    public ItemDisplayTransform getItemDisplayTransform() {
        displayManager.verifyMainThread();
        ItemDisplay entity = getBukkitEntity();
        return (entity != null) ? entity.getItemDisplayTransform() : null;
    }
}