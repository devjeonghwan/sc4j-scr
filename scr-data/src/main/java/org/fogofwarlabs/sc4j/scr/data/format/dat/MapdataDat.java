package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.jetbrains.annotations.NotNull;

/**
 * Provides the column schema of {@code arr/mapdata.dat} (65 entries, 260 bytes).
 */
public final class MapdataDat {
    /**
     * The number of mapdata.dat entries.
     */
    public static final int MAP_COUNT = 65;
    /**
     * The {@code Mission Dir} column name.
     */
    public static final @NotNull String MISSION_DIRECTORY = "Mission Dir";

    /**
     * Creates a {@link DatSchema} instance for {@code arr/mapdata.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema().fieldU32(MISSION_DIRECTORY, MAP_COUNT);
    }


    private MapdataDat() {
    }
}
