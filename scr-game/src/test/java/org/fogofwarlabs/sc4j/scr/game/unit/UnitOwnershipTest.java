package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that a unit given to a player from {@code 8} keeps its sprite owner, that an owner change sets the sprite owner
 * of the unit and its turret even when the unit already has the new owner, and that a given Carrier keeps its fighter
 * training state and the Interceptor it was building after its queue reset.
 */
@RequiresInstallation
final class UnitOwnershipTest {
    /**
     * The position of the Siege Tank in the running game.
     */
    private static final int TANK_X = 3560;
    private static final int TANK_Y = 2160;
    /**
     * The position of the Carrier in the running game.
     */
    private static final int CARRIER_X = 3400;
    private static final int CARRIER_Y = 2300;
    /**
     * The minerals of the Carrier's owner, enough to queue an Interceptor.
     */
    private static final int CARRIER_OWNER_MINERALS = 10000;
    /**
     * The fighter training states of a Carrier: starting its queued fighter, and building it.
     */
    private static final int TRAINING_STATE_START = 0;
    private static final int TRAINING_STATE_BUILDING = 2;
    /**
     * The owner of a neutral unit.
     */
    private static final int NEUTRAL_PLAYER = 11;
    /**
     * The queued type of an empty build queue, the type identifier {@code None}.
     */
    private static final int NO_UNIT_TYPE = 228;
    /**
     * The orders of a dying unit and of a Carrier that trains its fighters.
     */
    private static final int DIE_ORDER = 0;
    private static final int TRAIN_FIGHTER_ORDER = 63;

    @Test
    void change_owner_should_set_the_sprite_owner_of_the_unit_and_its_turret_when_the_owner_is_unchanged() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit tank = RunningGameScenario.createUnit(game,
                                                           ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE,
                                                           TANK_X, TANK_Y,
                                                           RunningGameScenario.COMPUTER);
                Unit turret = tank.getSubunit();

                Assertions.assertNotNull(turret);

                game.getUnitOwnership().giveTo(tank, PlayerTable.NEUTRAL_PLAYER_ID, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(NEUTRAL_PLAYER, tank.getOwnerIndex());
                Assertions.assertEquals(NEUTRAL_PLAYER, turret.getOwnerIndex());
                Assertions.assertEquals(RunningGameScenario.COMPUTER, tank.requireSprite().getOwner());
                Assertions.assertEquals(RunningGameScenario.COMPUTER, turret.requireSprite().getOwner());

                game.getUnitOwnership().changeOwner(tank, PlayerTable.NEUTRAL_PLAYER_ID, false, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(NEUTRAL_PLAYER, tank.requireSprite().getOwner());
                Assertions.assertEquals(NEUTRAL_PLAYER, turret.requireSprite().getOwner());
            });
        }
    }

    @Test
    void give_to_should_keep_the_fighter_training_state_and_build_unit_of_a_given_carrier() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Player human = game.getPlayerTable().getPlayer(RunningGameScenario.HUMAN);
                Unit carrier = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_X, CARRIER_Y, RunningGameScenario.HUMAN);

                Assertions.assertNotNull(human);

                human.setMinerals(CARRIER_OWNER_MINERALS);

                Assertions.assertTrue(game.getUnitProduction().pushBuildQueue(carrier, ScrUnitIds.PROTOSS_INTERCEPTOR) >= 0);

                carrier.setSecondaryOrder(ScrOrderIds.TRAIN_FIGHTER);
                carrier.setSecondaryOrderState(TRAINING_STATE_START);
                game.getUnitHangar().updateTrainFighter(carrier, RunningGameScenario.INSTRUCTION_LIMIT);

                Unit interceptor = carrier.getCurrentBuildUnit();

                Assertions.assertNotNull(interceptor);
                Assertions.assertEquals(TRAINING_STATE_BUILDING, carrier.getSecondaryOrderState());

                game.getUnitOwnership().giveTo(carrier, RunningGameScenario.COMPUTER, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(RunningGameScenario.COMPUTER, carrier.getOwnerIndex());
                Assertions.assertEquals(NO_UNIT_TYPE, UnitQueries.getQueuedType(carrier));
                Assertions.assertEquals(DIE_ORDER, interceptor.getPrimaryOrder());
                Assertions.assertEquals(TRAIN_FIGHTER_ORDER, carrier.getSecondaryOrder());
                Assertions.assertEquals(TRAINING_STATE_BUILDING, carrier.getSecondaryOrderState());
                Assertions.assertSame(interceptor, carrier.getCurrentBuildUnit());
            });
        }
    }
}
