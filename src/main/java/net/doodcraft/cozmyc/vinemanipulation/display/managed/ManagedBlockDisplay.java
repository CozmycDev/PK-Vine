package net.doodcraft.cozmyc.vinemanipulation.display.managed;

import com.projectkorra.projectkorra.ability.Ability;
import net.doodcraft.cozmyc.vinemanipulation.display.DisplayManager;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;

import java.util.UUID;

public class ManagedBlockDisplay extends ManagedDisplay {

    public ManagedBlockDisplay(UUID entityUUID, Ability owner, DisplayManager displayManager) {
        super(entityUUID, owner, displayManager);
    }

    /** Gets the underlying Bukkit BlockDisplay entity, if valid. */
    public BlockDisplay getBukkitEntity() {
        return displayManager.getValidBukkitEntity(entityUUID, BlockDisplay.class);
    }

    /**
     * Sets the BlockData for this display. Must be called from the main thread.
     * @param blockData The BlockData to display.
     */
    public void setBlockData(BlockData blockData) {
        displayManager.verifyMainThread();
        BlockDisplay entity = getBukkitEntity();
        if (entity != null) {
            entity.setBlock(blockData);
        }
    }

    /**
     * Gets the current BlockData of this display. Must be called from the main thread.
     * @return The current BlockData, or null if the entity is invalid.
     */
    public BlockData getBlockData() {
        displayManager.verifyMainThread();
        BlockDisplay entity = getBukkitEntity();
        return (entity != null) ? entity.getBlock() : null;
    }
}