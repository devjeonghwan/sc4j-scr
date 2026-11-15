package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.CampaignMissionScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that Operation Cwal finishes an overlong construction or training countdown at once when its result is negative as a signed WORD.
 */
@RequiresInstallation
final class UnitBuildTimeTest {
    /**
     * The campaign mission of the tests.
     */
    private static final @NotNull String TERRAN_MISSION = "bw-terran-1";
    /**
     * The Unix time of a typed line.
     */
    private static final int TYPED_TIME = 0x6543210F;

    private static final int STARTUP_TURNS = 3;
    private static final int MAXIMUM_CHEAT_TURNS = 10;
    private static final int MAXIMUM_TRAINING_TURNS = 20;
    private static final int COMPLETION_TURNS = 3;
    private static final int MINERALS = 1000;

    private static final int OVERLONG_BUILD_TIME = 54000;

    /**
     * A build time whose step under Operation Cwal crosses the sign bit of a WORD, and the build time it leaves.
     */
    private static final int SIGN_CROSSING_BUILD_TIME = 0x8005;
    private static final int SIGN_CROSSING_REMAINING_TIME = 0x7FF5;

    private static final int SHORT_BUILD_TIME = 40;
    private static final int SHORT_REMAINING_TIME = 24;
    private static final int FINAL_BUILD_TIME = 10;

    private interface MissionBody {
        void run(@NotNull LiveGame game, @NotNull ReplayRuntime live, @NotNull Game state);
    }

    private static void enableOperationCwal(@NotNull LiveGame game, @NotNull ReplayRuntime live, @NotNull Game state) {
        GameFrame cheatFrame = state.getFrame();

        Assertions.assertNotNull(cheatFrame);

        game.submitText("operation cwal", cheatFrame.getVisibility().getCheatFlags(), state.areCheatsEnabled(), state.getRuntimeRandom(), TYPED_TIME);

        for (int count = 0; count < MAXIMUM_CHEAT_TURNS && !state.isOperationCwal(); count++) {
            CampaignMissionScenario.exchangeTurnsAndStep(game, live, 1);
        }

        Assertions.assertTrue(state.isOperationCwal(), "typing operation cwal did not set cheat bit 0x2");
    }

    private static void runMission(@NotNull MissionBody body) throws IOException {
        CampaignMissionScenario.run(ExternalInput.INSTALLATION.getPath(), TERRAN_MISSION, true, (game, live) -> {
            Game state = live.getGame();

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, STARTUP_TURNS);
            body.run(game, live, state);
        });
    }


    @Test
    void advance_should_step_by_sixteen_with_signed_word_clamp_under_operation_cwal() throws IOException {
        runMission((game, live, state) -> {
            int mobileOwner = game.getLocalPlayer();
            UnitPool unitPool = state.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = null;

            for (int index = 1; index <= unitPool.getCapacity(); index++) {
                if (unitPool.isAllocated(index) && unitPool.getUnit(index).getOwnerIndex() == mobileOwner &&
                    (unitPool.getUnit(index).getStatus() & UnitStatusFlags.CAN_MOVE) != 0) {
                    unit = unitPool.getUnit(index);

                    break;
                }
            }

            if (unit == null) {
                Assertions.fail("No mobile unit of player " + mobileOwner);
            }

            unit.setRemainingBuildTime(OVERLONG_BUILD_TIME);
            state.getUnitBuildTime().advance(unit);

            Assertions.assertEquals(OVERLONG_BUILD_TIME - 1, unit.getRemainingBuildTime(), "without the cheat the countdown decrements");

            enableOperationCwal(game, live, state);
            unit.setRemainingBuildTime(OVERLONG_BUILD_TIME);
            state.getUnitBuildTime().advance(unit);

            Assertions.assertEquals(0, unit.getRemainingBuildTime(), "54000 - 16 is negative as a signed WORD");

            unit.setRemainingBuildTime(SIGN_CROSSING_BUILD_TIME);
            state.getUnitBuildTime().advance(unit);

            Assertions.assertEquals(SIGN_CROSSING_REMAINING_TIME, unit.getRemainingBuildTime());

            unit.setRemainingBuildTime(SHORT_BUILD_TIME);
            state.getUnitBuildTime().advance(unit);

            Assertions.assertEquals(SHORT_REMAINING_TIME, unit.getRemainingBuildTime());

            unit.setRemainingBuildTime(FINAL_BUILD_TIME);
            state.getUnitBuildTime().advance(unit);

            Assertions.assertEquals(0, unit.getRemainingBuildTime());
        });
    }

    @Test
    void trained_unit_with_overlong_build_time_finishes_at_once_under_operation_cwal() throws IOException {
        runMission((game, live, state) -> {
            int centerOwner = game.getLocalPlayer();
            int centerType = ScrUnitIds.TERRAN_COMMAND_CENTER;
            UnitPool unitPool = state.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit center = null;

            for (int index = 1; index <= unitPool.getCapacity(); index++) {
                if (unitPool.isAllocated(index) && unitPool.getUnit(index).getOwnerIndex() == centerOwner &&
                    unitPool.getUnit(index).getUnitId() == centerType) {
                    center = unitPool.getUnit(index);

                    break;
                }
            }

            if (center == null) {
                Assertions.fail("No unit of type " + centerType + " owned by player " + centerOwner);
            }

            Player player = state.getPlayerTable().getPlayer(game.getLocalPlayer());

            Assertions.assertNotNull(player);

            player.setMinerals(MINERALS);

            enableOperationCwal(game, live, state);

            Assertions.assertTrue(state.getUnitProduction().pushBuildQueue(center, ScrUnitIds.TERRAN_SCV) >= 0);

            center.setSecondaryOrder(ScrOrderIds.TRAIN);
            center.setSecondaryOrderState(0);
            center.setCurrentBuildUnit(null);

            Unit trained = null;

            for (int count = 0; count < MAXIMUM_TRAINING_TURNS && trained == null; count++) {
                CampaignMissionScenario.exchangeTurnsAndStep(game, live, 1);
                trained = center.getCurrentBuildUnit();
            }

            Assertions.assertNotNull(trained, "the Command Center did not start training");

            trained.setRemainingBuildTime(OVERLONG_BUILD_TIME);

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, COMPLETION_TURNS);

            Assertions.assertNotEquals(0, trained.getStatus() & UnitStatusFlags.COMPLETED, "the SCV is still in production");
        });
    }
}
