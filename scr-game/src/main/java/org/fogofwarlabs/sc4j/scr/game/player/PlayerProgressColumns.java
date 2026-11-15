package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.engine.content.registry.ContentRegistry;
import org.fogofwarlabs.sc4j.engine.content.registry.TableKind;
import org.fogofwarlabs.sc4j.engine.content.table.PlayerProgressTable;
import org.fogofwarlabs.sc4j.engine.entity.column.ByteColumn;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnKey;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnReset;
import org.fogofwarlabs.sc4j.engine.entity.column.IntColumn;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the SC:R per-player extension columns of the engine {@link PlayerProgressTable}: the game-context values the engine
 * columns do not hold. Every column is indexed player-major ({@code player * typeCount + typeIndex}).
 * <p>
 * The engine columns hold the upgrade levels and maximum levels, the unit counts and the completed unit counts. The native
 * availability and researched bytes keep any byte value (map settings and EUD write and read them as bytes), so their bytes are
 * SC:R columns and the engine booleans hold whether each byte is nonzero; {@link PlayerTechnology} and
 * {@link PlayerUnitAvailability} write both in one store. The upgrading and researching bit sets and their legacy rows have no
 * engine concept: each byte of a player's bit set is the entry {@code player * typeCount + byteIndex} of its column. The death
 * and kill tables are trigger memory of the SC:R mod (Deaths, Kill, Set Deaths and the EUD variables they hold), not a
 * generic progress value.
 */
public final class PlayerProgressColumns {
    /**
     * The availability byte of each player and unit type ({@code PUNI}); the engine column holds whether it is nonzero.
     */
    public static final @NotNull ColumnKey<ByteColumn> UNIT_AVAILABLE = ColumnKey.ofByte("scr.unitAvailable", PlayerUnitAvailability.ALLOWED,
                                                                                         ColumnReset.KEEP);
    /**
     * The death count of each player and unit type.
     */
    public static final @NotNull ColumnKey<IntColumn> DEATHS = ColumnKey.ofInt("scr.deaths", 0, ColumnReset.KEEP);
    /**
     * The kill count of each player and unit type.
     */
    public static final @NotNull ColumnKey<IntColumn> KILLS = ColumnKey.ofInt("scr.kills", 0, ColumnReset.KEEP);
    /**
     * The bytes of each player's upgrading bit set, one bit per upgrade.
     */
    public static final @NotNull ColumnKey<ByteColumn> UPGRADING = ColumnKey.ofByte("scr.upgrading", (byte) 0, ColumnReset.KEEP);
    /**
     * The bytes of each player's legacy classic upgrading bit set.
     */
    public static final @NotNull ColumnKey<ByteColumn> LEGACY_UPGRADING = ColumnKey.ofByte("scr.legacyUpgrading", (byte) 0, ColumnReset.KEEP);
    /**
     * The availability byte of each player and tech; the engine column holds whether it is nonzero.
     */
    public static final @NotNull ColumnKey<ByteColumn> TECH_AVAILABLE = ColumnKey.ofByte("scr.techAvailable", PlayerTechnology.AVAILABLE,
                                                                                         ColumnReset.KEEP);
    /**
     * The researched byte of each player and tech; the engine column holds whether it is nonzero.
     */
    public static final @NotNull ColumnKey<ByteColumn> TECH_RESEARCHED = ColumnKey.ofByte("scr.techResearched", (byte) 0, ColumnReset.KEEP);
    /**
     * The bytes of each player's researching bit set, one bit per tech.
     */
    public static final @NotNull ColumnKey<ByteColumn> RESEARCHING = ColumnKey.ofByte("scr.researching", (byte) 0, ColumnReset.KEEP);
    /**
     * The bytes of each player's legacy classic researching bit set.
     */
    public static final @NotNull ColumnKey<ByteColumn> LEGACY_RESEARCHING = ColumnKey.ofByte("scr.legacyResearching", (byte) 0, ColumnReset.KEEP);

    /**
     * Registers the columns on an open content registry; every column starts at its default.
     *
     * @param content the content registry
     * @throws IllegalStateException if the registry is closed
     */
    public static void register(@NotNull ContentRegistry content) {
        content.addPlayerColumn(TableKind.UNIT, UNIT_AVAILABLE, column -> {
        });
        content.addPlayerColumn(TableKind.UNIT, DEATHS, column -> {
        });
        content.addPlayerColumn(TableKind.UNIT, KILLS, column -> {
        });
        content.addPlayerColumn(TableKind.UPGRADE, UPGRADING, column -> {
        });
        content.addPlayerColumn(TableKind.UPGRADE, LEGACY_UPGRADING, column -> {
        });
        content.addPlayerColumn(TableKind.TECH, TECH_AVAILABLE, column -> {
        });
        content.addPlayerColumn(TableKind.TECH, TECH_RESEARCHED, column -> {
        });
        content.addPlayerColumn(TableKind.TECH, RESEARCHING, column -> {
        });
        content.addPlayerColumn(TableKind.TECH, LEGACY_RESEARCHING, column -> {
        });
    }


    private PlayerProgressColumns() {
    }
}
