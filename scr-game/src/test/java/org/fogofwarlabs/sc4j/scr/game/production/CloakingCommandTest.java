package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;

import java.io.IOException;

/**
 * Checks selected cloaking commands, their admission gates and their secondary-order state.
 */
@RequiresInstallation
final class CloakingCommandTest {
    private static final int UNIT_POSITION_X = 3640;
    private static final int UNIT_POSITION_Y = 2280;
    private static final int ENERGY_FRACTION_BITS = 8;
    private static final int INITIAL_ENERGY = 200 << ENERGY_FRACTION_BITS;
    private static final int RETAINED_SECONDARY_STATE = 7;
    private static final int RETAINED_SECONDARY_POSITION = 0x028E0141;
    private static final int FIRST_SELECTION_POSITION = 0;
    private static final int SECOND_SELECTION_POSITION = 1;
    private static final int HANDLE_WIDTH_BYTES = 2;
    private static final int CLOAK_ORDER = 109;
    private static final int DECLOAK_ORDER = 110;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            IgnoreException.execute(
                    gameData::close
            );
        }

        gameData = null;
    }

    private static @NotNull Unit selectUnit(@NotNull Game game, int type, int owner) {
        Unit unit = RunningGameScenario.createUnit(game, type, UNIT_POSITION_X, UNIT_POSITION_Y, owner);
        UnitPool pool = game.getUnitSystem().getUnitPool();
        UnitSelection selection = game.getSelection();

        Assertions.assertNotNull(pool);
        Assertions.assertNotNull(selection);

        long[] handles = {Integer.toUnsignedLong(pool.getHandle(unit.getIndex()))};

        selection.execute(game, new SelectionRequest(SelectionRequest.Operation.REPLACE, HANDLE_WIDTH_BYTES, handles), owner);

        Assertions.assertSame(unit, selection.getSelectedUnit(owner, FIRST_SELECTION_POSITION));

        unit.setEnergy(INITIAL_ENERGY);

        return unit;
    }


    private @Nullable ReplayRuntime runtime;

    @BeforeEach
    void startGame() throws IOException {
        Assertions.assertNotNull(gameData);

        this.runtime = RunningGameScenario.start(gameData);
    }

    @AfterEach
    void closeGame() {
        this.runtime = null;
    }

    @Test
    void cloak_should_charge_infested_kerrigan_once_and_keep_energy_when_decloaking() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = selectUnit(game, ScrUnitIds.HERO_INFESTED_KERRIGAN, RunningGameScenario.HUMAN);

            Assertions.assertTrue(game.getUnitTechnology().canUseTechnology(unit, ScrTechIds.PERSONNEL_CLOAKING));

            int cost = game.requireGameData().getTechTypes().getEnergyCost(ScrTechIds.PERSONNEL_CLOAKING) << ENERGY_FRACTION_BITS;

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, true);

            Assertions.assertEquals(CLOAK_ORDER, unit.getSecondaryOrder());
            Assertions.assertEquals(INITIAL_ENERGY - cost, unit.getEnergy());

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, false);

            Assertions.assertEquals(DECLOAK_ORDER, unit.getSecondaryOrder());
            Assertions.assertEquals(INITIAL_ENERGY - cost, unit.getEnergy());
        });
    }

    @Test
    void cloak_should_keep_secondary_state_when_the_cloak_order_is_already_active() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = selectUnit(game, ScrUnitIds.HERO_INFESTED_KERRIGAN, RunningGameScenario.HUMAN);
            Unit retainedBuild = RunningGameScenario.createUnit(game,
                                                                ScrUnitIds.TERRAN_MARINE,
                                                                UNIT_POSITION_X, UNIT_POSITION_Y,
                                                                RunningGameScenario.HUMAN);

            unit.setSecondaryOrder(ScrOrderIds.CLOAK);
            unit.setSecondaryOrderPosition(RETAINED_SECONDARY_POSITION);
            unit.setSecondaryOrderState(RETAINED_SECONDARY_STATE);
            unit.setCurrentBuildUnit(retainedBuild);

            int cost = game.requireGameData().getTechTypes().getEnergyCost(ScrTechIds.PERSONNEL_CLOAKING) << ENERGY_FRACTION_BITS;

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, true);

            Assertions.assertEquals(INITIAL_ENERGY - cost, unit.getEnergy());
            Assertions.assertEquals(RETAINED_SECONDARY_POSITION, unit.getSecondaryOrderPosition());
            Assertions.assertEquals(RETAINED_SECONDARY_STATE, unit.getSecondaryOrderState());
            Assertions.assertSame(retainedBuild, unit.getCurrentBuildUnit());
        });
    }

    @Test
    void cloak_should_ignore_a_selected_dying_unit() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = selectUnit(game, ScrUnitIds.HERO_INFESTED_KERRIGAN, RunningGameScenario.HUMAN);

            unit.setPrimaryOrder(ScrOrderIds.DIE);
            unit.setPrimaryOrderState(Unit.DIE_STATE_DESTROYED);

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, true);

            Assertions.assertEquals(INITIAL_ENERGY, unit.getEnergy());
            Assertions.assertNotEquals(CLOAK_ORDER, unit.getSecondaryOrder());
        });
    }

    @Test
    void cloak_should_ignore_a_unit_that_already_requires_a_detector() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = selectUnit(game, ScrUnitIds.HERO_INFESTED_KERRIGAN, RunningGameScenario.HUMAN);

            unit.setStatus(unit.getStatus() | UnitStatusFlags.REQUIRES_DETECTOR);

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, true);

            Assertions.assertEquals(INITIAL_ENERGY, unit.getEnergy());
            Assertions.assertNotEquals(CLOAK_ORDER, unit.getSecondaryOrder());
        });
    }

    @Test
    void cloak_should_require_the_ordinary_units_cloaking_research() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = selectUnit(game, ScrUnitIds.TERRAN_GHOST, RunningGameScenario.HUMAN);
            PlayerTechnology technology = game.getPlayerTable().getPlayerTechnology();

            Assertions.assertNotNull(technology);

            technology.setResearched(RunningGameScenario.HUMAN, ScrTechIds.PERSONNEL_CLOAKING, false);

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, true);

            Assertions.assertEquals(INITIAL_ENERGY, unit.getEnergy());
            Assertions.assertNotEquals(CLOAK_ORDER, unit.getSecondaryOrder());

            technology.setResearched(RunningGameScenario.HUMAN, ScrTechIds.PERSONNEL_CLOAKING, true);

            Assertions.assertTrue(game.getUnitTechnology().canUseTechnology(unit, ScrTechIds.PERSONNEL_CLOAKING));

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, true);

            Assertions.assertEquals(CLOAK_ORDER, unit.getSecondaryOrder());
        });
    }

    @Test
    void cloak_should_stop_at_the_first_null_selection_pointer() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = selectUnit(game, ScrUnitIds.HERO_INFESTED_KERRIGAN, RunningGameScenario.HUMAN);
            UnitSelection selection = game.getSelection();

            Assertions.assertNotNull(selection);

            selection.endCommand();
            selection.setSelectedPoolIndexRaw(RunningGameScenario.HUMAN, FIRST_SELECTION_POSITION, 0);
            selection.setSelectedPoolIndexRaw(RunningGameScenario.HUMAN, SECOND_SELECTION_POSITION, unit.getIndex());

            Assertions.assertNull(selection.getSelectedUnit(RunningGameScenario.HUMAN, FIRST_SELECTION_POSITION));
            Assertions.assertSame(unit, selection.getSelectedUnit(RunningGameScenario.HUMAN, SECOND_SELECTION_POSITION));

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, true);

            Assertions.assertEquals(INITIAL_ENERGY, unit.getEnergy());
            Assertions.assertNotEquals(CLOAK_ORDER, unit.getSecondaryOrder());
        });
    }

    @Test
    void cloak_should_ignore_a_selected_unit_owned_by_another_player() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = selectUnit(game, ScrUnitIds.HERO_INFESTED_KERRIGAN, RunningGameScenario.COMPUTER);
            UnitSelection selection = game.getSelection();

            Assertions.assertNotNull(selection);

            selection.endCommand();
            selection.setSelectedPoolIndexRaw(RunningGameScenario.HUMAN, FIRST_SELECTION_POSITION, unit.getIndex());
            selection.setSelectedPoolIndexRaw(RunningGameScenario.HUMAN, SECOND_SELECTION_POSITION, 0);

            Assertions.assertSame(unit, selection.getSelectedUnit(RunningGameScenario.HUMAN, FIRST_SELECTION_POSITION));
            Assertions.assertTrue(game.getUnitTechnology().canUseTechnology(unit, ScrTechIds.PERSONNEL_CLOAKING));

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, true);

            Assertions.assertEquals(INITIAL_ENERGY, unit.getEnergy());
            Assertions.assertNotEquals(CLOAK_ORDER, unit.getSecondaryOrder());
        });
    }

    @Test
    void cloak_should_keep_energy_and_secondary_order_below_the_technology_cost() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit unit = selectUnit(game, ScrUnitIds.HERO_INFESTED_KERRIGAN, RunningGameScenario.HUMAN);
            int cost = game.requireGameData().getTechTypes().getEnergyCost(ScrTechIds.PERSONNEL_CLOAKING) << ENERGY_FRACTION_BITS;
            int secondaryOrder = unit.getSecondaryOrder();

            Assertions.assertTrue(cost > 0);
            Assertions.assertTrue(game.getUnitTechnology().canUseTechnology(unit, ScrTechIds.PERSONNEL_CLOAKING));

            unit.setEnergy(cost - 1);

            game.getUnitTechnology().cloak(RunningGameScenario.HUMAN, true);

            Assertions.assertEquals(cost - 1, unit.getEnergy());
            Assertions.assertEquals(secondaryOrder, unit.getSecondaryOrder());
        });
    }
}
