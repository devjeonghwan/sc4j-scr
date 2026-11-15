package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Decompresses data compressed by the PKWare Compression Library (DCL).
 * <p>
 * This file is a Java port of {@code blast.c} version 1.3 by Mark Adler, which is
 * distributed under the zlib license. This port is an altered source version:
 * the callback-based I/O is replaced by byte array I/O, and the error handling is
 * replaced by exceptions. The original license text:
 * <p>
 * Copyright (C) 2003, 2012, 2013 Mark Adler.
 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the author be held liable for any damages arising from the use
 * of this software. Permission is granted to anyone to use this software for any
 * purpose, including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions: 1. The origin of this software
 * must not be misrepresented; you must not claim that you wrote the original
 * software. 2. Altered source versions must be plainly marked as such, and must
 * not be misrepresented as being the original software. 3. This notice may not be
 * removed or altered from any source distribution.
 */
public final class MpqExploderUtils {
    private static final int BYTE_MASK = 0xFF;
    /**
     * The longest code of the canonical Huffman tables.
     */
    private static final int MAXIMUM_CODE_BITS = 13;
    /**
     * The output past the capacity after which a repeating input stream is treated as endless.
     */
    private static final long REPEATING_INPUT_OVERRUN_LIMIT = 16L << 20;
    /**
     * The sliding window size.
     */
    private static final int WINDOW_SIZE = 4096;
    private static final int MINIMUM_DICTIONARY_BITS = 4;
    private static final int MAXIMUM_DICTIONARY_BITS = 6;
    /**
     * The length symbol that ends the stream.
     */
    private static final int END_LENGTH = 519;
    /**
     * A match of this length takes a two-bit distance suffix instead of the dictionary bits.
     */
    private static final int SHORT_MATCH_LENGTH = 2;
    private static final int SHORT_MATCH_DISTANCE_BITS = 2;
    /**
     * The largest symbol count of a compact code length table.
     */
    private static final int MAXIMUM_SYMBOLS = 256;
    /**
     * A compact length byte holds the repeat count minus one above the length in its low four bits.
     */
    private static final int REPEAT_SHIFT = 4;
    private static final int LENGTH_MASK = 0xF;

    private static final short @NotNull [] LITERAL_LENGTHS = {
            11, 124, 8, 7, 28, 7, 188, 13, 76, 4, 10, 8, 12, 10, 12, 10, 8, 23, 8,
            9, 7, 6, 7, 8, 7, 6, 55, 8, 23, 24, 12, 11, 7, 9, 11, 12, 6, 7, 22, 5,
            7, 24, 6, 11, 9, 6, 7, 22, 7, 11, 38, 7, 9, 8, 25, 11, 8, 11, 9, 12,
            8, 12, 5, 38, 5, 38, 5, 11, 7, 5, 6, 21, 6, 10, 53, 8, 7, 24, 10, 27,
            44, 253, 253, 253, 252, 252, 252, 13, 12, 45, 12, 45, 12, 61, 12, 45,
            44, 173
    };

    private static final short @NotNull [] LENGTH_LENGTHS = {2, 35, 36, 53, 38, 23};

    private static final short @NotNull [] DISTANCE_LENGTHS = {2, 20, 53, 230, 247, 151, 248};

    private static final short @NotNull [] LENGTH_BASE = {
            3, 2, 4, 5, 6, 7, 8, 9, 10, 12, 16, 24, 40, 72, 136, 264
    };

    private static final short @NotNull [] LENGTH_EXTRA = {
            0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8
    };

    private static final @NotNull Huffman LITERAL_CODE;
    private static final @NotNull Huffman LENGTH_CODE;
    private static final @NotNull Huffman DISTANCE_CODE;

    /**
     * Represents a canonical Huffman decoding table.
     */
    private static final class Huffman {
        private final short @NotNull [] count;
        private final short @NotNull [] symbol;

