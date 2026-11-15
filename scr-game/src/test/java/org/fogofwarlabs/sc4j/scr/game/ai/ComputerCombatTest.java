package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks computer combat decisions in a running game: stim packs, siege threats, transport pickups, target acquisition and attack moves.
 */
@RequiresInstallation
final class ComputerCombatTest {
    private static final int COMPUTER = 0;
    private static final int HUMAN = 1;

    private static final int STIM_MARINE_POSITION_X = 3600;
    private static final int STIM_MARINE_POSITION_Y = 2200;
    private static final int STIM_ATTACKER_POSITION_X = 3640;
    private static final int STIM_ZEALOT_POSITION_X = 3560;
    private static final int WEAK_MARINE_POSITION_Y = 2240;
    /**
     * The hit points of the weak Marine, below half of its maximum, in fixed point.
     */
    private static final int WEAK_MARINE_HIT_POINTS = 15 << 8;

    private static final int TANK_POSITION_X = 3560;
    private static final int TANK_POSITION_Y = 2300;
    private static final int SIEGE_ENEMY_POSITION_Y = 2400;
    /**
     * The distance in either axis within which another human unit may threaten the tank.
     */
    private static final int OTHER_ENEMY_DISTANCE = 600;

    private static final int PICKUP_ZEALOT_POSITION_X = 3700;
    private static final int PICKUP_POSITION_Y = 2300;
    private static final int PICKUP_DESTINATION_X = 3760;

    private static final int ACQUIRE_ZEALOT_POSITION_X = 3600;
    private static final int ACQUIRE_WORKER_POSITION_X = 3630;
    private static final int ACQUIRE_POSITION_Y = 2400;
    private static final int LOCKDOWN_TIMER = 130;

    private static final int ATTACK_MARINE_POSITION_X = 3300;
    private static final int ATTACK_ZEALOT_POSITION_X = 3340;
    private static final int ATTACK_MOVE_DESTINATION_X = 3700;
    private static final int ATTACK_POSITION_Y = 2600;

    /**
     * The hit points a stim pack costs, in fixed point.
     */
    private static final int STIM_PACK_HIT_POINT_COST = 10 << 8;
    private static final int STIMMED_SPEED_INCREASE = 37;
    private static final int FIRST_STIM_PACK_SOUND = 278;
    private static final int SECOND_STIM_PACK_SOUND = 279;
    private static final int ATTACK_MOVE_ORDER = 157;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void startGame() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void close() {
        runtime = null;

        if (gameData != null) {
            gameData.close();
        }

        gameData = null;
    }


    @Test
    void use_stim_pack_should_stim_healthy_marine_facing_strong_attacker_once() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();

            PlayerTechnology stimTechnology = game.getPlayerTable().getPlayerTechnology();

            Assertions.assertNotNull(stimTechnology);

