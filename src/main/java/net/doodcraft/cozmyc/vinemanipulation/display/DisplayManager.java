package net.doodcraft.cozmyc.vinemanipulation.display;

import com.google.common.collect.Sets;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.Ability;
import net.doodcraft.cozmyc.vinemanipulation.display.managed.ManagedBlockDisplay;
import net.doodcraft.cozmyc.vinemanipulation.display.managed.ManagedDisplay;
import net.doodcraft.cozmyc.vinemanipulation.display.managed.ManagedItemDisplay;
import net.doodcraft.cozmyc.vinemanipulation.display.managed.ManagedTextDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class DisplayManager {

    private static DisplayManager instance;

    private final Plugin plugin;
    private final Map<UUID, ManagedDisplay> managedDisplays = new ConcurrentHashMap<>();
    private final Map<Ability, Set<UUID>> abilityOwnership = new ConcurrentHashMap<>();

    private DisplayManager(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new DisplayManagerListener(this), plugin);
        ProjectKorra.log.info("DisplayManager initialized.");
    }

    /**
     * Initializes the DisplayManager singleton instance.
     * Should be called during ProjectKorra's onEnable.
     * @param plugin The ProjectKorra plugin instance.
     */
    public static void initialize(Plugin plugin) {
        if (instance == null) {
            instance = new DisplayManager(plugin);
        }
    }

    /**
     * Shuts down the DisplayManager, removing all managed displays.
     * Should be called during ProjectKorra's onDisable.
     */
    public static void shutdown() {
        if (instance != null) {
            instance.removeAllDisplays();
            instance = null;
            ProjectKorra.log.info("DisplayManager shut down.");
        }
    }

    /**
     * Gets the singleton instance of the DisplayManager.
     * @return The DisplayManager instance.
     * @throws IllegalStateException if the manager has not been initialized.
     */
    public static DisplayManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DisplayManager has not been initialized! Call initialize() first.");
        }
        return instance;
    }

    /**
     * Creates and registers a managed BlockDisplay.
     * Must be called from the main server thread (for now.)
     * @param owner The ability instance creating this display.
     * @param location The initial location to spawn the display.
     * @param blockData The BlockData the display should show.
     * @return The ManagedBlockDisplay wrapper, or null if spawning failed.
     */
    public ManagedBlockDisplay createBlockDisplay(Ability owner, Location location, BlockData blockData) {
        verifyMainThread();
        try {
            BlockDisplay entity = location.getWorld().spawn(location, BlockDisplay.class, (display) -> {
                display.setBlock(blockData);
                display.setPersistent(false);
            });

            ManagedBlockDisplay wrapper = new ManagedBlockDisplay(entity.getUniqueId(), owner, this);
            registerDisplay(wrapper, owner);
            return wrapper;
        } catch (Exception e) {
            ProjectKorra.log.log(Level.SEVERE, "Failed to spawn BlockDisplay for ability: " + owner.getName(), e);
            return null;
        }
    }

    /**
     * Creates and registers a managed ItemDisplay.
     * Must be called from the main server thread.
     * @param owner The ability instance creating this display.
     * @param location The initial location to spawn the display.
     * @param itemStack The ItemStack the display should show.
     * @return The ManagedItemDisplay wrapper, or null if spawning failed.
     */
    public ManagedItemDisplay createItemDisplay(Ability owner, Location location, ItemStack itemStack) {
        verifyMainThread();
        try {
            ItemDisplay entity = location.getWorld().spawn(location, ItemDisplay.class, (display) -> {
                display.setItemStack(itemStack);
                display.setPersistent(false);
            });

            ManagedItemDisplay wrapper = new ManagedItemDisplay(entity.getUniqueId(), owner, this);
            registerDisplay(wrapper, owner);
            return wrapper;
        } catch (Exception e) {
            ProjectKorra.log.log(Level.SEVERE, "Failed to spawn ItemDisplay for ability: " + owner.getName(), e);
            return null;
        }
    }

    /**
     * Creates and registers a managed TextDisplay.
     * Must be called from the main server thread.
     * @param owner The ability instance creating this display.
     * @param location The initial location to spawn the display.
     * @param text The initial text content (can include component styling).
     * @return The ManagedTextDisplay wrapper, or null if spawning failed.
     */
    public ManagedTextDisplay createTextDisplay(Ability owner, Location location, String text) {
        verifyMainThread();
        try {
            TextDisplay entity = location.getWorld().spawn(location, TextDisplay.class, (display) -> {
                display.setText(text);
                display.setPersistent(false);

                display.setBackgroundColor(Color.WHITE);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
            });

            ManagedTextDisplay wrapper = new ManagedTextDisplay(entity.getUniqueId(), owner, this);
            registerDisplay(wrapper, owner);
            return wrapper;
        } catch (Exception e) {
            ProjectKorra.log.log(Level.SEVERE, "Failed to spawn TextDisplay for ability: " + owner.getName(), e);
            return null;
        }
    }

    /**
     * Removes a specific managed display.
     * Can be called asynchronously, removal happens on the main thread.
     * @param display The ManagedDisplay wrapper to remove.
     */
    public void removeDisplay(ManagedDisplay display) {
        if (display == null) return;
        removeDisplay(display.getEntityId());
    }

    /**
     * Removes a managed display by its entity UUID.
     * Can be called asynchronously, removal happens on the main thread.
     * @param entityId The UUID of the Bukkit Display entity.
     */
    public void removeDisplay(UUID entityId) {
        ManagedDisplay wrapper = managedDisplays.remove(entityId);
        if (wrapper != null) {
            Ability owner = wrapper.getOwner();
            abilityOwnership.computeIfPresent(owner, (key, ownedSet) -> {
                ownedSet.remove(entityId);
                return ownedSet.isEmpty() ? null : ownedSet;
            });
            despawnEntity(entityId);
        } else {
            despawnEntity(entityId);
        }
    }

    /**
     * Removes all displays owned by a specific ability instance.
     * Typically called by the DisplayManagerListener on AbilityRemoveEvent.
     * @param abilityInstanceUUID The UUID of the ability instance.
     */
    public void removeDisplaysForAbility(Ability abilityInstanceUUID) {
        Set<UUID> ownedEntityIds = abilityOwnership.remove(abilityInstanceUUID);
        if (ownedEntityIds != null) {
            ProjectKorra.log.fine("Removing " + ownedEntityIds.size() + " displays for ability instance: " + abilityInstanceUUID);
            for (UUID entityId : ownedEntityIds) {
                managedDisplays.remove(entityId);
                despawnEntity(entityId);
            }
        }
    }

    /**
     * Removes all managed displays.
     */
    void removeAllDisplays() {
        ProjectKorra.log.info("Removing all managed displays (" + managedDisplays.size() + ")");
        Set<UUID> allEntityIds = Sets.newHashSet(managedDisplays.keySet());
        managedDisplays.clear();
        abilityOwnership.clear();
        for (UUID entityId : allEntityIds) {
            despawnEntity(entityId);
        }
    }

    private void registerDisplay(ManagedDisplay display, Ability ability) {
        managedDisplays.put(display.getEntityId(), display);
        abilityOwnership.computeIfAbsent(ability, k -> Sets.newConcurrentHashSet()).add(display.getEntityId());
        ProjectKorra.log.finest("Registered display " + display.getEntityId() + " for ability " + ability);
    }

    private void despawnEntity(UUID entityId) {
        if (Bukkit.isPrimaryThread()) {
            performDespawn(entityId);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    performDespawn(entityId);
                }
            }.runTask(plugin);
        }
    }

    private void performDespawn(UUID entityId) {
        verifyMainThread();
        Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof Display && entity.isValid()) {
            entity.remove();
            ProjectKorra.log.finest("Despawned display entity: " + entityId);
        }
    }

    /**
     * Helper to get the underlying Bukkit entity safely.
     * Checks if the entity exists, is valid, and is of the expected type.
     * MUST be called from the main server thread if used for modification.
     * @param entityId The UUID of the entity.
     * @param expectedType The expected class of the Display entity (e.g., BlockDisplay.class).
     * @return The entity cast to the expected type, or null if invalid/not found/wrong type.
     */
    public <T extends Display> T getValidBukkitEntity(UUID entityId, Class<T> expectedType) {
        verifyMainThread();
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && entity.isValid() && expectedType.isInstance(entity)) {
            return expectedType.cast(entity);
        }
        return null;
    }

    /**
     * Utility to ensure critical operations run on the main thread.
     * Throws an IllegalStateException if called from an async thread.
     */
    public void verifyMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("This operation must be performed on the main server thread.");
        }
    }
}