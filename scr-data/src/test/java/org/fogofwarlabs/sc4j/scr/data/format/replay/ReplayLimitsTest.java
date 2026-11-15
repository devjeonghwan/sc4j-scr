package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Tests synthetic {@code seRS} extensions against the bounded original {@code LMTS} reader contract.
 */
final class ReplayLimitsTest {
    private static final int @NotNull [] LOWER_VALUES = {5000, 2500, 500, 1700, 100, 2000, 500};
    private static final int @NotNull [] UPPER_VALUES = {10000, 5000, 1000, 3400, 400, 4000, 1000};
    /**
     * The index of the unit capacity in the limit values.
     */
    private static final int UNIT_CAPACITY_INDEX = 3;

    private static final @NotNull String LIMITS_TAG = "LMTS";
    /**
     * The decoded size of the limit section: seven DWORDs.
     */
    private static final int LIMIT_SECTION_SIZE = 28;
    /**
     * The bytes of the first, zlib chunk of the mixed-codec section; the PKWARE chunk holds the rest.
     */
    private static final int FIRST_CHUNK_SIZE = 15;

    private static byte @NotNull [] encodeValues(int @NotNull [] values) {
        ByteBuffer result = ByteBuffer.allocate(values.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int value : values) {
            result.putInt(value);
        }

        return result.array();
    }

    private static byte @NotNull [] encodeLimits(byte @NotNull [] decodedData, byte @NotNull [] @NotNull ... chunks) throws IOException {
        return ExtendedReplayScenario.encodeExtension(LIMITS_TAG, ExtendedReplayScenario.encodeSection(decodedData, chunks));
    }


    @Test
    void get_limits_should_keep_replay_defaults_without_an_extension() throws IOException {
        ReplayLimits limits = ReplayFile.parse(ExtendedReplayScenario.createReplay(new byte[0], false)).getLimits();

        Assertions.assertFalse(limits.hasExplicitSection());
        Assertions.assertFalse(limits.isFallback());
        Assertions.assertEquals(1700, limits.getUnitCapacity());
        Assertions.assertArrayEquals(LOWER_VALUES, limits.getRequestedValues());
        Assertions.assertArrayEquals(LOWER_VALUES, limits.getEffectiveValues());
    }

    @Test
    void get_limits_should_seek_absolute_extension_offset_and_skip_unknown_chunks() throws IOException {
        byte[] requestedData = encodeValues(UPPER_VALUES);
        ByteArrayOutputStream extensions = new ByteArrayOutputStream();

        extensions.write(ExtendedReplayScenario.encodeExtension("TEST", new byte[]{1, 2, 3, 4, 5}));
        extensions.write(encodeLimits(requestedData, requestedData));
        extensions.write(ExtendedReplayScenario.encodeExtension("TAIL", new byte[]{9}));

        ReplayLimits limits = ReplayFile.parse(ExtendedReplayScenario.createReplay(extensions.toByteArray(), true)).getLimits();

        Assertions.assertTrue(limits.hasExplicitSection());
        Assertions.assertFalse(limits.isFallback());
        Assertions.assertEquals(3400, limits.getUnitCapacity());
        Assertions.assertArrayEquals(UPPER_VALUES, limits.getEffectiveValues());
    }

    @Test
    void get_limits_should_decode_raw_and_zlib_tuples_without_masking_values() throws IOException {
        int[] requestedValues = {8192, 4096, 512, 2048, 256, 2048, 512};
        byte[] requestedData = encodeValues(requestedValues);

        for (byte[] chunk : new byte[][]{requestedData, ExtendedReplayScenario.compress(requestedData)}) {
            byte[] extensions = encodeLimits(requestedData, chunk);
            ReplayLimits limits = ReplayFile.parse(ExtendedReplayScenario.createReplay(extensions, false)).getLimits();

            Assertions.assertTrue(limits.hasExplicitSection());
            Assertions.assertFalse(limits.isFallback());
            Assertions.assertEquals(2048, limits.getUnitCapacity());
            Assertions.assertArrayEquals(requestedValues, limits.getRequestedValues());
            Assertions.assertArrayEquals(requestedValues, limits.getEffectiveValues());
        }
    }

