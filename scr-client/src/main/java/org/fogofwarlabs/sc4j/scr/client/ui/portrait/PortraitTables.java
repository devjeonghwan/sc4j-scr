package org.fogofwarlabs.sc4j.scr.client.ui.portrait;

import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.PortdataDat;
import org.fogofwarlabs.sc4j.scr.data.format.text.TblFile;
import org.fogofwarlabs.sc4j.scr.data.type.PortraitTypeTable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Loads portrait definitions and directory strings from the installed portrait tables.
 * Portrait players share the returned definition and path tables.
 */
public final class PortraitTables {
    /**
     * Creates a {@link PortraitTables} instance from the installed portrait tables.
     *
     * @param storage the installed game storage
     * @return the created {@link PortraitTables} instance
     * @throws IOException if {@code arr\portdata.dat} or {@code arr\portdata.tbl} is missing or malformed
     */
    public static @NotNull PortraitTables load(@NotNull CascStorage storage) throws IOException {
        return new PortraitTables(PortdataDat.createTypeTable(PortdataDat.createSchema().parse(storage.readFile("arr/portdata.dat"))),
                                  TblFile.parse(storage.readFile("arr/portdata.tbl")));
    }

    private final @NotNull PortraitTypeTable portraits;
    private final @NotNull TblFile paths;

    /**
     * Creates a {@link PortraitTables} instance with the portrait definitions and directory strings.
     *
     * @param portraits the portrait definitions
     * @param paths     the portrait directory strings
     */
    private PortraitTables(@NotNull PortraitTypeTable portraits, @NotNull TblFile paths) {
        this.portraits = portraits;
        this.paths     = paths;
    }

    /**
     * @return the portrait entries of {@code arr\portdata.dat}
     */
    public @NotNull PortraitTypeTable getPortraits() {
        return this.portraits;
    }

    /**
     * @return the directory strings of {@code arr\portdata.tbl}
     */
    public @NotNull TblFile getPaths() {
        return this.paths;
    }
}
