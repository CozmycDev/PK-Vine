package net.doodcraft.cozmyc.vinemanipulation.display.managed;

import com.projectkorra.projectkorra.ability.Ability;
import net.doodcraft.cozmyc.vinemanipulation.display.DisplayManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public abstract class ManagedDisplay {

    protected final UUID entityUUID;
    protected final Ability owner;
    protected final DisplayManager displayManager;

    protected ManagedDisplay(UUID entityUUID, Ability owner, DisplayManager displayManager) {
        this.entityUUID = entityUUID;
        this.owner = owner;
        this.displayManager = displayManager;
    }

    /** @return The UUID of the underlying Bukkit Display entity. */
    public UUID getEntityId() {
        return entityUUID;
    }

    /** @return The UUID of the owning ProjectKorra Ability instance. */
    public Ability getOwner() {
        return owner;
    }

    /** @return The DisplayManager instance used by this display. */
    public DisplayManager getDisplayManager() {
        return displayManager;
    }

    /**
     * Checks if the underlying Bukkit entity is still valid (exists and not dead).
     * Safe to call asynchronously.
     * @return true if the entity is valid, false otherwise.
     */
    public boolean isValid() {
        Entity entity = Bukkit.getEntity(entityUUID);
        return entity instanceof Display && entity.isValid();
    }

    /**
     * Requests the removal of this display entity via the DisplayManager.
     * Safe to call asynchronously; actual removal happens on the main thread.
     */
    public void remove() {
        displayManager.removeDisplay(this);
    }

    /**
     * Gets the underlying Bukkit Display entity.
     * Returns null if the entity is invalid or not found.
     * Warning: Direct modification should only occur on the main thread.
     * @return The Bukkit Display entity or null.
     */
    public Display getBukkitEntity() {
        return displayManager.getValidBukkitEntity(entityUUID, Display.class);
    }

    public void teleport(Location location) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            entity.teleport(location);
        }
    }

    public void setTransformation(Transformation transformation) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            entity.setTransformation(transformation);
        }
    }

    public Transformation getTransformation() {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        return (entity != null) ? entity.getTransformation() : null;
    }

    public void setScale(Vector3f scale) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            Transformation current = entity.getTransformation();
            entity.setTransformation(new Transformation(
                    current.getTranslation(),
                    current.getLeftRotation(),
                    scale, // New scale
                    current.getRightRotation()
            ));
        }
    }

    public void setLeftRotation(Quaternionf rotation) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            Transformation current = entity.getTransformation();
            entity.setTransformation(new Transformation(
                    current.getTranslation(),
                    rotation,
                    current.getScale(),
                    current.getRightRotation()
            ));
        }
    }

    public void setRightRotation(Quaternionf rotation) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            Transformation current = entity.getTransformation();
            entity.setTransformation(new Transformation(
                    current.getTranslation(),
                    current.getLeftRotation(),
                    current.getScale(),
                    rotation
            ));
        }
    }


    public void setInterpolationDelay(int ticks) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            entity.setInterpolationDelay(ticks);
        }
    }

    public void setInterpolationDuration(int ticks) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            entity.setInterpolationDuration(ticks);
        }
    }

    public void setViewRange(float range) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            entity.setViewRange(range);
        }
    }

    public void setBrightness(Display.Brightness brightness) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            entity.setBrightness(brightness);
        }
    }

    public void setBillboard(Display.Billboard billboard) {
        displayManager.verifyMainThread();
        Display entity = getBukkitEntity();
        if (entity != null) {
            entity.setBillboard(billboard);
        }
    }

    // add more wrappers for other methods, like width/height, glowing/color, etc
}