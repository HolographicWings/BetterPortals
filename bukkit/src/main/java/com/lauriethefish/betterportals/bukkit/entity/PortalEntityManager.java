package com.lauriethefish.betterportals.bukkit.entity;

import com.google.inject.assistedinject.Assisted;
import com.lauriethefish.betterportals.api.PortalPosition;
import com.lauriethefish.betterportals.bukkit.config.MiscConfig;
import com.lauriethefish.betterportals.bukkit.config.RenderConfig;
import com.lauriethefish.betterportals.bukkit.math.MathUtil;
import com.lauriethefish.betterportals.bukkit.math.PortalTransformations;
import com.lauriethefish.betterportals.bukkit.net.IPortalClient;
import com.lauriethefish.betterportals.bukkit.player.IPlayerData;
import com.lauriethefish.betterportals.bukkit.player.IPlayerDataManager;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.bukkit.portal.predicate.IPortalPredicateManager;
import com.lauriethefish.betterportals.shared.logging.Logger;
import com.lauriethefish.betterportals.shared.net.RequestException;
import com.lauriethefish.betterportals.shared.net.requests.TeleportRequest;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import jakarta.inject.Inject;
import java.util.*;
import java.util.function.Consumer;

// Stores the two lists of entities at the origin and destination of a portal
// (or only 1 if specified)
public class PortalEntityManager implements IPortalEntityManager {
    private final IPortal portal;
    private final MiscConfig miscConfig;
    private final RenderConfig renderConfig;
    private final IPortalPredicateManager predicateManager;
    private final Logger logger;
    private final IPortalClient portalClient;
    private final Set<Player> alreadyTeleporting = new HashSet<>();
    private final JavaPlugin pl;
    private final IEntityFinder entityFinder;
    private final IPlayerDataManager playerDataManager;

    private final boolean requireDestination;

    @Getter private Collection<Entity> destinationEntities = null;
    private Map<Entity, Location> originEntities = null;