    @Test
    void get_limits_should_replace_whole_tuple_for_any_invalid_signed_field() throws IOException {
        for (int valueIndex = 0; valueIndex < LOWER_VALUES.length; valueIndex++) {
            for (int invalidValue : new int[]{LOWER_VALUES[valueIndex] - 1, UPPER_VALUES[valueIndex] + 1, Integer.MIN_VALUE, -1}) {
                int[] requestedValues = LOWER_VALUES.clone();

                requestedValues[valueIndex] = invalidValue;

                byte[] requestedData = encodeValues(requestedValues);
                byte[] extensions = encodeLimits(requestedData, requestedData);
                ReplayLimits limits = ReplayFile.parse(ExtendedReplayScenario.createReplay(extensions, false)).getLimits();

                Assertions.assertTrue(limits.isFallback());
                Assertions.assertEquals(3400, limits.getUnitCapacity());
                Assertions.assertArrayEquals(requestedValues, limits.getRequestedValues());
                Assertions.assertArrayEquals(UPPER_VALUES, limits.getEffectiveValues());
            }
        }
    }

    @Test
    void get_limits_should_keep_last_decoded_tuple_and_return_independent_snapshots() throws IOException {
        byte[] lowerData = encodeValues(LOWER_VALUES);
        byte[] invalidData = new byte[LIMIT_SECTION_SIZE];
        ByteArrayOutputStream extensions = new ByteArrayOutputStream();

        extensions.write(encodeLimits(invalidData, ExtendedReplayScenario.compress(invalidData)));
        extensions.write(encodeLimits(lowerData, lowerData));

        ReplayLimits limits = ReplayFile.parse(ExtendedReplayScenario.createReplay(extensions.toByteArray(), false)).getLimits();

        limits.getRequestedValues()[UNIT_CAPACITY_INDEX] = -1;
        limits.getEffectiveValues()[UNIT_CAPACITY_INDEX] = -1;

        Assertions.assertFalse(limits.isFallback());
        Assertions.assertEquals(1700, limits.getUnitCapacity());
        Assertions.assertArrayEquals(LOWER_VALUES, limits.getRequestedValues());
    }

    @Test
    void get_limits_should_join_zlib_and_dcl_chunks_by_actual_decoded_sizes() throws IOException {
        byte[] secondPart = "AIAIAIAIAIAIA".getBytes(StandardCharsets.US_ASCII);
        byte[] requestedData = new byte[LIMIT_SECTION_SIZE];

        System.arraycopy(secondPart, 0, requestedData, FIRST_CHUNK_SIZE, secondPart.length);

        byte[] dclChunk = {0x00, 0x04, (byte) 0x82, 0x24, 0x25, (byte) 0x8F, (byte) 0x80, 0x7F};
        byte[] extensions = encodeLimits(requestedData, ExtendedReplayScenario.compress(new byte[FIRST_CHUNK_SIZE]), dclChunk);
        ReplayLimits limits = ReplayFile.parse(ExtendedReplayScenario.createReplay(extensions, false)).getLimits();

        Assertions.assertTrue(limits.hasExplicitSection());
        Assertions.assertTrue(limits.isFallback());
        Assertions.assertArrayEquals(requestedData, encodeValues(limits.getRequestedValues()));
    }

    @Test
    void parse_should_reject_bad_checksums_and_incorrect_decoded_sizes() throws IOException {
        byte[] requestedData = encodeValues(LOWER_VALUES);
        byte[] section = ExtendedReplayScenario.encodeSection(requestedData, requestedData);

        section[0] ^= 1;

        byte[] badChecksum = ExtendedReplayScenario.createReplay(ExtendedReplayScenario.encodeExtension(LIMITS_TAG, section), false);

        Assertions.assertEquals("The replay section checksum does not match.",
                                Assertions.assertThrows(IOException.class, () -> ReplayFile.parse(badChecksum)).getMessage());

        byte[] shortData = new byte[LIMIT_SECTION_SIZE - 1];
        byte[] shortReplay = ExtendedReplayScenario.createReplay(encodeLimits(shortData, ExtendedReplayScenario.compress(shortData)), false);

        Assertions.assertEquals("The replay limit section must decode to 28 bytes.",
                                Assertions.assertThrows(IOException.class, () -> ReplayFile.parse(shortReplay)).getMessage());

        byte[] longData = new byte[LIMIT_SECTION_SIZE + 1];
        byte[] longReplay = ExtendedReplayScenario.createReplay(encodeLimits(longData, ExtendedReplayScenario.compress(longData)), false);

        Assertions.assertThrows(IOException.class, () -> ReplayFile.parse(longReplay));
    }