        /**
         * Creates a {@link Huffman} instance.
         *
         * @param count  the number of symbols of each code length
         * @param symbol the canonically ordered symbols
         */
        private Huffman(short @NotNull [] count, short @NotNull [] symbol) {
            this.count  = count;
            this.symbol = symbol;
        }
    }

    /**
     * Holds the bit-level input and the sliding window output state.
     */
    private static final class State {
        private final byte @NotNull [] input;
        private final int maximumSize;
        private final byte @Nullable [] output;
        private final int outputOffset;
        private final boolean repeatingInput;

        private int inputPosition;
        private int bitBuffer;
        private int bitCount;

        private final byte @NotNull [] window;
        private int next;
        private boolean first;

        private final @NotNull ByteArrayOutputStream result;
        private int outputSize;
        private long producedSize;

        /**
         * Creates a {@link State} instance at the start of a stream.
         *
         * @param input          the compressed data
         * @param maximumSize    the output capacity in bytes
         * @param output         the destination buffer, or {@code null} to collect the output in {@code result}
         * @param outputOffset   the first destination index
         * @param repeatingInput whether an exhausted input restarts instead of ending the stream
         */
        private State(byte @NotNull [] input, int maximumSize, byte @Nullable [] output, int outputOffset, boolean repeatingInput) {
            this.input          = input;
            this.maximumSize    = maximumSize;
            this.output         = output;
            this.outputOffset   = outputOffset;
            this.repeatingInput = repeatingInput;

            this.inputPosition = 0;
            this.bitBuffer     = 0;
            this.bitCount      = 0;

            this.window = new byte[WINDOW_SIZE];
            this.next   = 0;
            this.first  = true;

            this.result       = new ByteArrayOutputStream();
            this.outputSize   = 0;
            this.producedSize = 0;
        }
    }

    static {
        LITERAL_CODE  = createHuffmanTable(LITERAL_LENGTHS);
        LENGTH_CODE   = createHuffmanTable(LENGTH_LENGTHS);
        DISTANCE_CODE = createHuffmanTable(DISTANCE_LENGTHS);
    }

    /**
     * Decompresses the given PKWare DCL stream.
     *
     * @param compressedData   the compressed data, starting at the literal flag byte
     * @param decompressedSize the expected decompressed size
     * @return the decompressed content
     * @throws IOException if the stream is malformed or the size mismatches
     */
    public static byte @NotNull [] explode(byte @NotNull [] compressedData, int decompressedSize) throws IOException {
        byte[] result = explodeUpTo(compressedData, decompressedSize);

        if (result.length != decompressedSize) {
            throw Exceptions.newIOException("The exploded size mismatches: expected {0}, got {1}", decompressedSize, result.length);
        }

        return result;
    }

    /**
     * Decompresses a PKWare DCL stream while retaining its actual output size.
     *
     * @param compressedData the compressed data, starting at the literal flag byte
     * @param maximumSize    the output capacity in bytes
     * @return the decompressed content, which may be shorter than the capacity
     * @throws IOException if the stream is malformed or exceeds the capacity
     */
    public static byte @NotNull [] explodeUpTo(byte @NotNull [] compressedData, int maximumSize) throws IOException {
        if (maximumSize < 0) {
            throw Exceptions.newIOException("The exploded output capacity is negative.");
        }

        State state = new State(compressedData, maximumSize, null, 0, false);

        decompress(state);

        state.result.write(state.window, 0, state.next);

        return state.result.toByteArray();
    }

