package org.fogofwarlabs.sc4j.scr.net.lan;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

/**
 * The game statistics of a LAN game: the game description that the provider session gives every joiner with the game
 * information, and that the game advertisement is built from ({@link LanGameAdvertisement}).
 * <p>
 * The statistics string holds lower case hexadecimal fields, each ended by a comma and left empty when it holds its
 * default: the saved game identifier, the map size (the width and the height in units of {@code 32} tiles, one digit each), the human
 * players with the maximum players (one digit each), the game speed, the map status, the game type, a zero byte, the
 * subtype, the network turn rate, the tileset and the replay flag. The host name and the map name follow, each ended by
 * a carriage return. A further carriage return starts the slot count tail: the marker {@code feedda7a}, the observers
 * present with the observer limit (one digit each), the closed slots and the network turn rate.
 */
public final class LanGameStatistics {
    /**
     * The map status a host's statistics carry.
     */
    public static final int HOST_MAP_STATUS = 1;

    /**
     * The marker that starts the slot count tail.
     */
    private static final int TAIL_MARKER = 0xFEEDDA7A;

    /**
     * The characters that end a field and a name, and the character a carriage return inside a name becomes.
     */
    private static final char FIELD_END = ',';
    private static final char LINE_END = '\r';
    private static final char LINE_END_REPLACEMENT = '\n';

    /**
     * The longest host name and map name in UTF-8 bytes.
     */
    private static final int HOST_NAME_LIMIT = 24;
    private static final int MAP_NAME_LIMIT = 31;

    /**
     * The map size digit: tiles are counted in units of {@code 32}.
     */
    private static final int MAP_SIZE_UNIT_SHIFT = 5;

    /**
     * The values that the fields leave empty: a {@code 256} by {@code 256} map, one of eight players, the speed {@code 4},
     * and no observers present of a limit of {@code 4}.
     */
    private static final int DEFAULT_MAP_SIZE = 0x88;
    private static final int DEFAULT_PLAYERS = 0x18;
    private static final int DEFAULT_SPEED = 4;
    private static final int DEFAULT_OBSERVERS = 4;

    /**
     * A field of two hexadecimal digits: the high digit and the low digit.
     */
    private static final int DIGIT_BITS = 4;
    private static final int DIGIT_MASK = 0xF;
    private static final int HEXADECIMAL_RADIX = 16;

    /**
     * The masks of an unsigned byte and an unsigned 16-bit word.
     */
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * The first byte value that is not ASCII.
     */
    private static final int ASCII_LIMIT = 0x80;

    /**
     * Reads the fields of a statistics string in UTF-8 bytes. A field that is not hexadecimal, or a name that is too long
     * or not ended, makes the reader fail; a failed reader gives every later field its default.
     */
    private static final class FieldReader {
        private final byte @NotNull [] text;

        private int position;
        private boolean failed;

        private FieldReader(byte @NotNull [] text) {
            this.text = text;

            this.position = 0;
            this.failed   = false;
        }

        /**
         * Reads the byte at the reading position without moving past it.
         *
         * @return the byte at the reading position, {@code 0} past the end
         */
        private int peek() {
            return this.position < this.text.length ? this.text[this.position] & BYTE_MASK : 0;
        }

        /**
         * Skips the byte at the reading position.
         */
        private void skip() {
            this.position++;
        }

        /**
         * Reads a hexadecimal field up to its comma.
         *
         * @param defaultValue the value of an empty field and of a failed reader
         * @return the value
         */
        private int readField(int defaultValue) {
            if (this.failed) {
                return defaultValue;
            }

            if (this.peek() == FIELD_END) {
                this.skip();

                return defaultValue;
            }

            int value = 0;

            while (this.peek() != FIELD_END) {
                int character = this.peek();
                int digit = character < ASCII_LIMIT ? Character.digit((char) character, HEXADECIMAL_RADIX) : -1;

                if (digit < 0) {
                    this.failed = true;

                    return value;
                }

                value = value * HEXADECIMAL_RADIX + digit;
                this.skip();
            }

            this.skip();

            return value;
        }

        /**
         * Reads a name up to its carriage return.
         *
         * @param limit the most bytes of the name
         * @return the name, or {@code null} if the reader failed
         */
        private @Nullable String readName(int limit) {
            if (this.failed) {
                return null;
            }

            int start = this.position;

            while (this.peek() != LINE_END) {
                if (this.position - start == limit || this.position >= this.text.length) {
                    this.failed = true;

                    return null;
                }

                this.skip();
            }

            String name = new String(this.text, start, this.position - start, StandardCharsets.UTF_8);

            this.skip();

            return name;
        }
    }

