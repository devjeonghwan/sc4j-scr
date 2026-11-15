package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.combat.CombatGameScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
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
 * Checks that {@link UnitOrderEligibility} lets a unit receive and target itself with the orders that the order table allows for its state.
 */
@RequiresInstallation
final class UnitOrderEligibilityTest {
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

    private static @NotNull Game createDuelGame(int firstUnitType, int secondUnitType) {
        Assertions.assertNotNull(gameData);

        return CombatGameScenario.createDuelGame(gameData, firstUnitType, secondUnitType);
    }


    @Test
    void order_eligibility_follows_order_table() {
        Game game = createDuelGame(ScrUnitIds.PROTOSS_PROBE, ScrUnitIds.PROTOSS_PROBE);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(CombatGameScenario.FIRST_UNIT_INDEX);

        Assertions.assertTrue(game.getUnitOrderEligibility().canReceiveOrder(unit, ScrOrderIds.ATTACK_UNIT, 0));
        Assertions.assertTrue(game.getUnitOrderEligibility().canReceiveOrder(unit, ScrOrderIds.PLACE_PROTOSS_BUILDING, 0));
        Assertions.assertFalse(game.getUnitOrderEligibility().canReceiveOrder(unit, ScrOrderIds.PLACE_BUILDING, 0));
        Assertions.assertFalse(game.getUnitOrderEligibility().canReceiveOrder(unit, ScrOrderIds.RALLY_POINT_UNIT, 0));

        unit.setStatus(unit.getStatus() | UnitStatusFlags.GROUNDED_BUILDING);

        Assertions.assertFalse(game.getUnitOrderEligibility().canReceiveOrder(unit, ScrOrderIds.ATTACK_UNIT, 0));
        Assertions.assertFalse(game.getUnitOrderEligibility().canReceiveOrder(unit, ScrOrderIds.ATTACK_DEFAULT, 0));
        Assertions.assertTrue(game.getUnitOrderEligibility().canReceiveOrder(unit, ScrOrderIds.RALLY_POINT_UNIT, 0));
        Assertions.assertTrue(game.getUnitOrderEligibility().canTargetSelf(unit, ScrOrderIds.RALLY_POINT_TILE));

        unit.setStatus(unit.getStatus() & ~UnitStatusFlags.GROUNDED_BUILDING | UnitStatusFlags.HALLUCINATION);

        Assertions.assertFalse(game.getUnitOrderEligibility().canReceiveOrder(unit, ScrOrderIds.PLACE_PROTOSS_BUILDING, 0));
        Assertions.assertTrue(game.getUnitOrderEligibility().canReceiveOrder(unit, ScrOrderIds.ATTACK_UNIT, 0));
    }
}
