package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.net.discovery.LanGameInformation;
import org.fogofwarlabs.sc4j.scr.net.discovery.LanRecord;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The advertisement of a hosted LAN game: the game record built from the provider session's player counts and game
 * statistics ({@link LanGameStatistics}) whenever the host refreshes it, and the record's revision, which advances each
 * time a rebuilt record differs from the advertised one.
 */
public final class LanGameAdvertisement {
    /**
     * The rank the host advertises.
     */
    private static final int RANK = 1;


    private final @NotNull String mapFileName;
    private final long mapFileSize;
    private final long mapCrc;
    private final @NotNull String mapMd5;
    private final int flags;

    private @Nullable LanGameInformation gameInformation;
    private long revision;
    /**
     * The advertised record encoded at its revision, {@code null} before the first {@link #update}.
     */
    private byte @Nullable [] encodedRecord;

    /**
     * Creates a {@link LanGameAdvertisement} instance for a hosted map file. Nothing is advertised before the first
     * {@link #update}.
     *
     * @param mapFileName the map's file name
     * @param mapFileSize the map's file size
     * @param mapCrc      the map's checksum ({@code map_crc}: the complement of the file's CRC-32)
     * @param mapMd5      the map's MD5 in upper case hex
     * @param flags       the game flags
     */
    public LanGameAdvertisement(@NotNull String mapFileName, long mapFileSize, long mapCrc, @NotNull String mapMd5, int flags) {
        this.mapFileName = mapFileName;
        this.mapFileSize = mapFileSize;
        this.mapCrc      = mapCrc;
        this.mapMd5      = mapMd5;
        this.flags       = flags;

        this.gameInformation = null;
        this.revision        = 0;
        this.encodedRecord   = null;
    }

    /**
     * @return the advertised game, or {@code null} before the first {@link #update}
     */
    public @Nullable LanGameInformation getGameInformation() {
        return this.gameInformation;
    }

    /**
     * @return the revision of the advertised game record: {@code 0} for the first record, one more for each changed record
     */
    public long getRevision() {
        return this.revision;
    }


    /**
     * Rebuilds the advertised game from the provider session. The players are those the session counts
     * ({@link ProviderSession#getCurrentPlayers()}), and the player slots are its maximum players or the players when
     * there are more; the observers and the observer slots are the session's observers present and observer limit. The
     * host name, the game type and subtype, the speed, the turn rate, the closed slots, the replay flag, the saved game id,
     * the map name, the tileset and the map size come from the session's game statistics, which carry no computer count:
     * the advertised computer players are {@code 0}.
     *
     * @param session the hosting provider session
     * @return {@code true} if the advertised record changed (always for the first record), {@code false} otherwise
     * @throws IllegalStateException if the session's game statistics cannot be parsed
     */
    public boolean update(@NotNull ProviderSession session) {
        LanGameStatistics statistics = LanGameStatistics.parse(session.getGameStatistics());

        if (statistics == null) {
            throw Exceptions.newIllegalStateException("The game statistics of game {0} cannot be parsed.", session.getGameName());
        }

        int playersCurrent = session.getCurrentPlayers();
        int playersMaximum = Integer.compareUnsigned(session.getMaximumPlayers(), playersCurrent) > 0 ? session.getMaximumPlayers() : playersCurrent;
        LanGameInformation created = new LanGameInformation(session.getGameName(), statistics.getHostName(),
                                                            statistics.getGameType(), statistics.getSubtype(),
                                                            statistics.getSpeed(), statistics.getTurnRate(),
                                                            playersCurrent, playersMaximum, 0,
                                                            session.getObservers(), session.getMaximumObservers(),
                                                            statistics.getClosedSlots(),
                                                            RANK,
                                                            this.flags, 0,
                                                            false, statistics.isReplay(),
                                                            Integer.toUnsignedLong(statistics.getSaveGameId()),
                                                            statistics.getMapName(), this.mapFileName,
                                                            this.mapFileSize, this.mapCrc,
                                                            this.mapMd5,
                                                            statistics.getTileset(),
                                                            statistics.getMapWidth(), statistics.getMapHeight());

        byte[] encoded = created.toRecord(this.revision).encode();

        if (Arrays.equals(this.encodedRecord, encoded)) {
            return false;
        }

        if (this.gameInformation != null) {
            this.revision++;
            encoded = created.toRecord(this.revision).encode();
        }

        this.gameInformation = created;
        this.encodedRecord   = encoded;

        return true;
    }

    /**
     * Creates a {@link LanRecord} instance of the advertised game at its revision.
     *
     * @return the created {@link LanRecord} instance
     * @throws IllegalStateException if nothing was advertised yet
     */
    public @NotNull LanRecord toRecord() {
        LanGameInformation advertised = this.gameInformation;

        if (advertised == null) {
            throw Exceptions.newIllegalStateException("No game is advertised.");
        }

        return advertised.toRecord(this.revision);
    }
}
