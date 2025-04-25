package net.doodcraft.cozmyc.vinemanipulation.display.animation.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class FabrikChain {

    private final List<ChainSegment> segments;
    private final Location startLocation;
    private Location targetLocation;
    private final FabrikSolver solver;
    private final List<Double> segmentLengths;
    private final List<Location> segmentLocations;

    private double totalLength;
    private boolean isAttached;

    /**
     * Creates a new IK chain with segments of equal length.
     *
     * @param startLocation The base location of the chain
     * @param initialDirection The initial direction vector
     * @param segmentLength Length of each segment
     * @param segmentCount Number of segments
     */
    public FabrikChain(Location startLocation, Vector initialDirection, double segmentLength, int segmentCount, int iterations, double tolerance) {
        this.startLocation = startLocation.clone();
        this.segments = new ArrayList<>();
        this.segmentLengths = new ArrayList<>();
        this.segmentLocations = new ArrayList<>();
        this.isAttached = true;
        this.solver = new FabrikSolver(iterations, tolerance);
        this.totalLength = segmentLength * segmentCount;

        Vector direction = initialDirection.clone().normalize();

        segmentLocations.add(startLocation.clone());
        Location current = startLocation.clone();

        for (int i = 0; i < segmentCount; i++) {
            segmentLengths.add(segmentLength);

            Vector offset = direction.clone().multiply(segmentLength);
            current = current.clone().add(offset);
            segmentLocations.add(current.clone());

            if (i == 0) {
                segments.add(new ChainSegment(segmentLocations.get(0), segmentLocations.get(1), null));
            } else {
                segments.add(new ChainSegment(segmentLocations.get(i), segmentLocations.get(i + 1), segments.get(i - 1)));
            }
        }

        this.targetLocation = segmentLocations.getLast().clone();
    }

    /**
     * Creates a chain with segments of varying lengths.
     *
     * @param startLocation    The base location of the chain
     * @param initialDirection The initial direction vector
     * @param segmentLengths   List of segment lengths
     */
    public FabrikChain(Location startLocation, Vector initialDirection, List<Double> segmentLengths, int iterations, double tolerance) {
        this.startLocation = startLocation.clone();
        this.segments = new ArrayList<>();
        this.segmentLengths = new ArrayList<>(segmentLengths);
        this.segmentLocations = new ArrayList<>();
        this.isAttached = true;
        this.solver = new FabrikSolver(iterations, tolerance);

        this.totalLength = 0;
        for (double length : segmentLengths) {
            this.totalLength += length;
        }

        Vector direction = initialDirection.clone().normalize();

        segmentLocations.add(startLocation.clone());
        Location current = startLocation.clone();

        for (int i = 0; i < segmentLengths.size(); i++) {
            double segmentLength = segmentLengths.get(i);

            Vector offset = direction.clone().multiply(segmentLength);
            current = current.clone().add(offset);
            segmentLocations.add(current.clone());

            if (i == 0) {
                segments.add(new ChainSegment(segmentLocations.get(0), segmentLocations.get(1), null));
            } else {
                segments.add(new ChainSegment(segmentLocations.get(i), segmentLocations.get(i + 1), segments.get(i - 1)));
            }
        }

        this.targetLocation = segmentLocations.getLast().clone();
    }

    /**
     * Updates the chain to reach for the target location.
     *
     * @param targetLocation The target location for the end of the chain
     */
    public void reach(Location targetLocation) {
        this.targetLocation = targetLocation.clone();

        List<Location> solvedLocations = solver.solve(
                startLocation,
                targetLocation,
                segmentLengths,
                segmentLocations
        );

        for (int i = 0; i < segments.size(); i++) {
            segments.get(i).updatePositions(solvedLocations.get(i), solvedLocations.get(i + 1));
        }
    }

    /**
     * Sets whether the chain is attached to its base location.
     */
    public void setAttached(boolean attached) {
        this.isAttached = attached;
    }

    /**
     * Sets a new start location for the chain.
     */
    public void setStartLocation(Location startLocation) {
        if (this.isAttached) {
            Vector offset = startLocation.clone().subtract(this.startLocation).toVector();

            this.startLocation.setX(startLocation.getX());
            this.startLocation.setY(startLocation.getY());
            this.startLocation.setZ(startLocation.getZ());

            for (Location location : segmentLocations) {
                location.add(offset);
            }

            for (int i = 0; i < segments.size(); i++) {
                segments.get(i).updatePositions(segmentLocations.get(i), segmentLocations.get(i + 1));
            }
        }
    }

    /**
     * Gets all segments in the chain.
     */
    public List<ChainSegment> getSegments() {
        return segments;
    }

    /**
     * Gets the end location of the chain.
     */
    public Location getEndLocation() {
        return segmentLocations.get(segmentLocations.size() - 1).clone();
    }

    /**
     * Gets the start location of the chain.
     */
    public Location getStartLocation() {
        return startLocation.clone();
    }

    /**
     * Gets the total chain length.
     */
    public double getTotalLength() {
        return totalLength;
    }

    /**
     * Adds a new segment to the end of the chain.
     *
     * @param length Length of the new segment
     */
    public void addSegment(double length) {
        ChainSegment lastSegment = segments.get(segments.size() - 1);
        Location endLocation = segmentLocations.get(segmentLocations.size() - 1).clone();

        Vector direction = endLocation.clone().subtract(segmentLocations.get(segmentLocations.size() - 2)).toVector();
        direction.normalize().multiply(length);

        Location newEndLocation = endLocation.clone().add(direction);
        segmentLocations.add(newEndLocation);

        segmentLengths.add(length);
        totalLength += length;

        ChainSegment newSegment = new ChainSegment(endLocation, newEndLocation, lastSegment);
        segments.add(newSegment);

        this.targetLocation = newEndLocation.clone();
    }

    /**
     * Removes the last segment from the chain.
     */
    public void removeLastSegment() {
        if (segments.size() <= 1) {
            return; // but not like, the *last* segment!
        }

        segments.remove(segments.size() - 1);
        totalLength -= segmentLengths.get(segmentLengths.size() - 1);
        segmentLengths.remove(segmentLengths.size() - 1);
        segmentLocations.remove(segmentLocations.size() - 1);

        this.targetLocation = segmentLocations.get(segmentLocations.size() - 1).clone();
    }

    /**
     * Represents a single segment in the IK chain.
     */
    public static class ChainSegment {
        private Location startLocation;
        private Location endLocation;
        private Vector direction;
        private double length;
        private ChainSegment parent;

        /**
         * Creates a new segment.
         */
        public ChainSegment(Location startLocation, Location endLocation, ChainSegment parent) {
            this.startLocation = startLocation.clone();
            this.endLocation = endLocation.clone();
            this.parent = parent;
            updateDirection();
        }

        /**
         * Updates the segment's direction vector based on start and end locations.
         */
        private void updateDirection() {
            this.direction = endLocation.clone().subtract(startLocation).toVector();
            this.length = direction.length();
            if (this.length > 0) {
                this.direction.normalize();
            }
        }

        /**
         * Updates the segment's positions.
         */
        public void updatePositions(Location start, Location end) {
            this.startLocation = start.clone();
            this.endLocation = end.clone();
            updateDirection();
        }

        /**
         * Gets the start location of this segment.
         */
        public Location getStartLocation() {
            return startLocation.clone();
        }

        /**
         * Gets the end location of this segment.
         */
        public Location getEndLocation() {
            return endLocation.clone();
        }

        /**
         * Sets the end location of this segment.
         */
        public void setEndLocation(Location location) {
            this.endLocation = location;
        }

        /**
         * Gets the direction vector of this segment.
         */
        public Vector getDirection() {
            return direction.clone();
        }

        /**
         * Gets the length of this segment.
         */
        public double getLength() {
            return length;
        }

        /**
         * Gets the parent segment.
         */
        public ChainSegment getParent() {
            return parent;
        }

        /**
         * Checks if this segment has a parent.
         */
        public boolean hasParent() {
            return parent != null;
        }
    }
}
