package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.economy.ResourceCost;
import org.fogofwarlabs.sc4j.engine.content.economy.ResourceKind;
import org.fogofwarlabs.sc4j.engine.content.faction.Faction;
import org.fogofwarlabs.sc4j.engine.economy.EconomySystem;
import org.fogofwarlabs.sc4j.scr.content.ScrNativeIds;
import org.fogofwarlabs.sc4j.scr.content.ScrResources;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Serves the SC:R economy as the engine {@link EconomySystem}: a view over the native stores, which stay where EUD and saved games address them.
 * The amounts are the minerals ({@link ScrResources#MINERALS}) and gas ({@link ScrResources#GAS}) of the SC:R {@link Player} of the slot; the
 * supply of a faction is the half-supply counter {@code race * 12 + player} of the {@link UnitAccounting}, by the native race of the faction.
 * <p>
 * The view holds no value of its own and is no state hash contributor: the SC:R game mixes the stores. A cost is all or nothing: both amounts are
 * tested before either is taken. The SC:R production keeps its own cost tests ({@code CostTests}), which store the tested costs per player.
 */
public final class ScrEconomySystem implements EconomySystem {
    private static int getCounterIndex(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull Faction faction) {
        return ScrNativeIds.toNative(faction) * PlayerTable.PLAYER_SLOTS + player.getIndex();
    }

    private @Nullable Game game;

    /**
     * Creates a {@link ScrEconomySystem} instance; it serves the SC:R game it finds when it is attached.
     */
    public ScrEconomySystem() {
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
     * Returns the amount of a resource kind a player holds.
     *
     * @param player       the player
     * @param resourceKind the resource kind
     * @return the amount
     * @throws IllegalArgumentException if the kind is no SC:R resource kind
     * @throws IllegalStateException    if the system is not attached or the slot has no SC:R player
     */
    @Override
    public int getAmount(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull ResourceKind resourceKind) {
        Player slot = this.requirePlayer(player);

        if (resourceKind == ScrResources.MINERALS) {
            return slot.getMinerals();
        }

        if (resourceKind == ScrResources.GAS) {
            return slot.getGas();
        }

        throw Exceptions.newIllegalArgumentException("Not an SC:R resource kind: {0}", resourceKind);
    }

    /**
     * Sets the amount of a resource kind a player holds.
     *
     * @param player       the player
     * @param resourceKind the resource kind
     * @param amount       the amount
     * @throws IllegalArgumentException if the kind is no SC:R resource kind
     * @throws IllegalStateException    if the system is not attached or the slot has no SC:R player
     */
    @Override
    public void setAmount(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull ResourceKind resourceKind, int amount) {
        Player slot = this.requirePlayer(player);

        if (resourceKind == ScrResources.MINERALS) {
            slot.setMinerals(amount);
        } else if (resourceKind == ScrResources.GAS) {
            slot.setGas(amount);
        } else {
            throw Exceptions.newIllegalArgumentException("Not an SC:R resource kind: {0}", resourceKind);
        }
    }

    /**
     * Checks whether a player holds every amount of a cost.
     *
     * @param player the player
     * @param cost   the cost
     * @return {@code true} if the player can pay the cost, {@code false} otherwise
     * @throws IllegalArgumentException if the cost names a kind that is no SC:R resource kind
     * @throws IllegalStateException    if the system is not attached or the slot has no SC:R player
     */
    @Override
    public boolean canAfford(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull ResourceCost cost) {
        for (int entry = 0; entry < cost.getCount(); entry++) {
            if (this.getAmount(player, cost.getKind(entry)) < cost.getAmount(entry)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Takes a cost from a player if the player can pay all of it.
     *
     * @param player the player
     * @param cost   the cost
     * @return {@code true} if the cost was taken, {@code false} if nothing changed
     * @throws IllegalArgumentException if the cost names a kind that is no SC:R resource kind
     * @throws IllegalStateException    if the system is not attached or the slot has no SC:R player
     */
    @Override
    public boolean spend(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull ResourceCost cost) {
        if (!this.canAfford(player, cost)) {
            return false;
        }

        for (int entry = 0; entry < cost.getCount(); entry++) {
            ResourceKind kind = cost.getKind(entry);

            this.setAmount(player, kind, this.getAmount(player, kind) - cost.getAmount(entry));
        }

        return true;
    }

    /**
     * Gives a cost back to a player, for example when production is cancelled.
     *
     * @param player the player
     * @param cost   the cost
     * @throws IllegalArgumentException if the cost names a kind that is no SC:R resource kind
     * @throws IllegalStateException    if the system is not attached or the slot has no SC:R player
     */
    @Override
    public void refund(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull ResourceCost cost) {
        for (int entry = 0; entry < cost.getCount(); entry++) {
            ResourceKind kind = cost.getKind(entry);

            this.setAmount(player, kind, this.getAmount(player, kind) + cost.getAmount(entry));
        }
    }

    /**
     * Returns the supply the units of a faction provide to a player: the provided half-supply counter of the faction's native race and player.
     *
     * @param player  the player
     * @param faction the faction
     * @return the provided supply
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if the unit accounting does not exist yet
     */
    @Override
    public int getSupplyProvided(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull Faction faction) {
        return this.requireGame().getPlayerTable().requireUnitAccounting().getSupplyProvided(getCounterIndex(player, faction));
    }

    /**
     * Returns the supply the units of a faction use for a player: the used half-supply counter of the faction's native race and player.
     *
     * @param player  the player
     * @param faction the faction
     * @return the used supply
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if the unit accounting does not exist yet
     */
    @Override
    public int getSupplyUsed(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull Faction faction) {
        return this.requireGame().getPlayerTable().requireUnitAccounting().getSupplyUsed(getCounterIndex(player, faction));
    }

    /**
     * Changes the supply the units of a faction provide to a player.
     *
     * @param player  the player
     * @param faction the faction
     * @param change  the change, negative when a provider is lost
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if the unit accounting does not exist yet
     */
    @Override
    public void addSupplyProvided(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull Faction faction, int change) {
        this.requireGame().getPlayerTable().requireUnitAccounting().addSupplyProvided(getCounterIndex(player, faction), change);
    }

    /**
     * Changes the supply the units of a faction use for a player.
     *
     * @param player  the player
     * @param faction the faction
     * @param change  the change, negative when a unit is lost
     * @throws IllegalStateException         if the system is not attached
     * @throws UnsupportedOperationException if the unit accounting does not exist yet
     */
    @Override
    public void addSupplyUsed(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull Faction faction, int change) {
        this.requireGame().getPlayerTable().requireUnitAccounting().addSupplyUsed(getCounterIndex(player, faction), change);
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R economy system is not attached.");
        }

        return attached;
    }

    private @NotNull Player requirePlayer(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player) {
        Player slot = this.requireGame().getPlayerTable().getPlayer(player.getIndex());

        if (slot == null) {
            throw Exceptions.newIllegalStateException("The SC:R player slot {0} is empty.", player.getIndex());
        }

        return slot;
    }
}
