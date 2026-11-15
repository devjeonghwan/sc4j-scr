package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that a command dispatch is an active scope that does not overlap frame phases, and that frame phases run one at a time after the
 * initialization.
 */
final class GameExecutionScopeTest {
    /**
     * The phase invocation index of the frame phase that follows the command dispatch.
     */
    private static final int FRAME_PHASE_INDEX = 3;
    /**
     * The instruction limit of the speed recalculation inside the frame phases.
     */
    private static final int INSTRUCTION_LIMIT = 512;
    /**
     * The frame phase indices the scope opens one at a time.
     */
    private static final int @NotNull [] FRAME_PHASE_INDICES = {0, 1, 6, 7, 8, 99, 100, Integer.MAX_VALUE};

    @Test
    void begin_command_dispatch_should_open_active_scope_outside_frame_phases() {
        GameExecutionScope scope = new GameExecutionScope();

        scope.finishInitialization();

        Assertions.assertThrows(UnsupportedOperationException.class, scope::requireActiveScope);

        scope.beginCommandDispatch();

        Assertions.assertTrue(scope.isCommandDispatchActive());

        scope.requireActiveScope();

        Assertions.assertThrows(IllegalStateException.class, () -> scope.beginFramePhase(0), "Dispatch and frame phases do not overlap.");
        Assertions.assertThrows(IllegalStateException.class, scope::beginCommandDispatch);

        scope.endCommandDispatch();

        Assertions.assertThrows(UnsupportedOperationException.class, scope::requireActiveScope);

        scope.beginFramePhase(FRAME_PHASE_INDEX);

        Assertions.assertThrows(IllegalStateException.class, scope::beginCommandDispatch);

        scope.endFramePhase();
    }

    @RequiresInstallation
    @Test
    void execution_scope_allows_one_frame_phase_at_a_time_after_initialization() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

            game.initializeExecutionScope();

            GameExecutionScope executionScope = game.getExecutionScope();

            Assertions.assertNotNull(executionScope);

            Assertions.assertThrows(IllegalStateException.class, () -> executionScope.beginFramePhase(0));

            executionScope.finishInitialization();

            Unit unit = UnitTestUtils.createUnit(1);

            UnitMemoryScenario.of(unit).writeMemoryWord(UnitMemoryScenario.UNIT_TYPE_OFFSET, ScrUnitIds.ZERG_OVERLORD);

            for (int phaseIndex : FRAME_PHASE_INDICES) {
                executionScope.beginFramePhase(phaseIndex);

                try {
                    Assertions.assertEquals(phaseIndex, executionScope.getFramePhase());
                    Assertions.assertThrows(IllegalStateException.class, () -> executionScope.beginFramePhase(0));

                    game.getUnitSpeed().recalculate(unit, INSTRUCTION_LIMIT);

                    Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
                } finally {
                    executionScope.endFramePhase();
                }

                Assertions.assertEquals(-1, executionScope.getFramePhase());
                Assertions.assertThrows(UnsupportedOperationException.class, executionScope::requireActiveScope);
            }

            Assertions.assertThrows(IllegalArgumentException.class, () -> executionScope.beginFramePhase(-1));
        }
    }
}