    /**
     * Creates a {@link LanGameStatistics} instance from a statistics string. A string whose head cannot be read has no
     * statistics; without a slot count tail the observers present, the observer limit and the closed slots are {@code 0}.
     *
     * @param statistics the statistics string
     * @return the created {@link LanGameStatistics} instance, or {@code null} if a field is not hexadecimal or a name is too long or not ended
     */
    public static @Nullable LanGameStatistics parse(@NotNull String statistics) {
        FieldReader reader = new FieldReader(statistics.getBytes(StandardCharsets.UTF_8));
        int saveGameIdentifier = reader.readField(0);
        int mapSize = reader.readField(DEFAULT_MAP_SIZE);
        int players = reader.readField(DEFAULT_PLAYERS);
        int speed = reader.readField(DEFAULT_SPEED) & BYTE_MASK;
        int mapStatus = reader.readField(0) & BYTE_MASK;
        int gameType = reader.readField(0) & BYTE_MASK;

        // The zero byte between the game type and the subtype is read and dropped.
        reader.readField(0);

        int subtype = reader.readField(0) & WORD_MASK;
        int turnRate = reader.readField(0);
        int tileset = reader.readField(0) & WORD_MASK;
        boolean replay = (reader.readField(0) & BYTE_MASK) != 0;
        String hostName = reader.readName(HOST_NAME_LIMIT);
        String mapName = reader.readName(MAP_NAME_LIMIT);

        if (hostName == null || mapName == null || reader.peek() != 0 && reader.peek() != LINE_END) {
            return null;
        }

        int observers = 0;
        int observerLimit = 0;
        int closedSlots = 0;

        // The tail follows the carriage return that ends the names.
        reader.skip();

        if (reader.peek() != 0 && reader.peek() != FIELD_END && reader.readField(0) == TAIL_MARKER) {
            int observerCounts = reader.readField(DEFAULT_OBSERVERS);

            observers     = observerCounts >>> DIGIT_BITS & DIGIT_MASK;
            observerLimit = observerCounts & DIGIT_MASK;
            closedSlots   = reader.readField(0);
        }

        int mapWidth = (mapSize >>> DIGIT_BITS & DIGIT_MASK) << MAP_SIZE_UNIT_SHIFT;
        int mapHeight = (mapSize & DIGIT_MASK) << MAP_SIZE_UNIT_SHIFT;

        return new LanGameStatistics(saveGameIdentifier,
                                     mapWidth, mapHeight,
                                     players >>> DIGIT_BITS & DIGIT_MASK, players & DIGIT_MASK,
                                     speed, mapStatus,
                                     gameType, subtype,
                                     turnRate, tileset, replay,
                                     hostName, mapName,
                                     observers, observerLimit, closedSlots);
    }

    /**
     * Cuts a name to its byte limit without splitting a character; a carriage return inside the name becomes a line feed.
     *
     * @param name  the name
     * @param limit the most UTF-8 bytes the name keeps
     * @return the cut name
     */
    private static @NotNull String limitName(@NotNull String name, int limit) {
        ByteBuffer bytes = ByteBuffer.allocate(limit);

        StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(name.replace(LINE_END, LINE_END_REPLACEMENT)), bytes, true);

