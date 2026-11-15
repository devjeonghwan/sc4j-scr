package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;

/**
 * Stores LAN game options in the {@code 32}-byte little-endian {@link GameTemplate} layout. Construction copies the eleven
 * option bytes; encoding preserves their order and zeroes the trailing padding.
 */
public final class LanGameOptions {
    /**
     * The size of the game data: the size of the game options ({@link GameTemplate#SIZE}).
     */
    public static final int SIZE = GameTemplate.SIZE;

    /**
     * Melee ({@code Templates/Melee(1).got}; the option bytes of {@code MeleeGameSetup}).
     */
    public static final @NotNull LanGameOptions MELEE = parse(GameTemplate.MELEE.encode());
    /**
     * Free For All ({@code Templates/Free For All(1).got}).
     */
    public static final @NotNull LanGameOptions FREE_FOR_ALL = parse(GameTemplate.FREE_FOR_ALL.encode());
    /**
     * Use Map Settings ({@code Templates/Use Map Settings(1).got}).
     */
    public static final @NotNull LanGameOptions USE_MAP_SETTINGS = parse(GameTemplate.USE_MAP_SETTINGS.encode());
    /**
     * Top vs Bottom with one top slot.
     */
    public static final @NotNull LanGameOptions TOP_VS_BOTTOM_1 = parse(
            HexFormat.of().parseHex("0f00010001000000010101020200010100010000000000320000000000000000")
    );

    /**
     * The number of teams of Top vs Bottom.
     */
    private static final int TOP_VS_BOTTOM_TEAMS = 2;
    /**
     * The slots per team by the teams option: {@code 4}, {@code 3} or {@code 2} for two, three or four teams, else {@code 0}.
     */
    private static final int @NotNull [] SLOTS_PER_TEAM = {0, 0, 4, 3, 2};

    /**
     * The masks of an unsigned byte and an unsigned 16-bit word.
     */
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Creates a {@link LanGameOptions} instance from the {@code 32}-byte LAN game data.
     *
     * @param data the {@code 32}-byte LAN game data
     * @return the created {@link LanGameOptions} instance
     * @throws IllegalArgumentException if the data is shorter than {@link #SIZE}
     */
    public static @NotNull LanGameOptions parse(byte @NotNull [] data) {
        if (data.length < SIZE) {
            throw Exceptions.newIllegalArgumentException("The game data holds {0} bytes, not {1}.", data.length, SIZE);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int typeIdentifier = buffer.get() & BYTE_MASK;
        int leagueIdentifier = buffer.get() & BYTE_MASK;
        int subtypeIdentifier = buffer.getShort() & WORD_MASK;
        int subtypeValue = buffer.getInt();
        byte[] options = new byte[GameTemplate.OPTION_COUNT];

        buffer.get(options);

        return new LanGameOptions(typeIdentifier, leagueIdentifier, subtypeIdentifier, subtypeValue, options, buffer.getInt(), buffer.getInt());
    }


    private final int typeIdentifier;
    private final int leagueIdentifier;

    private final int subtypeIdentifier;
    private final int subtypeValue;

    private final byte @NotNull [] options;

    private final int victoryValue;

    private final int resourcesValue;

    /**
     * Creates a {@link LanGameOptions} instance.
     *
     * @param typeIdentifier    the type id
     * @param leagueIdentifier  the league id
     * @param subtypeIdentifier the sub type id (Top vs Bottom: the number of top slots)
     * @param subtypeValue      the sub type value
     * @param options           the eleven option bytes
     * @param victoryValue      the victory value
     * @param resourcesValue    the resources value
     * @throws IllegalArgumentException if there are not eleven option bytes
     */
    public LanGameOptions(int typeIdentifier, int leagueIdentifier,
                          int subtypeIdentifier, int subtypeValue,
                          byte @NotNull [] options,
                          int victoryValue,
                          int resourcesValue) {
        if (options.length != GameTemplate.OPTION_COUNT) {
            throw Exceptions.newIllegalArgumentException("A game type has eleven option bytes.");
        }

        this.typeIdentifier   = typeIdentifier;
        this.leagueIdentifier = leagueIdentifier;

        this.subtypeIdentifier = subtypeIdentifier;
        this.subtypeValue      = subtypeValue;

        this.options = options.clone();

        this.victoryValue = victoryValue;

        this.resourcesValue = resourcesValue;
    }

    /**
     * @return the type id
     */
    public int getTypeId() {
        return this.typeIdentifier;
    }

    /**
     * @return the sub type id; Top vs Bottom's number of top slots
     */
    public int getSubTypeId() {
        return this.subtypeIdentifier;
    }

    /**
     * @return the resources value (the starting minerals of the template)
     */
    public int getResourcesValue() {
        return this.resourcesValue;
    }

    /**
     * @return a copy of the eleven option bytes
     */
    public byte @NotNull [] getOptions() {
        return this.options.clone();
    }

    /**
     * @return {@code true} if the options are Use Map Settings (victory, starting units and tournament are {@code 0}), {@code false} otherwise
     */
    public boolean isUseMapSettings() {
        return this.options[GameTemplate.VICTORY] == 0 && this.options[GameTemplate.STARTING_UNITS] == 0 &&
               this.options[GameTemplate.TOURNAMENT] == 0;
    }

    /**
     * @return {@code true} if the game type is Top vs Bottom, {@code false} otherwise
     */
    public boolean isTopVsBottom() {
        return this.typeIdentifier == GameTemplate.TYPE_TOP_VS_BOTTOM;
    }

    /**
     * @return {@code true} if the options are a team game (the teams option of a network game is set), {@code false} otherwise
     */
    public boolean isTeamGame() {
        return this.options[GameTemplate.TEAMS] != 0;
    }

    /**
     * @return the number of teams: {@code 2} for Top vs Bottom, else the teams option
     */
    public int getTeamCount() {
        return this.isTopVsBottom() ? TOP_VS_BOTTOM_TEAMS : this.options[GameTemplate.TEAMS] & BYTE_MASK;
    }

    /**
     * @return the slots per team of a team game: {@code 4}, {@code 3} or {@code 2} for two, three or four teams, else {@code 0}
     */
    public int getSlotsPerTeam() {
        int teams = this.options[GameTemplate.TEAMS];

        return teams >= 0 && teams < SLOTS_PER_TEAM.length ? SLOTS_PER_TEAM[teams] : 0;
    }


    /**
     * Encodes the options as the {@code 32}-byte LAN game data.
     *
     * @return the {@code 32}-byte game data
     */
    public byte @NotNull [] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) this.typeIdentifier).put((byte) this.leagueIdentifier)
              .putShort((short) this.subtypeIdentifier).putInt(this.subtypeValue).put(this.options)
              .putInt(this.victoryValue).putInt(this.resourcesValue);

        return buffer.array();
    }

    /**
     * Creates a {@link GameTemplate} instance of the same {@code 32} bytes as these options.
     *
     * @return the created {@link GameTemplate} instance
     */
    public @NotNull GameTemplate createTemplate() {
        return GameTemplate.parse(this.encode());
    }
}
