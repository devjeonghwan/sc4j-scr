package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.order.OrderRegistry;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrOrders;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerUnitRecord;
import org.fogofwarlabs.sc4j.scr.game.order.*;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Creates the {@link ScrOrderBehavior} of every unit order id: an {@link Order} step per order identifier and {@link OrderStage}, the behaviors of
 * the order types registered in an engine {@link OrderRegistry}.
 * <p>
 * Every order calls the game's systems at the time it runs, so the owners that world initialization installs later are found. Orders that share a
 * handler share an instance; identifiers without a hidden handler of their own get the hidden order timer.
 */
public final class NativeOrderTable {
    /**
     * The order timer value of a hidden unit after its timer expires.
     */
    private static final int HIDDEN_ORDER_TIMER = 8;
    /**
     * The order state in which a {@link ScrOrderIds#CREATE_PROTOSS_BUILDING} order continues as {@link ScrOrderIds#PLACE_PROTOSS_BUILDING}.
     */
    private static final int PROTOSS_BUILDING_PLACED_STATE = 2;
    /**
     * The random source of the order timer that {@link ScrOrderIds#GUARD} draws.
     */
    private static final int GUARD_RANDOM_SOURCE = 29;
    /**
     * The largest order timer that {@link ScrOrderIds#GUARD} draws.
     */
    private static final int GUARD_MAXIMUM_TIMER = 15;