    @Inject
    public PortalEntityManager(@Assisted IPortal portal, @Assisted boolean requireDestination, MiscConfig miscConfig, RenderConfig renderConfig, IPortalPredicateManager predicateManager, Logger logger, IPortalClient
            portalClient, JavaPlugin pl, IEntityFinder entityFinder, IPlayerDataManager playerDataManager) {
        this.portal = portal;
        this.requireDestination = requireDestination;
        this.miscConfig = miscConfig;
        this.renderConfig = renderConfig;
        this.predicateManager = predicateManager;
        this.logger = logger;
        this.portalClient = portalClient;
        this.pl = pl;
        this.entityFinder = entityFinder;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public void update(int ticksSinceActivated) {
        // Only update the entity lists when it's time to via the entity check interval
        if(ticksSinceActivated % miscConfig.getEntityCheckInterval() == 0) {
            updateEntityLists();
        }

        handleTeleportation();
    }

    private void updateEntityLists() {
        if(requireDestination) {
            destinationEntities = getNearbyEntities(destinationEntities, portal.getDestPos());
        }

        // Copy the old entities over to a new hashmap
        Map<Entity, Location> oldOriginEntities = originEntities;
        originEntities = new HashMap<>();

        getNearbyEntities(portal.getOriginPos(), entity -> {
            Location oldLocation = oldOriginEntities == null ? null : oldOriginEntities.get(entity);
            originEntities.put(entity, oldLocation != null ? oldLocation : entity.getLocation());
        });
    }

    private void handleTeleportation() {
        List<Entity> toRemove = new ArrayList<>();

        // Check each entity at the origin to see if it teleported
        for(Map.Entry<Entity, Location> entry : originEntities.entrySet()) {
            Entity entity = entry.getKey();
            // Only players can teleport through cross-server portals
            if (!(entity instanceof Player) && (!portal.allowsNonPlayerTeleportation() || portal.isCrossServer())) {
                continue;
            }

            Location lastPosition = entry.getValue();
            Location currentPosition = entity.getLocation();

            // Use an intersection check to see if it moved through the portal
            if (lastPosition != null) {
                boolean didWalkThroughPortal = portal.getTransformations()
                        .createIntersectionChecker(lastPosition.toVector())
                        .checkIfIntersects(currentPosition.toVector());


                if (didWalkThroughPortal && checkCanTeleport(entity)) {
                    if (portal.isCrossServer()) {
                        assert entity instanceof Player;
                        teleportCrossServer((Player) entity);
                    } else {
                        teleportLocal(entity);
                    }
                    toRemove.add(entity);
                    continue;
                }
            }

            entry.setValue(currentPosition);
        }
        toRemove.forEach(originEntities::remove);
    }

    public Collection<Entity> getOriginEntities() {
        return originEntities.keySet();
    }

    private Collection<Entity> getNearbyEntities(@Nullable Collection<Entity> existing, PortalPosition position) {
        return entityFinder.getNearbyEntities(existing, position.getLocation(), renderConfig.getMaxXZ(), renderConfig.getMaxY(), renderConfig.getMaxXZ());
    }

    private void getNearbyEntities(PortalPosition position, Consumer<Entity> sendTo) {
        entityFinder.getNearbyEntities(position.getLocation(), renderConfig.getMaxXZ(), renderConfig.getMaxY(), renderConfig.getMaxXZ(), sendTo);
    }

    /**
     * Verifies that <code>entity</code> can teleport using {@link IPortalPredicateManager}
     * @param entity Entity to check
     * @return Whether it can teleport
     */
    private boolean checkCanTeleport(Entity entity) {
        // Entities riding others do not get teleported by portals.
        // Instead, the vehicle teleports through the portal, and takes its passengers with it.
        if(entity.getVehicle() != null) {
            return false;
        }

        // Enforce teleportation predicates
        if(entity instanceof Player) {
            return predicateManager.canTeleport(portal, (Player) entity);
        }   else    {
            return true;
        }
    }

    /**
     * Limits the coordinates of <code>preferred</code> to avoid spawning players on top of portals when they're slightly inside the block hitbox
     * @param preferred Position to limit to the hitbox
     * @return The spawn position, or <code>preferred</code> if none was found.
     */
    private @NotNull Location limitToBlockHitbox(@NotNull Location preferred) {
        Location flooredPos = MathUtil.floor(preferred);
        Location blockOffset = preferred.clone().subtract(flooredPos);

        if(blockOffset.getZ() > 0.6 && preferred.clone().add(0.0, 0.0, 1.0).getBlock().getType().isSolid()) {
            blockOffset.setZ(0.6);
        }
        if(blockOffset.getX() > 0.6 && preferred.clone().add(1.0, 0.0, 0.0).getBlock().getType().isSolid()) {
            blockOffset.setX(0.6);
        }
        if(blockOffset.getZ() < 0.4 && preferred.clone().add(0.0, 0.0, -1.0).getBlock().getType().isSolid()) {
            blockOffset.setZ(0.4);
        }
        if(blockOffset.getX() < 0.4 && preferred.clone().add(-1.0, 0.0, 0.0).getBlock().getType().isSolid()) {
            blockOffset.setX(0.4);
        }
        logger.finer("Fixing position. Floored pos: %s. Block offset: %s", flooredPos.toVector(), blockOffset.toVector());

        return blockOffset.add(flooredPos);
    }

    /**
     * Moves the entity from the origin to the destination of the portal.
     * This also preserves/rotates entity velocity and direction.
     * @param entity The entity to be teleported
     */
    private void teleportLocal(Entity entity) {
        PortalTransformations transformations = portal.getTransformations();

        Location destPos;
        destPos = entity.getLocation().subtract(portal.getOriginPos().getVector());
        destPos = transformations.rotateToDestination(destPos.toVector()).toLocation(Objects.requireNonNull(portal.getDestPos().getWorld()));
        destPos.add(portal.getDestPos().getVector());
        destPos.setDirection(transformations.rotateToDestination(entity.getLocation().getDirection()));

        if(entity instanceof Player) {
            destPos = limitToBlockHitbox(destPos);
        }   else    {
            destPos.add(0.0, 0.2, 0.0);
        }

        // Teleporting an entity removes the velocity, so we have to re-add it
        Vector velocity = entity.getVelocity();
        velocity = transformations.rotateToDestination(velocity);

        logger.fine("Teleporting entity with ID %d and of type %s to position %s", entity.getEntityId(), entity.getType(), destPos.toVector());

        boolean handlePassengers = entity.getWorld() != destPos.getWorld();
        List<Entity> passengers = entity.getPassengers();
        if(handlePassengers) {
            for(Entity passenger : passengers) {
                entity.removePassenger(passenger);
                teleportLocal(passenger);
            }
        }


        entity.teleport(destPos);
        entity.setVelocity(velocity);

        if(handlePassengers) {
            passengers.forEach(entity::addPassenger);
        }
    }

    private void teleportCrossServer(Player player) {
        if(alreadyTeleporting.contains(player)) {
            return;
        }

        alreadyTeleporting.add(player);

        IPlayerData playerData = playerDataManager.getPlayerData(player);
        if(playerData == null) {
            logger.warning("Player with unregistered data %s", player.getUniqueId());
            return;
        }
        playerData.freezePortalViews();

        Location destPosition = portal.getTransformations().moveToDestination(player.getLocation());
        destPosition.setDirection(portal.getTransformations().rotateToDestination(player.getLocation().getDirection()));
        Vector destVelocity = portal.getTransformations().rotateToDestination(player.getVelocity());


        TeleportRequest request = new TeleportRequest();
        request.setDestWorldId(portal.getDestPos().getWorldId());
        request.setDestWorldName(portal.getDestPos().getWorldName());
        request.setDestServer(portal.getDestPos().getServerName());
        request.setPlayerId(player.getUniqueId());

        request.setRelocatePlayer(portal.isRelocatePlayer());
        
        request.setDestX(destPosition.getX());
        request.setDestY(destPosition.getY());
        request.setDestZ(destPosition.getZ());
        request.setDestVelX(destVelocity.getX());
        request.setDestVelY(destVelocity.getY());
        request.setDestVelZ(destVelocity.getZ());

        request.setFlying(player.isFlying());
        request.setGliding(player.isGliding());

        request.setDestPitch(destPosition.getPitch());
        request.setDestYaw(destPosition.getYaw());

        portalClient.sendRequestToProxy(request, (response) -> {
            try {
                response.checkForErrors();
                alreadyTeleporting.remove(player);
            }   catch(RequestException ex) {
                if(!pl.isEnabled()) {return;}
                logger.warning("An error occurred while attempting to teleport a player across servers");
                ex.printStackTrace();
            }
        });
    }
}
