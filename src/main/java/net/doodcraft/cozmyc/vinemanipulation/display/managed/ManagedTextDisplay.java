package net.doodcraft.cozmyc.vinemanipulation.display.managed;

import com.projectkorra.projectkorra.ability.Ability;
import net.doodcraft.cozmyc.vinemanipulation.display.DisplayManager;
import org.bukkit.Color;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.TextDisplay.TextAlignment;

import java.util.UUID;

public class ManagedTextDisplay extends ManagedDisplay {

    public ManagedTextDisplay(UUID entityUUID, Ability owner, DisplayManager displayManager) {
        super(entityUUID, owner, displayManager);
    }

    /** Gets the underlying Bukkit TextDisplay entity, if valid. */
    public TextDisplay getBukkitEntity() {
        return displayManager.getValidBukkitEntity(entityUUID, TextDisplay.class);
    }

    public void setText(String text) {
        displayManager.verifyMainThread();
        TextDisplay entity = getBukkitEntity();
        if (entity != null) {
            entity.setText(text);
        }
    }

    public String getText() {
        displayManager.verifyMainThread();
        TextDisplay entity = getBukkitEntity();
        return (entity != null) ? entity.getText() : null;
    }

    public void setLineWidth(int width) {
        displayManager.verifyMainThread();
        TextDisplay entity = getBukkitEntity();
        if (entity != null) {
            entity.setLineWidth(width);
        }
    }

    public void setAlignment(TextAlignment alignment) {
        displayManager.verifyMainThread();
        TextDisplay entity = getBukkitEntity();
        if (entity != null) {
            entity.setAlignment(alignment);
        }
    }

    public void setBackgroundColor(Color color) {
        displayManager.verifyMainThread();
        TextDisplay entity = getBukkitEntity();
        if (entity != null) {
            entity.setBackgroundColor(color);
        }
    }

    public void setTextOpacity(byte opacity) {
        displayManager.verifyMainThread();
        TextDisplay entity = getBukkitEntity();
        if (entity != null) {
            entity.setTextOpacity(opacity);
        }
    }
}
