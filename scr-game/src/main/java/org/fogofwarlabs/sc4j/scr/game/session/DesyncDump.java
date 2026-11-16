package org.fogofwarlabs.sc4j.scr.game.session;

import org.jetbrains.annotations.NotNull;

/**
 * The local simulation state at a network game's first Sync mismatch, as a plain-text file to compare with the other player's
 * file: a header, the compared record with every value that goes into it, the state the record hashed ({@code hashed} lines) and
 * the state of the frame about to run ({@code current} lines).
 * <p>
 * The state lines start with their section and come in a fixed order (players, rows, units and sprites by pool index), so a plain
 * text diff of two dumps shows the differing values. Values that differ between players by design (the local slot masks and
 * minimap count) are on the record lines only.
 */
final class DesyncDump {
    private static final @NotNull String HASHED_SECTION = "hashed";
    private static final @NotNull String CURRENT_SECTION = "current";

    private final @NotNull String record;
    private final @NotNull SyncState hashed;
    private final @NotNull SyncState current;

    /**
     * Creates a {@link DesyncDump} instance from copied states that the game no longer changes.
     *
     * @param record  the record lines
     * @param hashed  the state the record hashed
     * @param current the state of the frame about to run
     */
    DesyncDump(@NotNull String record, @NotNull SyncState hashed, @NotNull SyncState current) {
        this.record  = record;
        this.hashed  = hashed;
        this.current = current;
    }

    /**
     * Formats the dump file.
     *
     * @param header the header lines
     * @return the file text, with line feeds
     */
    @NotNull String format(@NotNull String header) {
        StringBuilder text = new StringBuilder(header);

        text.append('\n').append(this.record).append('\n');
        this.hashed.write(text, HASHED_SECTION);
        text.append('\n');
        this.current.write(text, CURRENT_SECTION);

        return text.toString();
    }
}
