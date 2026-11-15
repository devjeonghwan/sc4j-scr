package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the transport capacity of a unit at {@code 0} while its owner lacks the transport upgrade of its type
 * ({@link ScrUnitTypeView#getTransportUpgrade}, Ventral Sacs for the Overlord): the SC:R implementation of the transport capacity point. The
 * hook holds no state of its own; it finds the SC:R game when it is attached.
 */
public final class TransportUpgradeHook implements TransportCapacityHook, GameSystem {
    private @Nullable Game game;

    /**
     * Creates a {@link TransportUpgradeHook} instance; it reads the SC:R game it finds when it is attached.
     */
    public TransportUpgradeHook() {
        this.game = null;
    }

    /**
     * Resolves what this system uses from the game. Called once, after every system exists.
     * <p>
     * Binds the SC:R game system of the game.
     *
     * @param game the game
     */
    @Override
    public void attach(org.fogofwarlabs.sc4j.engine.game.@NotNull Game game) {
        this.game = game.getModSystem(Game.SYSTEM_KEY);
    }

    /**
     * Returns the transport capacity of a unit after this hook.
     * <p>
     * A type with a transport upgrade keeps its capacity only once its owner has the upgrade.
     *
     * @param unit     the unit, no hallucination
     * @param capacity the capacity before this hook
     * @return the capacity after this hook
     * @throws UnsupportedOperationException if the type needs a transport upgrade and the player upgrade levels are not loaded
     * @throws IllegalStateException         if the hook is not attached
     * @throws NullPointerException          if the world was built without game data
     */
    @Override
    public int getTransportCapacity(@NotNull Unit unit, int capacity) {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R transport upgrade hook is not attached.");
        }

        int upgrade = attached.requireGameData().getUnitTypes().getTransportUpgrade(unit.getUnitId());

        if (upgrade == ScrUpgradeIds.NONE) {
            return capacity;
        }

        PlayerUpgrades upgrades = attached.getPlayerTable().getPlayerUpgrades();

        if (upgrades == null) {
            throw Exceptions.newUnsupportedOperationException("Transport space of an upgraded transport requires player upgrade levels.");
        }

        return upgrades.getLevel(unit.getOwnerIndex(), upgrade) == 0 ? 0 : capacity;
    }
}
