package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates saved-file startup headers and splits the remaining independent body layout.
 * <p>
 * The sizes below are recorded independently of the production sections, because the layout itself is the save-file contract that the comparisons
 * check.
 */
public final class SaveGameLayoutCheck {
    static final int IMAGE_ENTRY_SIZE = 0x42;
    static final int SPRITE_ENTRY_SIZE = 0x26;
    static final int THINGY_ENTRY_SIZE = 18;
    private static final int FLINGY_ENTRY_SIZE = 0x4E;
    /**
     * A unit entry: the DWORD slot and the 336-byte classic unit.
     */
    public static final int UNIT_ENTRY_SIZE = 0x154;
    static final int UNIT_LIST_HEAD_COUNT = 21;
    static final int BULLET_ENTRY_SIZE = 0x72;
    static final int ORDER_ENTRY_SIZE = 20;
    static final int ORDER_LIST_HEAD_COUNT = 2;
    static final int CREEP_RECORDS_SIZE = 0x5000;
    static final int CREEP_HEADS_SIZE = 0x1000;
    static final int CREEP_LISTS_SIZE = 0x28;
    static final int CREEP_TAIL_SIZE = 0x14;
    static final int MAP_TILES_SIZE = 0x20000;
    static final int MAP_ARRAY_SIZE = 0x20000;
    static final int MAP_FLAGS_SIZE = 0x40000;
    static final int TRIGGER_ENTRY_SIZE = 0x960;
    static final int TRIGGER_HEAD_SIZE = 0x18;
    static final int TRIGGER_TAIL_SIZE = 0x500;
    static final int SELECTIONS_SIZE = 0x180;
    private static final int COMPUTER_REGION_POOL_SIZE = 0x4E24;
    /**
     * The computer players whose region records follow the region pool, in descending owner order.
     */
    public static final int COMPUTER_PLAYER_COUNT = 8;
    /**
     * The size of one computer region record.
     */
    public static final int COMPUTER_REGION_ENTRY_SIZE = 0x34;
    private static final int COMPUTER_CAPTAINS_SIZE = 0x7D04;
    private static final int COMPUTER_CAPTAIN_HEADS_SIZE = 0x40;
    private static final int COMPUTER_TASKS_SIZE = 0x5DC4;
    private static final int COMPUTER_TASK_DATA_SIZE = 0xABE4;
    private static final int COMPUTER_SECOND_TASK_DATA_SIZE = 0xB3B4;
    private static final int COMPUTER_TASK_HEADS_SIZE = 0x40;
    private static final int COMPUTER_PLAYERS_SIZE = 0x2740;
    private static final int COMPUTER_SCRIPTS_SIZE = 0x1454;
    private static final int COMPUTER_SCRIPT_HEADS_SIZE = 8;
    private static final int COMPUTER_TAIL_SIZE = 0x2EE8;
    private static final int SCREEN_COORDINATE_COUNT = 2;
    static final int FOLIAGE_HEADER_FIELD_COUNT = 2;

    static final int UNIT_CAPACITY = 3400;
    static final int ORDER_CAPACITY = 4000;
    static final int ROW_ARRAY_SIZE = 0x400;
    private static final int @NotNull [] SETTINGS_SIZES = {0x390, 0x1C8, 0xE4, 0x1C8, 0x1C8, 0x1C8, 0x1C8, 0x104, 0x104, 0x7A, 0x7A, 0x7A, 0x7A, 0x7A,
                                                           0x7A, 0x58, 0x58, 0x58, 0x58};

    static final int FOLIAGE_SIZE = 0x180008;

    /**
     * The offset of the elapsed frame DWORD in the saved game structure.
     */
    private static final int GAME_FRAME_OFFSET = 0x14C;

