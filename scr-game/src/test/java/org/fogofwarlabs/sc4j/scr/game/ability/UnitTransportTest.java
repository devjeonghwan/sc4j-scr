package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the walk over the units loaded in a container.
 */
@RequiresInstallation
final class UnitTransportTest {
    private static final int TRANSPORT_X = 512;
    private static final int TRANSPORT_Y = 512;

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
            gameData.close();
        }
    }


    @Test
    void for_each_loaded_unit_should_visit_loaded_units_in_slot_order_until_the_visitor_accepts_one() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            UnitTransport transport = game.getUnitTransport();
            int owner = RunningGameScenario.HUMAN;
            Unit dropship = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_DROPSHIP, TRANSPORT_X, TRANSPORT_Y, owner);
            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, TRANSPORT_X, TRANSPORT_Y, owner);
            Unit firebat = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_FIREBAT, TRANSPORT_X, TRANSPORT_Y, owner);
            Unit ghost = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_GHOST, TRANSPORT_X, TRANSPORT_Y, owner);
            List<Unit> visited = new ArrayList<>();

            transport.load(dropship, marine, RunningGameScenario.INSTRUCTION_LIMIT);
            transport.load(dropship, firebat, RunningGameScenario.INSTRUCTION_LIMIT);
            transport.load(dropship, ghost, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertTrue(transport.forEachLoadedUnit(dropship, loaded -> {
                visited.add(loaded);

                return loaded == firebat;
            }));
            Assertions.assertEquals(List.of(marine, firebat), visited, "the walk ends at the accepted unit");

            visited.clear();

            Assertions.assertFalse(transport.forEachLoadedUnit(dropship, loaded -> {
                visited.add(loaded);

                return false;
            }));
            Assertions.assertEquals(List.of(marine, firebat, ghost), visited, "every loaded unit in slot order");
            Assertions.assertFalse(transport.forEachLoadedUnit(marine, loaded -> true), "a unit without loaded units");
        });
    }
}
