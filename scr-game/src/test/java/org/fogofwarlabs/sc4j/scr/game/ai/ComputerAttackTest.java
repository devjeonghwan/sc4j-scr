package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that a prepared computer attack targets an enemy expansion only with an idle attack group, and which attack group types
 * the staging region still misses.
 * <p>
 * The unit record layout is the contract of the queue index test: queue index {@code 5} is the EUD-visible energy WORD.
 */
@RequiresInstallation
final class ComputerAttackTest {
    private static final int COMPUTER = 0;
    private static final int HUMAN = 1;

    /**
     * The region of the computer region record that the missing attack types are counted for.
     */
    private static final int STAGING_REGION = 1;
    private static final int EGG_POSITION_X = 3600;
    private static final int EGG_POSITION_Y = 2600;
    /**
     * The build queue index whose WORD is the energy, the first WORD after the five queue slots.
     */
    private static final int ENERGY_QUEUE_INDEX = 5;
    private static final int COMMAND_CENTER_POSITION_X = 600;
    private static final int COMMAND_CENTER_POSITION_Y = 600;

    private static final int ATTACK_GATHERING_STATE = 8;

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
    void assign_expansion_target_should_stage_prepared_attack_against_enemy_expansion() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = game.getComputerPlayerPhase();

            Assertions.assertNotNull(phase);

            ComputerPlayer player = phase.getPlayer(COMPUTER);

            player.clearAttack(true);
            player.setAttackPreparation(1);
            player.setFlags(player.getFlags() | ComputerPlayer.FLAG_ATTACK_READY);

            RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_COMMAND_CENTER, COMMAND_CENTER_POSITION_X, COMMAND_CENTER_POSITION_Y, HUMAN);

            game.getComputerAttack().assignExpansionTarget(phase, COMPUTER);

            int staging = player.getAttackRegion();

            Assertions.assertNotEquals(0, staging, "The staging region is stored.");

            ComputerRegions regions = phase.getRegions();
            int index = regions.getIndex(COMPUTER, staging - 1);

            Assertions.assertEquals(ATTACK_GATHERING_STATE, regions.getState(index));
            Assertions.assertNotEquals(0, regions.getLinkedRegion(index), "The target region is the linked region.");
            Assertions.assertFalse(player.isAttackPrepared(), "The prepared attack is consumed.");
            Assertions.assertEquals(0, player.getFlags() & ComputerPlayer.FLAG_ATTACK_READY);
        });
    }

    @Test
    void assign_expansion_target_should_keep_attack_prepared_without_idle_group_unit() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = game.getComputerPlayerPhase();

            Assertions.assertNotNull(phase);

            ComputerPlayer player = phase.getPlayer(COMPUTER);

            player.clearAttack(true);
            player.addAttackUnits(1, ScrUnitIds.PROTOSS_CARRIER);
            player.setAttackPreparation(1);
            player.setFlags(player.getFlags() | ComputerPlayer.FLAG_ATTACK_READY);

            game.getComputerAttack().assignExpansionTarget(phase, COMPUTER);

            Assertions.assertEquals(0, player.getAttackRegion(), "Without an idle Carrier the group is incomplete.");
            Assertions.assertTrue(player.isAttackPrepared());

            player.clearAttack(true);
            player.setAttackPreparation(0);
        });
    }

    @Test
    void count_missing_should_count_the_energy_as_the_queued_type_of_an_egg_at_queue_index_five() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = game.getComputerPlayerPhase();

            Assertions.assertNotNull(phase);

            ComputerPlayer player = phase.getPlayer(COMPUTER);
            int index = phase.getRegions().getIndex(COMPUTER, STAGING_REGION);
            int[] missing = new int[player.getAttackTypes().length];

            player.clearAttack(true);
            player.addAttackUnits(1, ScrUnitIds.ZERG_HYDRALISK);

            Assertions.assertEquals(1, game.getComputerAttack().countMissing(phase, index, missing, false));

            Unit egg = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_EGG, EGG_POSITION_X, EGG_POSITION_Y, COMPUTER);

            egg.setStatus(egg.getStatus() & ~UnitStatusFlags.COMPLETED);
            egg.setBuildQueueIndex(ENERGY_QUEUE_INDEX);
            egg.setEnergy(ScrUnitIds.ZERG_HYDRALISK);

            // The Egg in production counts as its queued type, the Hydralisk in its energy WORD.
            Assertions.assertEquals(0, game.getComputerAttack().countMissing(phase, index, missing, false));

            egg.setStatus(egg.getStatus() | UnitStatusFlags.COMPLETED);
            player.clearAttack(true);
        });
    }
}