    /**
     * Creates a {@link SaveGameLayoutCheck} instance using the shared startup header and independent body layout.
     *
     * @param data the byte contents
     * @return the created {@link SaveGameLayoutCheck} instance
     * @throws IllegalArgumentException if the shared startup header is malformed
     * @throws UncheckedIOException     if an independent body part is truncated or malformed
     */
    public static @NotNull SaveGameLayoutCheck parse(byte @NotNull [] data) {
        SaveGameFile file;

        try {
            file = SaveGameFile.parse(data);
        } catch (IOException exception) {
            throw Exceptions.newIllegalArgumentException(exception, "Cannot split the saved game header: {0}", exception.getMessage());
        }

        Map<String, byte[]> parts = new LinkedHashMap<>();

        parts.put("format", encodeInteger(SaveGameFile.FORMAT_MARK));
        parts.put("information", file.getInformation());
        parts.put("trailerOffset", encodeInteger(file.openTrailer().getPosition()));
        parts.put("players", file.getPlayers());
        parts.put("game", file.getGameStructure());
        parts.put("recordSlot", encodeInteger(file.getRecordSlot()));

        SaveFileReader input = new SaveFileReader(data, file.openBody().getPosition());

        readCountedPart(parts, "images", input, IMAGE_ENTRY_SIZE);
        readCountedPart(parts, "sprites", input, SPRITE_ENTRY_SIZE);
        readRawPart(parts, "spriteFirstRows", input, ROW_ARRAY_SIZE);
        readRawPart(parts, "spriteLastRows", input, ROW_ARRAY_SIZE);
        readCountedPart(parts, "thingies", input, THINGY_ENTRY_SIZE);
        readCountedPart(parts, "secondaryThingies", input, THINGY_ENTRY_SIZE);
        readCountedPart(parts, "flingy", input, FLINGY_ENTRY_SIZE);

        parts.put("unitFlags", input.readSection(UNIT_CAPACITY));

        int unitCount = input.readDword();

        parts.put("unitCount", encodeInteger(unitCount));
        parts.put("units", unitCount == 0 ? new byte[0] : input.readSection(unitCount * UNIT_ENTRY_SIZE));
        readRawPart(parts, "unitHeads", input, UNIT_LIST_HEAD_COUNT * Integer.BYTES);
        readCountedPart(parts, "bullets", input, BULLET_ENTRY_SIZE);
        parts.put("orders", input.readSection(ORDER_CAPACITY * ORDER_ENTRY_SIZE));
        readRawPart(parts, "orderHeads", input, ORDER_LIST_HEAD_COUNT * Integer.BYTES);
        readSizedPart(parts, "terrainWords", input);
        readSizedPart(parts, "terrainBytes", input);

        parts.put("creepRecords", input.readSection(CREEP_RECORDS_SIZE));
        parts.put("creepHeads", input.readSection(CREEP_HEADS_SIZE));
        parts.put("creepLists", input.readSection(CREEP_LISTS_SIZE));
        parts.put("creepTail", input.readSection(CREEP_TAIL_SIZE));
        parts.put("mapTiles", input.readSection(MAP_TILES_SIZE));
        parts.put("mapArray", input.readSection(MAP_ARRAY_SIZE));
        parts.put("mapFlags", input.readSection(MAP_FLAGS_SIZE));

        int triggerCount = input.readWord();

        parts.put("triggerCount", encodeInteger(triggerCount));
        parts.put("triggers", input.readSection(triggerCount * TRIGGER_ENTRY_SIZE + TRIGGER_HEAD_SIZE));
        parts.put("triggerTail", input.readSection(TRIGGER_TAIL_SIZE));
        readRawPart(parts, "optionalSize", input, Integer.BYTES);
        parts.put("selections", input.readSection(SELECTIONS_SIZE));
        readSizedPart(parts, "pathing", input);

        byte[] regionCountBytes = input.readSection(Integer.BYTES);

        parts.put("aiRegionCount", regionCountBytes);

        int regionCount = ByteBuffer.wrap(regionCountBytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0);

        parts.put("aiRegionPool", input.readSection(COMPUTER_REGION_POOL_SIZE));

        for (int player = COMPUTER_PLAYER_COUNT - 1; player >= 0; player--) {
            parts.put("aiRegions" + player, input.readSection(regionCount * COMPUTER_REGION_ENTRY_SIZE));
        }

        parts.put("aiCaptains", input.readSection(COMPUTER_CAPTAINS_SIZE));
        parts.put("aiCaptainHeads", input.readSection(COMPUTER_CAPTAIN_HEADS_SIZE));
        parts.put("aiTasks", input.readSection(COMPUTER_TASKS_SIZE));
        parts.put("aiTaskData", input.readSection(COMPUTER_TASK_DATA_SIZE));
        parts.put("aiTaskData2", input.readSection(COMPUTER_SECOND_TASK_DATA_SIZE));
        parts.put("aiTaskHeads", input.readSection(COMPUTER_TASK_HEADS_SIZE));
        parts.put("aiPlayers", input.readSection(COMPUTER_PLAYERS_SIZE));
        parts.put("aiScripts", input.readSection(COMPUTER_SCRIPTS_SIZE));
        parts.put("aiScriptHeads", input.readSection(COMPUTER_SCRIPT_HEADS_SIZE));
        parts.put("aiTail", input.readSection(COMPUTER_TAIL_SIZE));

        for (int index = 0; index < SETTINGS_SIZES.length; index++) {
            parts.put("settings" + index, input.readSection(SETTINGS_SIZES[index]));
        }

        readRawPart(parts, "screen", input, SCREEN_COORDINATE_COUNT * Integer.BYTES);
        readRawPart(parts, "foliageHeader", input, FOLIAGE_HEADER_FIELD_COUNT * Integer.BYTES);
        parts.put("foliage", input.readStream(FOLIAGE_SIZE));
        readRawPart(parts, "trailer", input, input.getRemaining());

        return new SaveGameLayoutCheck(parts);
    }

