package net.doodcraft.cozmyc.vinemanipulation.display.animation;

import com.projectkorra.projectkorra.ability.Ability;
import net.doodcraft.cozmyc.vinemanipulation.display.DisplayManager;
import net.doodcraft.cozmyc.vinemanipulation.display.managed.ManagedDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class DisplayAnimation {

    protected final Ability owner;
    protected final DisplayManager displayManager;
    protected List<ManagedDisplay> managedDisplays;
    protected boolean running;
    protected long startTime;

    /**
     * Creates a new DisplayAnimation.
     * @param owner The Ability instance owning this animation.
     * @param displayManager The DisplayManager instance.
     */
    protected DisplayAnimation(Ability owner, DisplayManager displayManager) {
        this.owner = owner;
        this.displayManager = displayManager;
        this.managedDisplays = new ArrayList<>(); // Or allow passing initial list?
        this.running = false;
    }

    /**
     * The core update logic for the animation. Called periodically (e.g., every server tick
     * from the owning ability's progress() method).
     * Implementations should calculate the state for the current step and update
     * the properties of the managedDisplays.
     *
     * @param deltaTime The time elapsed (in milliseconds) since the last tick, can be used for frame-independent movement.
     */
    public abstract void tick(long deltaTime);

    /**
     * Starts the animation. Sets the running state to true and performs
     * any necessary initialization (like creating initial displays).
     */
    public void start() {
        if (!this.running) {
            this.running = true;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * Stops the animation.
     * @param removeDisplays If true, all displays currently managed by this animation will be removed via the DisplayManager.
     */
    public void stop(boolean removeDisplays) {
        if (this.running) {
            this.running = false;
            if (removeDisplays) {
                List<ManagedDisplay> displaysToRemove = new ArrayList<>(this.managedDisplays);
                this.managedDisplays.clear();
                for (ManagedDisplay display : displaysToRemove) {
                    displayManager.removeDisplay(display);
                }
            }
        }
    }

    /** @return true if the animation is currently running, false otherwise. */
    public boolean isRunning() {
        return running;
    }

    /**
     * Adds a ManagedDisplay to be controlled by this animation.
     * Note: Consider thread safety if displays can be added asynchronously while ticking.
     * @param display The display to add.
     */
    public void addDisplay(ManagedDisplay display) {
        if (display != null && !this.managedDisplays.contains(display)) {
            this.managedDisplays.add(display);
        }
    }

    /**
     * Removes a ManagedDisplay from this animation's control.
     * Note: This does NOT remove the display entity itself unless stop(true) is called later
     * or the display is removed externally.
     * @param display The display to remove control of.
     */
    public void removeDisplay(ManagedDisplay display) {
        this.managedDisplays.remove(display);
    }

    /**
     * Gets an unmodifiable list of the displays currently managed by this animation.
     * @return An unmodifiable list of ManagedDisplays.
     */
    public List<ManagedDisplay> getManagedDisplays() {
        return Collections.unmodifiableList(managedDisplays);
    }
}
