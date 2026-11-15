package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

/**
 * Checks that trained workers publish unit-ready notifications until the next iteration.
 */
@RequiresInstallation
final class NotificationEventsTest {
    /**
     * The frame the replay is played to.
     */
    private static final int FINAL_FRAME = 2000;
    /**
     * The fewest SCVs that the replay trains and reports ready before {@link #FINAL_FRAME}.
     */
    private static final int MINIMUM_READY_SCVS = 4;

    @Test
    void trained_workers_raise_unit_ready_notifications() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            Game game = runtime.getGame();
            int hostTicks = 0;
            int readyCount = 0;

            while (game.getCurrentFrame() < FINAL_FRAME) {
                Assertions.assertTrue(runtime.step(GameSpeed.FASTEST_DURATION, hostTicks += GameSpeed.FASTEST_DURATION));

                NotificationEvents events = game.getInterfaceLink().getNotificationEvents();

                for (int index = 0; index < events.getCount(); index++) {
                    NotificationEvents.Event event = events.get(index);

                    if (event.getKind() == NotificationEvents.Kind.UNIT_READY) {
                        UnitPool pool = game.getUnitSystem().getUnitPool();

                        Assertions.assertNotNull(pool);

                        Unit unit = pool.getUnit(event.getPoolIndex());

                        Assertions.assertEquals(event.getOwner(), unit.getOwnerIndex());

                        if (unit.getUnitId() == ScrUnitIds.TERRAN_SCV) {
                            readyCount++;
                        }
                    }
                }
            }

            Assertions.assertTrue(readyCount >= MINIMUM_READY_SCVS, "SCVs trained in the first 2000 frames are reported ready: " + readyCount);
        }
    }
}
