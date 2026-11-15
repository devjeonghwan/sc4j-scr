package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.constant.WeaponHitTypes;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.CampaignMissionScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that the computer adopts trigger-created units, the Broodlings of its Queens and its hallucinations.
 */
@RequiresInstallation
final class ComputerAdoptedUnitTest {
    /**
     * The campaign mission of the mission test, played as Brood War.
     */
    private static final @NotNull String CAMPAIGN_MISSION = "bw-protoss-1";
    private static final int CANAL_X = 1472;
    private static final int CANAL_Y = 1280;
    /**
     * The owner of the trigger-created Zerglings, player 5.
     */
    private static final int ZERGLING_OWNER = 4;
    /**
     * The distance below which a Zergling stands beside the Nydus Canal.
     */
    private static final int CANAL_DISTANCE_LIMIT = 128;
    /**
     * The frames of the mission that run before the Zergling is searched.
     */
    private static final int START_FRAMES = 30;
    /**
     * The frame before which the Zergling has to attack the Zealot.
     */
    private static final int ATTACK_FRAME_LIMIT = 400;
    /**
     * The distance above the Zergling at which the Zealot is created.
     */
    private static final int ZEALOT_DISTANCE = 96;

    private static final int COMPUTER = 0;
    private static final int HUMAN = 1;
    private static final int QUEEN_POSITION_X = 3640;
    private static final int QUEEN_POSITION_Y = 2480;
    private static final int TARGET_POSITION_X = 3680;
    private static final int TARGET_POSITION_Y = 2520;
    private static final int TEMPLAR_POSITION_X = 3660;
    private static final int ZEALOT_POSITION_X = 3700;
    private static final int CASTER_POSITION_Y = 2340;
    /**
     * The energy of the High Templar, {@code 200} in fixed point.
     */
    private static final int TEMPLAR_ENERGY = 200 << 8;

    /**
     * The frames that refresh the vision of newly created units.
     */
    private static final int VISION_FRAMES = 2;
    /**
     * The order updates of a cast.
     */
    private static final int CAST_FRAMES = 48;

    private static final int GUARD_KIND = 1;
    private static final int MILITARY_KIND = 4;
    private static final int IDLE_REGION_STATE = 0;

    private static @Nullable GameData runningGameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void startGame() throws IOException {
        runningGameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime         = RunningGameScenario.start(runningGameData);
    }

    @AfterAll
    static void close() {
        runtime = null;

        if (runningGameData != null) {
            runningGameData.close();
        }

        runningGameData = null;
    }

    private static @NotNull Unit findZergling(@NotNull Game simulation) {
        UnitPool pool = simulation.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit unit = pool.getUnit(index);

            if (unit.getUnitId() != ScrUnitIds.ZERG_ZERGLING || unit.getOwnerIndex() != ZERGLING_OWNER) {
                continue;
            }

            int distance = Math.abs(unit.getPositionX() - CANAL_X) + Math.abs(unit.getPositionY() - CANAL_Y);

            if (distance < bestDistance) {
                best         = unit;
                bestDistance = distance;
            }
        }

        Assertions.assertNotNull(best, "No Zergling of player 5 at the Nydus Canal");
        Assertions.assertTrue(bestDistance < CANAL_DISTANCE_LIMIT, "No Zergling of player 5 beside the Nydus Canal");

