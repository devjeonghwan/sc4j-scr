package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
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
 * Checks computer reactions to attacks: scanner sweeps against undetected attackers, the cancellation of hit incomplete buildings and
 * the enemy strength of a region.
 */
@RequiresInstallation
final class ComputerDefenseTest {
    private static final int COMPUTER = 0;
    private static final int HUMAN = 1;

    private static final int COMSAT_POSITION_X = 3600;
    private static final int COMSAT_POSITION_Y = 2560;
    private static final int DEFENDER_POSITION_X = 3500;
    private static final int ATTACKER_POSITION_X = 3540;
    private static final int DEFENDER_POSITION_Y = 2500;
    /**
     * The energy of the Comsat Station, {@code 200} in fixed point.
     */
    private static final int COMSAT_ENERGY = 200 << 8;

    private static final int GATEWAY_POSITION_X = 3520;
    private static final int HIT_ATTACKER_POSITION_X = 3600;
    private static final int GATEWAY_POSITION_Y = 2720;
    /**
     * The hit points of the hit Gateway, below a quarter of its maximum, in fixed point.
     */
    private static final int GATEWAY_HIT_POINTS = 100 << 8;

    /**
     * The position of the human Bunker, away from the units of the other tests.
     */
    private static final int BUNKER_X = 640;
    private static final int BUNKER_Y = 640;
    /**
     * A loaded-unit slot past the four slots a Bunker provides.
     */
    private static final int OUTER_PASSENGER_SLOT = 5;

    private static final int SCANNER_SWEEP_ORDER = 139;
    private static final int DIE_ORDER = 0;

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
    void request_detection_should_order_comsat_to_scan_undetected_attacker() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit comsat = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_COMSAT_STATION, COMSAT_POSITION_X, COMSAT_POSITION_Y, COMPUTER);

            comsat.setEnergy(COMSAT_ENERGY);

            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, DEFENDER_POSITION_X, DEFENDER_POSITION_Y, COMPUTER);

            ComputerUnitRecordScenario.ensureMilitaryRecord(marine);

            Unit attacker = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_GHOST, ATTACKER_POSITION_X, DEFENDER_POSITION_Y, HUMAN);

            ComputerDefense detectionDefense = game.getComputerDefense();
            ComputerPlayerPhase detectionPhase = game.getComputerPlayerPhase();

            Assertions.assertNotNull(detectionPhase);

            detectionDefense.requestDetection(detectionPhase, marine, attacker, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(SCANNER_SWEEP_ORDER, comsat.getPrimaryOrder());
            Assertions.assertSame(attacker, comsat.getOrderTargetUnit());
            Assertions.assertNotEquals(0, game.getComputerPlayerPhase().getPlayer(COMPUTER).getFlags() & ComputerPlayer.FLAG_PATROL_ASSIGNMENT);
        });
    }

    @Test
    void on_hit_should_cancel_incomplete_building_below_quarter_life_hit_by_attacker() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerTown town = null;
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstOwnerIndex(COMPUTER); index != 0 && town == null; index = pool.getNextOwnerIndex(index)) {
                ComputerUnitRecord record = pool.getUnit(index).getComputerRecord();

                if (record != null && record.getKind() == ComputerUnitRecord.KIND_BUILDING) {
                    town = record.town;
                }
            }

            Assertions.assertNotNull(town, "The computer's buildings belong to a town.");

            Unit gateway = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_GATEWAY, GATEWAY_POSITION_X, GATEWAY_POSITION_Y, COMPUTER);

            gateway.setStatus(gateway.getStatus() & ~UnitStatusFlags.COMPLETED);

            ComputerUnitRecord record = new ComputerUnitRecord(ComputerUnitRecord.KIND_BUILDING, gateway.getIndex());

            record.town = town;
            gateway.setComputerRecord(record);

            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, HIT_ATTACKER_POSITION_X, GATEWAY_POSITION_Y, HUMAN);
            Player owner = game.getPlayerTable().getPlayer(COMPUTER);

            Assertions.assertNotNull(owner);

            int minerals = owner.getMinerals();

            Assertions.assertNotNull(gameData);

            int refund = gameData.getUnits().get("Mineral Cost", ScrUnitIds.PROTOSS_GATEWAY) * 3 / 4;

            gateway.setShieldPoints(0);
            gateway.setHitPoints(GATEWAY_HIT_POINTS);

            ComputerDefense unattributedHitDefense = game.getComputerDefense();
            ComputerPlayerPhase unattributedHitPhase = game.getComputerPlayerPhase();

            Assertions.assertNotNull(unattributedHitPhase);

            unattributedHitDefense.onHit(unattributedHitPhase, gateway, null, true, false);

            Assertions.assertNotEquals(DIE_ORDER, gateway.getPrimaryOrder(), "A hit without an attacker does not cancel the building.");
            Assertions.assertEquals(minerals, owner.getMinerals());

            ComputerDefense attackerHitDefense = game.getComputerDefense();
            ComputerPlayerPhase attackerHitPhase = game.getComputerPlayerPhase();

            Assertions.assertNotNull(attackerHitPhase);

            attackerHitDefense.onHit(attackerHitPhase, gateway, marine, true, false);

            Assertions.assertEquals(DIE_ORDER, gateway.getPrimaryOrder(), "The cancelled building dies.");
            Assertions.assertEquals(minerals + refund, owner.getMinerals(), "Three quarters of the cost are refunded.");
        });
    }

    @Test
    void get_region_strength_should_count_a_bunker_passenger_in_any_of_the_eight_loaded_unit_slots() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerDefense defense = game.getComputerDefense();
            ComputerPlayerPhase phase = game.getComputerPlayerPhase();
            Unit bunker = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_BUNKER, BUNKER_X, BUNKER_Y, HUMAN);
            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, BUNKER_X, BUNKER_Y, HUMAN);

            Assertions.assertNotNull(phase);

            game.getUnitTransport().load(bunker, marine, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertSame(marine, game.getUnitTransport().getLoadedUnit(bunker, 0));

            int region = game.getComputerTerrain().getUnitRegion(bunker);
            int handle = bunker.getLoadedUnitHandle(0);
            int extensionBits = bunker.getLoadedUnitHandleExtensionBits() & Unit.LOADED_HANDLE_EXTENSION_MASK;
            int firstSlotStrength = defense.getRegionStrength(phase, COMPUTER, region, false);

            bunker.setLoadedUnitHandle(0, 0);
            bunker.setLoadedUnitHandle(OUTER_PASSENGER_SLOT, handle);
            bunker.setLoadedUnitHandleExtensionBits(extensionBits << (OUTER_PASSENGER_SLOT * Unit.LOADED_HANDLE_EXTENSION_BITS));

            int outerSlotStrength = defense.getRegionStrength(phase, COMPUTER, region, false);

            bunker.setLoadedUnitHandle(OUTER_PASSENGER_SLOT, 0);

            int emptyStrength = defense.getRegionStrength(phase, COMPUTER, region, false);

            Assertions.assertNotEquals(emptyStrength, firstSlotStrength, "The Bunker counts its passenger.");
            Assertions.assertEquals(firstSlotStrength, outerSlotStrength, "A passenger past the provided space still counts.");
        });
    }
}
