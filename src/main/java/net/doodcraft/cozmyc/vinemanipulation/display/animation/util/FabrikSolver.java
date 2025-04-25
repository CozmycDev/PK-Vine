package net.doodcraft.cozmyc.vinemanipulation.display.animation.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class FabrikSolver {

    private final int maxIterations;
    private final double tolerance;

    /**
     * Creates a new FabrikSolver instance.
     *
     * @param maxIterations The maximum number of iterations to perform.
     * @param tolerance The position tolerance threshold.
     */
    public FabrikSolver(int maxIterations, double tolerance) {
        this.maxIterations = Math.max(1, maxIterations);
        this.tolerance = tolerance * tolerance;
    }

    /**
     * Solves the IK chain using a segment-based approach. Returns a list of locations
     * representing the chain positions.
     *
     * @param root The root (starting) position of the chain
     * @param target The target position for the chain end
     * @param segmentLengths List of segment lengths
     * @param locationPool Pre-allocated list of locations to reduce GC pressure
     * @return The solved chain positions
     */
    public List<Location> solve(Location root, Location target, List<Double> segmentLengths, List<Location> locationPool) {
        if (root == null || target == null || segmentLengths == null || segmentLengths.isEmpty()) {
            return new ArrayList<>();
        }

        int numSegments = segmentLengths.size();
        int numJoints = numSegments + 1;

        while (locationPool.size() < numJoints) {
            locationPool.add(root.clone());
        }

        double totalLength = 0;
        for (double length : segmentLengths) {
            totalLength += length;
        }

        initializeChain(root, target, segmentLengths, locationPool);

        double distanceToTarget = root.distance(target);
        if (distanceToTarget > totalLength) {
            createStraightChain(root, target, segmentLengths, locationPool);
            return locationPool.subList(0, numJoints);
        }

        double errorSq = Double.MAX_VALUE;
        int iteration = 0;

        while (errorSq > tolerance && iteration < maxIterations) {
            backward(target, segmentLengths, locationPool);

            forward(root, segmentLengths, locationPool);

            Location endEffector = locationPool.get(numJoints - 1);
            errorSq = endEffector.distanceSquared(target);

            iteration++;
        }

        return locationPool.subList(0, numJoints);
    }

    /**
     * Initialize the chain as a straight line from root toward target
     */
    private void initializeChain(Location root, Location target, List<Double> segmentLengths, List<Location> locationPool) {
        Vector direction = target.clone().subtract(root).toVector();

        direction.normalize();

        locationPool.get(0).setX(root.getX());
        locationPool.get(0).setY(root.getY());
        locationPool.get(0).setZ(root.getZ());

        double currentLength = 0;
        for (int i = 0; i < segmentLengths.size(); i++) {
            currentLength += segmentLengths.get(i);
            Vector offset = direction.clone().multiply(currentLength);

            Location point = locationPool.get(i + 1);
            point.setWorld(root.getWorld());
            point.setX(root.getX() + offset.getX());
            point.setY(root.getY() + offset.getY());
            point.setZ(root.getZ() + offset.getZ());
        }
    }

    /**
     * Creates a straight chain pointing toward the target when target is out of reach.
     */
    private void createStraightChain(Location root, Location target, List<Double> segmentLengths, List<Location> locationPool) {
        Vector direction = target.clone().subtract(root).toVector();

        direction.normalize();

        locationPool.get(0).setX(root.getX());
        locationPool.get(0).setY(root.getY());
        locationPool.get(0).setZ(root.getZ());

        double currentLength = 0;
        for (int i = 0; i < segmentLengths.size(); i++) {
            currentLength += segmentLengths.get(i);
            Vector offset = direction.clone().multiply(currentLength);

            Location point = locationPool.get(i + 1);
            point.setWorld(root.getWorld());
            point.setX(root.getX() + offset.getX());
            point.setY(root.getY() + offset.getY());
            point.setZ(root.getZ() + offset.getZ());
        }
    }

    /**
     * Backward pass - working from the target back to the root.
     */
    private void backward(Location target, List<Double> segmentLengths, List<Location> locationPool) {
        int lastIndex = segmentLengths.size();

        Location last = locationPool.get(lastIndex);
        last.setX(target.getX());
        last.setY(target.getY());
        last.setZ(target.getZ());

        for (int i = lastIndex - 1; i >= 0; i--) {
            Location current = locationPool.get(i);
            Location next = locationPool.get(i + 1);

            Vector direction = current.clone().subtract(next).toVector();
            double length = direction.length();

            double segmentLength = segmentLengths.get(i);
            direction.multiply(segmentLength / length);

            current.setX(next.getX() + direction.getX());
            current.setY(next.getY() + direction.getY());
            current.setZ(next.getZ() + direction.getZ());
        }
    }

    /**
     * Forward pass - working from the root forward to the target.
     */
    private void forward(Location root, List<Double> segmentLengths, List<Location> locationPool) {
        Location first = locationPool.get(0);
        first.setX(root.getX());
        first.setY(root.getY());
        first.setZ(root.getZ());

        for (int i = 0; i < segmentLengths.size(); i++) {
            Location current = locationPool.get(i);
            Location next = locationPool.get(i + 1);

            Vector direction = next.clone().subtract(current).toVector();
            double length = direction.length();

            double segmentLength = segmentLengths.get(i);
            direction.multiply(segmentLength / length);

            next.setX(current.getX() + direction.getX());
            next.setY(current.getY() + direction.getY());
            next.setZ(current.getZ() + direction.getZ());
        }
    }
}
