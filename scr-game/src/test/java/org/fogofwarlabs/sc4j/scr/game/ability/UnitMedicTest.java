package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
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
 * Verifies Medic target eligibility and healing orders.
 */
@RequiresInstallation
final class UnitMedicTest {
    /**
     * The team size of the selection outside a team game.
     */
    private static final int NO_TEAM_SIZE = 0;

    private static final int INSTRUCTION_LIMIT = 10000;
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    private static final int MEDIC_POSITION_X = 200;
    private static final int PATIENT_POSITION_X = 220;
    private static final int UNIT_POSITION_Y = 200;
    private static final int MEDIC_INDEX = 1;
    private static final int PATIENT_INDEX = 2;
    private static final int HANDLE_WIDTH_BYTES = 2;

    private static final int DAMAGE = 10;
    private static final int MEDIC_ENERGY = 50;
    private static final int MEDIC_HEAL_ORDER = 176;
    private static final int HEAL_STATE_HEALING = 1;
    private static final int MAXIMUM_HEAL_AMOUNT = 200;
    private static final int HIT_POINTS_PER_ENERGY = 2;
    private static final int HEALING_STATE_HEALED = 1;

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

    private static @NotNull Game createState(int patientType) {
        Game game = TwoUnitGameScenario.createGame(gameData);
        int medicType = ScrUnitIds.TERRAN_MEDIC;

        TwoUnitGameScenario.initializeUnit(game, MEDIC_INDEX, medicType, MEDIC_POSITION_X, UNIT_POSITION_Y, ScrOrderIds.MEDIC_IDLE);
        TwoUnitGameScenario.initializeUnit(game, PATIENT_INDEX, patientType, PATIENT_POSITION_X, UNIT_POSITION_Y, ScrOrderIds.MEDIC_IDLE);

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        UnitSelection selection = new UnitSelection(pool, false, NO_TEAM_SIZE, PlayerTable.PLAYABLE_PLAYER_SLOTS);

        game.setSelection(selection);
        selection.execute(game,
                          new SelectionRequest(SelectionRequest.Operation.REPLACE,
                                               HANDLE_WIDTH_BYTES,
                                               new long[]{Integer.toUnsignedLong(pool.getHandle(MEDIC_INDEX))}),
                          TwoUnitGameScenario.PLAYER);

        return game;
    }


    @Test
    void can_heal_should_accept_only_damaged_real_target_other_than_medic() {
        Game game = createState(ScrUnitIds.TERRAN_MARINE);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit medic = pool.getUnit(MEDIC_INDEX);
        Unit marine = pool.getUnit(PATIENT_INDEX);

        Assertions.assertFalse(game.getUnitMedic().canHeal(medic, marine));

        marine.setHitPoints(marine.getHitPoints() - (DAMAGE << FIXED_POINT_FRACTION_BITS));

        Assertions.assertTrue(game.getUnitMedic().canHeal(medic, marine));
        Assertions.assertFalse(game.getUnitMedic().canHeal(medic, medic));
        Assertions.assertFalse(game.getUnitMedic().canHeal(medic, null));

        marine.setStatus(marine.getStatus() | UnitStatusFlags.HALLUCINATION);

        Assertions.assertFalse(game.getUnitMedic().canHeal(medic, marine));
    }

    @Test
    void idle_medic_heals_nearby_damaged_unit_at_half_energy_per_hit_point() {
        Game game = createState(ScrUnitIds.TERRAN_MARINE);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit medic = pool.getUnit(MEDIC_INDEX);
        Unit marine = pool.getUnit(PATIENT_INDEX);

        Assertions.assertNotNull(gameData);

        int maximum = gameData.getUnits().get("Hit Points", ScrUnitIds.TERRAN_MARINE);

        marine.setHitPoints(maximum - (DAMAGE << FIXED_POINT_FRACTION_BITS));
        medic.setEnergy(MEDIC_ENERGY << FIXED_POINT_FRACTION_BITS);

        int randomCount = game.getRandom().getTotalRandomCounts();

        game.getUnitMedic().updateIdle(medic, INSTRUCTION_LIMIT);

        Assertions.assertEquals(MEDIC_HEAL_ORDER, medic.getPrimaryOrder());
        Assertions.assertSame(marine, medic.getOrderTargetUnit());

        medic.setPrimaryOrderState(HEAL_STATE_HEALING);

        game.getUnitMedic().updateHeal(medic, INSTRUCTION_LIMIT);

        int healed = Math.min(DAMAGE << FIXED_POINT_FRACTION_BITS, MAXIMUM_HEAL_AMOUNT);

        Assertions.assertEquals(maximum - (DAMAGE << FIXED_POINT_FRACTION_BITS) + healed, marine.getHitPoints());
        Assertions.assertEquals((MEDIC_ENERGY << FIXED_POINT_FRACTION_BITS) - healed / HIT_POINTS_PER_ENERGY, medic.getEnergy());
        Assertions.assertEquals(HEALING_STATE_HEALED, marine.getHealingState());
        Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
    }
}
