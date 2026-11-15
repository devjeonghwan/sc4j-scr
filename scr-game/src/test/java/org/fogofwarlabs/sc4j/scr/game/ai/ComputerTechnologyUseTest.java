package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.production.ScrRequirementSystem;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that the computer technology use test stores the requirement failure code.
 */
@RequiresInstallation
final class ComputerTechnologyUseTest {
    private static final int COMPUTER = 0;
    private static final int HUMAN = 1;

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
    void can_use_technology_should_store_requirement_failure_code() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = game.getComputerPlayerPhase();

            Assertions.assertNotNull(phase);

            Unit zealot = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_ZEALOT, 3600, 2300, COMPUTER);

            Assertions.assertFalse(phase.canUseTechnology(zealot, ScrTechIds.STIM_PACKS));

            int expected = ScrRequirementSystem.getFailureCode(game.getRequirements().checkTechnologyUse(zealot, ScrTechIds.STIM_PACKS, COMPUTER));

            Assertions.assertNotEquals(0, expected);
            Assertions.assertEquals(expected, phase.getLastError(), "The failed test stores its requirement failure code.");

            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, 3600, 2340, HUMAN);

            PlayerTechnology stimTechnology = game.getPlayerTable().getPlayerTechnology();

            Assertions.assertNotNull(stimTechnology);

            stimTechnology.setResearched(HUMAN, ScrTechIds.STIM_PACKS, true);

            Assertions.assertTrue(phase.canUseTechnology(marine, ScrTechIds.STIM_PACKS));
            Assertions.assertEquals(0, phase.getLastError());
        });
    }
}
