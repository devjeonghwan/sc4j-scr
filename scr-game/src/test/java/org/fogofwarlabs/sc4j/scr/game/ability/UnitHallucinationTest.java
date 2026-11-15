package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Verifies that a hallucinated Carrier loses its Interceptors and a hallucinated Reaver hallucinates its stored Scarabs.
 */
@RequiresInstallation
final class UnitHallucinationTest {
    private static final int CARRIER_X = 3560;
    private static final int CARRIER_Y = 2160;
    private static final int REAVER_X = 3700;
    private static final int REAVER_Y = 2440;
    private static final int DIE_ORDER = 0;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void closeGameData() {
        runtime = null;

        if (gameData != null) {
            IgnoreException.execute(
                    gameData::close
            );
        }

        gameData = null;
    }


    @Test
    void convert_to_hallucination_should_kill_stored_interceptors_of_carrier() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            UnitCreationContext context = game.getUnitSystem().getUnitCreationContext();

            Assertions.assertNotNull(context);

            Unit carrier = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_X, CARRIER_Y, RunningGameScenario.COMPUTER);
            Unit interceptor = game.getUnitHangar().createStoredSubordinate(carrier,
                                                                            ScrUnitIds.PROTOSS_INTERCEPTOR,
                                                                            RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotNull(interceptor);
            Assertions.assertEquals(1, carrier.getHangarCount(false));

            game.getUnitHallucination().convertToHallucination(carrier,
                                                               context.preservesNegativeTypeCounts(),
                                                               context.isCompletionRefreshSuppressed(),
                                                               context.getLocalPlayer(),
                                                               RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotEquals(0, carrier.getStatus() & UnitStatusFlags.HALLUCINATION);
            Assertions.assertEquals(0, carrier.getHangarCount(false), "The hangar is emptied.");
            Assertions.assertNull(interceptor.getHangarParent());
            Assertions.assertEquals(DIE_ORDER, interceptor.getPrimaryOrder(), "A stored Interceptor dies.");
        });
    }

    @Test
    void convert_to_hallucination_should_convert_stored_scarabs_of_reaver() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            UnitCreationContext context = game.getUnitSystem().getUnitCreationContext();

            Assertions.assertNotNull(context);

            Unit reaver = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_REAVER, REAVER_X, REAVER_Y, RunningGameScenario.COMPUTER);
            Unit scarab = game.getUnitHangar().createStoredSubordinate(reaver, ScrUnitIds.PROTOSS_SCARAB, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotNull(scarab);

            game.getUnitHallucination().convertToHallucination(reaver,
                                                               context.preservesNegativeTypeCounts(),
                                                               context.isCompletionRefreshSuppressed(),
                                                               context.getLocalPlayer(),
                                                               RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(1, reaver.getHangarCount(false));
            Assertions.assertNotEquals(0, scarab.getStatus() & UnitStatusFlags.HALLUCINATION, "The stored Scarabs become hallucinations.");
        });
    }
}