        return best;
    }


    @Test
    void trigger_created_zergling_guards_its_position_and_attacks_approaching_enemy() throws IOException {
        CampaignMissionScenario.run(ExternalInput.INSTALLATION.getPath(), CAMPAIGN_MISSION, true, (game, live) -> {
            Game simulation = live.getGame();
            int frame = 0;

            for (; frame < START_FRAMES; frame++) {
                game.exchangeTurn(simulation.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
            }

            // The trigger "Create Unit" of the Nydus Canal at (1472, 1280) made this Zergling after the map units.
            Unit zergling = findZergling(simulation);
            ComputerUnitRecord record = zergling.getComputerRecord();

            Assertions.assertNotNull(record, "the trigger-created Zergling has no computer record");
            Assertions.assertEquals(GUARD_KIND, record.getKind());
            Assertions.assertEquals(zergling.getPositionX(), record.guardX);
            Assertions.assertEquals(zergling.getPositionY(), record.guardY);

            int unitTypeIdentifier = ScrUnitIds.PROTOSS_ZEALOT;

            int positionX = zergling.getPositionX();
            int positionY = zergling.getPositionY() - ZEALOT_DISTANCE;

            int owner = game.getLocalPlayer();

            Unit zealot = CampaignMissionScenario.createUnit(simulation, unitTypeIdentifier, positionX, positionY, owner);

            boolean attacked = false;

            for (; frame < ATTACK_FRAME_LIMIT && !attacked; frame++) {
                game.exchangeTurn(simulation.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));

                attacked = zergling.getOrderTargetUnit() == zealot;
            }

            Assertions.assertTrue(attacked, "the Zergling never attacked the Zealot beside its canal");
        });
    }

    @Test
    void computer_queen_broodlings_join_a_military_region() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit queen = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_QUEEN, QUEEN_POSITION_X, QUEEN_POSITION_Y, COMPUTER);

            ComputerUnitRecordScenario.ensureMilitaryRecord(queen);

            Unit target = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, TARGET_POSITION_X, TARGET_POSITION_Y, HUMAN);
            Sprite sprite = target.getSpriteOrNull();

            Assertions.assertNotNull(sprite);

            UnitPool poolBeforeBroodlings = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(poolBeforeBroodlings);

            int before = poolBeforeBroodlings.getAllocatedCount();

            game.getUnitSpells().hit(WeaponHitTypes.BROODLINGS,
                                     COMPUTER,
                                     queen,
                                     target,
                                     sprite.getX(), sprite.getY(),
                                     sprite.getX(), sprite.getY(),
                                     RunningGameScenario.INSTRUCTION_LIMIT);

            int broodlings = 0;
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstOwnerIndex(COMPUTER); index != 0; index = pool.getNextOwnerIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() != ScrUnitIds.ZERG_BROODLING) {
                    continue;
                }

                broodlings++;

                ComputerUnitRecord record = unit.getComputerRecord();

                Assertions.assertNotNull(record, "The Broodling is linked to a military list.");
                Assertions.assertEquals(MILITARY_KIND, record.getKind());

                ComputerPlayerPhase broodlingPhase = game.getComputerPlayerPhase();

                Assertions.assertNotNull(broodlingPhase);

                Assertions.assertNotEquals(IDLE_REGION_STATE, broodlingPhase.getRegions().getState(record.regionIndex));
            }

            Assertions.assertTrue(broodlings > 0 && pool.getAllocatedCount() > before);
        });
    }

    @Test
    void computer_hallucination_is_handed_to_its_computer_player() {
        Unit[] caster = new Unit[2];

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();

            PlayerTechnology hallucinationTechnology = game.getPlayerTable().getPlayerTechnology();

            Assertions.assertNotNull(hallucinationTechnology);

            hallucinationTechnology.setResearched(COMPUTER, ScrTechIds.HALLUCINATION, true);
            caster[0] = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_HIGH_TEMPLAR, TEMPLAR_POSITION_X, CASTER_POSITION_Y, COMPUTER);
            caster[1] = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_ZEALOT, ZEALOT_POSITION_X, CASTER_POSITION_Y, COMPUTER);
        });

        Assertions.assertNotNull(runtime);

        Game game = runtime.getGame();

        for (int frame = 0; frame < VISION_FRAMES; frame++) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }

        Unit templar = caster[0];

        Assertions.assertNotNull(templar);

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            templar.setEnergy(TEMPLAR_ENERGY);
            game.getOrders().issue(templar,
                                   ScrOrderIds.CAST_HALLUCINATION,
                                   caster[1],
                                   caster[1].getPositionX(), caster[1].getPositionY(),
                                   ScrUnitIds.NONE,
                                   false,
                                   RunningGameScenario.INSTRUCTION_LIMIT);
        });

        for (int frame = 0; frame < CAST_FRAMES && templar.getPrimaryOrder() == ScrOrderIds.CAST_HALLUCINATION; frame++) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            int hallucinations = 0;
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstOwnerIndex(COMPUTER); index != 0; index = pool.getNextOwnerIndex(index)) {
                Unit unit = pool.getUnit(index);

                if ((unit.getStatus() & UnitStatusFlags.HALLUCINATION) == 0 || unit.getUnitId() != ScrUnitIds.PROTOSS_ZEALOT) {
                    continue;
                }

                hallucinations++;

                Assertions.assertNotNull(unit.getComputerRecord(), "The hallucination gets a computer record.");
            }

            Assertions.assertTrue(hallucinations > 0,
                                  "order " + templar.getPrimaryOrder() + " state " + templar.getPrimaryOrderState() +
                                  " energy " + templar.getEnergy());
        });
    }
}
