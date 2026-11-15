package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays a replay of the test resources in a client session up to a frame and finds its units.
 */
public final class ClientReplayScenario {
    /**
     * Loads a replay into a client session of the installation with a {@code 640 x 480} view, starts it and steps it to a frame or its end.
     *
     * @param replay the replay, a {@link GameTestResources} name
     * @param frame  the last completed frame to reach
     * @return the started session, which the caller closes
     * @throws IOException           if the installation or the replay cannot be read
     * @throws IllegalStateException if the installation is not configured or names no directory
     */
    public static @NotNull ClientSession start(@NotNull String replay, long frame) throws IOException {
        ClientSession session = ClientSession.load(ExternalInput.INSTALLATION.getPath(), GameTestResources.getPath(replay));

        session.getCamera().setViewportSize(RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT);
        session.startReplay();

        while (session.getLastCompletedFrame() < frame && session.stepFrame(false)) {
            // Step to the frame or to the end of the replay.
        }

        return session;
    }

    /**
     * Lists the completed units of an owner and a type that have a sprite, in pool order.
     *
     * @param game               the game
     * @param owner              the owning player
     * @param unitTypeIdentifier the unit type
     * @return the units
     */
    public static @NotNull List<Unit> findCompletedUnits(@NotNull Game game, int owner, int unitTypeIdentifier) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        List<Unit> units = new ArrayList<>();

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit unit = pool.getUnit(index);

            if (unit.getOwnerIndex() == owner && unit.getUnitId() == unitTypeIdentifier && (unit.getStatus() & UnitStatusFlags.COMPLETED) != 0 &&
                unit.getSpriteOrNull() != null) {
                units.add(unit);
            }
        }

        return units;
    }


    /**
     * Creates a {@link ClientReplayScenario} instance.
     */
    private ClientReplayScenario() {
    }
}
