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
 * Verifies which Command Centers a Queen can infest.
 */
@RequiresInstallation
final class UnitInfestationTest {
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    private static final int ONE_HIT_POINT = 1 << FIXED_POINT_FRACTION_BITS;
    private static final int HALF_HIT_POINTS_DIVISOR = 2;

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
    void can_be_infested_should_require_completed_command_center_below_half_hit_points() {
        Game game = TwoUnitGameScenario.createGame(gameData, ScrUnitIds.ZERG_QUEEN, ScrUnitIds.TERRAN_COMMAND_CENTER);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit center = pool.getUnit(TwoUnitGameScenario.TARGET_INDEX);

        Assertions.assertNotNull(gameData);

        int maximum = gameData.getUnits().get("Hit Points", ScrUnitIds.TERRAN_COMMAND_CENTER);

        Assertions.assertFalse(game.getUnitInfestation().canBeInfested(center));

        center.setHitPoints(maximum / HALF_HIT_POINTS_DIVISOR);

        Assertions.assertFalse(game.getUnitInfestation().canBeInfested(center));

        center.setHitPoints(maximum / HALF_HIT_POINTS_DIVISOR - ONE_HIT_POINT);

        Assertions.assertTrue(game.getUnitInfestation().canBeInfested(center));

        center.setStatus(center.getStatus() & ~UnitStatusFlags.COMPLETED);

        Assertions.assertFalse(game.getUnitInfestation().canBeInfested(center));
    }
}
