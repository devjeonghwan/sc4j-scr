package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeaderBuilder;
import org.fogofwarlabs.sc4j.scr.game.map.MapStringSection;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.save.pool.*;
import org.fogofwarlabs.sc4j.scr.game.save.section.*;
import org.fogofwarlabs.sc4j.scr.game.state.GameContext;
import org.fogofwarlabs.sc4j.scr.game.state.GameSetup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Holds an SC:R saved-game file ({@link SaveGameKind}: {@code .snx}, {@code .mlx}, {@code .sng}, {@code .mlt}).
 * <p>
 * File layout:
 * <ol>
 * <li>the banner ({@link SaveGameDescription}) and its end byte {@code 0x1A};</li>
 * <li>the plain DWORD {@link #FORMAT_MARK} (WORD version 4, WORD format 109);</li>
 * <li>the game information section ({@link GameInformationSection}, {@code 0xB5} bytes);</li>
 * <li>the plain DWORD file offset of the trailer ({@link TrailerSection}), stored when the trailer is written;</li>
 * <li>the player records section ({@link #PLAYERS_SIZE} bytes: the twelve player records of the replay header);</li>
 * <li>the game structure section ({@link GameStructureSection}, {@link GameContext#CONTEXT_SIZE} bytes);</li>
 * <li>the plain DWORD record slot (the saving machine's own player record);</li>
 * <li>the game parts {@link SaveGameUtils#read} reads in order (images through foliage), then the trailer.</li>
 * </ol>
 * A section is the replay section codec (CRC-32, block count, then {@code 0x2000}-byte blocks each stored or
 * compressed). The pre-load reads the parts up to the record slot before the game starts; {@link #parse} keeps them, the
 * limits and the string section the started game needs.
 */
public final class SaveGameFile {
    /**
     * The plain DWORD after the banner: WORD version 4, WORD format 109.
     */
    public static final int FORMAT_MARK = GameInformationSection.VERSION | GameInformationSection.FORMAT << Short.SIZE;
    /**
     * The size of the player records section, the twelve player records of the replay header.
     */
    public static final int PLAYERS_SIZE = ReplayHeaderBuilder.PLAYER_COUNT * ReplayHeaderBuilder.PLAYER_RECORD_SIZE;

    private static final int MAXIMUM_BANNER_LENGTH = 0x10000;
    /**
     * The section sizes of the creep recession part, in file order.
     */
    private static final int @NotNull [] CREEP_SIZES = {
            CreepRecessionSection.RECORDS_SIZE, CreepRecessionSection.BUCKETS_SIZE, CreepRecessionSection.LISTS_SIZE,
            CreepRecessionSection.COUNTS_SIZE
    };
    /**
     * The size of the MTXM tile array section.
     */
    private static final int MAP_TILES_SIZE = MapArraysSection.TILE_CAPACITY * Short.BYTES;
    private static final int BYTE_MASK = 0xFF;

    /**
     * Creates a {@link SaveGameDescription} instance from the saved-game banner.
     *
     * @param data the file contents
     * @return the created {@link SaveGameDescription} instance
     * @throws IOException if the banner has no end byte
     */
    public static @NotNull SaveGameDescription parseDescription(byte @NotNull [] data) throws IOException {
        return SaveGameDescription.parse(Arrays.copyOf(data, findBannerEnd(data)));
    }

    /**
     * Checks for a saved-game banner followed by {@link #FORMAT_MARK}.
     *
     * @param data the file contents
     * @return {@code true} if the file has a banner and the saved-game format mark, {@code false} otherwise
     */
    public static boolean isNativeFile(byte @NotNull [] data) {
        int bannerEnd;

        try {
            bannerEnd = findBannerEnd(data);
        } catch (IOException exception) {
            return false;
        }

        return data.length >= bannerEnd + 1 + Integer.BYTES && new SaveFileReader(data, bannerEnd + 1).readDword() == FORMAT_MARK;
    }

    /**
     * Creates a {@link SaveGameFile} instance from the banner and startup sections.
     * The remaining game state is read by {@link SaveGameUtils#read}.
     *
     * @param data the file contents
     * @return the created {@link SaveGameFile} instance
     * @throws IOException if the file is not a saved game of format 109 version 4 or its header is malformed
     */
    public static @NotNull SaveGameFile parse(byte @NotNull [] data) throws IOException {
        int bannerEnd = findBannerEnd(data);
        SaveGameDescription description = SaveGameDescription.parse(Arrays.copyOf(data, bannerEnd));

        try {
            SaveFileReader input = new SaveFileReader(data, bannerEnd + 1);
            int mark = input.readDword();

            if (mark != FORMAT_MARK) {
                throw Exceptions.newIOException("The file is no native saved game of format 109 version 4 (mark 0x{0}).", Integer.toHexString(mark));
            }

            byte[] information = input.readSection(GameInformationSection.SIZE);

            GameInformationSection.requireVersion(information);

            int trailerOffset = input.readDword();
            byte[] players = input.readSection(PLAYERS_SIZE);
            byte[] gameStructure = input.readSection(GameContext.CONTEXT_SIZE);
            int recordSlot = input.readDword();

            if (trailerOffset < input.getPosition() || trailerOffset > data.length) {
                throw Exceptions.newIOException("The saved game has the trailer offset {0} outside its game parts.", trailerOffset);
            }

            return new SaveGameFile(data.clone(), description, information, players, gameStructure, recordSlot, input.getPosition(), trailerOffset);
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static void skipCounted(@NotNull SaveFileReader input, int entrySize) {
        int count = input.readWord();

        if (count != 0) {
            input.readSection(count * entrySize);
        }
    }

    private static int findBannerEnd(byte @NotNull [] data) throws IOException {
        int limit = Math.min(data.length, MAXIMUM_BANNER_LENGTH);

        for (int index = 0; index < limit; index++) {
            if ((data[index] & BYTE_MASK) == SaveGameDescription.END_OF_BANNER) {
                return index;
            }
        }

        throw Exceptions.newIOException("The file has no saved game banner.");
    }

    private final byte @NotNull [] data;

    private final @NotNull SaveGameDescription description;
    private final byte @NotNull [] information;
    private final byte @NotNull [] players;
    private final byte @NotNull [] gameStructure;
    private final int recordSlot;

    /**
     * The offset of the first game part after the record slot.
     */
    private final int bodyOffset;
    private final int trailerOffset;

    private SaveGameFile(byte @NotNull [] data,
                         @NotNull SaveGameDescription description,
                         byte @NotNull [] information,
                         byte @NotNull [] players,
                         byte @NotNull [] gameStructure,
                         int recordSlot,
                         int bodyOffset,
                         int trailerOffset) {
        this.data = data;

        this.description   = description;
        this.information   = information;
        this.players       = players;
        this.gameStructure = gameStructure;
        this.recordSlot    = recordSlot;

        this.bodyOffset    = bodyOffset;
        this.trailerOffset = trailerOffset;
    }

    /**
     * @return the banner
     */
    public @NotNull SaveGameDescription getDescription() {
        return this.description;
    }

    /**
     * @return a copy of the game information section ({@link GameInformationSection})
     */
    public byte @NotNull [] getInformation() {
        return this.information.clone();
    }

    /**
     * @return a copy of the twelve 36-byte player records
     */
    public byte @NotNull [] getPlayers() {
        return this.players.clone();
    }

    /**
     * @return a copy of the game structure
     */
    public byte @NotNull [] getGameStructure() {
        return this.gameStructure.clone();
    }

    /**
     * @return the saving machine's own record slot
     */
    public int getRecordSlot() {
        return this.recordSlot;
    }

    /**
     * @return the generator state at the save
     */
    public int getRandomState() {
        return GameInformationSection.readRandomState(this.information);
    }

    /**
     * @return the save id, the Save command's time DWORD
     */
    public int getSaveGameId() {
        return GameInformationSection.readSaveGameId(this.information);
    }

    /**
     * Reads the pool limits the game ran with ({@code LMTS}), which size the pools of the started game.
     *
     * @return the seven limits, or {@code null} when the trailer has none
     */
    public int @Nullable [] getLimits() {
        return TrailerSection.readLimits(this.openTrailer());
    }

    /**
     * Reads the map string section the trailer keeps ({@code STR } or {@code STRx}).
     *
     * @return the string section, or {@code null} when the trailer has none
     */
    public @Nullable MapStringSection getStringSection() {
        return TrailerSection.readStrings(this.openTrailer());
    }

    /**
     * Reads the saved terrain the started game's map takes ({@code MTXM}): the parts before the map arrays are walked by
     * their stored sizes (the pools sized by {@link #getLimits()}), then the first {@code width * height} WORDs of the
     * tile array are taken.
     *
     * @param width  the map width in tiles
     * @param height the map height in tiles
     * @return the tile WORDs, little-endian
     * @throws UncheckedIOException if a part is malformed or the trailer has no limits
     */
    public byte @NotNull [] readMapTiles(int width, int height) {
        int[] limits = this.getLimits();
        SaveFileReader input = this.openBody();

        if (limits == null) {
            throw SaveFileReader.createFailure("The saved game has no pool limits.");
        }

        skipCounted(input, ImagePoolSection.ENTRY_SIZE);
        skipCounted(input, SpritePoolSection.ENTRY_SIZE);
        input.readBytes(SpritePoolSection.ROW_ARRAYS_SIZE);
        skipCounted(input, ThingyPoolSection.ENTRY_SIZE);
        skipCounted(input, ThingyPoolSection.ENTRY_SIZE);
        skipCounted(input, FlingyPoolSection.ENTRY_SIZE);
        input.readSection(limits[GameSetup.UNIT_LIMIT]);

        int units = input.readDword();

        if (units != 0) {
            input.readSection(units * UnitPoolSection.ENTRY_SIZE);
        }

        input.readBytes(UnitPoolSection.HEADS_SIZE);
        skipCounted(input, BulletPoolSection.ENTRY_SIZE);
        input.readSection(limits[GameSetup.ORDER_LIMIT] * OrderPool.SAVE_RECORD_SIZE);
        input.readBytes(OrderPoolSection.HEADS_SIZE);
        input.readSection(input.readDword());
        input.readSection(input.readDword());

        for (int size : CREEP_SIZES) {
            input.readSection(size);
        }

        return Arrays.copyOf(input.readSection(MAP_TILES_SIZE), width * height * Short.BYTES);
    }

    /**
     * @return a copy of the file contents
     */
    public byte @NotNull [] toByteArray() {
        return this.data.clone();
    }

    /**
     * Creates a {@link SaveFileReader} instance at the first game part after the record slot.
     *
     * @return the created {@link SaveFileReader} instance, which ends at the trailer
     */
    @NotNull SaveFileReader openBody() {
        return new SaveFileReader(Arrays.copyOf(this.data, this.trailerOffset), this.bodyOffset);
    }

    /**
     * Creates a {@link SaveFileReader} instance at the trailer.
     *
     * @return the created {@link SaveFileReader} instance
     */
    @NotNull SaveFileReader openTrailer() {
        return new SaveFileReader(this.data, this.trailerOffset);
    }
}