    /**
     * Reads the elapsed frame of a saved game structure.
     *
     * @param gameStructure the saved game structure
     * @return the elapsed frame DWORD
     */
    public static int readGameFrame(byte @NotNull [] gameStructure) {
        return ByteBuffer.wrap(gameStructure).order(ByteOrder.LITTLE_ENDIAN).getInt(GAME_FRAME_OFFSET);
    }

    /**
     * Reads an undecoded part into the insertion-ordered part map.
     *
     * @param parts the parts
     * @param name  the part name
     * @param input the serialized input
     * @param size  the decoded part size
     */
    private static void readRawPart(@NotNull Map<String, byte[]> parts, @NotNull String name, @NotNull SaveFileReader input, int size) {
        parts.put(name, input.readBytes(size));
    }

    /**
     * Reads a WORD-counted part and retains its count separately.
     *
     * @param parts     the parts
     * @param name      the part name
     * @param input     the serialized input
     * @param entrySize the recorded entry width
     */
    private static void readCountedPart(@NotNull Map<String, byte[]> parts, @NotNull String name, @NotNull SaveFileReader input, int entrySize) {
        int count = input.readWord();

        parts.put(name + "Count", encodeInteger(count));
        parts.put(name, count == 0 ? new byte[0] : input.readSection(count * entrySize));
    }

    /**
     * Reads a DWORD-sized decoded part into the part map.
     *
     * @param parts the parts
     * @param name  the part name
     * @param input the serialized input
     */
    private static void readSizedPart(@NotNull Map<String, byte[]> parts, @NotNull String name, @NotNull SaveFileReader input) {
        int size = input.readDword();

        parts.put(name, input.readSection(size));
    }

    /**
     * Encodes one signed DWORD in little-endian order.
     *
     * @param value the signed DWORD value
     * @return the four little-endian bytes
     */
    private static byte @NotNull [] encodeInteger(int value) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }


    private final @NotNull Map<String, byte[]> parts;

    /**
     * Creates a {@link SaveGameLayoutCheck} instance for the supplied parts.
     *
     * @param parts the parts in file order
     */
    private SaveGameLayoutCheck(@NotNull Map<String, byte[]> parts) {
        this.parts = parts;
    }

    /**
     * @return the parts in file order
     */
    public @NotNull Map<String, byte[]> getParts() {
        return this.parts;
    }

    /**
     * Finds a part by name.
     *
     * @param name the part name
     * @return the part, or {@code null} if the layout has no part of this name
     */
    byte @Nullable [] findPart(@NotNull String name) {
        return this.parts.get(name);
    }

    /**
     * Returns a part that the layout must contain.
     *
     * @param name the part name
     * @return the part
     * @throws AssertionError if the layout has no part of this name
     */
    public byte @NotNull [] requirePart(@NotNull String name) {
        byte[] part = this.parts.get(name);

        Assertions.assertNotNull(part);

        return part;
    }
}