    /**
     * Decompresses a PKWare DCL stream into a caller buffer, discarding output beyond its capacity.
     * Partial output is stored before a stream failure is thrown. Repeating input restarts from its first byte;
     * a stream exceeding the capacity by 16 MiB fails as endless.
     *
     * @param compressedData the compressed data, starting at the literal flag byte
     * @param output         the destination buffer
     * @param outputOffset   the first destination index
     * @param capacity       the number of bytes the destination accepts
     * @param repeatingInput whether an exhausted input restarts instead of ending the stream
     * @return the number of bytes stored, at most {@code capacity}
     * @throws IOException if the stream is malformed or truncated; stored bytes remain in {@code output}
     */
    public static int explodeInto(byte @NotNull [] compressedData, byte @NotNull [] output, int outputOffset, int capacity, boolean repeatingInput) throws IOException {
        State state = new State(compressedData, capacity, output, outputOffset, repeatingInput);

        try {
            decompress(state);
        } finally {
            writeOutput(state, state.next);
        }

        return state.outputSize;
    }

    private static void decompress(@NotNull State state) throws IOException {
        int literalFlag = readBits(state, Byte.SIZE);

        if (literalFlag > 1) {
            throw Exceptions.newIOException("The literal flag is invalid: {0}", literalFlag);
        }

        int dictionaryBits = readBits(state, Byte.SIZE);

        if (dictionaryBits < MINIMUM_DICTIONARY_BITS || dictionaryBits > MAXIMUM_DICTIONARY_BITS) {
            throw Exceptions.newIOException("The dictionary size is invalid: {0}", dictionaryBits);
        }

        while (true) {
            if (readBits(state, 1) != 0) {
                // Length/distance pair.
                int symbol = decodeSymbol(state, LENGTH_CODE);
                int length = LENGTH_BASE[symbol] + readBits(state, LENGTH_EXTRA[symbol]);

                if (length == END_LENGTH) {
                    break;
                }

                if (state.output == null && length > state.maximumSize - state.result.size() - state.next) {
                    throw Exceptions.newIOException("The exploded data exceeds the output capacity.");
                }

                int distanceBits = length == SHORT_MATCH_LENGTH ? SHORT_MATCH_DISTANCE_BITS : dictionaryBits;
                int distance = (decodeSymbol(state, DISTANCE_CODE) << distanceBits) + readBits(state, distanceBits) + 1;

                if (state.first && distance > state.next) {
                    throw Exceptions.newIOException("The distance is too far back: {0}", distance);
                }

                // Copy length bytes from distance bytes back.
                do {
                    int from = state.next - distance;
                    int copy = WINDOW_SIZE;

                    if (state.next < distance) {
                        from += copy;
                        copy = distance;
                    }

                    copy -= state.next;

                    if (copy > length) {
                        copy = length;
                    }

                    length -= copy;

                    int to = state.next;

                    state.next += copy;

                    for (int copyIndex = 0; copyIndex < copy; copyIndex++) {
                        state.window[to + copyIndex] = state.window[from + copyIndex];
                    }

                    if (state.next == WINDOW_SIZE) {
                        writeOutput(state, WINDOW_SIZE);
                        state.next  = 0;
                        state.first = false;
                    }
                } while (length != 0);
            } else {
                // Literal byte.
                if (state.output == null && state.result.size() + state.next == state.maximumSize) {
                    throw Exceptions.newIOException("The exploded data exceeds the output capacity.");
                }

                int symbol = literalFlag != 0 ? decodeSymbol(state, LITERAL_CODE) : readBits(state, Byte.SIZE);

                state.window[state.next++] = (byte) symbol;

                if (state.next == WINDOW_SIZE) {
                    writeOutput(state, WINDOW_SIZE);
                    state.next  = 0;
                    state.first = false;
                }
            }
        }
    }

    private static void writeOutput(@NotNull State state, int count) throws IOException {
        byte[] output = state.output;

        if (output == null) {
            state.result.write(state.window, 0, count);

            return;
        }

        int storedCount = Math.min(count, state.maximumSize - state.outputSize);

        System.arraycopy(state.window, 0, output, state.outputOffset + state.outputSize, storedCount);
        state.outputSize += storedCount;
        state.producedSize += count;

        if (state.repeatingInput && state.producedSize - state.maximumSize > REPEATING_INPUT_OVERRUN_LIMIT) {
            throw Exceptions.newIOException("The repeating explode input produces no end code.");
        }
    }