    @Test
    void parse_should_check_encoded_length_before_decompression_and_treat_equal_length_as_raw() throws IOException {
        byte[] requestedData = encodeValues(LOWER_VALUES);
        byte[] compressedData = ExtendedReplayScenario.compress(requestedData);

        Assertions.assertTrue(compressedData.length > requestedData.length);

        byte[] oversizedReplay = ExtendedReplayScenario.createReplay(encodeLimits(requestedData, compressedData), false);

        Assertions.assertEquals("The replay limit chunk exceeds the remaining output capacity.",
                                Assertions.assertThrows(IOException.class, () -> ReplayFile.parse(oversizedReplay)).getMessage());

        byte[] rawData = Arrays.copyOf(ExtendedReplayScenario.compress(new byte[LIMIT_SECTION_SIZE]), LIMIT_SECTION_SIZE);
        byte[] rawReplay = ExtendedReplayScenario.createReplay(encodeLimits(rawData, rawData), false);

        Assertions.assertArrayEquals(rawData, encodeValues(ReplayFile.parse(rawReplay).getLimits().getRequestedValues()));
    }

    @Test
    void parse_should_reject_invalid_offsets_and_truncated_extension_lengths() throws IOException {
        byte[] emptyReplay = ExtendedReplayScenario.createReplay(new byte[0], false);

        for (int extensionOffset : new int[]{1, 19, emptyReplay.length + 1, -1}) {
            byte[] invalidReplay = emptyReplay.clone();

            ByteBuffer.wrap(invalidReplay).order(ByteOrder.LITTLE_ENDIAN).putInt(ExtendedReplayScenario.EXTENSION_OFFSET_POSITION, extensionOffset);

            Assertions.assertThrows(IOException.class, () -> ReplayFile.parse(invalidReplay));
        }

        byte[] truncatedExtension = ExtendedReplayScenario.encodeExtension(LIMITS_TAG, new byte[]{1});

        ByteBuffer.wrap(truncatedExtension).order(ByteOrder.LITTLE_ENDIAN).putInt(ExtendedReplayScenario.EXTENSION_SIZE_POSITION, Integer.MAX_VALUE);

        byte[] truncatedReplay = ExtendedReplayScenario.createReplay(truncatedExtension, false);

        Assertions.assertThrows(IOException.class, () -> ReplayFile.parse(truncatedReplay));
    }

    @Test
    void parse_should_accept_trailing_fragments_and_huge_unknown_extensions_but_not_negative_length() throws IOException {
        byte[] incompleteHeader = ExtendedReplayScenario.createReplay(new byte[]{1, 2, 3}, false);

        Assertions.assertFalse(ReplayFile.parse(incompleteHeader).getLimits().hasExplicitSection());

        byte[] unknownExtension = ExtendedReplayScenario.encodeExtension("TEST", new byte[]{1});

        ByteBuffer.wrap(unknownExtension).order(ByteOrder.LITTLE_ENDIAN).putInt(ExtendedReplayScenario.EXTENSION_SIZE_POSITION, Integer.MAX_VALUE);

        byte[] unknownReplay = ExtendedReplayScenario.createReplay(unknownExtension, false);

        Assertions.assertEquals(1700, ReplayFile.parse(unknownReplay).getLimits().getUnitCapacity());

        ByteBuffer.wrap(unknownExtension).order(ByteOrder.LITTLE_ENDIAN).putInt(ExtendedReplayScenario.EXTENSION_SIZE_POSITION, -1);

        byte[] negativeLengthReplay = ExtendedReplayScenario.createReplay(unknownExtension, false);

        Assertions.assertThrows(IOException.class, () -> ReplayFile.parse(negativeLengthReplay));
    }

    @Test
    void get_limits_should_ignore_trailing_limit_bytes_when_offset_is_zero() throws IOException {
        byte[] requestedData = encodeValues(UPPER_VALUES);
        byte[] replayData = ExtendedReplayScenario.createReplay(encodeLimits(requestedData, requestedData), true);

        ByteBuffer.wrap(replayData).order(ByteOrder.LITTLE_ENDIAN).putInt(ExtendedReplayScenario.EXTENSION_OFFSET_POSITION, 0);

        ReplayLimits limits = ReplayFile.parse(replayData).getLimits();

        Assertions.assertFalse(limits.hasExplicitSection());
        Assertions.assertEquals(1700, limits.getUnitCapacity());
    }
}
