package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.scr.core.test.TestResourceUtils;
import org.fogofwarlabs.sc4j.scr.data.test.DataTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Tests replay encoding with synthetic command streams and native SC:R recordings.
 */
final class ReplayWriterTest {
    private static final int FIRST_COMMAND_FRAME = 3;
    private static final int SECOND_COMMAND_FRAME = 5;
    private static final int REPEATED_COMMAND_FRAME = 9;
    private static final int RIGHT_CLICK_PAYLOAD_SIZE = 11;
    private static final int SYNTHETIC_ENGINE = 1;
    private static final int SYNTHETIC_FRAME_COUNT = 10;
    private static final int SYNTHETIC_RANDOM_SEED = 1234;
    private static final int SYNTHETIC_MAP_SIZE = 64;
    private static final int SYNTHETIC_GAME_TYPE = 2;
    private static final int SYNTHETIC_GAME_SUBTYPE = 1;
    private static final int SYNTHETIC_BUILD_NUMBER = 13515;
    private static final int LONG_NAME_SLOT = 1;
    /**
     * The speed override selector a written replay keeps.
     */
    private static final int SPEED_OVERRIDE_INDEX = 3;
    private static final int SPLIT_NAME_SLOT = 2;

    private static final int BYTE_MASK = 0xFF;
    private static final int EXTENSION_OFFSET_FIELD = 16;
    private static final int EXTENSION_HEADER_SIZE = 2 * Integer.BYTES;
    private static final int ZLIB_HEADER_BYTE = 0x78;
    private static final int CHUNK_SIZE = 0x2000;
    private static final int COMMAND_SECTION_INDEX = 3;
    private static final int NAME_SECTION_INDEX = 6;
    private static final int RIGHT_CLICK_COMMAND_COUNT = 30;
    private static final @NotNull String SKIN_TAG = "SKIN";
    private static final @NotNull String CUSTOM_COLORS_TAG = "CCLR";
    /**
     * The unit capacity of the current limits the synthetic replay records.
     */
    private static final int CURRENT_UNIT_CAPACITY = 3400;

    /**
     * The size of one record of the name section.
     */
    private static final int NAME_RECORD_SIZE = 0x60;
    private static final int NAME_COUNT = 12;
    /**
     * A name holding {@code a} and the first two UTF-8 bytes of a Hangul character, as a name cut inside a character holds
     * them.
     */
    private static final byte @NotNull [] SPLIT_NAME = {'a', (byte) 0xED, (byte) 0x95};
    private static final byte LONG_NAME_BYTE = 'x';

    private static @NotNull List<byte[]> readRawSections(byte @NotNull [] file) {
        ByteBuffer buffer = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        List<byte[]> sections = new ArrayList<>();

        sections.add(readSection(buffer));

        int extension = buffer.getInt();

        while (buffer.position() < extension) {
            sections.add(readSection(buffer));
        }

        // 0 id, 1 header, 2 command size, 3 commands, 4 map size, 5 map, 6 names.
        return sections;
    }

    private static byte @NotNull [] readExtension(byte @NotNull [] file, @NotNull String tag) {
        ByteBuffer buffer = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);

        buffer.position(buffer.getInt(EXTENSION_OFFSET_FIELD));

        while (buffer.remaining() >= EXTENSION_HEADER_SIZE) {
            byte[] name = new byte[Integer.BYTES];

            buffer.get(name);

            int size = buffer.getInt();

            if (new String(name, StandardCharsets.US_ASCII).equals(tag)) {
                return readSection(buffer);
            }

            buffer.position(buffer.position() + size);
        }