            stimTechnology.setResearched(COMPUTER, ScrTechIds.STIM_PACKS, true);

            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, STIM_MARINE_POSITION_X, STIM_MARINE_POSITION_Y, COMPUTER);
            Unit goliath = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_GOLIATH, STIM_ATTACKER_POSITION_X, STIM_MARINE_POSITION_Y, HUMAN);

            ComputerUnitRecordScenario.ensureMilitaryRecord(marine);
            marine.setOrderTargetUnit(goliath);

            int hitPoints = marine.getHitPoints();

            game.getInterfaceLink().getSoundEvents().clear();

            Assertions.assertTrue(game.getComputerCombat().useStimPack(marine, RunningGameScenario.INSTRUCTION_LIMIT));
            Assertions.assertEquals(hitPoints - STIM_PACK_HIT_POINT_COST, marine.getHitPoints());

            Assertions.assertEquals(1, game.getInterfaceLink().getSoundEvents().getCount());
            Assertions.assertTrue(game.getInterfaceLink().getSoundEvents().getSoundId(0) == FIRST_STIM_PACK_SOUND ||
                                  game.getInterfaceLink().getSoundEvents().getSoundId(0) == SECOND_STIM_PACK_SOUND);

            Sprite stimSprite = marine.getSpriteOrNull();

            Assertions.assertNotNull(stimSprite);

            Assertions.assertEquals(stimSprite.getX(), game.getInterfaceLink().getSoundEvents().getPositionX(0));
            Assertions.assertEquals(STIMMED_SPEED_INCREASE, marine.getSpeedIncreaseByte());
            Assertions.assertFalse(game.getComputerCombat().useStimPack(marine, RunningGameScenario.INSTRUCTION_LIMIT),
                                   "A stimmed Marine does not stim again.");

            Unit weak = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, STIM_MARINE_POSITION_X, WEAK_MARINE_POSITION_Y, COMPUTER);

            ComputerUnitRecordScenario.ensureMilitaryRecord(weak);
            weak.setOrderTargetUnit(goliath);
            weak.setHitPoints(WEAK_MARINE_HIT_POINTS);

            Assertions.assertFalse(game.getComputerCombat().useStimPack(weak, RunningGameScenario.INSTRUCTION_LIMIT),
                                   "Below half of its hit points a Marine does not stim.");

            Unit zealot = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_ZEALOT, STIM_ZEALOT_POSITION_X, WEAK_MARINE_POSITION_Y, COMPUTER);

            ComputerUnitRecordScenario.ensureMilitaryRecord(zealot);
            zealot.setOrderTargetUnit(goliath);

            Assertions.assertFalse(game.getComputerCombat().useStimPack(zealot, RunningGameScenario.INSTRUCTION_LIMIT),
                                   "Only Marines and Firebats stim.");
        });
    }

    @Test
    void is_siege_threatened_should_report_only_detected_enemy_in_range() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit tank = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE, TANK_POSITION_X, TANK_POSITION_Y, COMPUTER);

            ComputerUnitRecordScenario.ensureMilitaryRecord(tank);

            Unit goliath = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_GOLIATH, TANK_POSITION_X, SIEGE_ENEMY_POSITION_Y, HUMAN);

            Assertions.assertTrue(game.getComputerCombat().isSiegeThreatened(tank), "An enemy Goliath 100 pixels away is in siege range.");

            goliath.setStatus(goliath.getStatus() | UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED);
            goliath.setDetectionFlags(0);

            boolean others = false;

            UnitPool ownerPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(ownerPool);

            for (int index = ownerPool.getFirstOwnerIndex(HUMAN); index != 0; index = ownerPool.getNextOwnerIndex(index)) {
                Unit other = ownerPool.getUnit(index);

                others |= other != goliath &&
                          Math.abs(other.getPositionX() - TANK_POSITION_X) < OTHER_ENEMY_DISTANCE &&
                          Math.abs(other.getPositionY() - TANK_POSITION_Y) < OTHER_ENEMY_DISTANCE;
            }

            if (!others) {
                Assertions.assertFalse(game.getComputerCombat().isSiegeThreatened(tank), "An undetected enemy does not threaten the tank.");
            }
        });
    }

    @Test
    void requires_pickup_should_keep_move_target_of_unit_waiting_for_transport() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit zealot = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_ZEALOT, PICKUP_ZEALOT_POSITION_X, PICKUP_POSITION_Y, COMPUTER);

            ComputerUnitRecordScenario.ensureMilitaryRecord(zealot);
            zealot.setPrimaryOrder(ScrOrderIds.PICKUP4);

            int destinationX = zealot.getMovementDestinationX();
            int destinationY = zealot.getMovementDestinationY();

            Assertions.assertTrue(game.getComputerCombat().requiresPickup(zealot, PICKUP_DESTINATION_X, PICKUP_POSITION_Y),
                                  "A unit in order Pickup4 requires a pickup.");
            Assertions.assertFalse(game.getUnitPositioning().setMovementDestination(zealot, PICKUP_DESTINATION_X, PICKUP_POSITION_Y));
            Assertions.assertEquals(destinationX, zealot.getMovementDestinationX());
            Assertions.assertEquals(destinationY, zealot.getMovementDestinationY());
        });
    }

    @Test
    void acquire_target_should_keep_disabled_automatic_target() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit zealot = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_ZEALOT, ACQUIRE_ZEALOT_POSITION_X, ACQUIRE_POSITION_Y, COMPUTER);
            Unit worker = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_SCV, ACQUIRE_WORKER_POSITION_X, ACQUIRE_POSITION_Y, HUMAN);

            ComputerUnitRecordScenario.ensureMilitaryRecord(zealot);
            worker.setLockdownTimer(LOCKDOWN_TIMER);
            zealot.setAutomaticTargetUnit(worker);

            Assertions.assertTrue(game.getComputerCombat().acquireTarget(zealot, 0, true, false, RunningGameScenario.INSTRUCTION_LIMIT));
            Assertions.assertSame(worker, zealot.getAutomaticTargetUnit(), "A disabled target skips the attacked-owner test.");
            Assertions.assertSame(worker, zealot.getOrderTargetUnit());
        });
    }

    @Test
    void update_attack_move_should_attack_automatic_target_and_queue_attack_move() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, ATTACK_MARINE_POSITION_X, ATTACK_POSITION_Y, HUMAN);
            Unit zealot = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_ZEALOT, ATTACK_ZEALOT_POSITION_X, ATTACK_POSITION_Y, COMPUTER);

            Assertions.assertNull(marine.getComputerRecord());

            game.getOrders().issue(marine, ScrOrderIds.ATK_MOVE_EP, null,
                                   ATTACK_MOVE_DESTINATION_X, ATTACK_POSITION_Y,
                                   ScrUnitIds.NONE, false, RunningGameScenario.INSTRUCTION_LIMIT);
            marine.setPrimaryOrderState(1);
            marine.setAutomaticTargetUnit(zealot);

            game.getComputerCombat().updateAttackMove(marine, RunningGameScenario.INSTRUCTION_LIMIT);

            GameData unitData = game.getGameData();

            Assertions.assertNotNull(unitData);

            DatTable units = unitData.getUnits();

            Assertions.assertEquals(units.get("Attack Unit", marine.getUnitId()), marine.getPrimaryOrder(), "The unit attacks its automatic target.");
            Assertions.assertSame(zealot, marine.getOrderTargetUnit());
            Assertions.assertNull(marine.getAutomaticTargetUnit());
            Assertions.assertNotNull(marine.getFirstOrder());
            Assertions.assertEquals(ATTACK_MOVE_ORDER, marine.getFirstOrder().getOrderId(), "The attack move is queued behind the attack.");
        });
    }
}
