package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a LAN game advertisement and preserves its attribute insertion order in {@link #toRecord(long)}.
 * The record carries the game type as its SDK type ({@link GameTemplate#toSdkType}).
 */
public final class LanGameInformation {
    /**
     * The attribute keys in the order the host writes them.
     */
    private static final @NotNull String NAME = "_name";
    private static final @NotNull String PROXY = "proxy";
    private static final @NotNull String MAP_CRC = "map_crc";
    private static final @NotNull String CLOSED_SLOTS = "closed_slots";
    private static final @NotNull String MAP_FILE_SIZE = "map_file_size";
    private static final @NotNull String MAP_NAME = "map_name";
    private static final @NotNull String NETWORK_TURN_RATE = "net_turn_rate";
    private static final @NotNull String MAP_MD5 = "map_md5";
    private static final @NotNull String IS_REPLAY = "is_replay";
    private static final @NotNull String PLAYERS_CURRENT = "players_current";
    private static final @NotNull String TYPE = "_type";
    private static final @NotNull String OBSERVERS_CURRENT = "observers_current";
    private static final @NotNull String SUBTYPE = "_subtype";
    private static final @NotNull String GAME_SPEED = "game_speed";
    private static final @NotNull String SAVE_GAME_ID = "save_game_id";
    private static final @NotNull String MAP_FILE_NAME = "map_file_name";
    private static final @NotNull String FLAGS = "flags";
    private static final @NotNull String PLAYERS_COMPUTER = "players_ai";
    private static final @NotNull String PLAYERS_MAXIMUM = "players_max";
    private static final @NotNull String HOST_NAME = "host_name";
    private static final @NotNull String MAP_TILE_SET = "map_tile_set";
    private static final @NotNull String RANK = "rank";
    private static final @NotNull String OBSERVERS_MAXIMUM = "observers_max";
    private static final @NotNull String MAP_HEIGHT = "map_height";
    private static final @NotNull String SDK_FLAGS = "_flags";
    private static final @NotNull String MAP_WIDTH = "map_width";

    /**
     * Creates a {@link LanGameInformation} instance from a game record.
     *
     * @param record the record
     * @return the created {@link LanGameInformation} instance, or {@code null} if the record lacks the name or the type
     */
    public static @Nullable LanGameInformation of(@NotNull LanRecord record) {
        String name = record.findText(NAME);
        String type = record.findText(TYPE);

        if (name == null || type == null) {
            return null;
        }

        return new LanGameInformation(
                name, readTextAttribute(record, HOST_NAME),
                GameTemplate.fromSdkType((int) readLongAttribute(record, TYPE)), (int) readLongAttribute(record, SUBTYPE),
                (int) readLongAttribute(record, GAME_SPEED), (int) readLongAttribute(record, NETWORK_TURN_RATE),
                (int) readLongAttribute(record, PLAYERS_CURRENT), (int) readLongAttribute(record, PLAYERS_MAXIMUM),
                (int) readLongAttribute(record, PLAYERS_COMPUTER),
                (int) readLongAttribute(record, OBSERVERS_CURRENT), (int) readLongAttribute(record, OBSERVERS_MAXIMUM),
                (int) readLongAttribute(record, CLOSED_SLOTS),
                (int) readLongAttribute(record, RANK),
                (int) readLongAttribute(record, FLAGS), (int) readLongAttribute(record, SDK_FLAGS),
                Boolean.parseBoolean(record.findText(PROXY)), Boolean.parseBoolean(record.findText(IS_REPLAY)),
                readLongAttribute(record, SAVE_GAME_ID),
                readTextAttribute(record, MAP_NAME), readTextAttribute(record, MAP_FILE_NAME),
                readLongAttribute(record, MAP_FILE_SIZE), readLongAttribute(record, MAP_CRC),
                readTextAttribute(record, MAP_MD5),
                (int) readLongAttribute(record, MAP_TILE_SET),
                (int) readLongAttribute(record, MAP_WIDTH), (int) readLongAttribute(record, MAP_HEIGHT)
        );
    }

    private static @NotNull String readTextAttribute(@NotNull LanRecord record, @NotNull String key) {
        String value = record.findText(key);

        return value == null ? "" : value;
    }

    private static long readLongAttribute(@NotNull LanRecord record, @NotNull String key) {
        String value = record.findText(key);

        if (value == null) {
            return 0;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }


    private final @NotNull String name;
    private final @NotNull String hostName;

    private final int gameType;
    private final int subtype;

    private final int gameSpeed;
    private final int networkTurnRate;

    private final int playersCurrent;
    private final int playersMaximum;
    private final int playersComputer;

    private final int observersCurrent;
    private final int observersMaximum;

    private final int closedSlots;

    private final int rank;

    private final int flags;
    private final int sdkFlags;

    private final boolean proxy;
    private final boolean replay;

    private final long saveGameId;

    private final @NotNull String mapName;
    private final @NotNull String mapFileName;

    private final long mapFileSize;
    private final long mapCrc;

    private final @NotNull String mapMd5;

    private final int mapTileSet;

    private final int mapWidth;
    private final int mapHeight;

    /**
     * Creates a {@link LanGameInformation} instance.
     *
     * @param name             the game name ({@code _name}, also the instance name)
     * @param hostName         the host's toon name
     * @param gameType         the game type identifier
     * @param subtype          the game type's subtype ({@code _subtype})
     * @param gameSpeed        the game speed, {@code 0..6}
     * @param networkTurnRate  the network turns per second
     * @param playersCurrent   the players in the lobby
     * @param playersMaximum   the player slots
     * @param playersComputer  the computer players
     * @param observersCurrent the observers in the lobby
     * @param observersMaximum the observer slots
     * @param closedSlots      the closed slots
     * @param rank             the host's rank value
     * @param flags            the game's flags
     * @param sdkFlags         the SDK's {@code _flags}
     * @param proxy            whether the game is routed through a proxy
     * @param replay           whether the game is a replay
     * @param saveGameId       the saved game identifier, {@code 0} for a new game
     * @param mapName          the map's scenario name
     * @param mapFileName      the map's file name
     * @param mapFileSize      the map's file size
     * @param mapCrc           the map's checksum
     * @param mapMd5           the map's MD5 in upper case hex
     * @param mapTileSet       the map's tileset
     * @param mapWidth         the map width in tiles
     * @param mapHeight        the map height in tiles
     */
    public LanGameInformation(@NotNull String name, @NotNull String hostName,
                              int gameType, int subtype,
                              int gameSpeed, int networkTurnRate,
                              int playersCurrent, int playersMaximum, int playersComputer,
                              int observersCurrent, int observersMaximum,
                              int closedSlots,
                              int rank,
                              int flags, int sdkFlags,
                              boolean proxy, boolean replay,
                              long saveGameId,
                              @NotNull String mapName, @NotNull String mapFileName,
                              long mapFileSize, long mapCrc,
                              @NotNull String mapMd5,
                              int mapTileSet,
                              int mapWidth, int mapHeight) {
        this.name     = name;
        this.hostName = hostName;

        this.gameType = gameType;
        this.subtype  = subtype;

        this.gameSpeed       = gameSpeed;
        this.networkTurnRate = networkTurnRate;

        this.playersCurrent  = playersCurrent;
        this.playersMaximum  = playersMaximum;
        this.playersComputer = playersComputer;

        this.observersCurrent = observersCurrent;
        this.observersMaximum = observersMaximum;

        this.closedSlots = closedSlots;

        this.rank = rank;

        this.flags    = flags;
        this.sdkFlags = sdkFlags;

        this.proxy  = proxy;
        this.replay = replay;

        this.saveGameId = saveGameId;

        this.mapName     = mapName;
        this.mapFileName = mapFileName;

        this.mapFileSize = mapFileSize;
        this.mapCrc      = mapCrc;

        this.mapMd5 = mapMd5;

        this.mapTileSet = mapTileSet;

        this.mapWidth  = mapWidth;
        this.mapHeight = mapHeight;
    }

    /**
     * @return the game name
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the host's toon name
     */
    public @NotNull String getHostName() {
        return this.hostName;
    }

    /**
     * @return the game type identifier, {@code 0} if the record's type is unknown
     */
    public int getGameType() {
        return this.gameType;
    }

    /**
     * @return the game type's subtype
     */
    public int getSubtype() {
        return this.subtype;
    }

    /**
     * @return the game speed
     */
    public int getGameSpeed() {
        return this.gameSpeed;
    }

    /**
     * @return the network turns per second
     */
    public int getNetworkTurnRate() {
        return this.networkTurnRate;
    }

    /**
     * @return the players in the lobby
     */
    public int getPlayersCurrent() {
        return this.playersCurrent;
    }

    /**
     * @return the player slots
     */
    public int getPlayersMaximum() {
        return this.playersMaximum;
    }

    /**
     * @return the observers in the lobby
     */
    public int getObserversCurrent() {
        return this.observersCurrent;
    }

    /**
     * @return the observer slots
     */
    public int getObserversMaximum() {
        return this.observersMaximum;
    }

    /**
     * @return the closed slots
     */
    public int getClosedSlots() {
        return this.closedSlots;
    }

    /**
     * @return the advertised game flags
     */
    public int getFlags() {
        return this.flags;
    }

    /**
     * @return {@code true} if the game is a replay, {@code false} otherwise
     */
    public boolean isReplay() {
        return this.replay;
    }

    /**
     * @return the advertised saved game identifier, {@code 0} for a new game
     */
    public long getSaveGameId() {
        return this.saveGameId;
    }

    /**
     * @return the map's scenario name
     */
    public @NotNull String getMapName() {
        return this.mapName;
    }

    /**
     * @return the map's file name
     */
    public @NotNull String getMapFileName() {
        return this.mapFileName;
    }

    /**
     * @return the map's file size
     */
    public long getMapFileSize() {
        return this.mapFileSize;
    }

    /**
     * @return the map's checksum
     */
    public long getMapCrc() {
        return this.mapCrc;
    }

    /**
     * @return the map's MD5 in upper case hex
     */
    public @NotNull String getMapMd5() {
        return this.mapMd5;
    }

    /**
     * @return the map's tileset
     */
    public int getMapTileSet() {
        return this.mapTileSet;
    }

    /**
     * @return the map width in tiles
     */
    public int getMapWidth() {
        return this.mapWidth;
    }

    /**
     * @return the map height in tiles
     */
    public int getMapHeight() {
        return this.mapHeight;
    }


    /**
     * Creates a {@link LanRecord} instance in the host's attribute order.
     *
     * @param revision the record revision
     * @return the created {@link LanRecord} instance
     * @throws IllegalArgumentException if the game type has no SDK type
     */
    public @NotNull LanRecord toRecord(long revision) {
        int sdkType = GameTemplate.toSdkType(this.gameType);

        if (sdkType == GameTemplate.NO_SDK_TYPE) {
            throw Exceptions.newIllegalArgumentException("No SDK type for game type {0}.", this.gameType);
        }

        return LanRecord.createBuilder(this.name, revision)
                        .add(NAME, this.name)
                        .add(PROXY, Boolean.toString(this.proxy))
                        .add(MAP_CRC, Long.toString(this.mapCrc))
                        .add(CLOSED_SLOTS, Integer.toString(this.closedSlots))
                        .add(MAP_FILE_SIZE, Long.toString(this.mapFileSize))
                        .add(MAP_NAME, this.mapName)
                        .add(NETWORK_TURN_RATE, Integer.toString(this.networkTurnRate))
                        .add(MAP_MD5, this.mapMd5)
                        .add(IS_REPLAY, Boolean.toString(this.replay))
                        .add(PLAYERS_CURRENT, Integer.toString(this.playersCurrent))
                        .add(TYPE, Integer.toString(sdkType))
                        .add(OBSERVERS_CURRENT, Integer.toString(this.observersCurrent))
                        .add(SUBTYPE, Integer.toString(this.subtype))
                        .add(GAME_SPEED, Integer.toString(this.gameSpeed))
                        .add(SAVE_GAME_ID, Long.toString(this.saveGameId))
                        .add(MAP_FILE_NAME, this.mapFileName)
                        .add(FLAGS, Integer.toString(this.flags))
                        .add(PLAYERS_COMPUTER, Integer.toString(this.playersComputer))
                        .add(PLAYERS_MAXIMUM, Integer.toString(this.playersMaximum))
                        .add(HOST_NAME, this.hostName)
                        .add(MAP_TILE_SET, Integer.toString(this.mapTileSet))
                        .add(RANK, Integer.toString(this.rank))
                        .add(OBSERVERS_MAXIMUM, Integer.toString(this.observersMaximum))
                        .add(MAP_HEIGHT, Integer.toString(this.mapHeight))
                        .add(SDK_FLAGS, Integer.toString(this.sdkFlags))
                        .add(MAP_WIDTH, Integer.toString(this.mapWidth))
                        .build();
    }
}
