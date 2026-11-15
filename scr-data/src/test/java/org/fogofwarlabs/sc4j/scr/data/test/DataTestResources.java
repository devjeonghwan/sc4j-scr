package org.fogofwarlabs.sc4j.scr.data.test;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.TestResourceUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Names the replays of the {@code scr-game} test resources that the data format tests read (the module's test runtime
 * class path holds that resource directory) and lists the installed map files. The same replay files are named by
 * {@code GameTestResources} of the game test fixtures.
 */
public final class DataTestResources {
    /**
     * A native SC:R single-player melee game of 7,054 frames, Terran against a random-race computer on Bottleneck.
     */
    public static final @NotNull String REPLAY_TERRAN_VS_COMPUTER = "replays/focused/internal/INTERNAL_00000009.rep";
    /**
     * A native SC:R single-player melee game of 730 frames, Zerg against a Zerg computer on Bottleneck.
     */
    public static final @NotNull String REPLAY_ZVZ_COMPUTER_SHORT = "replays/focused/internal/INTERNAL_00000006.rep";
    /**
     * A native SC:R single-player melee game of 29,100 frames against three computers on Cyclone.
     */
    public static final @NotNull String REPLAY_FREE_FOR_ALL_COMPUTERS = "replays/focused/internal/INTERNAL_00000008.rep";

    private static final @NotNull String MAP_EXTENSION = ".scm";
    private static final @NotNull String EXPANSION_MAP_EXTENSION = ".scx";

    /**
     * Lists the map files ({@code .scm} and {@code .scx}) below the {@code Maps} folder of the installation.
     *
     * @return the map files in path order
     * @throws IOException if the folder cannot be read
     */
    public static @NotNull List<Path> listInstalledMaps() throws IOException {
        return TestResourceUtils.listFiles(ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER), MAP_EXTENSION, EXPANSION_MAP_EXTENSION);
    }


    /**
     * Creates a {@link DataTestResources} instance.
     */
    private DataTestResources() {
    }
}