    private static void setStep(ScrOrderBehavior.@NotNull Builder @NotNull [] behaviors,
                                @NotNull OrderStage stage,
                                @NotNull Order step,
                                int @NotNull ... orderIds) {
        for (int orderId : orderIds) {
            behaviors[orderId].setStep(stage, step);
        }
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link NativeOrderTable} instance for one game.
     *
     * @param game the game whose systems the orders call
     */
    public NativeOrderTable(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Creates the behaviors of every order id: the steps of each id per stage, registered in an engine order registry over the order types of
     * {@link ScrOrders}, and the behavior of {@link ScrOrderIds#NONE}.
     *
     * @return the created {@link ScrOrderBehaviors} instance, with a frozen registry
     */
    public @NotNull ScrOrderBehaviors createBehaviors() {
        ScrOrderBehavior.Builder[] behaviors = new ScrOrderBehavior.Builder[ScrOrderBehaviors.ORDER_COUNT];

        for (int orderId = 0; orderId < behaviors.length; orderId++) {
            behaviors[orderId] = new ScrOrderBehavior.Builder();
        }

        Order die = (context, unit) -> this.game.getUnitLifecycle().die(unit, context.getInstructionLimit());
        Order resetCollision = (context, unit) -> this.game.getUnitConstruction().resetCollision(unit, context.getInstructionLimit());
        Order resetHarvestCollision = (context, unit) -> this.game.getUnitHarvesting().resetHarvestCollision(unit, context.getInstructionLimit());
        Order harvestGas = (context, unit) -> this.game.getUnitHarvesting().harvestGas(unit, context.getInstructionLimit());
        Order nukeLaunch = (context, unit) -> this.game.getUnitNuclear().updateLaunch(unit, context.getInstructionLimit());
        Order infesting = (context, unit) -> this.game.getUnitInfestation().updateInfesting(unit, context.getInstructionLimit());
        Order powerupIdle = (context, unit) -> this.game.getUnitPowerups().updateIdle(unit, false, context.getInstructionLimit());
        Order unusedPowerup = (context, unit) -> this.game.getUnitPowerups().updateIdle(unit, true, context.getInstructionLimit());
        Order trainFighter = (context, unit) -> this.game.getUnitHangar().updateTrainFighter(unit, context.getInstructionLimit());
        Order cloak = (context, unit) -> this.game.getUnitCloaking().updateSecondary(unit);

        this.registerMainUnconditional(behaviors, die);
        this.registerMainEveryFrame(behaviors, resetCollision, resetHarvestCollision);
        this.registerMainTimer(behaviors, harvestGas, nukeLaunch, infesting, powerupIdle, unusedPowerup);
        this.registerMainIdle(behaviors);
        this.registerSecondary(behaviors, trainFighter, cloak);
        this.registerHidden(behaviors,
                            die,
                            resetCollision,
                            resetHarvestCollision,
                            harvestGas,
                            nukeLaunch,
                            infesting,
                            powerupIdle,
                            unusedPowerup,
                            trainFighter,
                            cloak);

        OrderRegistry<ScrOrderBehavior> registry = new OrderRegistry<>(ScrOrders.getTypes());

        for (int orderId = 0; orderId < ScrOrders.COUNT; orderId++) {
            registry.register(ScrOrders.get(orderId), behaviors[orderId].build());
        }

        registry.freeze();

        return new ScrOrderBehaviors(registry, behaviors[ScrOrderIds.NONE].build());
    }

    private void registerMainUnconditional(ScrOrderBehavior.@NotNull Builder @NotNull [] behaviors, @NotNull Order die) {
        setStep(behaviors, OrderStage.MAIN_UNCONDITIONAL, die, ScrOrderIds.DIE);
        setStep(behaviors,
                OrderStage.MAIN_UNCONDITIONAL,
                (context, unit) -> this.game.getUnitWarping().update(unit, context.getInstructionLimit()),
                ScrOrderIds.INCOMPLETE_WARPING);
        setStep(behaviors,
                OrderStage.MAIN_UNCONDITIONAL,
                (context, unit) -> this.game.getUnitWarping().updateWarpIn(unit, context.getInstructionLimit()),
                ScrOrderIds.WARP_IN);
        setStep(behaviors,
                OrderStage.MAIN_UNCONDITIONAL,
                (context, unit) -> this.game.getUnitNuclear().updateTrack(unit, context.getInstructionLimit()),
                ScrOrderIds.NUKE_TRACK);
    }

    private void registerMainEveryFrame(ScrOrderBehavior.@NotNull Builder @NotNull [] behaviors,
                                        @NotNull Order resetCollision,
                                        @NotNull Order resetHarvestCollision) {
        OrderStage stage = OrderStage.MAIN_EVERY_FRAME;

        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitConstruction().updateProtoss(unit, context.getInstructionLimit()),
                ScrOrderIds.PLACE_PROTOSS_BUILDING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitConstruction().buildDrone(unit, context.getInstructionLimit()),
                ScrOrderIds.DRONE_BUILD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitConstruction().updateZergMorphing(unit, context.getInstructionLimit()),
                ScrOrderIds.INCOMPLETE_MORPHING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitResearch().updateResearch(unit, context.getInstructionLimit()),
                ScrOrderIds.RESEARCH_TECH);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitResearch().updateUpgrade(unit, context.getInstructionLimit()),
                ScrOrderIds.UPGRADE);
        setStep(behaviors, stage, resetCollision, ScrOrderIds.RESET_COLLISION);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitMorphing().update(unit, context.getInstructionLimit()),
                ScrOrderIds.ZERG_UNIT_MORPH);
        setStep(behaviors, stage, (context, unit) -> this.game.getUnitMorphing().birth(unit, context.getInstructionLimit()), ScrOrderIds.ZERG_BIRTH);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHarvesting().interruptGathering(unit, context.getInstructionLimit()),
                ScrOrderIds.GATHERING_INTERRUPTED, ScrOrderIds.GATHER_WAIT_INTERRUPTED);
        setStep(behaviors, stage, resetHarvestCollision, ScrOrderIds.RESET_HARVEST_COLLISION);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().placeFlagBeacon(unit, context.getInstructionLimit()),
                ScrOrderIds.CTFCOP2);
        setStep(behaviors, stage, (context, unit) -> this.game.getCritterOrders().update(unit), ScrOrderIds.CRITTER);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getArchonOrders().updateWarp(unit, context.getInstructionLimit()),
                ScrOrderIds.ARCHON_WARP, ScrOrderIds.DARK_ARCHON_MELD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getArchonOrders().updateSummon(unit, context.getInstructionLimit()),
                ScrOrderIds.COMPLETING_ARCHON_SUMMON);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHangar().updateScarabAttack(unit, context.getInstructionLimit()),
                ScrOrderIds.SCARAB_ATTACK);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitShieldBattery().updateRechargeShieldsUnit(unit, context.getInstructionLimit()),
                ScrOrderIds.RECHARGE_SHIELDS_UNIT);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitShieldBattery().updateRemoveOverlay(unit, context.getInstructionLimit()),
                ScrOrderIds.RECHARGE_SHIELDS_UNIT_REMOVE_OVERLAY);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitNuclear().updateTrain(unit, context.getInstructionLimit()),
                ScrOrderIds.NUKE_TRAIN);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitCloaking().updateInitializeArbiter(unit, context.getInstructionLimit()),
                ScrOrderIds.INITIALIZE_ARBITER);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTurret().updateGuard(unit, context.getInstructionLimit()),
                ScrOrderIds.TURRET_GUARD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTurret().updateAttack(unit, context.getInstructionLimit()),
                ScrOrderIds.TURRET_ATTACK);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTurret().updateSieging(unit, context.getInstructionLimit()),
                ScrOrderIds.SIEGING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTurret().updateUnsieging(unit, context.getInstructionLimit()),
                ScrOrderIds.UNSIEGING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitMedic().updateHeal(unit, context.getInstructionLimit()),
                ScrOrderIds.MEDIC_HEAL);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitMedic().updateHealMove(unit, context.getInstructionLimit()),
                ScrOrderIds.HEAL_MOVE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitMedic().updateHoldPosition(unit, context.getInstructionLimit()),
                ScrOrderIds.MEDIC_HOLD_POSITION);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitMedic().updateHealToIdle(unit, context.getInstructionLimit()),
                ScrOrderIds.MEDIC_HEAL_TO_IDLE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTerranConstruction().placeBuilding(unit, context.getInstructionLimit()),
                ScrOrderIds.PLACE_BUILDING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTerranConstruction().updateConstructing(unit, context.getInstructionLimit()),
                ScrOrderIds.CONSTRUCTING_BUILDING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTerranConstruction().updateRepair(unit, context.getInstructionLimit()),
                ScrOrderIds.REPAIR);
        setStep(behaviors,
                stage,
                (context, unit) ->
                        this.game.getUnitTerranConstruction().updateIncompleteBuilding(unit, context.getInstructionLimit()),
                ScrOrderIds.INCOMPLETE_BUILDING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitBuildingFlight().updateLanding(unit, context.getInstructionLimit()),
                ScrOrderIds.BUILDING_LAND);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitBuildingFlight().updateLiftoff(unit, context.getInstructionLimit()),
                ScrOrderIds.BUILDING_LIFTOFF);
    }

    private void registerMainTimer(ScrOrderBehavior.@NotNull Builder @NotNull [] behaviors,
                                   @NotNull Order harvestGas,
                                   @NotNull Order nukeLaunch,
                                   @NotNull Order infesting,
                                   @NotNull Order powerupIdle,
                                   @NotNull Order unusedPowerup) {
        OrderStage stage = OrderStage.MAIN_TIMER;
        Order moveToTarget = (context, unit) -> this.game.getUnitAttack().updateMoveToTargetOrder(unit, context.getInstructionLimit());
        Order pointMovement = (context, unit) -> this.game.getUnitPointMovement().update(unit, context.getInstructionLimit());

        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getPsi().initializeProvider(unit, context.getInstructionLimit()),
                ScrOrderIds.INITIALIZE_PSI_PROVIDER);
        setStep(behaviors, stage, this::updateStop, ScrOrderIds.STOP);
        setStep(behaviors, stage, pointMovement, ScrOrderIds.MOVE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitFollowMovement().update(unit, context.getInstructionLimit()),
                ScrOrderIds.FOLLOW);
        setStep(behaviors, stage, this::updateGuard, ScrOrderIds.GUARD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updatePlayerGuard(unit, context.getInstructionLimit()),
                ScrOrderIds.PLAYER_GUARD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateAttackUnit(unit, context.getInstructionLimit()),
                ScrOrderIds.ATTACK_UNIT);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateAttackFixedRange(unit, context.getInstructionLimit()),
                ScrOrderIds.ATTACK_FIXED_RANGE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateAttackMove(unit, context.getInstructionLimit()),
                ScrOrderIds.ATTACK_MOVE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateTowerGuard(unit, context.getInstructionLimit()),
                ScrOrderIds.TOWER_GUARD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateTowerAttack(unit, context.getInstructionLimit()),
                ScrOrderIds.TOWER_ATTACK);
        // Harvest2 moves to its target like the attack orders.
        setStep(behaviors,
                stage,
                moveToTarget,
                ScrOrderIds.MOVE_TO_ATTACK, ScrOrderIds.MOVE_TO_REPAIR, ScrOrderIds.CARRIER_MOVE_TO_ATTACK, ScrOrderIds.REAVER_MOVE_TO_ATTACK,
                ScrOrderIds.MOVE_TO_FIRE_YAMATO_GUN, ScrOrderIds.MOVE_TO_INFEST, ScrOrderIds.HARVEST2);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAddons().placeAddon(unit, context.getInstructionLimit()),
                ScrOrderIds.PLACE_ADDON);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitBuildingFlight().updateLiftingOff(unit, context.getInstructionLimit()),
                ScrOrderIds.LIFTING_OFF);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateHoldPosition(unit, context.getInstructionLimit()),
                ScrOrderIds.HOLD_POSITION);
        setStep(behaviors, stage, (context, unit) -> this.game.getUnitAttack().updatePatrol(unit, context.getInstructionLimit()), ScrOrderIds.PATROL);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateStayInRange(unit, context.getInstructionLimit()),
                ScrOrderIds.STAY_IN_RANGE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitCommands().updateRightClickAction(unit, context.getInstructionLimit()),
                ScrOrderIds.RIGHT_CLICK_ACTION);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHangar().updateCarrier(unit, context.getInstructionLimit()),
                ScrOrderIds.CARRIER, ScrOrderIds.CARRIER_FIGHT, ScrOrderIds.CARRIER_HOLD_POSITION);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHangar().updateReaver(unit, context.getInstructionLimit()),
                ScrOrderIds.REAVER, ScrOrderIds.REAVER_FIGHT, ScrOrderIds.REAVER_HOLD_POSITION);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHangar().updateParentAttack(unit, context.getInstructionLimit()),
                ScrOrderIds.CARRIER_ATTACK, ScrOrderIds.REAVER_ATTACK);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHangar().updateParentStop(unit, context.getInstructionLimit()),
                ScrOrderIds.CARRIER_STOP, ScrOrderIds.REAVER_STOP);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getComputerOrders().updateNeutral(unit, context.getInstructionLimit()),
                ScrOrderIds.NEUTRAL);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitRescue().update(unit, context.getInstructionLimit()),
                ScrOrderIds.RESCUE_PASSIVE);
        setStep(behaviors, stage, powerupIdle, ScrOrderIds.POWERUP_IDLE);
        setStep(behaviors, stage, unusedPowerup, ScrOrderIds.UNUSED_POWERUP);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().openDoor(unit, context.getInstructionLimit()),
                ScrOrderIds.OPEN_DOOR);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().closeDoor(unit, context.getInstructionLimit()),
                ScrOrderIds.CLOSE_DOOR);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().enable(unit, context.getInstructionLimit()),
                ScrOrderIds.ENABLE_DOODAD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().disable(unit, context.getInstructionLimit()),
                ScrOrderIds.DISABLE_DOODAD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().updateHiddenGun(unit, context.getInstructionLimit()),
                ScrOrderIds.HIDDEN_GUN);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().hideTrap(unit, context.getInstructionLimit()),
                ScrOrderIds.HIDE_TRAP);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().revealTrap(unit, context.getInstructionLimit()),
                ScrOrderIds.REVEAL_TRAP);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().initializeFlagBeacon(unit, context.getInstructionLimit()),
                ScrOrderIds.CTFCOP_INIT);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getDoodadOrders().updateFlagBeacon(unit, context.getInstructionLimit()),
                ScrOrderIds.CTFCOP_STARTED);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitInfestation().updateInfested(unit, context.getInstructionLimit()),
                ScrOrderIds.INFESTED_COMMAND_CENTER);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitInfestation().updateCast(unit, context.getInstructionLimit()),
                ScrOrderIds.CAST_INFESTATION);
        setStep(behaviors, stage, infesting, ScrOrderIds.INFESTING_COMMAND_CENTER);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateSuicideUnit(unit, context.getInstructionLimit()),
                ScrOrderIds.SUICIDE_UNIT);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateSuicideLocation(unit, context.getInstructionLimit()),
                ScrOrderIds.SUICIDE_LOCATION);
        setStep(behaviors, stage, pointMovement, ScrOrderIds.REAVER_CARRIER_MOVE);
        setStep(behaviors,
                stage,
                (context, unit) -> {
                    throw Exceptions.newIllegalStateException("Order CARRIER_IGNORE2 is never dispatched.");
                },
                ScrOrderIds.CARRIER_IGNORE2);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHangar().updateInterceptorAttack(unit, context.getInstructionLimit()),
                ScrOrderIds.INTERCEPTOR_ATTACK);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHangar().updateInterceptorReturn(unit, context.getInstructionLimit()),
                ScrOrderIds.INTERCEPTOR_RETURN);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitLifecycle().kill(unit, context.getInstructionLimit()),
                ScrOrderIds.SELF_DESTRUCTING);
        setStep(behaviors, stage, this::updateNothing, ScrOrderIds.NOTHING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitConstruction().startDroneBuild(unit, context.getInstructionLimit()),
                ScrOrderIds.DRONE_START_BUILD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitConstruction().startZergBuildingMorph(unit, context.getInstructionLimit()),
                ScrOrderIds.ZERG_BUILDING_MORPH);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitConstruction().landDrone(unit, context.getInstructionLimit()),
                ScrOrderIds.DRONE_LAND);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getLarvaSpawner().updatePrimary(unit, context.getInstructionLimit()),
                ScrOrderIds.LARVA);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getComputerOrders().updateComputer(unit, context.getInstructionLimit()),
                ScrOrderIds.COMPUTER_AI);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getComputerCombat().updateAttackMove(unit, context.getInstructionLimit()),
                ScrOrderIds.ATK_MOVE_EP);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getComputerCombat().updateHarassMove(unit, context.getInstructionLimit()),
                ScrOrderIds.HARASS_MOVE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getComputerCombat().updatePickupWait(unit, context.getInstructionLimit()),
                ScrOrderIds.PICKUP4);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getComputerCombat().updateGuardPost(unit, context.getInstructionLimit()),
                ScrOrderIds.GUARD_POST);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getComputerCombat().updateReturn(unit, context.getInstructionLimit()),
                ScrOrderIds.COMPUTER_RETURN);
        setStep(behaviors, stage, (context, unit) -> this.game.getComputerOrders().updatePatrol(unit), ScrOrderIds.AI_PATROL);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHarvesting().moveToGas(unit, context.getInstructionLimit()),
                ScrOrderIds.HARVEST1, ScrOrderIds.MOVE_TO_GAS);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHarvesting().waitForGas(unit, context.getInstructionLimit()),
                ScrOrderIds.WAIT_FOR_GAS);
        setStep(behaviors, stage, harvestGas, ScrOrderIds.HARVEST_GAS);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHarvesting().moveToMinerals(unit, context.getInstructionLimit()),
                ScrOrderIds.MOVE_TO_MINERALS);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHarvesting().waitForMinerals(unit, context.getInstructionLimit()),
                ScrOrderIds.WAIT_FOR_MINERALS);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHarvesting().mineMinerals(unit, context.getInstructionLimit()),
                ScrOrderIds.MINING_MINERALS);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitHarvesting().returnMinerals(unit, context.getInstructionLimit()),
                ScrOrderIds.RETURN_GAS, ScrOrderIds.RETURN_MINERALS);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTransport().updatePickupIdle(unit, context.getInstructionLimit()),
                ScrOrderIds.PICKUP_IDLE);
        setStep(behaviors,
                stage,
                (context, unit) ->
                        this.game.getUnitShieldBattery().updateRechargeShieldsBattery(unit, context.getInstructionLimit()),
                ScrOrderIds.RECHARGE_SHIELDS_BATTERY);
        // Queen Hold Position runs the Suicide Hold Position order.
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAttack().updateSuicideHoldPosition(unit, context.getInstructionLimit()),
                ScrOrderIds.SUICIDE_HOLD_POSITION, ScrOrderIds.QUEEN_HOLD_POSITION);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitMedic().updateIdle(unit, context.getInstructionLimit()),
                ScrOrderIds.MEDIC_IDLE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTurret().updateWatchTarget(unit, context.getInstructionLimit()),
                ScrOrderIds.WATCH_TARGET);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updatePlaceMine(unit, context.getInstructionLimit()),
                ScrOrderIds.PLACE_MINE);
        setStep(behaviors, stage, nukeLaunch, ScrOrderIds.NUKE_LAUNCH);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitNuclear().updatePaint(unit, context.getInstructionLimit()),
                ScrOrderIds.NUKE_PAINT);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitNuclear().updateUnit(unit, context.getInstructionLimit()),
                ScrOrderIds.NUKE_UNIT);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitNuclear().updateCastNuclearStrike(unit, context.getInstructionLimit()),
                ScrOrderIds.CAST_NUCLEAR_STRIKE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitNydus().updateBuildExit(unit, context.getInstructionLimit()),
                ScrOrderIds.BUILD_NYDUS_EXIT);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitNydus().updateEnter(unit, context.getInstructionLimit()),
                ScrOrderIds.ENTER_NYDUS_CANAL);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updateSpiderMine(unit, context.getInstructionLimit()),
                ScrOrderIds.SPIDER_MINE);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updateSpell(unit, context.getInstructionLimit()),
                ScrOrderIds.FIRE_YAMATO_GUN, ScrOrderIds.CAST_LOCKDOWN, ScrOrderIds.CAST_DARK_SWARM, ScrOrderIds.CAST_PARASITE,
                ScrOrderIds.CAST_SPAWN_BROODLINGS, ScrOrderIds.CAST_EMP_SHOCKWAVE, ScrOrderIds.CAST_PSIONIC_STORM, ScrOrderIds.CAST_IRRADIATE,
                ScrOrderIds.CAST_PLAGUE, ScrOrderIds.CAST_CONSUME, ScrOrderIds.CAST_ENSNARE, ScrOrderIds.CAST_STASIS_FIELD, ScrOrderIds.CAST_RESTORATION,
                ScrOrderIds.CAST_DISRUPTION_WEB, ScrOrderIds.CAST_OPTICAL_FLARE, ScrOrderIds.CAST_MAELSTROM);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updateRecall(unit, context.getInstructionLimit()),
                ScrOrderIds.CAST_RECALL);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updateScannerSweep(unit, context.getInstructionLimit()),
                ScrOrderIds.CAST_SCANNER_SWEEP);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updateScanner(unit, context.getInstructionLimit()),
                ScrOrderIds.SCANNER);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updateDefensiveMatrix(unit, context.getInstructionLimit()),
                ScrOrderIds.CAST_DEFENSIVE_MATRIX);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updateHallucination(unit, context.getInstructionLimit()),
                ScrOrderIds.CAST_HALLUCINATION);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updateMindControl(unit, context.getInstructionLimit()),
                ScrOrderIds.CAST_MIND_CONTROL);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitSpells().updateFeedback(unit, context.getInstructionLimit()),
                ScrOrderIds.CAST_FEEDBACK);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitBurrowing().updateBurrowing(unit, context.getInstructionLimit()),
                ScrOrderIds.BURROWING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitBurrowing().updateBurrowed(unit, context.getInstructionLimit()),
                ScrOrderIds.BURROWED);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitBurrowing().updateUnburrowing(unit, context.getInstructionLimit()),
                ScrOrderIds.UNBURROWING);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTransport().updateEnterTransport(unit, context.getInstructionLimit()),
                ScrOrderIds.ENTER_TRANSPORT);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTransport().updatePickupTransport(unit, context.getInstructionLimit()),
                ScrOrderIds.PICKUP_TRANSPORT);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTransport().updatePickupBunker(unit, context.getInstructionLimit()),
                ScrOrderIds.PICKUP_BUNKER);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTransport().updateUnload(unit, context.getInstructionLimit()),
                ScrOrderIds.UNLOAD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitTransport().updateMoveUnload(unit, context.getInstructionLimit()),
                ScrOrderIds.MOVE_UNLOAD);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getComputerOrders().updateJunkYardDog(unit, context.getInstructionLimit()),
                ScrOrderIds.JUNK_YARD_DOG);
        setStep(behaviors, stage, this::updateCreateProtossBuilding, ScrOrderIds.CREATE_PROTOSS_BUILDING);
        setStep(behaviors, stage, this::updateStoppingCreepGrowth, ScrOrderIds.STOPPING_CREEP_GROWTH);
        setStep(behaviors, stage, this::updateInitCreepGrowth, ScrOrderIds.INIT_CREEP_GROWTH);
    }

    /**
     * Registers the orders that the main-order dispatch accepts without a handler of their own: only their order timer counts down. They run only as
     * secondary or hidden orders, or do nothing.
     */
    private void registerMainIdle(ScrOrderBehavior.@NotNull Builder @NotNull [] behaviors) {
        int[] idleOrders = {ScrOrderIds.NOTHING_WAIT, ScrOrderIds.NUKE_WAIT, ScrOrderIds.BUNKER_GUARD, ScrOrderIds.ATTACK_DEFAULT,
                            ScrOrderIds.ATTACK_TILE, ScrOrderIds.HOVER, ScrOrderIds.UNUSED_NOTHING, ScrOrderIds.BUILD_ADDON, ScrOrderIds.TRAIN,
                            ScrOrderIds.RALLY_POINT_UNIT, ScrOrderIds.RALLY_POINT_TILE, ScrOrderIds.TRAIN_FIGHTER, ScrOrderIds.SHIELD_BATTERY,
                            ScrOrderIds.DRONE_LIFT_OFF, ScrOrderIds.SPAWNING_LARVA, ScrOrderIds.SPREAD_CREEP, ScrOrderIds.GUARDIAN_ASPECT,
                            ScrOrderIds.CLOAK, ScrOrderIds.DECLOAK, ScrOrderIds.CLOAK_NEARBY_UNITS, ScrOrderIds.TELEPORT,
                            ScrOrderIds.HALLUCINATION2, ScrOrderIds.FATAL};

        for (int order : idleOrders) {
            behaviors[order].setIdle();
        }
    }

    private void registerSecondary(ScrOrderBehavior.@NotNull Builder @NotNull [] behaviors, @NotNull Order trainFighter, @NotNull Order cloak) {
        OrderStage stage = OrderStage.SECONDARY;

        setStep(behaviors, OrderStage.SECONDARY_UNCONDITIONAL, this::updateHallucination, ScrOrderIds.HALLUCINATION2);
        setStep(behaviors,
                stage,
                (context, unit) ->
                        this.game.getLarvaSpawner().updateIdleHatchery(unit, context.getOwnerPlayerType(), context.getInstructionLimit()),
                ScrOrderIds.SPAWNING_LARVA);
        setStep(behaviors,
                stage,
                (context, unit) ->
                        this.game.getLarvaSpawner().updateHatchery(unit, context.getOwnerPlayerType(), context.getInstructionLimit()),
                ScrOrderIds.SPREAD_CREEP);
        setStep(behaviors, stage, (context, unit) -> this.game.getUnitTraining().update(unit, context.getInstructionLimit()), ScrOrderIds.TRAIN);
        setStep(behaviors, stage, trainFighter, ScrOrderIds.TRAIN_FIGHTER);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitAddons().buildAddon(unit, context.getInstructionLimit()),
                ScrOrderIds.BUILD_ADDON);
        setStep(behaviors, stage, cloak, ScrOrderIds.CLOAK, ScrOrderIds.DECLOAK);
        setStep(behaviors,
                stage,
                (context, unit) -> this.game.getUnitShieldBattery().updateSecondary(unit, context.getInstructionLimit()),
                ScrOrderIds.SHIELD_BATTERY);
        setStep(behaviors, stage, (context, unit) -> this.game.getUnitCloaking().updateCloakNearbyUnits(unit), ScrOrderIds.CLOAK_NEARBY_UNITS);
    }

    private void registerHidden(ScrOrderBehavior.@NotNull Builder @NotNull [] behaviors,
                                @NotNull Order die,
                                @NotNull Order resetCollision,
                                @NotNull Order resetHarvestCollision,
                                @NotNull Order harvestGas,
                                @NotNull Order nukeLaunch,
                                @NotNull Order infesting,
                                @NotNull Order powerupIdle,
                                @NotNull Order unusedPowerup,
                                @NotNull Order trainFighter,
                                @NotNull Order cloak) {
        OrderStage stage = OrderStage.HIDDEN_MAIN;
        Order hiddenTimer = this::updateHiddenTimer;

        setStep(behaviors, stage, die, ScrOrderIds.DIE);
        setStep(behaviors,
                stage,
                this::updateHiddenGuard,
                ScrOrderIds.PLAYER_GUARD, ScrOrderIds.TURRET_GUARD, ScrOrderIds.TURRET_ATTACK, ScrOrderIds.ENTER_TRANSPORT);
        setStep(behaviors, stage, unusedPowerup, ScrOrderIds.UNUSED_POWERUP);
        setStep(behaviors, stage, powerupIdle, ScrOrderIds.POWERUP_IDLE);
        setStep(behaviors,
                stage,
                Order.EMPTY,
                ScrOrderIds.NOTHING, ScrOrderIds.NOTHING_WAIT, ScrOrderIds.NEUTRAL, ScrOrderIds.MEDIC_IDLE, ScrOrderIds.MEDIC_HEAL);
        setStep(behaviors, stage, infesting, ScrOrderIds.INFESTING_COMMAND_CENTER);
        setStep(behaviors, stage, harvestGas, ScrOrderIds.HARVEST_GAS);
        setStep(behaviors, stage, nukeLaunch, ScrOrderIds.NUKE_LAUNCH);
        setStep(behaviors, stage, resetCollision, ScrOrderIds.RESET_COLLISION);
        setStep(behaviors, stage, resetHarvestCollision, ScrOrderIds.RESET_HARVEST_COLLISION);

        for (ScrOrderBehavior.Builder behavior : behaviors) {
            if (behavior.getStep(stage) == null) {
                behavior.setStep(stage, hiddenTimer);
            }
        }

        setStep(behaviors, OrderStage.HIDDEN_SECONDARY, trainFighter, ScrOrderIds.TRAIN_FIGHTER);
        setStep(behaviors, OrderStage.HIDDEN_SECONDARY, cloak, ScrOrderIds.CLOAK, ScrOrderIds.DECLOAK);
    }

    private void updateStop(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        this.game.getUnitPointMovement().stopAndFaceDestination(unit);
        this.game.getIscriptExecutor().runToIdle(unit, context.getInstructionLimit());
        this.game.getOrders().finishOrder(unit, context.getInstructionLimit());
    }

    /**
     * Turns {@link ScrOrderIds#GUARD} into the guard order of the owner: {@link ScrOrderIds#PLAYER_GUARD} for players, and for computers
     * {@link ScrOrderIds#GUARD_POST} when the unit has a guard record, {@link ScrOrderIds#PLAYER_GUARD} for a hero without a record, else
     * {@link ScrOrderIds#COMPUTER_AI}.
     *
     * @param context the dispatch context, whose player type of the unit's owner for this frame the order reads
     * @param unit    the guarding unit
     * @throws NullPointerException if the world was built without game data
     */
    private void updateGuard(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        int remaining = this.game.getRandom().next(GUARD_RANDOM_SOURCE, 0, GUARD_MAXIMUM_TIMER);

        unit.setOrderTimer(remaining);

        if (context.getOwnerPlayerType() != PlayerTypes.COMPUTER) {
            unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
        } else {
            unit.setPrimaryOrder(ScrOrderIds.COMPUTER_AI);

            ComputerUnitRecord record = unit.getComputerRecord();

            if (record != null) {
                if (record.getKind() == ComputerUnitRecord.KIND_GUARD) {
                    unit.setPrimaryOrder(ScrOrderIds.GUARD_POST);
                }
            } else if ((this.game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.HERO) != 0) {
                unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
            }
        }
    }

    /**
     * Activates a queued order.
     */
    private void updateNothing(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        if (unit.getFirstOrder() != null) {
            this.game.getOrders().activateNext(unit, context.getInstructionLimit());
        }
    }

    /**
     * Continues as {@link ScrOrderIds#PLACE_PROTOSS_BUILDING} in its placed state.
     */
    private void updateCreateProtossBuilding(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        unit.setPrimaryOrder(ScrOrderIds.PLACE_PROTOSS_BUILDING);
        unit.setPrimaryOrderState(PROTOSS_BUILDING_PLACED_STATE);

        this.game.getUnitConstruction().updateProtoss(unit, context.getInstructionLimit());
    }

    /**
     * Continues with {@link ScrOrderIds#COMPUTER_AI} for a unit with a computer record, else with the unit type's return-to-idle order; the secondary
     * order becomes {@link ScrOrderIds#NOTHING}.
     */
    private void updateStoppingCreepGrowth(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        this.game.getOrders().returnToIdle(unit, context.getInstructionLimit());

        ScrOrderSystem.setSecondaryOrder(unit, ScrOrderIds.NOTHING);
    }

    /**
     * Continues with the idle order ({@link ScrOrderIds#COMPUTER_AI} for a unit with a computer record) and starts {@link ScrOrderIds#SPREAD_CREEP} as
     * the secondary order.
     */
    private void updateInitCreepGrowth(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        this.game.getOrders().returnToIdle(unit, context.getInstructionLimit());

        ScrOrderSystem.setSecondaryOrder(unit, ScrOrderIds.SPREAD_CREEP);
    }

    /**
     * Removes a hallucination that any status effect reaches, before the disabled test.
     */
    private void updateHallucination(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        if (unit.getDefensiveMatrixHealth() != 0 || unit.getSpeedIncreaseByte() != 0 || unit.getSpeedDecreaseByte() != 0 ||
            unit.getLockdownTimer() != 0 || unit.getIrradiateTimer() != 0 || unit.getStasisTimer() != 0 ||
            unit.getParasiteFlags() != 0 || unit.getStormTimer() != 0 || unit.getPlagueTimer() != 0 ||
            unit.getBlindnessState() != 0 || unit.getMaelstromTimer() != 0) {
            this.game.getUnitLifecycle().kill(unit, context.getInstructionLimit());
        }
    }

    /**
     * Ends a guard or transport order of a hidden unit: {@link ScrOrderIds#BUNKER_GUARD} inside a bunker, else {@link ScrOrderIds#NOTHING}.
     */
    private void updateHiddenGuard(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        int idle = (unit.getStatus() & UnitStatusFlags.IN_BUNKER) != 0 ? ScrOrderIds.BUNKER_GUARD : ScrOrderIds.NOTHING;

        this.game.getOrders().issueUntargeted(unit, idle, context.getInstructionLimit());
    }

    /**
     * Counts the order timer of any other hidden order down; when it expires, a unit with {@link ScrOrderIds#BUNKER_GUARD}, or inside a bunker with
     * {@link ScrOrderIds#COMPUTER_AI}, runs the bunker guard.
     */
    private void updateHiddenTimer(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        int previousCountdown = unit.getPrimaryOrderCountdown();

        if (previousCountdown != 0) {
            unit.setPrimaryOrderCountdown(previousCountdown - 1);
        } else {
            int order = unit.getPrimaryOrder();

            unit.setPrimaryOrderCountdown(HIDDEN_ORDER_TIMER);

            if (order == ScrOrderIds.BUNKER_GUARD ||
                order == ScrOrderIds.COMPUTER_AI && (unit.getStatus() & UnitStatusFlags.IN_BUNKER) != 0) {
                this.game.getUnitTransport().updateBunkerGuard(unit, context.getInstructionLimit());
            }
        }
    }
}
