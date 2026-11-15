package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrTechTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUpgradeTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.presentation.CommandErrorEvents;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitRacesUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the cost tests of a game and keeps the costs they last tested per player: the mineral, gas and supply DWORD arrays.
 * <p>
 * Every test first stores the tested costs of its player there, then compares; the spend subtracts the stored mineral and gas costs, whatever test
 * stored them. The unit test stores the supply only when it tests supply, the technology and upgrade tests store a supply of {@code 0}. A failing
 * test with the error display on reports through {@link CommandErrorEvents}. The arrays live as long as this game and start at {@code 0}.
 */
public final class CostTests {
    /**
     * The mask of the WORD upgrade cost results.
     */
    private static final int WORD_MASK = 0xFFFF;

    private final int @NotNull [] mineralCosts;
    private final int @NotNull [] gasCosts;
    private final int @NotNull [] supplyCosts;

    /**
     * Creates a {@link CostTests} instance with all last tested costs {@code 0}.
     */
    public CostTests() {
        this.mineralCosts = new int[PlayerTable.PLAYER_SLOTS];
        this.gasCosts     = new int[PlayerTable.PLAYER_SLOTS];
        this.supplyCosts  = new int[PlayerTable.PLAYER_SLOTS];
    }

    /**
     * @return the last tested mineral costs per player, live
     */
    public int @NotNull [] getMineralCosts() {
        return this.mineralCosts;
    }

    /**
     * @return the last tested gas costs per player, live
     */
    public int @NotNull [] getGasCosts() {
        return this.gasCosts;
    }

    /**
     * @return the last tested supply costs per player, live
     */
    public int @NotNull [] getSupplyCosts() {
        return this.supplyCosts;
    }


