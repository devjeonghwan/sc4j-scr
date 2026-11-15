package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.visibility.SelectedUnitScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the cloak and siege commands: cloaking a Ghost orders the cloak and charges its energy, the commands need the research and skip the units
 * they do not apply to.
 */
@RequiresInstallation
final class UnitTechnologyTest {
    private static final int GHOST_POSITION_X = 3640;
    private static final int GHOST_POSITION_Y = 2280;
    /**
     * The Cloak and Decloak orders.
     */
    private static final int CLOAK_ORDER = 109;
    private static final int DECLOAK_ORDER = 110;

    private static final int ENERGY_FRACTION_BITS = 8;
    private static final int INITIAL_ENERGY = 200 << ENERGY_FRACTION_BITS;

    /**
     * The instruction budget of every order and script call.
     */
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * The slot of the first player, who owns the unit of the selected-unit game.
     */
    private static final int FIRST_PLAYER = SelectedUnitScenario.FIRST_PLAYER;
    /**
     * The pool index of the unit of the selected-unit game.
     */
    private static final int UNIT_POOL_INDEX = SelectedUnitScenario.UNIT_POOL_INDEX;
    /**
     * The energy of the Ghost before the cloak command.
     */
    private static final int GHOST_ENERGY = 100;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void startGame() throws IOException {
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

    /**
     * Creates a {@link Game} instance on a small installed-tileset map with one completed unit of the given type, owned and selected by the first
     * player ({@link SelectedUnitScenario#createGame}).
     *
     * @param unitType the unit type
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createGame(int unitType) {
        Assertions.assertNotNull(gameData);

        return SelectedUnitScenario.createGame(gameData, unitType);
    }


    @Test
    void cloak_unit_should_order_cloak_and_charge_energy() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            PlayerTechnology cloakTechnology = game.getPlayerTable().getPlayerTechnology();

            Assertions.assertNotNull(cloakTechnology);

            cloakTechnology.setResearched(RunningGameScenario.COMPUTER, ScrTechIds.PERSONNEL_CLOAKING, true);

            Unit ghost = RunningGameScenario.createUnit(game,
                                                        ScrUnitIds.TERRAN_GHOST,
                                                        GHOST_POSITION_X, GHOST_POSITION_Y,
                                                        RunningGameScenario.COMPUTER);

            ghost.setEnergy(INITIAL_ENERGY);
            game.getUnitTechnology().cloakUnit(ghost);

            Assertions.assertEquals(CLOAK_ORDER, ghost.getSecondaryOrder());

            Assertions.assertNotNull(gameData);

            int cost = gameData.getTechdata().get("Energy", ScrTechIds.PERSONNEL_CLOAKING) << ENERGY_FRACTION_BITS;

            Assertions.assertEquals(INITIAL_ENERGY - cost, ghost.getEnergy());
        });
    }

    @Test
    void cloak_command_needs_research_and_charges_energy_and_decloak_command_follows() {
        Assertions.assertNotNull(gameData);

        Game game = createGame(ScrUnitIds.TERRAN_GHOST);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit ghost = unitPool.getUnit(UNIT_POOL_INDEX);
        PlayerTechnology technology = PlayerProgressTestUtils.createPlayerTechnology(true);

        game.getPlayerTable().setPlayerTechnology(technology);
        ghost.setEnergy(GHOST_ENERGY << ENERGY_FRACTION_BITS);

        game.getUnitTechnology().cloak(FIRST_PLAYER, true);

        Assertions.assertEquals(GHOST_ENERGY << ENERGY_FRACTION_BITS, ghost.getEnergy());
        Assertions.assertNotEquals(CLOAK_ORDER, ghost.getSecondaryOrder());

        technology.setResearched(FIRST_PLAYER, ScrTechIds.PERSONNEL_CLOAKING, true);

        game.getUnitTechnology().cloak(FIRST_PLAYER, true);

        Assertions.assertEquals((GHOST_ENERGY - gameData.getTechdata().get("Energy", ScrTechIds.PERSONNEL_CLOAKING)) << ENERGY_FRACTION_BITS,
                                ghost.getEnergy());
        Assertions.assertEquals(CLOAK_ORDER, ghost.getSecondaryOrder());

        game.getUnitTechnology().cloak(FIRST_PLAYER, false);

        Assertions.assertEquals(DECLOAK_ORDER, ghost.getSecondaryOrder());
    }

    @Test
    void siege_command_skips_units_that_are_not_tanks_in_the_opposite_mode() {
        Game game = createGame(ScrUnitIds.TERRAN_GHOST);
        PlayerTechnology technology = PlayerProgressTestUtils.createPlayerTechnology(true);

        technology.setResearched(FIRST_PLAYER, ScrTechIds.TANK_SIEGE_MODE, true);
        game.getPlayerTable().setPlayerTechnology(technology);

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        int order = unitPool.getUnit(UNIT_POOL_INDEX).getPrimaryOrder();

        Assertions.assertFalse(game.getUnitTechnology().siege(FIRST_PLAYER, true, false, INSTRUCTION_LIMIT));
        Assertions.assertFalse(game.getUnitTechnology().siege(FIRST_PLAYER, false, false, INSTRUCTION_LIMIT));

        Assertions.assertEquals(order, unitPool.getUnit(UNIT_POOL_INDEX).getPrimaryOrder());
    }
}
