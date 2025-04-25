package net.doodcraft.cozmyc.vinemanipulation.display.animation;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.Ability;
import net.doodcraft.cozmyc.vinemanipulation.display.DisplayManager;
import net.doodcraft.cozmyc.vinemanipulation.display.animation.util.FabrikChain;
import net.doodcraft.cozmyc.vinemanipulation.display.managed.ManagedBlockDisplay;
import net.doodcraft.cozmyc.vinemanipulation.display.managed.ManagedDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FabrikChainAnimation extends DisplayAnimation {

    private FabrikChain fabrikChain;
    private List<Vector> segmentVelocities;
    private final Vector3f cachedScaleVector;
    private final Vector gravityVector = new Vector(0, -1, 0);
    private final Vector tempVector = new Vector();
    private final Location sourceLocation;
    private Location currentTargetLocation;
    private final BlockData segmentData;
    private List<ManagedBlockDisplay> blockDisplays;
    private Location previousTargetLocation;

    private final float displayScale;
    private final int maxSegments;
    private final int minSegments;
    private final double desiredSpacing;
    private final double maxChainLength;
    private final double extensionSpeed;
    private boolean isChainHidden = false;
    public boolean snapped = false;
    private double slackFactor;
    private int dynamicSegmentCount;
    private double gravityStrength;
    public double stiffness;
    private double dampingFactor;
    private double currentChainLength;
    private boolean isFullyExtended;
    private double targetSmoothing = 0.85;
    private int iterations;
    private double tolerance;

    /**
     * Creates a new chain animation with physical constraints.
     *
     * @param owner                     The ability owner
     * @param displayManager            The display manager
     * @param sourceLocation            The root location
     * @param initialTargetLocation     The initial target location
     * @param segmentData               The block data for chain segments
     * @param displayScale              The scale of each block display
     * @param maxSegments               Maximum number of segments
     * @param minSegments               Minimum number of segments
     * @param desiredSpacing            Desired spacing between segments
     * @param maxChainLength            Maximum chain length
     * @param extensionSpeed            Extension speed in blocks per second
     * @param fabrikIterations          Number of FABRIK solver iterations
     * @param fabrikTolerance           FABRIK solver tolerance
     * @param gravityStrength           Gravity effect strength (0 for no gravity)
     * @param maxAngleConstraintDegrees Maximum angle constraint in degrees
     * @param stiffness                 Chain stiffness factor (0-1)
     */
    public FabrikChainAnimation(
            Ability owner,
            DisplayManager displayManager,
            Location sourceLocation,
            Location initialTargetLocation,
            BlockData segmentData,
            float displayScale,
            int maxSegments,
            int minSegments,
            double desiredSpacing,
            double maxChainLength,
            double extensionSpeed,
            int fabrikIterations,
            double fabrikTolerance,
            double gravityStrength,
            double maxAngleConstraintDegrees, // needs reimplementing, determined max angle rotation for segments
            double stiffness,
            double slackFactor
    ) {
        super(owner, displayManager);

        this.sourceLocation = sourceLocation.clone();
        this.currentTargetLocation = initialTargetLocation.clone();
        this.segmentData = segmentData;
        this.displayScale = displayScale;
        this.maxSegments = Math.max(1, maxSegments);
        this.minSegments = Math.max(1, minSegments);
        this.desiredSpacing = desiredSpacing;
        this.maxChainLength = Math.max(1.0, maxChainLength);
        this.extensionSpeed = Math.max(0.1, extensionSpeed);
        this.gravityStrength = Math.max(0, gravityStrength);
        this.stiffness = Math.max(0, Math.min(1, stiffness));
        this.dampingFactor = 0.7;
        this.blockDisplays = new CopyOnWriteArrayList<>();
        this.segmentVelocities = new ArrayList<>(maxSegments);
        this.slackFactor = Math.max(0.1, Math.min(2.0, slackFactor));
        this.cachedScaleVector = new Vector3f(displayScale, displayScale, displayScale);
        this.iterations = fabrikIterations;
        this.tolerance = fabrikTolerance;

        super.managedDisplays = (List<ManagedDisplay>) (List<?>) this.blockDisplays;
    }

    /**
     * Updates the target location for the chain's end point.
     *
     * @param targetLocation The new target location.
     */
    public void setTargetLocation(Location targetLocation) {
        if (targetLocation != null) {
            previousTargetLocation = this.currentTargetLocation != null ?
                    this.currentTargetLocation.clone() : targetLocation.clone();
            this.currentTargetLocation = targetLocation.clone();
        }
    }

    @Override
    public void start() {
        super.start();
        this.currentChainLength = 0.0;
        this.isFullyExtended = false;
        this.isChainHidden = false;

        this.segmentVelocities = new ArrayList<>(maxSegments);
        for (int i = 0; i < maxSegments; i++) {
            segmentVelocities.add(new Vector(0, 0, 0));
        }

        Vector initialDirection = currentTargetLocation.clone().subtract(sourceLocation).toVector();
        if (initialDirection.lengthSquared() < 0.001) {
            initialDirection = new Vector(0, 1, 0);
        }

        List<Double> initialSegmentLengths = new ArrayList<>();
        double initialSegmentLength = desiredSpacing;
        for (int i = 0; i < minSegments; i++) {
            initialSegmentLengths.add(initialSegmentLength);
        }

        this.fabrikChain = new FabrikChain(sourceLocation, initialDirection, initialSegmentLengths, iterations, tolerance);
    }

    @Override
    public void tick(long deltaTime) {
        if (!this.running || this.currentTargetLocation == null || this.sourceLocation == null ||
                !this.sourceLocation.getWorld().equals(this.currentTargetLocation.getWorld())) {
            return;
        }

        double deltaTimeSeconds = deltaTime / 1000.0;

        tempVector.setX(currentTargetLocation.clone().getX() - sourceLocation.clone().getX());
        tempVector.setY(currentTargetLocation.clone().getY() - sourceLocation.clone().getY());
        tempVector.setZ(currentTargetLocation.clone().getZ() - sourceLocation.clone().getZ());
        double totalRequiredDistance = tempVector.length();

        if (totalRequiredDistance < 0.8) {
            if (!isChainHidden) {
                hideAllDisplays();
                isChainHidden = true;
            }
            return;
        } else if (isChainHidden && totalRequiredDistance > 0.6) {
            showAllDisplays();
            isChainHidden = false;
        }

        double potentialChainLength = Math.min(totalRequiredDistance + 0.05, this.maxChainLength); // epsilon

        if (!isFullyExtended) {
            currentChainLength += extensionSpeed * deltaTimeSeconds;
            if (currentChainLength >= potentialChainLength) {
                currentChainLength = potentialChainLength;
                isFullyExtended = true;
            }
        } else {
            currentChainLength = Math.max(currentChainLength, potentialChainLength * 0.95);
        }

        if (currentChainLength < 0.1) {
            currentChainLength = 0.1;
        }

        // this is kind of borked atm using the new variable segment lengths, breaks spacing length config in a profound way,
        // but lets ignore that for now (just dont change spacing in config yet)
        double restingLength = calculateRestingLength();
        double lengthForSegments = isFullyExtended ? restingLength : totalRequiredDistance;
        dynamicSegmentCount = Math.min(maxSegments, Math.max(minSegments,
                (int) Math.ceil(lengthForSegments / desiredSpacing)));
        dynamicSegmentCount = Math.max(3, dynamicSegmentCount);

        updateChainSegmentCount(dynamicSegmentCount);

        adjustManagedDisplayCount(dynamicSegmentCount);

        if (blockDisplays.isEmpty()) {
            return;
        }

        if (previousTargetLocation != null) {
            currentTargetLocation = previousTargetLocation.clone().add(
                    currentTargetLocation.clone().subtract(previousTargetLocation)
                            .multiply(1 - targetSmoothing));
        }
        previousTargetLocation = currentTargetLocation.clone();

        fabrikChain.reach(currentTargetLocation);

        // gravity and lateral physics
        applyPhysicsToChain(fabrikChain.getSegments(), deltaTimeSeconds);

        List<FabrikChain.ChainSegment> segments = fabrikChain.getSegments();

        // transformations
        updateDisplayEntities(segments);

//        Bukkit.getLogger().info("totalRequiredDistance: " + totalRequiredDistance +
//                ", potentialChainLength (before): " + totalRequiredDistance +
//                ", potentialChainLength (after): " + potentialChainLength +
//                ", currentChainLength: " + currentChainLength +
//                ", isFullyExtended: " + isFullyExtended +
//                ", dynamicSegmentCount: " + dynamicSegmentCount +
//                ", restingLength: " + restingLength);
    }

    /**
     * Applies physics effects to the chain segments. This is currently tailored for Vines.
     */
    private void applyPhysicsToChain(List<FabrikChain.ChainSegment> segments, double deltaTime) {
        if (segments.size() < 2) return;

        // Apply velocity-based physics first (newtonian sag)
        for (int i = 1; i < segments.size(); i++) {
            Vector velocity = segmentVelocities.get(i - 1);

            // Enhance gravity effect
            double gravityMultiplier = 1.0; // set to 1.0 for now, might remove or change later
            int midpoint = segments.size() / 2;
            double normalizedPos = Math.abs(i - midpoint) / (double)midpoint;
            double sagFactor = 1.0 - (normalizedPos * normalizedPos);
            gravityMultiplier += (sagFactor * slackFactor);

            velocity.add(gravityVector.clone().multiply(gravityStrength * gravityMultiplier * deltaTime));
            velocity.multiply(dampingFactor);

            Location currentPos = segments.get(i).getEndLocation();
            currentPos.add(velocity.clone().multiply(deltaTime));
            segmentVelocities.set(i - 1, velocity);
        }

        // Length constraint solving
        Location prevPoint = segments.getFirst().getStartLocation().clone();
        for (FabrikChain.ChainSegment segment : segments) {
            Location currentPoint = segment.getEndLocation();

            Vector direction = currentPoint.clone().subtract(prevPoint).toVector();
            double currentDistance = direction.length();

            if (Math.abs(currentDistance - segment.getLength()) > 0.01) {
                direction.normalize().multiply(segment.getLength());
                Location newPos = prevPoint.clone().add(direction);
                segment.setEndLocation(newPos);
            }

            prevPoint = segment.getEndLocation().clone();
        }

        // Target pulling
        Location endPos = segments.get(segments.size()-1).getEndLocation();
        if (endPos.distance(currentTargetLocation) > 0.5) {
            Vector pullDirection = currentTargetLocation.clone().subtract(endPos).toVector().normalize();

            for (int i = segments.size()-1; i >= 0; i--) {
                double pullFactor = (double)(segments.size() - i) / segments.size();
                FabrikChain.ChainSegment segment = segments.get(i);

                if (i > 0) {
                    Location pos = segment.getEndLocation();
                    pos.add(pullDirection.clone().multiply(0.3 * pullFactor));
                    segment.setEndLocation(pos);
                }
            }
        }

        // "non-physics" parabolic sag, applies even if another force counters it (visual correction pass)
        // yes, this seems redundant compared to our newtonian sag, but it may not be!
        Vector directLine = currentTargetLocation.clone().subtract(sourceLocation).toVector();
        double directLength = directLine.length();
        double maxSag = Math.min(directLength * 0.1 * slackFactor, 1.0);

        for (int i = 1; i < segments.size() - 1; i++) {
            int midpoint = segments.size() / 2;
            double normalizedPos = Math.abs(i - midpoint) / (double)midpoint;
            double sagFactor = 1.0 - (normalizedPos * normalizedPos);

            // Apply minimum guaranteed sag
            Location pos = segments.get(i).getEndLocation();
            double currentY = pos.getY();
            double targetY = pos.getY() - (maxSag * sagFactor);

            // Only pull down, don't push up
            if (currentY > targetY) {
                pos.setY(targetY);
                segments.get(i).setEndLocation(pos);
            }
        }
    }

    /**
     * Updates the FabrikChain to have the right number of segments.
     */
    private void updateChainSegmentCount(int targetSegmentCount) {
        List<FabrikChain.ChainSegment> currentSegments = fabrikChain.getSegments();
        if (currentSegments.size() == targetSegmentCount) {
            return;
        }

        while (currentSegments.size() < targetSegmentCount) {
            fabrikChain.addSegment(desiredSpacing);
            if (segmentVelocities.size() < targetSegmentCount) {
                segmentVelocities.add(new Vector(0, 0, 0));
            }
        }

        while (currentSegments.size() > targetSegmentCount) {
            fabrikChain.removeLastSegment();
            if (!segmentVelocities.isEmpty()) {
                segmentVelocities.remove(segmentVelocities.size() - 1);
            }
        }

        while (segmentVelocities.size() < targetSegmentCount -1) {
            segmentVelocities.add(new Vector(0, 0, 0));
        }
        while (segmentVelocities.size() > targetSegmentCount - 1 && !segmentVelocities.isEmpty()) {
            segmentVelocities.remove(segmentVelocities.size() - 1);
        }
    }

    /**
     * Adds extra length to account for sag/gravity.
     */
    private double calculateRestingLength() {
        if (sourceLocation == null || currentTargetLocation == null) {
            return 0.0;
        }
        double directDistance = sourceLocation.distance(currentTargetLocation);

        return directDistance * Math.max(1.0, slackFactor);
    }

    /**
     * Updates the display entities based on segment positions.
     */
    private void updateDisplayEntities(List<FabrikChain.ChainSegment> segments) {
        for (int i = 0; i < segments.size() && i < blockDisplays.size(); i++) {
            FabrikChain.ChainSegment segment = segments.get(i);
            ManagedBlockDisplay managedDisplay = blockDisplays.get(i);
            BlockDisplay display = managedDisplay.getBukkitEntity();

            if (display == null || !display.isValid()) {
                continue;
            }

            Location start = segment.getStartLocation();
            Location end = segment.getEndLocation();
            Vector direction = end.clone().subtract(start).toVector();
            Location mid = start.clone().add(direction.multiply(0.5));

            int lightLevel = Math.max(display.getLocation().getBlock().getLightFromBlocks(),
                    display.getLocation().getBlock().getLightFromSky());
            lightLevel = Math.max(2, lightLevel);

            // todo: particle based fallback for bedrock clients
            Color darkGreen = Color.fromRGB(20, 100, 20);
            Particle.DustOptions plantDust = new Particle.DustOptions(darkGreen, 0.3f);
            if (Math.random() < 0.15) {
                playPlantbendingParticles(mid, 1, 0.75, 0.0, 0.75);
                //mid.getWorld().spawnParticle(Particle.DUST, mid, 0, 0.75, 0.0, 0.75, 0.0, plantDust, true);
            }

            if (direction.lengthSquared() > 1e-6) {
                try {
                    float[] angles = calculateYawPitch(direction);
                    float yawRad = (float) Math.toRadians(angles[0] + 90f);
                    float pitchRad = (float) Math.toRadians(angles[1] + 90f);
                    float rollRad = (float) Math.toRadians(90f);

                    Quaternionf q = new Quaternionf()
                            .rotateY(yawRad)
                            .rotateZ(rollRad)
                            .rotateX(pitchRad);

                    Transformation tf = new Transformation(
                            new Vector3f(0, 0, 0),
                            new Quaternionf(),
                            new Vector3f(displayScale, displayScale, displayScale),
                            q
                    );

                    display.setTransformation(tf);
                    display.setBrightness(new Display.Brightness(lightLevel, lightLevel));
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[FabrikChainAnimation] Error updating transformation: " + e.getMessage());
                }

                display.setTeleportDuration(3);

                Location currentLoc = display.getLocation();
                if (currentLoc.distanceSquared(mid) > 0.01) {
                    display.teleport(mid);
                }
            }
        }
    }

    /**
     * Calculates yaw and pitch angles from a direction vector.
     */
    private float[] calculateYawPitch(Vector vector) {
        float[] angles = new float[2];

        double length = vector.length();
        if (length > 1E-6) {
            vector.multiply(1.0 / length);
        } else {
            vector.setX(0);
            vector.setY(1);
            vector.setZ(0);
        }

        double y = Math.max(-1.0, Math.min(1.0, -vector.getY()));
        double pitch = Math.asin(y);
        double yaw = Math.atan2(vector.getX(), vector.getZ());

        angles[0] = (float) Math.toDegrees(yaw);
        angles[1] = (float) Math.toDegrees(pitch);

        return angles;
    }

    /**
     * Adjusts the number of managed BlockDisplays to match the target count.
     */
    private void adjustManagedDisplayCount(int targetCount) {
        while (blockDisplays.size() < targetCount) {
            ManagedBlockDisplay newDisplay = displayManager.createBlockDisplay(this.owner, this.sourceLocation, this.segmentData);

            // potentially expensive for what it is,
            // find another way to temporarily hide displays while being added to the chain?
            // you can see segments *before* transformations are applied for about a tick otherwise, looks weird
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideEntity(ProjectKorra.plugin, newDisplay.getBukkitEntity());
            }

            if (newDisplay != null) {
                Bukkit.getScheduler().runTaskLater(ProjectKorra.plugin, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.showEntity(ProjectKorra.plugin, newDisplay.getBukkitEntity());
                    }
                }, 2L);

                newDisplay.setScale(this.cachedScaleVector);
                newDisplay.setBrightness(new Display.Brightness(15, 15));
                newDisplay.setInterpolationDelay(-1);
                newDisplay.setInterpolationDuration(3);

                BlockDisplay entity = newDisplay.getBukkitEntity();
                entity.teleport(sourceLocation);

                blockDisplays.add(newDisplay);
            } else {
                break;
            }
        }

        while (blockDisplays.size() > targetCount) {
            ManagedBlockDisplay displayToRemove = blockDisplays.removeLast();
            if (displayToRemove != null) {
                displayToRemove.remove();
            }
        }

        super.managedDisplays = (List<ManagedDisplay>) (List<?>) this.blockDisplays;
    }

    private void hideAllDisplays() {
        for (ManagedBlockDisplay display : blockDisplays) {
            BlockDisplay blockDisplay = display.getBukkitEntity();
            blockDisplay.setTeleportDuration(1);
            blockDisplay.setViewRange(0);
        }
    }

    private void showAllDisplays() {
        for (ManagedBlockDisplay display : blockDisplays) {
            BlockDisplay blockDisplay = display.getBukkitEntity();
            blockDisplay.setViewRange(64);
        }
    }

    /**
     * Removes multiple segments from the end of the chain.
     *
     * @param count The number of segments to remove
     */
    public void bulkRemoveSegments(int count) {
        if (count <= 0 || blockDisplays.isEmpty()) return;

        count = Math.min(count, blockDisplays.size());

        List<ManagedBlockDisplay> displaysToRemove = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            if (blockDisplays.isEmpty()) break;
            displaysToRemove.add(blockDisplays.removeLast());
        }

        super.managedDisplays = (List<ManagedDisplay>) (List<?>) blockDisplays;

        for (int i = 0; i < count; i++) {
            if (fabrikChain.getSegments().size() > 1) {
                fabrikChain.removeLastSegment();
            }
            if (!segmentVelocities.isEmpty()) {
                segmentVelocities.remove(segmentVelocities.size() - 1);
            }
        }

        Bukkit.getScheduler().runTask(ProjectKorra.plugin, () -> {
            for (ManagedBlockDisplay display : displaysToRemove) {
                if (display != null) {
                    display.remove();
                }
            }
        });
    }

    /**
     * Retracts the chain by removing segments from the end.
     *
     * @param retractionRate How many segments to remove per retraction
     */
    public void retractChain(int retractionRate) {
        retractionRate = Math.max(1, retractionRate);

        double targetLength = Math.max(0, currentChainLength - (retractionRate * desiredSpacing));
        int targetSegmentCount = Math.max(0, (int) (targetLength / desiredSpacing));

        int segmentsToRemove = Math.max(0, blockDisplays.size() - targetSegmentCount);

        if (segmentsToRemove > 0) {
            bulkRemoveSegments(segmentsToRemove);
        }

        currentChainLength = targetLength;
    }

    @Override
    public void stop(boolean removeDisplays) {
        super.stop(removeDisplays);

        for (Vector velocity : segmentVelocities) {
            velocity.setX(0);
            velocity.setY(0);
            velocity.setZ(0);
        }
    }

    /**
     * Gets whether the chain is fully extended.
     *
     * @return true if the chain is fully extended
     */
    public boolean isFullyExtended() {
        return isFullyExtended;
    }

    /**
     * Gets whether the chain has been snapped/cut.
     *
     * @return true if the chain has been snapped/cut
     */
    public boolean isSnapped() {
        return this.snapped;
    }

    /**
     * Sets whether the chain has been snapped/cut.
     *
     * @param snapped the new snapped state
     */
    public void setSnapped(boolean snapped) {
        this.snapped = snapped;
    }

    /**
     * Handles the chain being cut at a specific location, splitting it into two parts
     * with one part falling to the ground.
     *
     * @param cutLocation The location where the chain was cut
     * @return true if the chain was successfully cut, false if already cut or invalid location
     */
    public boolean cutChainAt(Location cutLocation) {
        if (this.snapped || cutLocation == null || blockDisplays.isEmpty()) {
            return false;
        }

        int cutSegmentIndex = -1;
        double minDistanceSquared = Double.MAX_VALUE;

        for (int i = 0; i < blockDisplays.size(); i++) {
            ManagedBlockDisplay display = blockDisplays.get(i);
            BlockDisplay blockDisplay = display.getBukkitEntity();
            double distSq = blockDisplay.getLocation().distanceSquared(cutLocation);

            if (distSq < minDistanceSquared) {
                minDistanceSquared = distSq;
                cutSegmentIndex = i;
            }
        }

        if (cutSegmentIndex == -1 || minDistanceSquared > 4.0) {
            return false;
        }

        this.snapped = true;

        final Location cutSegmentLocation = blockDisplays.get(cutSegmentIndex).getBukkitEntity().getLocation().clone();

        //startFallingAnimation(cutSegmentIndex, cutSegmentLocation);

        return true;
    }

    public void playPlantbendingParticles(final Location loc, final int amount, final double xOffset, final double yOffset, final double zOffset) {
        //loc.getWorld().spawnParticle(Particle.BLOCK_CRACK, loc.clone().add(0.5, 0, 0.5), amount, xOffset, yOffset, zOffset, Material.OAK_LEAVES.createBlockData());
    }
}
