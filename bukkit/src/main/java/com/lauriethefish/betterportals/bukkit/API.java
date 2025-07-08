package com.lauriethefish.betterportals.bukkit;

import com.google.inject.Inject;
import com.lauriethefish.betterportals.api.*;
import com.lauriethefish.betterportals.bukkit.portal.IPortal;
import com.lauriethefish.betterportals.bukkit.portal.IPortalManager;
import com.lauriethefish.betterportals.bukkit.portal.predicate.IPortalPredicateManager;
import com.lauriethefish.betterportals.shared.logging.Logger;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Implementation of the BetterPortals API
 */
public class API extends BetterPortalsAPI {
    private final Logger logger;
    private final IPortal.Factory portalFactory;
    private final IPortalManager portalManager;
    private final IPortalPredicateManager portalPredicateManager;

    @Inject
    public API(Logger logger, IPortal.Factory portalFactory, IPortalManager portalManager, IPortalPredicateManager portalPredicateManager) {
        this.logger = logger;
        this.portalFactory = portalFactory;
        this.portalManager = portalManager;
        this.portalPredicateManager = portalPredicateManager;

        onEnable();
    }

    public void onEnable() {
        logger.fine("Setting API instance");
        BetterPortalsAPI.setInstance(this);
    }

    public void onDisable() {
        logger.fine("Removing API instance");
        BetterPortalsAPI.setInstance(null);
    }

    /**
     * Checks that the plugin is enabled before allowing an API call
     */
    private void verifyEnabled() {
        BetterPortalsAPI.get(); // Throws IllegalStateException if disabled
    }

    @Override
    public @NotNull BetterPortal createPortal(@NotNull PortalPosition originPosition, @NotNull PortalPosition destinationPosition, @NotNull Vector size, @Nullable UUID owner, @Nullable String name, boolean isCustom) {
        verifyEnabled();

        IPortal portal = portalFactory.create(originPosition, destinationPosition, size, isCustom, UUID.randomUUID(), owner, name, true, true);
        portalManager.registerPortal(portal);

        return portal;
    }

    @Override
    public void addPortalActivationPredicate(@NotNull PortalPredicate predicate) {
        verifyEnabled();

        portalPredicateManager.addActivationPredicate(predicate);
    }

    @Override
    public void removePortalActivationPredicate(@NotNull PortalPredicate predicate) {
        verifyEnabled();

        if(!portalPredicateManager.removeActivationPredicate(predicate)) {
            throw new UnknownPredicateException(predicate);
        }
    }

    @Override
    public void addPortalViewPredicate(@NotNull PortalPredicate predicate) {
        verifyEnabled();

        portalPredicateManager.addViewPredicate(predicate);
    }

    @Override
    public void removePortalViewPredicate(@NotNull PortalPredicate predicate) {
        verifyEnabled();

        if(!portalPredicateManager.removeViewPredicate(predicate)) {
            throw new UnknownPredicateException(predicate);
        }
    }

    @Override
    public void addPortalTeleportPredicate(@NotNull PortalPredicate predicate) {
        verifyEnabled();

        portalPredicateManager.addTeleportPredicate(predicate);
    }

    @Override
    public void removePortalTeleportPredicate(@NotNull PortalPredicate predicate) {
        verifyEnabled();

        if(!portalPredicateManager.removeTeleportPredicate(predicate)) {
            throw new UnknownPredicateException(predicate);
        }
    }

    @Override
    public BetterPortal getPortalById(@NotNull UUID id) {
        verifyEnabled();
        return portalManager.getPortalById(id);
    }
}
