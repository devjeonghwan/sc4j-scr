package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Evaluates computer-player unit predicates and forwards unit lifecycle notifications to the computer-player phase.
 * <p>
 * Type flags read the {@code units.dat} Special Ability Flags.
 */
public final class ComputerUnits {
    /**
     * Orders {@code DRONE_START_BUILD + bit} of a worker walking to place its queued building: Drone Start Build, Drone
     * Build, Place Building, Place Protoss Building and Drone Land.
     */
    private static final long BUILDING_ORDER_OFFSETS = 0x200000000063L;
    /**
     * The highest bit of {@link #BUILDING_ORDER_OFFSETS} the test reads.
     */
    private static final int BUILDING_ORDER_LAST_OFFSET = 0x2D;

    /**
     * @param unit the unit
     * @return {@code true} if the unit has no sprite or its primary order is Die, {@code false} otherwise
     */
    public static boolean isDeadOrHidden(@NotNull Unit unit) {
        return unit.getSpriteOrNull() == null || unit.getPrimaryOrder() == ScrOrderIds.DIE;
    }

    /**
     * @param unit the unit
     * @return {@code true} if the unit is incomplete or completing an Archon merge or collision reset, {@code false} otherwise
     */
    static boolean isUnavailableForTown(@NotNull Unit unit) {
        if ((unit.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
            return true;
        }

        int order = unit.getPrimaryOrder();

        return order == ScrOrderIds.COMPLETING_ARCHON_SUMMON || order == ScrOrderIds.RESET_COLLISION;
    }

    /**
     * @param race the player race BYTE
     * @return the race's first resource depot type
     */
    static int getResourceDepotType(int race) {
        return ScrRaces.getTownHallId(getDefinedRace(race));
    }

    /**
     * @param race the player race BYTE
     * @return the race's refinery type
     */
    static int getRefineryType(int race) {
        return ScrRaces.getRefineryId(getDefinedRace(race));
    }

    /**
     * @param race the player race BYTE
     * @return the race's worker type
     */
    static int getWorkerType(int race) {
        return ScrRaces.getWorkerId(getDefinedRace(race));
    }

    /**
     * @param race the player race BYTE
     * @return the race whose definition the race branches read: Zerg and Terran themselves, every other value Protoss
     */
    private static int getDefinedRace(int race) {
        return race == ScrRaceIds.TERRAN || race == ScrRaceIds.ZERG ? race : ScrRaceIds.PROTOSS;
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link ComputerUnits} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public ComputerUnits(@NotNull Game game) {
        this.game = game;
    }

    /**
     * @param unit the unit
     * @return {@code true} if the base type of the unit is one of the six critters, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    boolean isCritter(@NotNull Unit unit) {
        int type = this.game.requireGameData().getBaseType(unit);

        return type == ScrUnitIds.CRITTER_RHYNADON || type == ScrUnitIds.CRITTER_BENGALAAS ||
               type >= ScrUnitIds.CRITTER_SCANTID && type <= ScrUnitIds.CRITTER_URSADON;
    }

    /**
     * @param unit the unit
     * @return {@code true} if the type is a worker, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    public boolean isWorker(@NotNull Unit unit) {
        int type = unit.getUnitId();
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();

        return units.hasEntry(type) && (units.getFlags(type) & UnitTypeFlags.WORKER) != 0;
    }

    /**
     * Tests the subunit flag without a separate type-bound check.
     *
     * @param unit the unit
     * @return {@code true} if the type is a subunit, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    boolean isSubunit(@NotNull Unit unit) {
        return (this.game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.SUBUNIT) != 0;
    }

    /**
     * Returns whether the unit is an enemy of the owner; a neutral unit resolves its owner through the sprite owner.
     *
     * @param owner the evaluating player
     * @param unit  the evaluated unit
     * @return {@code true} if the alliance BYTE is zero, {@code false} otherwise
     */
    boolean isEnemy(int owner, @NotNull Unit unit) {
        return this.game.getUnitTargeting().isEnemy(owner, unit);
    }

    /**
     * @param unit the unit
     * @return {@code true} if the unit is a building other than a Vespene Geyser, a Larva, an Egg or an Overlord, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    boolean isTownBuilding(@NotNull Unit unit) {
        int type = this.game.requireGameData().getBaseType(unit);

        if (UnitQueries.isBuilding(this.game.requireGameData().getUnitTypes(), unit.getUnitId()) && type != ScrUnitIds.RESOURCE_VESPENE_GEYSER) {
            return true;
        }

        return type == ScrUnitIds.ZERG_LARVA || type == ScrUnitIds.ZERG_EGG || type == ScrUnitIds.ZERG_OVERLORD;
    }


    /**
     * Counts the units of a native type a player has as the computer player counts them: the units of the type and of every added unit type
     * whose base type it is ({@link GameData#getBaseType(int)}).
     *
     * @param unitType the native unit type
     * @param player   the player slot
     * @return the unit count, including incomplete units
     * @throws UnsupportedOperationException if the unit accounting is not initialized
     * @throws NullPointerException          if the world was built without game data
     */
    int getTypeCount(int unitType, int player) {
        return this.countTypeUnits(unitType, player, false);
    }

    /**
     * Counts the completed units of a native type a player has as the computer player counts them: the units of the type and of every added
     * unit type whose base type it is ({@link GameData#getBaseType(int)}).
     *
     * @param unitType the native unit type
     * @param player   the player slot
     * @return the completed unit count
     * @throws UnsupportedOperationException if the unit accounting is not initialized
     * @throws NullPointerException          if the world was built without game data
     */
    int getCompletedTypeCount(int unitType, int player) {
        return this.countTypeUnits(unitType, player, true);
    }

    private int countTypeUnits(int unitType, int player, boolean completed) {
        UnitAccounting accounting = this.game.getPlayerTable().requireUnitAccounting();
        int count = completed ? accounting.getCompletedTypeCount(unitType, player) : accounting.getTypeCount(unitType, player);
        GameData gameData = this.game.requireGameData();
        int addedCount = gameData.getAddedUnitTypes().getCount();

        for (int added = 0; added < addedCount; added++) {
            int addedType = ScrAddedUnitTypes.FIRST_ID + added;

            if (gameData.getBaseType(addedType) == unitType) {
                count += completed ? accounting.getCompletedTypeCount(addedType, player) : accounting.getTypeCount(addedType, player);
            }
        }

        return count;
    }

    /**
     * Counts how many units of the type the unit is producing. A worker counts its queued building while it walks to
     * build it (orders {@code 25}, {@code 26}, {@code 30}, {@code 31} or {@code 70} without a target); an egg or a morphing
     * building counts its current production entry ({@link UnitQueries#getQueuedType}); other units count their five
     * queue slots.
     *
     * @param unit the producer
     * @param type the requested unit type
     * @return the number of matching production slots
     */
    int countProducing(@NotNull Unit unit, int type) {
        if (this.isWorker(unit)) {
            int order = unit.getPrimaryOrder();
            Unit target;
            int secondary = unit.getSecondaryOrder();

            if (order == ScrOrderIds.PLACE_BUILDING || order == ScrOrderIds.CONSTRUCTING_BUILDING || order == ScrOrderIds.REPAIR ||
                UnitQueries.isPlacingProtossBuilding(unit)) {
                target = unit.getOrderTargetUnit();
            } else if (secondary == ScrOrderIds.TRAIN || secondary == ScrOrderIds.TRAIN_FIGHTER || UnitQueries.isBuildingAddon(unit)) {
                target = unit.getCurrentBuildUnit();
            } else {
                target = null;
            }

            if (target != null) {
                return 0;
            }

            if (UnitQueries.getQueueSlotType(unit, 0) != type) {
                return 0;
            }

            int stage = order - ScrOrderIds.DRONE_START_BUILD;

            return stage >= 0 && stage <= BUILDING_ORDER_LAST_OFFSET && (BUILDING_ORDER_OFFSETS >>> stage & 1) != 0 ? 1 : 0;
        }

        int unitType = unit.getUnitId();
        int order = unit.getPrimaryOrder();

        if (this.game.requireGameData().getUnitTypes().isEgg(unitType) || order == ScrOrderIds.ZERG_BUILDING_MORPH ||
            order == ScrOrderIds.INCOMPLETE_MORPHING) {
            return UnitQueries.getQueuedType(unit) == type ? 1 : 0;
        }

        int count = 0;

        for (int offset = 0; offset < Unit.BUILD_QUEUE_SLOTS; offset++) {
            if (UnitQueries.getQueueSlotType(unit, offset) == type) {
                count++;
            }
        }

        return count;
    }

    /**
     * Calls {@link ComputerPlayerPhase#registerCreatedUnit} when the game has a computer player.
     *
     * @param producer         the producing or transforming unit
     * @param unit             the created unit
     * @param instructionLimit the iscript budget for issued orders
     */
    public void notifyCreated(@NotNull Unit producer, @NotNull Unit unit, int instructionLimit) {
        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

        if (phase != null && phase.hasComputerPlayers()) {
            phase.registerCreatedUnit(producer, unit, instructionLimit);
        }
    }

    /**
     * Credits returned resources to the owner's computer budget. The resource return calls it for every owner, so the
     * amount counts immediately, not only at the next player update.
     *
     * @param owner    the returning worker's owner
     * @param minerals the returned minerals
     * @param gas      the returned gas
     */
    public void notifyResourcesReturned(int owner, int minerals, int gas) {
        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

        if (phase == null || owner < 0 || owner >= PlayerTable.PLAYABLE_PLAYER_SLOTS) {
            return;
        }

        ComputerPlayer player = phase.findPlayer(owner);

        if (player != null) {
            player.creditBudgets(minerals, gas);
        }
    }

    /**
     * Calls {@link ComputerPlayerPhase#registerCompletedUnit} when the game has a computer player.
     *
     * @param producer         the producing or transforming unit
     * @param unit             the completed unit
     * @param instructionLimit the iscript budget for issued orders
     */
    public void notifyCompleted(@NotNull Unit producer, @NotNull Unit unit, int instructionLimit) {
        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

        if (phase != null && phase.hasComputerPlayers()) {
            phase.registerCompletedUnit(producer, unit, instructionLimit);
        }
    }

    /**
     * Calls {@link ComputerPlayerPhase#rebindWorker} when the game has a computer player.
     *
     * @param unit             the worker that became a building
     * @param instructionLimit the iscript budget for issued orders
     */
    public void notifyWorkerTransformed(@NotNull Unit unit, int instructionLimit) {
        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

        if (phase != null && phase.hasComputerPlayers()) {
            phase.rebindWorker(unit, instructionLimit);
        }
    }

    /**
     * Calls {@link ComputerPlayerPhase#rebindBuilding} when the game has a computer player.
     *
     * @param unit             the building that became a unit
     * @param instructionLimit the iscript budget for issued orders
     */
    public void notifyBuildingTransformed(@NotNull Unit unit, int instructionLimit) {
        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

        if (phase != null && phase.hasComputerPlayers()) {
            phase.rebindBuilding(unit, instructionLimit);
        }
    }

    /**
     * Calls {@link ComputerPlayerPhase#removeUnit} when the game has a computer player.
     *
     * @param unit  the dying or transferred unit
     * @param death whether the unit is dying
     */
    public void notifyRemoved(@NotNull Unit unit, boolean death) {
        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

        if (phase != null && phase.hasComputerPlayers()) {
            phase.removeUnit(unit, death);
        }
    }

    /**
     * Calls {@link ComputerPlayerPhase#adoptUnit} when the game has a computer player.
     *
     * @param unit             the created unit or the unit that changed owner
     * @param instructionLimit the iscript budget for issued orders
     */
    public void notifyAdopted(@NotNull Unit unit, int instructionLimit) {
        ComputerPlayerPhase phase = this.game.getComputerPlayerPhase();

        if (phase != null && phase.hasComputerPlayers()) {
            phase.adoptUnit(unit, instructionLimit);
        }
    }
}
