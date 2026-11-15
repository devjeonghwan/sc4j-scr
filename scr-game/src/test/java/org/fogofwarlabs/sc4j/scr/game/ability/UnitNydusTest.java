package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
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
 * Verifies which units can enter a Nydus Canal.
 */
@RequiresInstallation
final class UnitNydusTest {
    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @Test
    void can_enter_should_require_completed_linked_exit_of_same_owner() {
        Game game = TwoUnitGameScenario.createGame(gameData, ScrUnitIds.ZERG_ZERGLING, ScrUnitIds.ZERG_NYDUS_CANAL, ScrUnitIds.ZERG_NYDUS_CANAL);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit zergling = pool.getUnit(TwoUnitGameScenario.CASTER_INDEX);
        Unit canal = pool.getUnit(TwoUnitGameScenario.TARGET_INDEX);

        Assertions.assertFalse(game.getUnitNydus().canEnter(zergling, canal));

        Unit exit = pool.getUnit(TwoUnitGameScenario.THIRD_INDEX);

        canal.setNydusExit(exit);

        Assertions.assertTrue(game.getUnitNydus().canEnter(zergling, canal));

        exit.setStatus(exit.getStatus() & ~UnitStatusFlags.COMPLETED);

        Assertions.assertFalse(game.getUnitNydus().canEnter(zergling, canal));

        exit.setStatus(exit.getStatus() | UnitStatusFlags.COMPLETED);
        zergling.setOwnerIndex(TwoUnitGameScenario.OTHER_PLAYER);

        Assertions.assertFalse(game.getUnitNydus().canEnter(zergling, canal));
    }
}
