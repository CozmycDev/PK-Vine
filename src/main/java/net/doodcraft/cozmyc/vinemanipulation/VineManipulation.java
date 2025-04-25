package net.doodcraft.cozmyc.vinemanipulation;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.PlantAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.attribute.markers.DayNightFactor;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import net.doodcraft.cozmyc.vinemanipulation.display.DisplayManager;
import net.doodcraft.cozmyc.vinemanipulation.display.animation.FabrikChainAnimation;
import net.doodcraft.cozmyc.vinemanipulation.display.managed.ManagedDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class VineManipulation extends PlantAbility implements AddonAbility {

    public enum State { SELECTED, EXTENDING, TENSIONING, PULLING, HANGING }

    private State currentState;
    private Block source;
    private Location originLoc;
    private Entity target;

    private long stateStartTime;

    private boolean isClimbable;
    public boolean targetingOtherEntity;
    private long lastProgressTimeMillis;
    private double currentTension;
    private double maxTension;
    private double tensionArcConstraint;
    private double maxTensionDistance;
    private double allowedSlack;
    private double allowedMovementDistance;
    private double maxVineLength;
    private float displayScale;
    private double stiffness;
    private double gravityStrength;
    private int maxAngleConstraintDegrees;
    private int maxSegments;
    private int minSegments;
    private double desiredSpacing;
    private int fabrikIterations;
    private double fabrikTolerance;
    private String segmentMaterial;
    private boolean dynamicMaterials;

    private FabrikChainAnimation vineAnimation;

    private final Vector tempVector = new Vector();

    @Attribute(Attribute.SELECT_RANGE)
    public double selectRange; // Range players can select vines from
    @Attribute(Attribute.COOLDOWN) @DayNightFactor(invert = true)
    public long selfCooldown; // Cooldown after using it on self
    @Attribute("EntityCooldown") @DayNightFactor(invert = true)
    public long entityCooldown; // Cooldown after using it against another entity
    @Attribute("PullStrength")
    private double pullStrength; // Base velocity of vine pull
    @Attribute("PullDuration")
    private long pullDuration; // Duration entity is pulled after vine attaches
    @Attribute("HangDuration")
    private long hangDuration; // Duration player can hang after vine attaches
    @Attribute("ExtensionSpeed") @DayNightFactor(invert = true)
    private double extensionSpeed;

    public VineManipulation(Player player) {
        super(player);

        if (this.bPlayer.isOnCooldown(this)) {
            return;
        }

        setFields();

        currentState = State.SELECTED;

        Block targetBlock = player.getTargetBlockExact((int) selectRange);
        if (targetBlock == null) {
            StaticMethods.sendActionBar(player, "&e* &cNo plant found! &e*");
            return;
        }

        RayTraceResult result = player.rayTraceBlocks(selectRange);
        BlockFace face = result != null ? result.getHitBlockFace() : null;

        if (face == null || !isPlant(targetBlock)) {
            StaticMethods.sendActionBar(player, "&e* &cNo plant found! &e*");
            return;
        }

        this.source = targetBlock;
        this.originLoc = this.source.getLocation().add(0.5, 0.5, 0.5);
        this.isClimbable = (face == BlockFace.DOWN);
        this.stateStartTime = System.currentTimeMillis();
        this.currentTension = 0.0;

        if (isClimbable) {
            StaticMethods.sendActionBar(player, "&2* &aSelected " + source.getType().name().toLowerCase() + " &2* (&ebottom face&2)");
        } else {
            StaticMethods.sendActionBar(player, "&2* &aSelected " + source.getType().name().toLowerCase() + " &2*");
        }

        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.defaultConfig.get();
        String path = "Abilities.Water.VineManipulation.";

        this.selectRange = config.getDouble(path + "SelectRange");
        this.pullStrength = config.getDouble(path + "PullStrength");
        this.displayScale = (float) config.getDouble(path + "Vine.DisplayScale");
        this.selfCooldown = config.getLong(path + "Cooldown.Self");
        this.entityCooldown = config.getLong(path + "Cooldown.Entity");
        this.maxVineLength = config.getDouble(path + "Vine.Length");
        this.pullDuration = config.getLong(path + "Duration.Pull");
        this.hangDuration = config.getLong(path + "Duration.Hang");

        this.maxSegments = config.getInt(path + "Vine.Performance.MaxSegments");
        this.minSegments = config.getInt(path + "Vine.Performance.MinSegments");
        this.desiredSpacing = config.getDouble(path + "Vine.Performance.DesiredSpacing");
        this.fabrikIterations = config.getInt(path + "Vine.Performance.FabrikIterations");
        this.fabrikTolerance = config.getDouble(path + "Vine.Performance.FabrikTolerance");

        this.extensionSpeed = config.getDouble(path + "Vine.ExtensionSpeed");
        this.gravityStrength = config.getDouble(path + "Vine.GravitySag");

        this.segmentMaterial = config.getString(path + "Vine.Segment.Material");
        this.dynamicMaterials = config.getBoolean(path + "Vine.Segment.Dynamic");
        this.maxAngleConstraintDegrees = config.getInt(path + "Vine.Segment.MaxAngleConstraint");

        this.stiffness = config.getDouble(path + "Vine.Stiffness");
        this.allowedSlack = config.getDouble(path + "Tension.AllowedSlack");
        this.maxTensionDistance = config.getDouble(path + "Tension.MaxTensionDistance");
        this.maxTension = config.getDouble(path + "Tension.MaxForce");
        this.tensionArcConstraint = config.getDouble(path + "Tension.ArcConstraint");

        this.allowedMovementDistance = Math.max(maxVineLength, maxTensionDistance) + 1.5;
    }

    @Override
    public void progress() {
        long deltaTime;
        if (!canProgress()) {
            removeDueToFailure();
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (this.lastProgressTimeMillis == 0) {
            this.lastProgressTimeMillis = this.getStartTime();
        }

        deltaTime = currentTime - this.lastProgressTimeMillis;
        deltaTime = Math.clamp(deltaTime, 1, 200); // java 21

        long timeInCurrentState = currentTime - this.stateStartTime;

        if (this.vineAnimation != null && this.vineAnimation.isRunning()) {

            if (this.vineAnimation.snapped) {
                removeDueToFailure();
                this.lastProgressTimeMillis = currentTime;
                return;
            }

            if (target != null && target.isValid()) {
                Location targetAttachPoint = target.getLocation().add(0, target.getHeight() * 0.8, 0);
                this.vineAnimation.setTargetLocation(targetAttachPoint);
                this.vineAnimation.tick(deltaTime);
            } else if (currentState == State.EXTENDING || currentState == State.PULLING) {
                removeDueToFailure();
                this.lastProgressTimeMillis = currentTime;
                return;
            }
        }

        switch (currentState) {
            case SELECTED:
                handleSelectedState(timeInCurrentState);
                break;
            case TENSIONING:
                handleTensioningState();
                break;
            case EXTENDING:
                handleExtendingState(timeInCurrentState);
                break;
            case PULLING:
                handlePullingState(timeInCurrentState);
                break;
            case HANGING:
                handleHangingState(timeInCurrentState);
                break;
        }

        if ((currentState == State.EXTENDING || currentState == State.PULLING || currentState == State.HANGING)
                && getRunningTicks() % 4 == 0) {
             playPlantbendingParticles(originLoc, 1, 0.1, 0.1, 0.1);
        }

        this.lastProgressTimeMillis = currentTime;
    }

    private boolean canProgress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }

        if (source == null || !isPlant(source)) {
            return false;
        }

        if (player.getLocation().distanceSquared(originLoc) > allowedMovementDistance * allowedMovementDistance) {
            return false;
        }

        if (vineAnimation != null && vineAnimation.snapped) {
            return false;
        }

        return true;
    }

    private void handleSelectedState(long timeInState) {
        if (timeInState > 5000) {
            remove();
            return;
        }

        if (System.currentTimeMillis() % 500 < 50) {
            playPlantbendingParticles(originLoc, 2, 0.3, 0.3, 0.3);
        }
    }

    private void handleExtendingState(long timeInState) {
        if (target == null || !target.isValid()) {
            removeDueToFailure();
            return;
        }

        if (timeInState > 5000) {
            player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.5f, 1.2f);
            removeDueToFailure();
            return;
        }

        if (target.getLocation().distanceSquared(originLoc) > allowedMovementDistance * allowedMovementDistance * 1.1) {
            player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.5f, 1.2f);
            removeDueToFailure();
            return;
        }

        if (vineAnimation != null && vineAnimation.isFullyExtended()) {
            float pitch = ThreadLocalRandom.current().nextFloat(0.8f, 1.1f);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GRASS_BREAK, 1.0f, pitch);
            source.getWorld().playSound(source.getLocation(), Sound.BLOCK_MOSS_BREAK, 0.8f, pitch);

            this.stateStartTime = System.currentTimeMillis();

            if (isClimbable && target == player) {
                if (target.getLocation().distanceSquared(originLoc) < 6.5) {
                    currentState = State.HANGING;
                } else {
                    currentState = State.PULLING;
                    applyInitialPull();
                }
            } else {
                currentState = State.PULLING;
                applyInitialPull();
            }
        }
    }

    private void handlePullingState(long timeInState) {
        if (target == null || !target.isValid()) {
            removeDueToFailure();
            return;
        }

        double distSq = target.getLocation().distanceSquared(originLoc);
        if (target == player && !isClimbable && distSq < 1.5) {
            removeDueToCompletion();
            return;
        }

        if (target == player && player.isSneaking()) {
            removeDueToCompletion();
            return;
        }

        if (timeInState > pullDuration) {
            removeDueToCompletion();
            return;
        }

        if (isClimbable && target == player && distSq < 6.0) {
            currentState = State.HANGING;
            stateStartTime = System.currentTimeMillis();
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
            return;
        }

        if (target != player && distSq < 1.5) {
            removeDueToCompletion();
            return;
        }

        pullEntity();

        if (System.currentTimeMillis() % 200 < 50) {
            playPlantbendingParticles(
                    target.getLocation().add(0, target.getHeight()*0.5, 0),
                    2, 0.2, 0.2, 0.2
            );
        }
    }

    private void handleHangingState(long timeInState) {
        if (target != player || !player.isValid() || player.isDead()) {
            removeDueToFailure();
            return;
        }

        if (player.isSneaking()) {
            removeDueToCompletion();
            return;
        }

        if (timeInState > hangDuration) {
            removeDueToCompletion();
            return;
        }

        if (player.getLocation().getY() >= originLoc.getY()) {
            removeDueToCompletion();
            return;
        }

        Vector toSource = originLoc.clone().subtract(player.getLocation()).toVector().normalize();
        Vector lookDir = player.getLocation().getDirection();

        double verticalVelocity;
        if (player.getLocation().getPitch() > 77.0F) {
            verticalVelocity = -0.2;
        } else if (lookDir.dot(toSource) > 0.77) {
            verticalVelocity = 0.3;
        } else {
            verticalVelocity = 0.0;
        }

        Vector finalVel = new Vector(0, verticalVelocity, 0);
        player.setVelocity(finalVel);
        player.setFallDistance(0);
    }

    private void executeGroundLaunch() {
        if (player == null || !player.isValid()) {
            removeDueToFailure();
            return;
        }

        Vector playerLookDir = player.getLocation().getDirection();
        Vector toSource = originLoc.clone().subtract(player.getLocation()).toVector().normalize();

        double angle = Math.toDegrees(Math.acos(playerLookDir.dot(toSource)));

        double effectiveConstraint = this.tensionArcConstraint;

        if (angle > effectiveConstraint) {
            Vector up = new Vector(0, 1, 0);
            Vector right = toSource.clone().crossProduct(up).normalize();
            Vector adjustedUp = right.clone().crossProduct(toSource).normalize();

            double rightComponent = playerLookDir.dot(right);
            double upComponent = playerLookDir.dot(adjustedUp);

            double radians = Math.toRadians(effectiveConstraint);
            double sinAngle = Math.sin(radians);

            Vector adjustedDir = toSource.clone().multiply(Math.cos(radians));
            adjustedDir.add(right.clone().multiply(rightComponent * sinAngle));
            adjustedDir.add(adjustedUp.clone().multiply(upComponent * sinAngle));

            adjustedDir.normalize();

            double lookY = playerLookDir.getY();
            adjustedDir.setY(Math.clamp(lookY * 0.5 + 0.3, 0.2, 0.8));

            playerLookDir = adjustedDir.normalize();
        }

        double launchForce = 0.8 + this.currentTension;
        player.setVelocity(playerLookDir.multiply(launchForce));

        if (player.getLocation().getY() < originLoc.getY() - 1) {
            player.setVelocity(player.getVelocity().add(new Vector(0, 0.45, 0)));
        }

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT,
                1.0f, 0.8f + (float)(this.currentTension/this.maxTension) * 0.4f);

        this.currentState = State.PULLING;
        this.stateStartTime = System.currentTimeMillis();
    }

    public void activatePull(Entity targetEntity) {
        if (currentState != State.SELECTED) {
            return;
        }

        this.target = targetEntity;
        this.targetingOtherEntity = (targetEntity != player);
        this.stateStartTime = System.currentTimeMillis();

        if (!targetingOtherEntity) {
            this.currentState = State.TENSIONING;

            if (this.vineAnimation == null) {
                Location initialTargetLoc = targetEntity.getLocation().add(0, targetEntity.getHeight() * 0.8, 0);
                BlockData vineData = getSegmentBlockData(this.originLoc);

                this.vineAnimation = new FabrikChainAnimation(
                        this,
                        getDisplayManager(),
                        this.originLoc,
                        initialTargetLoc,
                        vineData,
                        this.displayScale,
                        this.maxSegments,
                        this.minSegments,
                        this.desiredSpacing,
                        this.maxVineLength,
                        this.extensionSpeed,
                        this.fabrikIterations,
                        this.fabrikTolerance,
                        this.gravityStrength,
                        this.maxAngleConstraintDegrees,
                        this.stiffness,
                        1.0
                );
                this.vineAnimation.start();
            }

            float pitch = ThreadLocalRandom.current().nextFloat(0.5f, 0.8f);
            source.getWorld().playSound(source.getLocation(), Sound.BLOCK_VINE_STEP, 0.5f, pitch);
            return;
        }

        this.currentState = State.EXTENDING;

        if (this.vineAnimation == null) {
            Location initialTargetLoc = targetEntity.getLocation().add(0, targetEntity.getHeight() * 0.8, 0);
            BlockData vineData = getSegmentBlockData(this.originLoc);

            this.vineAnimation = new FabrikChainAnimation(
                    this,
                    getDisplayManager(),
                    this.originLoc,
                    initialTargetLoc,
                    vineData,
                    this.displayScale,
                    this.maxSegments,
                    this.minSegments,
                    this.desiredSpacing,
                    this.maxVineLength,
                    this.extensionSpeed,
                    this.fabrikIterations,
                    this.fabrikTolerance,
                    this.gravityStrength,
                    this.maxAngleConstraintDegrees,
                    this.stiffness,
                    0.5
            );
            this.vineAnimation.start();
        }

        float pitch = ThreadLocalRandom.current().nextFloat(0.5f, 0.8f);
        source.getWorld().playSound(source.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, pitch);
    }

    private void handleTensioningState() {
        if (!this.vineAnimation.isFullyExtended()) return;

        Location playerLoc = player.getLocation();
        Vector offset = playerLoc.toVector().subtract(originLoc.toVector());
        double currentDistance = offset.length();

        double tensionDistance = Math.max(0, currentDistance - allowedSlack);

        double tensionRatio = Math.min(1.0, tensionDistance / maxTensionDistance);

        this.currentTension = tensionRatio * maxTension;

        if (vineAnimation != null) {
            vineAnimation.stiffness = Math.min(0.9, this.currentTension / this.maxTension);
        }

        Vector velocity = player.getVelocity();
        Vector pullDir = offset.clone().normalize();

        double tensionThreshold = 0.75;
        if (tensionRatio >= tensionThreshold) {
            double effectiveRatio = (tensionRatio - tensionThreshold) / (1.0 - tensionThreshold);

            if (tensionRatio >= 0.99) {
                double dot = velocity.dot(pullDir);
                if (dot > 0) {
                    Vector blockedVel = velocity.clone().subtract(pullDir.multiply(dot * 1.35));

                    blockedVel.setY(Math.min(blockedVel.getY(), -0.4));
                    player.setVelocity(blockedVel);

                    if (getRunningTicks() % 6 == 0 && effectiveRatio > 0.3) {
                        playPlantbendingParticles(playerLoc.clone().add(0, 1, 0), 1, 0.2, 0.2, 0.2);
                        float pitch = 1.4f + (float)effectiveRatio * 0.7f;
                        player.getWorld().playSound(playerLoc, Sound.BLOCK_BAMBOO_STEP, 0.3f, pitch);
                    }
                }
            } else if (effectiveRatio > 0.0) {
                double resistForce = 0.12 * effectiveRatio;
                Vector resistance = pullDir.clone().multiply(-resistForce);

                Vector playerLookHorizontal = player.getLocation().getDirection().clone().setY(0).normalize();
                Vector perpendicular = playerLookHorizontal.clone().crossProduct(new Vector(0, 1, 0)).normalize();

                Vector horizontalPull = pullDir.clone().setY(0).normalize();
                double perpDot = perpendicular.dot(horizontalPull);

                resistance.multiply(Math.abs(perpDot) * 0.5 + 0.5);

                player.setVelocity(player.getVelocity().add(resistance));

                int interval = Math.max(1, (int)(6 - effectiveRatio * 4));
                if (getRunningTicks() % interval == 0 && effectiveRatio > 0.3) {
                    playPlantbendingParticles(playerLoc.clone().add(0, 1, 0), 1, 0.2, 0.2, 0.2);

                    float pitch = 1.4f + (float)effectiveRatio * 0.7f;
                    player.getWorld().playSound(playerLoc, Sound.BLOCK_BAMBOO_STEP, 0.3f, pitch);
                }
            }
        }

        int percent = (int)(tensionRatio * 100);
        if (tensionRatio < tensionThreshold) {
            StaticMethods.sendActionBar(player, "&aTension: &f" + percent + "%");
        } else if (tensionRatio > 0.98) {
            StaticMethods.sendActionBar(player, "&2Fully stretched!");
        } else {
            StaticMethods.sendActionBar(player, "&aTension: &f" + percent + "% &a(&e&ostretching&a)");
        }

        if (!player.isOnGround() && tensionRatio > 0.1) {
            executeGroundLaunch();
        }
    }

    private void applyInitialPull() {
        if (target == null || !target.isValid()) return;

        tempVector.setX(originLoc.getX() - target.getLocation().getX());
        tempVector.setY(originLoc.getY() - target.getLocation().getY());
        tempVector.setZ(originLoc.getZ() - target.getLocation().getZ());

        if (tempVector.lengthSquared() < 1E-6) return;

        double initialPullMultiplier = 1.5;
        tempVector.normalize().multiply(pullStrength * initialPullMultiplier);

        if (isClimbable && target == player) {
            tempVector.setY(Math.max(0.4, tempVector.getY()));
        } else {
            tempVector.setY(Math.max(-0.8, Math.min(0.8, tempVector.getY())));
        }

        target.setVelocity(target.getVelocity().add(tempVector));

        if (target instanceof Player p) {
            p.setFallDistance(0);
        }
    }

    private void pullEntity() {
        if (target == null || !target.isValid()) {
            return;
        }

        Vector toOrigin = new Vector(
                originLoc.getX() - target.getLocation().getX(),
                originLoc.getY() - target.getLocation().getY(),
                originLoc.getZ() - target.getLocation().getZ()
        );
        double distance = toOrigin.length();

        if (distance > allowedMovementDistance) {
            Vector pullBack = toOrigin.clone().normalize().multiply(0.4);
            target.setVelocity(pullBack);
            if (target instanceof Player p) {
                p.setFallDistance(0);
            }
            return;
        }

        if (target == player && isClimbable && distance < 1.5) {
            Vector holdVelocity = player.getVelocity().clone();
            holdVelocity.setY(Math.max(0, holdVelocity.getY()));
            player.setVelocity(holdVelocity);
            player.setFallDistance(0);
            return;
        }

        if (target != player && distance < 1.5) {
            Vector centeringForce = toOrigin.clone().normalize().multiply(0.1);
            target.setVelocity(centeringForce);
            if (target instanceof Player p) {
                p.setFallDistance(0);
            }
            return;
        }

        double pullForce = this.pullStrength;

        if (currentState == State.TENSIONING) {
            Bukkit.getLogger().log(Level.INFO, "Tension: " + this.currentTension);
            if (distance > 7.0) {
                pullForce *= 1.1;
            } else if (distance <= 7.0) {
                pullForce *= 0.7;
            }

            pullForce = pullForce * this.currentTension;
        }

        Vector pullDirection = toOrigin.clone().normalize();

        Vector currentVelocity = target.getVelocity();

        Vector pullVector = pullDirection.clone().multiply(pullForce);

        if (!(isClimbable && target == player && distance < 6.5)) {
            pullVector.setY(pullVector.getY() - 0.05);
        }

        if (target == player) {
            Vector horizontalVelocity = currentVelocity.clone().setY(0);
            double dotProduct = horizontalVelocity.dot(pullDirection.clone().setY(0));
            Vector parallelComponent = pullDirection.clone().setY(0).multiply(dotProduct);
            Vector perpendicularComponent = horizontalVelocity.clone().subtract(parallelComponent);

            perpendicularComponent.multiply(0.7);

            pullVector.add(perpendicularComponent);
        }

        target.setVelocity(pullVector);

        if (target instanceof Player p) {
            p.setFallDistance(0);
        }
    }

    private void removeDueToCompletion() {
        if (targetingOtherEntity) {
            bPlayer.addCooldown(this, entityCooldown);
        } else {
            bPlayer.addCooldown(this, selfCooldown);
        }
        remove();
    }

    private void removeDueToFailure() {
        bPlayer.addCooldown(this, selfCooldown);
        remove();
    }

    @Override
    public void remove() {
        if (this.vineAnimation != null) {
            for (ManagedDisplay display : vineAnimation.getManagedDisplays()) {
                playPlantbendingParticles(display.getBukkitEntity().getLocation(), 1, 0.5, 0.5, 0.5);
                display.getBukkitEntity().getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, display.getBukkitEntity().getLocation(), 0, 0.5, 0.0, 0.5, 0.0, null, true);
                Color darkGreen = Color.fromRGB(10, 10, 10);
                Particle.DustOptions plantDust = new Particle.DustOptions(darkGreen, 0.25f);
                if (Math.random() < 0.35) {
                    playPlantbendingParticles(display.getBukkitEntity().getLocation(), 1, 0.0, 0.0, 0.0);
                    //display.getBukkitEntity().getWorld().spawnParticle(Particle.DUST, display.getBukkitEntity().getLocation(), 0, 0.0, 0.0, 0.0, 0.0, plantDust, true);
                }
            }
            player.getWorld().playSound(originLoc, Sound.BLOCK_HANGING_ROOTS_BREAK, 2.0f, 1.75f);
            this.vineAnimation.stop(true);
            this.vineAnimation = null;
        }
        super.remove();
    }

    private BlockData getSegmentBlockData(Location originLoc) {
        if (this.dynamicMaterials) {
            return originLoc.getBlock().getBlockData();
        } else {
            try {
                Material mat = Material.matchMaterial(this.segmentMaterial);
                if (mat != null && mat.isBlock()) {
                    return mat.createBlockData();
                }
            } catch (Exception e) {
                ProjectKorra.log.warning("PlantTether: Invalid SegmentMaterial configured: " + this.segmentMaterial + ". Defaulting to KELP_PLANT.");
            }
        }

        return Material.OAK_LEAVES.createBlockData();
    }

    public boolean isSelected() {
        return currentState == State.SELECTED;
    }

    public State getCurrentState() {
        return this.currentState;
    }

    @Override
    public long getCooldown() {
        return 0; // dynamic cooldown
    }

    @Override
    public String getName() {
        return "VineManipulation";
    }

    @Override
    public Location getLocation() {
        return originLoc; // target block
    }

    @Override
    public ArrayList<Location> getLocations() {
        ArrayList<Location> locations = new ArrayList<>();
        for (ManagedDisplay display : vineAnimation.getManagedDisplays()) {
            locations.add(display.getBukkitEntity().getLocation());
        }
        return locations;
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void load() {
        DisplayManager.initialize(ProjectKorra.plugin);

        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(new VineManipulationListener(), ProjectKorra.plugin);

        String path = "Abilities.Water.VineManipulation.";
        FileConfiguration config = ConfigManager.defaultConfig.get();

        config.addDefault(path + "Enabled", true);
        config.addDefault(path + "SelectRange", 18.0);
        config.addDefault(path + "PullStrength", 0.65);
        config.addDefault(path + "Cooldown.Self", 1000);
        config.addDefault(path + "Cooldown.Entity", 5500);
        config.addDefault(path + "Duration.Pull", 6000);
        config.addDefault(path + "Duration.Hang", 25000);

        config.addDefault(path + "Vine.ExtensionSpeed", 32.0);
        config.addDefault(path + "Vine.Segment.Material", "CAVE_VINES");
        config.addDefault(path + "Vine.Segment.DynamicMaterials", false);
        config.addDefault(path + "Vine.Length", 26.0);

        config.addDefault(path + "Vine.Performance.MaxSegments", 104);
        config.addDefault(path + "Vine.Performance.MinSegments", 3);
        config.addDefault(path + "Vine.Performance.DesiredSpacing", 0.25);
        config.addDefault(path + "Vine.Performance.FabrikIterations", 15);
        config.addDefault(path + "Vine.Performance.FabrikTolerance", 0.01);
        config.addDefault(path + "Vine.GravitySag", 0.75);
        config.addDefault(path + "Vine.Segment.MaxAngleConstraint", 135);
        config.addDefault(path + "Vine.DisplayScale", 0.15);
        config.addDefault(path + "Vine.Stiffness", 0.1);

        config.addDefault(path + "Tension.MaxForce", 3.0);
        config.addDefault(path + "Tension.ArcConstraint", 70.0);
        config.addDefault(path + "Tension.AllowedSlack", 1.5);
        config.addDefault(path + "Tension.MaxTensionDistance", 26.0);

        ConfigManager.defaultConfig.save();

        FileConfiguration lang = ConfigManager.languageConfig.get();

        lang.addDefault("Abilities.Water.VineManipulation.Description", "PlantBenders can launch a fast-growing vine from a plant source to tether entities or create climbable ropes. " +
                "The vine extends rapidly towards the target, pulling them upon attachment or allowing the bender to ascend/descend if anchored appropriately.");

        lang.addDefault("Abilities.Water.VineManipulation.Instructions", "Tap sneak on a plant source. " +
                "Left click an entity or air to launch the vine. " +
                "If targeting the bottom face of a block, the vine becomes climbable, or a tether for your victims. " +
                "Hold the vine and move to increase tension before jumping to increase your swing velocity. " +
                "While climbing: look up/down to ascend/descend. " +
                "Sneak again to cancel.");

        ConfigManager.languageConfig.save();

        setupCollisions();

        ProjectKorra.log.info(getName() + " by " + getAuthor() + " loaded.");
    }

    private void setupCollisions() {
        ProjectKorra.getCollisionManager().addCollision(new Collision(this, null, true, false));
    }

    @Override
    public boolean isCollidable() {
        return this.isStarted(); // maybe add the snapped boolean
    }

    @Override
    public double getCollisionRadius() {
        return 1.0;
    }

    @Override
    public void handleCollision(Collision collision) {
        if (collision == null) return;

        Bukkit.getLogger().log(Level.INFO, "Handling collision with vine...");

        if (collision.isRemovingFirst() && this.equals(collision.getAbilityFirst())) {

            Bukkit.getLogger().log(Level.INFO, "Vine is removing first...");

            Location collisionLocation = collision.getLocationFirst();

            if (collisionLocation != null && vineAnimation != null && vineAnimation.cutChainAt(collisionLocation)) {
                Bukkit.getLogger().log(Level.INFO, "Cut the vine at " + collisionLocation.toString());

                collisionLocation.getWorld().playSound(collisionLocation,
                        org.bukkit.Sound.BLOCK_VINE_BREAK, 1.0f, 0.8f);


                playPlantbendingParticles(collisionLocation, 15, 0.5, 0.5, 0.5);

                this.vineAnimation.snapped = true;
            }
        }
    }

    @Override
    public void stop() {
        ProjectKorra.log.info(getName() + " stopped.");
        // should be in onDisable
        DisplayManager.shutdown();
    }

    @Override
    public String getAuthor() {
        return "ProjectKorra";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    public DisplayManager getDisplayManager() {
        // should be in onEnable
        if (DisplayManager.getInstance() == null) {
            throw new IllegalStateException("DisplayManager accessed before ProjectKorra enabled or after disabled!");
        }
        return DisplayManager.getInstance();
    }
}
