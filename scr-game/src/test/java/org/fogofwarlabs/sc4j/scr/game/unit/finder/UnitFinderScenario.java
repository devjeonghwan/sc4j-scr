package org.fogofwarlabs.sc4j.scr.game.unit.finder;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Starts the two-versus-two Baekdudaegan replay ({@link GameTestResources#REPLAY_TVT_BAEKDUDAEGAN}) and runs it to an early frame, for the
 * tests that read the unit finder's query stamps and search counter in a running game.
 */
public final class UnitFinderScenario {
    private static final int START_FRAME = 200;
    private static final int STEP_MILLISECONDS = 42;

    /**
     * Starts the captured replay and advances it to its start frame.
     *
     * @param gameData the installed data retained by the replay world
     * @return the initialized replay world
     * @throws IOException if the replay or its map cannot be read or decoded
     */
    public static @NotNull Game start(@NotNull GameData gameData) throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN)));
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        int tick = 0;

        while (runtime.getGame().getCurrentFrame() < START_FRAME) {
            Assertions.assertTrue(runtime.step(STEP_MILLISECONDS, tick += STEP_MILLISECONDS));
        }

        return runtime.getGame();
    }


    /**
     * Creates a {@link UnitFinderScenario} instance.
     */
    private UnitFinderScenario() {
    }
}