    private static boolean prepareInputByte(@NotNull State state) {
        if (state.inputPosition < state.input.length) {
            return true;
        }

        if (state.repeatingInput && state.input.length > 0) {
            state.inputPosition = 0;

            return true;
        }

        return false;
    }

    private static int readBits(@NotNull State state, int need) throws IOException {
        int value = state.bitBuffer;

        while (state.bitCount < need) {
            if (!prepareInputByte(state)) {
                throw Exceptions.newIOException("The explode stream is truncated.");
            }

            value |= (state.input[state.inputPosition++] & BYTE_MASK) << state.bitCount;
            state.bitCount += Byte.SIZE;
        }

        state.bitBuffer = value >>> need;
        state.bitCount -= need;

        return value & ((1 << need) - 1);
    }

    private static int decodeSymbol(@NotNull State state, @NotNull Huffman huffman) throws IOException {
        int bitBuffer = state.bitBuffer;
        int left = state.bitCount;
        int code = 0;
        int first = 0;
        int index = 0;
        int length = 1;
        int countIndex = 1;

        while (true) {
            while (left-- > 0) {
                code |= (bitBuffer & 1) ^ 1;
                bitBuffer >>>= 1;

                int count = huffman.count[countIndex++];

                if (code < first + count) {
                    state.bitBuffer = bitBuffer;
                    state.bitCount  = (state.bitCount - length) & (Byte.SIZE - 1);

                    return huffman.symbol[index + (code - first)];
                }

                index += count;
                first += count;
                first <<= 1;
                code <<= 1;
                length++;
            }

            left = (MAXIMUM_CODE_BITS + 1) - length;

            if (left == 0) {
                throw Exceptions.newIOException("The explode stream holds an invalid code.");
            }

            if (!prepareInputByte(state)) {
                throw Exceptions.newIOException("The explode stream is truncated.");
            }

            bitBuffer = state.input[state.inputPosition++] & BYTE_MASK;

            if (left > Byte.SIZE) {
                left = Byte.SIZE;
            }
        }
    }

    private static @NotNull Huffman createHuffmanTable(short @NotNull [] encodedLengths) {
        short[] lengths = new short[MAXIMUM_SYMBOLS];
        int symbol = 0;
        int encodedIndex = 0;
        int remaining = encodedLengths.length;

        do {
            int length = encodedLengths[encodedIndex++] & BYTE_MASK;
            int left = (length >> REPEAT_SHIFT) + 1;

            length &= LENGTH_MASK;

            do {
                lengths[symbol++] = (short) length;
            } while (--left > 0);
        } while (--remaining > 0);

        int symbolCount = symbol;
        short[] count = new short[MAXIMUM_CODE_BITS + 1];

        for (int symbolIndex = 0; symbolIndex < symbolCount; symbolIndex++) {
            count[lengths[symbolIndex]]++;
        }

        int left = 1;

        for (int length = 1; length <= MAXIMUM_CODE_BITS; length++) {
            left <<= 1;
            left -= count[length];

            if (left < 0) {
                throw Exceptions.newIllegalArgumentException("The code lengths are over-subscribed.");
            }
        }

        short[] offsets = new short[MAXIMUM_CODE_BITS + 1];

        for (int length = 1; length < MAXIMUM_CODE_BITS; length++) {
            offsets[length + 1] = (short) (offsets[length] + count[length]);
        }

        short[] symbolTable = new short[symbolCount];

        for (int symbolIndex = 0; symbolIndex < symbolCount; symbolIndex++) {
            if (lengths[symbolIndex] != 0) {
                symbolTable[offsets[lengths[symbolIndex]]++] = (short) symbolIndex;
            }
        }

        return new Huffman(count, symbolTable);
    }


    private MpqExploderUtils() {
    }
}
