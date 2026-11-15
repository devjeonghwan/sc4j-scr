package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.order.ScrOrderSystem;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitTypeTraitsUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Applies training and morph commands to the persistent production queue.
 * <p>
 * {@link ScrRequirementSystem#checkBuild} tests production requirements. Commands retain selection order and charge accepted non-building queue
 * entries immediately.
 */
public final class UnitProduction {
    /**
     * Tests whether the producer is producing, researching or upgrading.
     *
     * @param producer the producing unit
     * @return {@code true} if the producer is producing, researching or upgrading (the busy test of the requirement scripts), {@code false} otherwise
     */
    public static boolean isBusy(@NotNull Unit producer) {
        return UnitQueries.isProducing(producer) || producer.getResearchType() != ScrTechIds.NONE ||
               producer.getUpgradeType() != ScrUpgradeIds.NONE;
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitProduction} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitProduction(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies the production requirement script ({@link ScrRequirementSystem#checkBuild}) for the producer's owner.
     *
     * @param producer the producing unit
     * @param unitType the requested {@code units.dat} type
     * @return {@code true} if the production requirements are satisfied, {@code false} otherwise
     */
    public boolean canBuild(@NotNull Unit producer, int unitType) {
        return this.game.getRequirements().checkBuild(producer, unitType, producer.getOwnerIndex()) == 0;
    }


    /**
     * Enqueues a unit on the player's single selected producer after checking its production requirements.
     *
     * @param owner    the command player
     * @param unitType the requested {@code units.dat} type
     * @return {@code true} if training was accepted, {@code false} otherwise
     * @throws UnsupportedOperationException if selection storage is absent
     */
    public boolean train(int owner, int unitType) {
        UnitSelection selection = this.game.getSelection();

        if (selection == null) {
            throw Exceptions.newUnsupportedOperationException("Training requires initialized selection storage.");
        }

        Unit producer = selection.getSelectedUnit(owner, 0);

        if (producer == null || selection.getSelectedUnit(owner, 1) != null || producer.getOwnerIndex() != owner) {
            return false;
        }

        if (producer.isDestroyed()) {
            return false;
        }

        if (!this.canBuild(producer, unitType) || !UnitTypeTraitsUtils.isQueueUnitType(this.game.requireGameData().getUnitTypes(), unitType)) {
            return false;
        }

        if (this.pushBuildQueue(producer, unitType) < 0) {
            return false;
        }

        ScrOrderSystem.setSecondaryOrder(producer, ScrOrderIds.TRAIN);

        return true;
    }

    /**
     * Enqueues Larva, Mutalisk and Hydralisk morphs in selection order.
     * <p>
     * A Hydralisk morphs only into a Lurker and passes the Lurker Aspect technology-use test ({@link ScrRequirementSystem#checkTechnologyUse}); a
     * Larva or Mutalisk passes the production requirements ({@link #canBuild}).
     *
     * @param owner            the command player
     * @param unitType         the requested {@code units.dat} type
     * @param instructionLimit the order cleanup animation instruction budget
     * @return {@code true} if any selected unit accepted the morph, {@code false} otherwise
     * @throws UnsupportedOperationException if selection storage is absent
     */
    public boolean morph(int owner, int unitType, int instructionLimit) {
        UnitSelection selection = this.game.getSelection();

        if (selection == null) {
            throw Exceptions.newUnsupportedOperationException("Morphing requires initialized selection storage.");
        }

        boolean accepted = false;

        for (int position = 0; position < UnitSelection.MAXIMUM_SELECTED; position++) {
            Unit producer = selection.getSelectedUnit(owner, position);

            if (producer == null) {
                break;
            }

            if (producer.getOwnerIndex() != owner) {
                continue;
            }

            if (producer.isDestroyed()) {
                continue;
            }

            int producerType = this.game.getBaseType(producer);

            if (producerType == ScrUnitIds.ZERG_HYDRALISK && this.game.getBaseType(unitType) == ScrUnitIds.ZERG_LURKER) {
                if (this.game.getRequirements().checkTechnologyUse(producer, ScrTechIds.LURKER_ASPECT, owner) != 0) {
                    continue;
                }
            } else if (producerType == ScrUnitIds.ZERG_LARVA || producerType == ScrUnitIds.ZERG_MUTALISK) {
                if (!this.canBuild(producer, unitType)) {
                    continue;
                }
            } else {
                continue;
            }

            if (producer.getPrimaryOrder() == ScrOrderIds.ZERG_UNIT_MORPH) {
                continue;
            }

            if (this.pushBuildQueue(producer, unitType) < 0) {
                continue;
            }

            this.game.getOrders().issueUntargeted(producer, ScrOrderIds.ZERG_UNIT_MORPH, instructionLimit);

            accepted = true;
        }

        return accepted;
    }

    /**
     * Enqueues a Zerg building morph for each selected Zerg building. The building cost is charged later by
     * {@link UnitConstruction#startZergBuildingMorph}.
     * <p>
     * The requested type is a native building type, or an added unit type that a Zerg building morphs into
     * ({@link ScrUnitTypeView#isBuildingMorphType}); the selected building's own requirement script decides which building may morph.
     *
     * @param owner            the command player
     * @param unitType         the requested type
     * @param instructionLimit the order cleanup animation instruction budget
     * @return {@code true} if any selected building accepted the morph, {@code false} otherwise
     * @throws UnsupportedOperationException if selection storage is absent
     * @throws NullPointerException          if the world was built without game data and the type is outside the native building types
     */
    public boolean morphBuilding(int owner, int unitType, int instructionLimit) {
        if ((unitType < ScrUnitIds.ZERG_INFESTED_COMMAND_CENTER || unitType > ScrUnitIds.SPECIAL_CEREBRATE_DAGGOTH) &&
            !this.isAddedBuildingMorphType(unitType)) {
            return false;
        }

        UnitSelection selection = this.game.getSelection();

        if (selection == null) {
            throw Exceptions.newUnsupportedOperationException("Building morphs require initialized selection storage.");
        }

        boolean accepted = false;

        for (int position = 0; position < UnitSelection.MAXIMUM_SELECTED; position++) {
            Unit producer = selection.getSelectedUnit(owner, position);

            if (producer == null) {
                break;
            }

            if (producer.isDestroyed()) {
                continue;
            }

            if (producer.getOwnerIndex() != owner || producer.getUnitId() < ScrUnitIds.ZERG_INFESTED_COMMAND_CENTER ||
                producer.getUnitId() > ScrUnitIds.SPECIAL_CEREBRATE_DAGGOTH) {
                continue;
            }

            if (!this.canBuild(producer, unitType) || this.pushBuildQueue(producer, unitType) < 0) {
                continue;
            }

            if (producer.getPrimaryOrder() != ScrOrderIds.ZERG_BUILDING_MORPH) {
                this.game.getOrders().issueUntargeted(producer, ScrOrderIds.ZERG_BUILDING_MORPH, instructionLimit);
            }

            accepted = true;
        }

        return accepted;
    }

    /**
     * Pushes a unit type into a producer's build queue: the first empty slot from the current queue index receives the type, supply is checked only
     * when that slot is the current one, and the cost is charged immediately unless the type is a building or an added building morph result, whose
     * cost the start of the construction or morph charges. Used by computer players, which bypass selection.
     *
     * @param producer the producing unit
     * @param unitType the queued {@code units.dat} type
     * @return the queue slot that received the type, or {@code -1} when the queue is full or the cost or supply is not available
     * @throws UnsupportedOperationException if the queue index is outside the queue
     * @throws NullPointerException          if the world was built without game data
     */
    public int pushBuildQueue(@NotNull Unit producer, int unitType) {
        int owner = producer.getOwnerIndex();
        int firstSlot = producer.getBuildQueueIndex();

        if (firstSlot >= Unit.BUILD_QUEUE_SLOTS) {
            throw Exceptions.newUnsupportedOperationException("Training queue index is outside native storage: {0}", firstSlot);
        }

        int queueSize = 0;

        while (queueSize < Unit.BUILD_QUEUE_SLOTS &&
               producer.getBuildQueueUnitType((firstSlot + queueSize) % Unit.BUILD_QUEUE_SLOTS) != ScrUnitIds.NONE) {
            queueSize++;
        }

        if (queueSize == Unit.BUILD_QUEUE_SLOTS) {
            return -1;
        }

        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        CostTests costs = this.game.getCostTests();

        // Supply is tested only when the slot is the current one; a failure shows its error (display on).
        if (!costs.testUnit(this.game, owner, unitType, queueSize == 0, true)) {
            return -1;
        }

        producer.setBuildQueueUnitType((firstSlot + queueSize) % Unit.BUILD_QUEUE_SLOTS, unitType);

        // The spend charges the tested costs unless the type is a building or an added building morph result.
        if ((units.getFlags(unitType) & UnitTypeFlags.BUILDING) == 0 && !this.isAddedBuildingMorphType(unitType)) {
            costs.spend(this.game, owner);
        }

        return (firstSlot + queueSize) % Unit.BUILD_QUEUE_SLOTS;
    }

    /**
     * @param unitType the requested type
     * @return {@code true} if the type is an added unit type that a Zerg building morphs into, {@code false} otherwise
     */
    private boolean isAddedBuildingMorphType(int unitType) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();

        return units.isAddedType(unitType) && units.isBuildingMorphType(unitType);
    }
}
