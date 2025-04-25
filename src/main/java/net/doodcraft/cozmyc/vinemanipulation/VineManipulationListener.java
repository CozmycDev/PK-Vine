package net.doodcraft.cozmyc.vinemanipulation;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class VineManipulationListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;

        String boundAbility = bPlayer.getBoundAbilityName();
        if (!event.isSneaking() || !boundAbility.equalsIgnoreCase("vinemanipulation")) return;

        VineManipulation tether = CoreAbility.getAbility(player, VineManipulation.class);
        if (tether == null) {
            new VineManipulation(player);
        } else {
            if (tether.getCurrentState() != VineManipulation.State.SELECTED) {
                bPlayer.addCooldown(tether, tether.targetingOtherEntity ? tether.entityCooldown : tether.selfCooldown);
            }
            tether.remove();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null || !bPlayer.getBoundAbilityName().equalsIgnoreCase("vinemanipulation")) return;

        VineManipulation tether = CoreAbility.getAbility(player, VineManipulation.class);
        if (tether != null && tether.isSelected()) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                BlockFace face = event.getBlockFace();
                Entity targetEntity;

                if (face == BlockFace.DOWN && VineManipulation.isPlant(event.getClickedBlock())) {
                    targetEntity = player;
                } else {
                    targetEntity = GeneralMethods.getTargetedEntity(player, tether.selectRange);
                    if (targetEntity == null || targetEntity == player) {
                        targetEntity = player;
                    }
                }

                if (targetEntity != null) {
                    tether.activatePull(targetEntity);
                    event.setCancelled(true);
                }
            } else if (event.getAction() == Action.LEFT_CLICK_AIR) {
                Entity targetEntity = GeneralMethods.getTargetedEntity(player, tether.selectRange);
                if (targetEntity == null || targetEntity == player) {
                    targetEntity = player;
                }
                tether.activatePull(targetEntity);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null || !bPlayer.getBoundAbilityName().equalsIgnoreCase("vinemanipulation")) return;

        VineManipulation tether = CoreAbility.getAbility(player, VineManipulation.class);
        if (tether != null && tether.isSelected()) {
            tether.activatePull(event.getRightClicked());
            event.setCancelled(true);
        }
    }
}