    /**
     * Applies the unit cost test: stores the type's mineral and gas costs, then, when supply is tested, runs the supply test ({@link #testSupply}),
     * then the resource test ({@link #testResources}). The type is not checked: the empty queue type {@code 228} reads the units.dat memory past the
     * cost and supply columns ({@link ScrUnitTypeView}).
     *
     * @param game        the game
     * @param owner       the player
     * @param unitType    the units.dat type
     * @param checkSupply whether supply is tested
     * @param showError   whether a failure shows its error
     * @return {@code true} if the player can pay, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    public boolean testUnit(@NotNull Game game, int owner, int unitType, boolean checkSupply, boolean showError) {
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();

        this.mineralCosts[owner] = units.getMineralCost(unitType);
        this.gasCosts[owner]     = units.getGasCost(unitType);

        if (checkSupply && !this.testSupply(game, owner, unitType, showError)) {
            return false;
        }

        return this.testResources(game, owner, showError);
    }

    /**
     * Applies the supply test: stores the type's supply ({@code 2} times it for a {@link UnitTypeFlags#TWO_UNITS_IN_ONE_EGG} type); a type without
     * supply or with the supply-check exemption passes. A Lurker's stored supply then drops by the Hydralisk's. The used supply of the type's race
     * plus the stored supply must not exceed the maximum and the available supply less the stored supply must not be negative.
     *
     * @param game      the game
     * @param owner     the player
     * @param unitType  the units.dat type
     * @param showError whether a failure shows its error
     * @return {@code true} if the supply fits, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    public boolean testSupply(@NotNull Game game, int owner, int unitType, boolean showError) {
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();
        int flags = units.getFlags(unitType);
        int supply = units.getSupplyRequired(unitType);

        if ((flags & UnitTypeFlags.TWO_UNITS_IN_ONE_EGG) != 0) {
            supply *= UnitMorphing.OFFSPRING_COUNT;
        }

        this.supplyCosts[owner] = supply;

        if (supply == 0 || (flags & UnitTypeFlags.IGNORE_SUPPLY_CHECK) != 0) {
            return true;
        }

        if (game.getBaseType(unitType) == ScrUnitIds.ZERG_LURKER) {
                                      supply -= units.getSupplyRequired(ScrUnitIds.ZERG_HYDRALISK);
            this.supplyCosts[owner] = supply;
        }

        int result = game.getSupplyCheck().check(unitType, owner, supply);

        if (result == SupplyCheck.PASSED) {
            return true;
        }

        if (showError) {
            game.getInterfaceLink().getCommandErrorEvents().raiseSupply(owner,
                                                                        UnitRacesUtils.getSupplyRace(units, unitType),
                                                                        result == SupplyCheck.LIMIT_EXCEEDED);
        }

        return false;
    }

    /**
     * Applies the technology cost test: stores the technology's mineral and gas costs and a supply of {@code 0}, then runs the resource test.
     *
     * @param game       the game
     * @param owner      the player
     * @param technology the techdata.dat identifier
     * @param showError  whether a failure shows its error
     * @return {@code true} if the player can pay, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    public boolean testTechnology(@NotNull Game game, int owner, int technology, boolean showError) {
        ScrTechTypeView technologies = game.requireGameData().getTechTypes();

        this.mineralCosts[owner] = technologies.getMineralCost(technology);
        this.gasCosts[owner]     = technologies.getGasCost(technology);
        this.supplyCosts[owner]  = 0;

        return this.testResources(game, owner, showError);
    }

    /**
     * Applies the upgrade cost test: stores the next level's mineral and gas costs (WORD results) and a supply of {@code 0}, then runs the resource
     * test.
     *
     * @param game      the game
     * @param owner     the player
     * @param upgrade   the upgrades.dat identifier
     * @param showError whether a failure shows its error
     * @return {@code true} if the player can pay, {@code false} otherwise
     * @throws IllegalStateException if the player upgrade storage is missing
     * @throws NullPointerException  if the world was built without game data
     */
    public boolean testUpgrade(@NotNull Game game, int owner, int upgrade, boolean showError) {
        ScrUpgradeTypeView upgrades = game.requireGameData().getUpgradeTypes();
        PlayerUpgrades playerUpgrades = game.getPlayerTable().getPlayerUpgrades();

        if (playerUpgrades == null) {
            throw Exceptions.newIllegalStateException("Upgrade cost tests require the player upgrade storage.");
        }

        int level = playerUpgrades.getLevel(owner, upgrade);

        this.mineralCosts[owner] = upgrades.getMineralCost(upgrade) + upgrades.getMineralCostFactor(upgrade) * level & WORD_MASK;
        this.gasCosts[owner]     = upgrades.getGasCost(upgrade) + upgrades.getGasCostFactor(upgrade) * level & WORD_MASK;
        this.supplyCosts[owner]  = 0;

        return this.testResources(game, owner, showError);
    }

    /**
     * Applies the resource test: the player's minerals, then gas, compared (signed) with the stored costs; a failure shows the not-enough-minerals or
     * not-enough-gas message with the console race.
     *
     * @param game      the game
     * @param owner     the player
     * @param showError whether a failure shows its error
     * @return {@code true} if the player can pay, {@code false} otherwise
     */
    private boolean testResources(@NotNull Game game, int owner, boolean showError) {
        Player player = game.getPlayerTable().requirePlayer(owner);

        if (player.getMinerals() < this.mineralCosts[owner]) {
            if (showError) {
                game.getInterfaceLink().getCommandErrorEvents().raiseResources(owner, false);
            }

            return false;
        }

        if (player.getGas() < this.gasCosts[owner]) {
            if (showError) {
                game.getInterfaceLink().getCommandErrorEvents().raiseResources(owner, true);
            }

            return false;
        }

        return true;
    }

    /**
     * Applies the spend: subtracts the player's stored mineral and gas costs.
     *
     * @param game  the game
     * @param owner the player
     */
    public void spend(@NotNull Game game, int owner) {
        Player player = game.getPlayerTable().requirePlayer(owner);

        player.setMinerals(player.getMinerals() - this.mineralCosts[owner]);
        player.setGas(player.getGas() - this.gasCosts[owner]);
    }
}