        return new String(bytes.array(), 0, bytes.position(), StandardCharsets.UTF_8);
    }

    private static void appendField(@NotNull StringBuilder text, int value, int defaultValue) {
        if (value != defaultValue) {
            text.append(Integer.toHexString(value));
        }

        text.append(FIELD_END);
    }


    private final int saveGameIdentifier;

    private final int mapWidth;
    private final int mapHeight;

    private final int humanPlayers;
    private final int maximumPlayers;

    private final int speed;
    private final int mapStatus;

    private final int gameType;
    private final int subtype;

    private final int turnRate;
    private final int tileset;
    private final boolean replay;

    private final @NotNull String hostName;
    private final @NotNull String mapName;

    private final int observers;
    private final int observerLimit;
    private final int closedSlots;

    /**
     * Creates a {@link LanGameStatistics} instance. The host name keeps at most {@code 24} and the map name at most
     * {@code 31} UTF-8 bytes, and a carriage return inside a name becomes a line feed.
     *
     * @param saveGameIdentifier the saved game identifier, {@code 0} for a new game
     * @param mapWidth           the map width in tiles
     * @param mapHeight          the map height in tiles
     * @param humanPlayers       the human players in the lobby
     * @param maximumPlayers     the maximum players
     * @param speed              the game speed, {@code 0}..{@code 6}
     * @param mapStatus          the map status
     * @param gameType           the game type identifier
     * @param subtype            the game type's subtype
     * @param turnRate           the network turns per second
     * @param tileset            the map's tileset
     * @param replay             whether the game is a replay
     * @param hostName           the host's name
     * @param mapName            the map's scenario name
     * @param observers          the observers present
     * @param observerLimit      the observer limit
     * @param closedSlots        the closed slots
     */
    public LanGameStatistics(int saveGameIdentifier,
                             int mapWidth, int mapHeight,
                             int humanPlayers, int maximumPlayers,
                             int speed, int mapStatus,
                             int gameType, int subtype,
                             int turnRate, int tileset, boolean replay,
                             @NotNull String hostName, @NotNull String mapName,
                             int observers, int observerLimit, int closedSlots) {
        this.saveGameIdentifier = saveGameIdentifier;

        this.mapWidth  = mapWidth;
        this.mapHeight = mapHeight;

        this.humanPlayers   = humanPlayers;
        this.maximumPlayers = maximumPlayers;

        this.speed     = speed;
        this.mapStatus = mapStatus;

        this.gameType = gameType;
        this.subtype  = subtype;

        this.turnRate = turnRate;
        this.tileset  = tileset;
        this.replay   = replay;

        this.hostName = limitName(hostName, HOST_NAME_LIMIT);
        this.mapName  = limitName(mapName, MAP_NAME_LIMIT);

        this.observers     = observers;
        this.observerLimit = observerLimit;
        this.closedSlots   = closedSlots;
    }

    /**
     * @return the saved game identifier, {@code 0} for a new game
     */
    public int getSaveGameId() {
        return this.saveGameIdentifier;
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
     * @return the human players in the lobby
     */
    public int getHumanPlayers() {
        return this.humanPlayers;
    }

    /**
     * @return the maximum players
     */
    public int getMaximumPlayers() {
        return this.maximumPlayers;
    }

    /**
     * @return the game speed
     */
    public int getSpeed() {
        return this.speed;
    }

    /**
     * @return the map status
     */
    public int getMapStatus() {
        return this.mapStatus;
    }

    /**
     * @return the game type identifier
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
     * @return the network turns per second
     */
    public int getTurnRate() {
        return this.turnRate;
    }

    /**
     * @return the map's tileset
     */
    public int getTileset() {
        return this.tileset;
    }

    /**
     * @return {@code true} if the game is a replay, {@code false} otherwise
     */
    public boolean isReplay() {
        return this.replay;
    }

    /**
     * @return the host's name
     */
    public @NotNull String getHostName() {
        return this.hostName;
    }

    /**
     * @return the map's scenario name
     */
    public @NotNull String getMapName() {
        return this.mapName;
    }

    /**
     * @return the observers present
     */
    public int getObservers() {
        return this.observers;
    }

    /**
     * @return the observer limit
     */
    public int getObserverLimit() {
        return this.observerLimit;
    }

    /**
     * @return the closed slots
     */
    public int getClosedSlots() {
        return this.closedSlots;
    }


    /**
     * Creates a {@link LanGameStatistics} instance with the lobby's slot counts and every other value of these statistics.
     *
     * @param humanPlayers  the human players in the lobby
     * @param observers     the observers present
     * @param observerLimit the observer limit
     * @param closedSlots   the closed slots
     * @return the created {@link LanGameStatistics} instance
     */
    public @NotNull LanGameStatistics createWithSlotCounts(int humanPlayers, int observers, int observerLimit, int closedSlots) {
        return new LanGameStatistics(this.saveGameIdentifier,
                                     this.mapWidth, this.mapHeight,
                                     humanPlayers, this.maximumPlayers,
                                     this.speed, this.mapStatus,
                                     this.gameType, this.subtype,
                                     this.turnRate, this.tileset, this.replay,
                                     this.hostName, this.mapName,
                                     observers, observerLimit, closedSlots);
    }

    /**
     * Encodes these statistics to the statistics string.
     *
     * @return the statistics string
     */
    public @NotNull String encode() {
        StringBuilder text = new StringBuilder();
        // Each low digit takes its whole byte: a value of 16 or more (a map height of 512 or more) spills into the high digit.
        int mapSize = (this.mapWidth >>> MAP_SIZE_UNIT_SHIFT & DIGIT_MASK) << DIGIT_BITS | this.mapHeight >>> MAP_SIZE_UNIT_SHIFT & BYTE_MASK;
        int players = this.humanPlayers << DIGIT_BITS & BYTE_MASK | this.maximumPlayers & BYTE_MASK;
        int observerCounts = this.observers << DIGIT_BITS & BYTE_MASK | this.observerLimit & BYTE_MASK;

        appendField(text, this.saveGameIdentifier, 0);
        appendField(text, mapSize, DEFAULT_MAP_SIZE);
        appendField(text, players, DEFAULT_PLAYERS);
        appendField(text, this.speed & BYTE_MASK, DEFAULT_SPEED);
        appendField(text, this.mapStatus & BYTE_MASK, 0);
        appendField(text, this.gameType & BYTE_MASK, 0);
        // The zero byte between the game type and the subtype.
        appendField(text, 0, 0);
        appendField(text, this.subtype & WORD_MASK, 0);
        appendField(text, this.turnRate, 0);
        appendField(text, this.tileset & WORD_MASK, 0);
        appendField(text, this.replay ? 1 : 0, 0);
        text.append(this.hostName).append(LINE_END);
        text.append(this.mapName).append(LINE_END);

        text.append(LINE_END);
        appendField(text, TAIL_MARKER, 0);
        appendField(text, observerCounts, DEFAULT_OBSERVERS);
        appendField(text, this.closedSlots & BYTE_MASK, 0);
        appendField(text, this.turnRate & BYTE_MASK, 0);

        return text.toString();
    }
}