        return Assertions.fail("Missing extension " + tag);
    }

    private static byte @NotNull [] readSection(@NotNull ByteBuffer buffer) {
        buffer.getInt();

        int count = buffer.getInt();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (int chunkIndex = 0; chunkIndex < count; chunkIndex++) {
            int length = buffer.getInt();
            byte[] data = new byte[length];

            buffer.get(data);

            if (length > Integer.BYTES && (data[0] & BYTE_MASK) == ZLIB_HEADER_BYTE) {
                try (Inflater inflater = new Inflater()) {
                    inflater.setInput(data);

                    byte[] outputBuffer = new byte[CHUNK_SIZE];

                    try {
                        int produced = inflater.inflate(outputBuffer);

                        output.write(outputBuffer, 0, produced);
                    } catch (DataFormatException exception) {
                        Assertions.fail(exception);
                    }
                }
            } else {
                output.writeBytes(data);
            }
        }

        return output.toByteArray();
    }


    @Test
    void write_should_rewrite_recorded_replays_byte_for_byte() throws IOException {
        String[] names = {DataTestResources.REPLAY_TERRAN_VS_COMPUTER, DataTestResources.REPLAY_ZVZ_COMPUTER_SHORT,
                          DataTestResources.REPLAY_FREE_FOR_ALL_COMPUTERS};
        int checked = 0;

        for (String name : names) {
            byte[] original = TestResourceUtils.readAllBytes(name);
            ReplayFile replay = ReplayFile.parse(original);
            List<byte[]> sections = readRawSections(original);
            byte[] commands = ReplayWriter.encodeCommands(replay.getCommands());

            Assertions.assertArrayEquals(sections.get(COMMAND_SECTION_INDEX), commands, name + " command blocks");

            byte[] headerData = ReplayFile.readHeaderSection(original);
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            byte[] written = new ReplayWriter(headerData,
                                              commands,
                                              mapData,
                                              sections.get(NAME_SECTION_INDEX),
                                              readExtension(original, SKIN_TAG),
                                              replay.getLimits().getRequestedValues(),
                                              replay.getGameVersion().getFlags(),
                                              readExtension(original, CUSTOM_COLORS_TAG),
                                              replay.getGameVersion().getBlock()).write();

            Assertions.assertArrayEquals(original, written, name);
            checked++;
        }

        Assertions.assertEquals(names.length, checked);
    }

    @Test
    void split_blocks_should_match_the_blocks_parsed_from_recorded_replays() throws IOException {
        String[] names = {DataTestResources.REPLAY_TERRAN_VS_COMPUTER, DataTestResources.REPLAY_ZVZ_COMPUTER_SHORT,
                          DataTestResources.REPLAY_FREE_FOR_ALL_COMPUTERS};

        for (String name : names) {
            ReplayFile replay = ReplayFile.parse(TestResourceUtils.readAllBytes(name));
            List<ReplayCommandBlock> parsed = replay.getCommandBlocks();
            List<ReplayCommandBlock> split = ReplayWriter.splitBlocks(replay.getCommands());

            Assertions.assertEquals(parsed.size(), split.size(), name);

            for (int index = 0; index < parsed.size(); index++) {
                Assertions.assertEquals(parsed.get(index).getFrame(), split.get(index).getFrame(), name);
                Assertions.assertEquals(parsed.get(index).getSize(), split.get(index).getSize(), name);
                Assertions.assertEquals(parsed.get(index).getFirstCommandIndex(), split.get(index).getFirstCommandIndex(), name);
                Assertions.assertEquals(parsed.get(index).getCommandCount(), split.get(index).getCommandCount(), name);
            }
        }
    }

    @Test
    void encode_commands_should_split_blocks_by_frame_and_byte_length_limit() throws IOException {
        List<ReplayCommand> commands = new ArrayList<>();

        commands.add(new ReplayCommand(FIRST_COMMAND_FRAME, 0, ReplayCommandIds.STOP, new byte[]{0}));
        commands.add(new ReplayCommand(FIRST_COMMAND_FRAME, 0, ReplayCommandIds.HOLD_POSITION, new byte[]{0}));
        commands.add(new ReplayCommand(SECOND_COMMAND_FRAME, 0, ReplayCommandIds.STOP, new byte[]{0}));

        for (int index = 0; index < RIGHT_CLICK_COMMAND_COUNT; index++) {
            commands.add(new ReplayCommand(REPEATED_COMMAND_FRAME, 0, ReplayCommandIds.RIGHT_CLICK_121, new byte[RIGHT_CLICK_PAYLOAD_SIZE]));
        }

        byte[] encoded = ReplayWriter.encodeCommands(commands);
        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        List<int[]> blocks = new ArrayList<>();

        while (buffer.hasRemaining()) {
            int frame = buffer.getInt();
            int length = buffer.get() & BYTE_MASK;

            blocks.add(new int[]{frame, length});
            buffer.position(buffer.position() + length);
        }

        // 13 bytes per right click: 19 fit below 255 (247 + 1 + 13 >= 255 splits), the other 11 follow.
        Assertions.assertEquals(4, blocks.size());
        Assertions.assertArrayEquals(new int[]{3, 6}, blocks.get(0));
        Assertions.assertArrayEquals(new int[]{5, 3}, blocks.get(1));
        Assertions.assertArrayEquals(new int[]{9, 19 * 13}, blocks.get(2));
        Assertions.assertArrayEquals(new int[]{9, 11 * 13}, blocks.get(3));

        byte[] header = new ReplayHeaderBuilder().setEngine(SYNTHETIC_ENGINE)
                                                 .setFrames(SYNTHETIC_FRAME_COUNT)
                                                 .setSeed(SYNTHETIC_RANDOM_SEED)
                                                 .setMapSize(SYNTHETIC_MAP_SIZE, SYNTHETIC_MAP_SIZE)
                                                 .setGameType(SYNTHETIC_GAME_TYPE, SYNTHETIC_GAME_SUBTYPE)
                                                 .build();
        byte[] mapData = {1, 2, 3};
        byte[][] names = new byte[NAME_COUNT][0];

        byte[] file = new ReplayWriter(header,
                                       encoded,
                                       mapData,
                                       ReplayWriter.encodeNames(names),
                                       new byte[ReplayWriter.SKIN_SIZE],
                                       ReplayWriter.CURRENT_LIMITS,
                                       ReplayWriter.CURRENT_FIX_FLAGS,
                                       new byte[ReplayWriter.CUSTOM_COLORS_SIZE],
                                       ReplayWriter.encodeVersionBlock(SYNTHETIC_BUILD_NUMBER, 0)).write();
        ReplayFile parsed = ReplayFile.parse(file);

        Assertions.assertEquals(commands.size(), parsed.getCommands().size());
        Assertions.assertEquals(CURRENT_UNIT_CAPACITY, parsed.getLimits().getUnitCapacity());
        Assertions.assertEquals(SYNTHETIC_BUILD_NUMBER, parsed.getGameVersion().getBuild());
        Assertions.assertEquals(SYNTHETIC_RANDOM_SEED, parsed.getHeader().getRandomSeed());
    }

    @Test
    void encode_names_should_put_each_name_cut_to_its_record() {
        byte[][] names = new byte[NAME_COUNT][0];
        byte[] longName = new byte[NAME_RECORD_SIZE + 1];
        byte[] expected = new byte[NAME_COUNT * NAME_RECORD_SIZE];

        Arrays.fill(longName, LONG_NAME_BYTE);
        names[LONG_NAME_SLOT]  = longName;
        names[SPLIT_NAME_SLOT] = SPLIT_NAME;
        Arrays.fill(expected, NAME_RECORD_SIZE, SPLIT_NAME_SLOT * NAME_RECORD_SIZE - 1, LONG_NAME_BYTE);
        System.arraycopy(SPLIT_NAME, 0, expected, SPLIT_NAME_SLOT * NAME_RECORD_SIZE, SPLIT_NAME.length);

        Assertions.assertArrayEquals(expected, ReplayWriter.encodeNames(names));
    }

    @Test
    void write_should_write_empty_command_history_without_data_section() throws IOException {
        byte[] header = new ReplayHeaderBuilder().setEngine(SYNTHETIC_ENGINE)
                                                 .setMapSize(SYNTHETIC_MAP_SIZE, SYNTHETIC_MAP_SIZE)
                                                 .setGameType(SYNTHETIC_GAME_TYPE, SYNTHETIC_GAME_SUBTYPE)
                                                 .build();
        byte[][] names = new byte[NAME_COUNT][0];

        byte[] file = new ReplayWriter(header,
                                       new byte[0],
                                       new byte[]{1},
                                       ReplayWriter.encodeNames(names),
                                       new byte[ReplayWriter.SKIN_SIZE],
                                       ReplayWriter.CURRENT_LIMITS,
                                       ReplayWriter.CURRENT_FIX_FLAGS,
                                       new byte[ReplayWriter.CUSTOM_COLORS_SIZE],
                                       ReplayWriter.encodeVersionBlock(SYNTHETIC_BUILD_NUMBER, 0)).write();

        Assertions.assertEquals(0, ReplayFile.parse(file).getCommands().size());
    }

    @Test
    void write_should_keep_a_speed_override_in_its_section_and_write_no_section_without_one() throws IOException {
        byte[] header = new ReplayHeaderBuilder().setEngine(SYNTHETIC_ENGINE)
                                                 .setMapSize(SYNTHETIC_MAP_SIZE, SYNTHETIC_MAP_SIZE)
                                                 .setGameType(SYNTHETIC_GAME_TYPE, SYNTHETIC_GAME_SUBTYPE)
                                                 .build();
        byte[] names = ReplayWriter.encodeNames(new byte[NAME_COUNT][0]);
        byte[] versionBlock = ReplayWriter.encodeVersionBlock(SYNTHETIC_BUILD_NUMBER, 0);

        byte[] plain = new ReplayWriter(header, new byte[0], new byte[]{1}, names, new byte[ReplayWriter.SKIN_SIZE], ReplayWriter.CURRENT_LIMITS,
                                        ReplayWriter.CURRENT_FIX_FLAGS, new byte[ReplayWriter.CUSTOM_COLORS_SIZE], versionBlock).write();
        byte[] withoutOverride = new ReplayWriter(header, new byte[0], new byte[]{1}, names, new byte[ReplayWriter.SKIN_SIZE],
                                                  ReplayWriter.CURRENT_LIMITS, ReplayWriter.CURRENT_FIX_FLAGS,
                                                  new byte[ReplayWriter.CUSTOM_COLORS_SIZE], versionBlock, 0).write();
        byte[] withOverride = new ReplayWriter(header, new byte[0], new byte[]{1}, names, new byte[ReplayWriter.SKIN_SIZE],
                                               ReplayWriter.CURRENT_LIMITS, ReplayWriter.CURRENT_FIX_FLAGS,
                                               new byte[ReplayWriter.CUSTOM_COLORS_SIZE], versionBlock, SPEED_OVERRIDE_INDEX).write();
        ReplayFile parsed = ReplayFile.parse(withOverride);

        Assertions.assertArrayEquals(plain, withoutOverride, "a game without an override writes the native sections only");
        Assertions.assertEquals(0, ReplayFile.parse(plain).getSpeedOverrideIndex());
        Assertions.assertEquals(SPEED_OVERRIDE_INDEX, parsed.getSpeedOverrideIndex());
        Assertions.assertEquals(ReplayFile.parse(plain).getGameVersion().getBuild(), parsed.getGameVersion().getBuild());
        Assertions.assertArrayEquals(Arrays.copyOf(withOverride, plain.length), plain, "the section follows the native sections");
    }
}
