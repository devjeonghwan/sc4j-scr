package org.fogofwarlabs.sc4j.scr.game.resource;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks resource-depot selection for a completed worker in a genuine running game.
 */
@RequiresInstallation
final class UnitResourceSearchTest {
    private static final int EXPECTED_COMPLETED_COMMAND_CENTER_COUNT = 1;

    private static final int WORKER_CREATION_X = 3560;
    private static final int WORKER_CREATION_Y = 2160;

    /**
     * Finds the unique completed own Command Center admitted by the running scenario.
     *
     * @param game the running game
     * @return the admitted completed depot
     */
    private static @NotNull Unit findOnlyOwnCommandCenter(@NotNull Game game) {
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(accounting);
        Assertions.assertEquals(EXPECTED_COMPLETED_COMMAND_CENTER_COUNT,
                                accounting.getCompletedTypeCount(ScrUnitIds.TERRAN_COMMAND_CENTER, RunningGameScenario.HUMAN),
                                "The fixture must have exactly one completed human Command Center.");
        Assertions.assertEquals(0, accounting.getCompletedTypeCount(ScrUnitIds.ZERG_HATCHERY, RunningGameScenario.HUMAN));
        Assertions.assertEquals(0, accounting.getCompletedTypeCount(ScrUnitIds.PROTOSS_NEXUS, RunningGameScenario.HUMAN));
        Assertions.assertEquals(0, accounting.getCompletedTypeCount(ScrUnitIds.ZERG_LAIR, RunningGameScenario.HUMAN));
        Assertions.assertEquals(0, accounting.getCompletedTypeCount(ScrUnitIds.ZERG_HIVE, RunningGameScenario.HUMAN));

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit result = null;

        for (int unitIndex = pool.getFirstCreatedIndex(true); unitIndex != 0; unitIndex = pool.getNextCreatedIndex(unitIndex)) {
            Unit unit = pool.getUnit(unitIndex);

            if (unit.getOwnerIndex() != RunningGameScenario.HUMAN || unit.getUnitId() != ScrUnitIds.TERRAN_COMMAND_CENTER ||
                (unit.getStatus() & (UnitStatusFlags.GROUNDED_BUILDING | UnitStatusFlags.COMPLETED)) !=
                (UnitStatusFlags.GROUNDED_BUILDING | UnitStatusFlags.COMPLETED) || unit.getPrimaryOrder() == ScrOrderIds.DIE) {
                continue;
            }

            Assertions.assertNull(result, "The fixture must expose exactly one completed human Command Center.");
            result = unit;
        }

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getSpriteOrNull());
        Assertions.assertTrue(result.getMinimumHorizontalEdgeIndex() >= 0);
        Assertions.assertTrue(game.getUnitResourceSearch().isActiveDepot(result));

        return result;
    }


    @Test
    void find_depot_should_accept_a_worker_without_the_ground_elevation_path_flag() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit depot = findOnlyOwnCommandCenter(game);
                Unit worker = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_SCV,
                                                             WORKER_CREATION_X, WORKER_CREATION_Y, RunningGameScenario.HUMAN);

                Assertions.assertNotNull(worker.getSpriteOrNull());
                Assertions.assertTrue(worker.getMinimumHorizontalEdgeIndex() >= 0);

                int initialPathFlags = worker.getPathCollisionFlags();

                worker.setPathCollisionFlags(initialPathFlags & ~UnitInitializer.GROUND_ELEVATION_PATH_FLAG);

                try {
                    Assertions.assertEquals(0, worker.getPathCollisionFlags() & UnitInitializer.GROUND_ELEVATION_PATH_FLAG);
                    Assertions.assertSame(depot, game.getUnitResourceSearch().findDepot(worker),
                                          "A worker without the ground-elevation path flag can use its own active depot.");
                } finally {
                    worker.setPathCollisionFlags(initialPathFlags);
                }

                Assertions.assertEquals(initialPathFlags, worker.getPathCollisionFlags());
            });
        }
    }

    @Test
    void find_depot_should_return_the_own_depot_for_a_ground_worker_and_reject_a_non_depot() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit depot = findOnlyOwnCommandCenter(game);
                Unit worker = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_SCV,
                                                             WORKER_CREATION_X, WORKER_CREATION_Y, RunningGameScenario.HUMAN);

                Assertions.assertNotNull(worker.getSpriteOrNull());
                Assertions.assertTrue((worker.getPathCollisionFlags() & UnitInitializer.GROUND_ELEVATION_PATH_FLAG) != 0);
                Assertions.assertNotNull(game.getPathSystem().getTerrainContours());
                Assertions.assertTrue(worker.sharesTerrainComponent(game.getPathSystem().getTerrainContours().getRegions(),
                                                                    (short) depot.requireSprite().getX(), (short) depot.requireSprite().getY()),
                                      "The fixture must put the worker and own depot in the same component.");
                Assertions.assertSame(depot, game.getUnitResourceSearch().findDepot(worker));
                Assertions.assertFalse(game.getUnitResourceSearch().isActiveDepot(worker));
            });
        }
    }
}
