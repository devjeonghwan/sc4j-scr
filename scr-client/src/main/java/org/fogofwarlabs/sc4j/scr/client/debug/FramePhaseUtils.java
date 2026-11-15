package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.engine.client.host.FrameTiming;
import org.fogofwarlabs.sc4j.engine.client.render.GraphicsDevice;
import org.fogofwarlabs.sc4j.scr.client.configuration.InstallLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Names the phases of the client loop for the engine {@link FrameTiming} and opens the timing that the system property
 * {@value FrameTiming#PROPERTY} or the environment variable {@value FrameTiming#ENVIRONMENT_VARIABLE} requests. The phases are numbered in
 * call order, and each mark closes the phase that started at the previous mark.
 */
public final class FramePhaseUtils {
    /**
     * The input and simulation phase, the first of the loop phases.
     */
    public static final int INPUT_AND_SIMULATION = 0;
    /**
     * The terrain rendering phase.
     */
    public static final int TERRAIN = 1;
    /**
     * The scene extraction phase.
     */
    public static final int SCENE_EXTRACTION = 2;
    /**
     * The scene finishing phase.
     */
    public static final int SCENE_FINISH = 3;
    /**
     * The world overlay phase.
     */
    public static final int WORLD_OVERLAY = 4;
    /**
     * The game HUD phase.
     */
    public static final int HUD = 5;
    /**
     * The game console phase.
     */
    public static final int CONSOLE = 6;
    /**
     * The buffer swap phase, the last one.
     */
    public static final int SWAP = 7;

    private static final @NotNull Logger LOGGER = Logger.of(FramePhaseUtils.class);
    private static final @NotNull List<String> NAMES = List.of("inputSimulation", "terrain", "sceneExtraction", "sceneFinish", "worldOverlay",
                                                               "hud", "console", "swap");

    /**
     * Opens the frame timing of the client loop when it is requested.
     *
     * @param installLayout the selected client directories, which resolve a relative output file
     * @return the opened {@link FrameTiming}, or {@code null} if timing is not requested or its output cannot be initialized
     */
    public static @Nullable FrameTiming open(@NotNull InstallLayout installLayout) {
        Path target = FrameTiming.findConfiguredFile();

        if (target == null) {
            return null;
        }

        try {
            return FrameTiming.open(installLayout.resolveUserPath(target), NAMES, GraphicsDevice.getCurrent());
        } catch (IOException exception) {
            LOGGER.warning("FRAME TIMING DISABLED: " + exception.getMessage());

            return null;
        }
    }

    private FramePhaseUtils() {
    }
}
