package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Decompresses the first MPQ bzip2 stream with block and combined CRC validation.
 * Malformed, truncated or capacity-exceeding streams fail.
 */
final class MpqBzip2 {
    private static final int BYTE_MASK = 0xFF;
    private static final int BLOCK_MAGIC_HIGH = 0x314159;
    private static final int BLOCK_MAGIC_LOW = 0x265359;
    private static final int END_MAGIC_HIGH = 0x177245;
    private static final int END_MAGIC_LOW = 0x385090;
    /**
     * The bits of each half of a block or end magic.
     */
    private static final int MAGIC_HALF_BITS = 24;
    private static final int MAXIMUM_BLOCK_SIZE_DIGIT = 9;
    private static final int BLOCK_UNIT = 100000;
    private static final int ORIGIN_POINTER_BITS = 24;
    /**
     * The permitted origin-pointer slack above the block limit before decoding.
     */
    private static final int ORIGIN_POINTER_SLACK = 10;

    private static final int BYTE_VALUES = 256;
    /**
     * The symbol map: sixteen groups of sixteen byte values, each group present when its bit is set.
     */
    private static final int MAPPING_GROUPS = 16;
    private static final int MAPPING_GROUP_SIZE = 16;
    private static final int MAPPING_TOP_BIT = 0x8000;
    /**
     * The alphabet adds two symbols to the used bytes: the second run symbol and the end of block.
     */
    private static final int ALPHABET_EXTRA_SYMBOLS = 2;
    private static final int GROUP_COUNT_BITS = 3;
    private static final int MAXIMUM_GROUPS = 6;
    private static final int MINIMUM_GROUPS = 2;
    private static final int SELECTOR_COUNT_BITS = 15;
    private static final int MAXIMUM_SELECTORS = 18002;
    private static final int GROUP_SIZE = 50;
    private static final int MAXIMUM_ALPHABET_SIZE = 258;
    private static final int CODE_LENGTH_BITS = 5;
    private static final int MAXIMUM_CODE_LENGTH = 20;
    /**
     * The initial minimum code length, above every valid length.
     */
    private static final int INITIAL_MINIMUM_CODE_LENGTH = 32;
    private static final int CODE_LENGTH_TABLE_SIZE = 23;
    private static final int RUN_A = 0;
    private static final int RUN_B = 1;
    private static final int RUN_B_WEIGHT = 2;
    private static final int MAXIMUM_RUN_WEIGHT = 2 * 1024 * 1024;

    /**
     * The run lengths of the initial run-length stage: four equal bytes are followed by a count of extra repeats.
     */
    private static final int RUN_OF_TWO = 2;
    private static final int RUN_OF_THREE = 3;
    private static final int RUN_OF_FOUR = 4;

    private static final int CRC_INITIAL = 0xFFFFFFFF;
    private static final int CRC_TOP_BIT = 0x80000000;
    private static final int CRC_POLYNOMIAL = 0x04C11DB7;
    private static final int CRC_TOP_BYTE_SHIFT = 24;

    /**
     * The shift of the successor position that each block entry keeps above its byte value.
     */
    private static final int SUCCESSOR_SHIFT = 8;

    private static final int @NotNull [] RANDOM_NUMBERS = {
            619, 720, 127, 481, 931, 816, 813, 233, 566, 247, 985, 724, 205, 454, 863, 491, 741, 242, 949, 214, 733, 859, 335, 708,
            621, 574, 73, 654, 730, 472, 419, 436, 278, 496, 867, 210, 399, 680, 480, 51, 878, 465, 811, 169, 869, 675, 611, 697,
            867, 561, 862, 687, 507, 283, 482, 129, 807, 591, 733, 623, 150, 238, 59, 379, 684, 877, 625, 169, 643, 105, 170, 607,
            520, 932, 727, 476, 693, 425, 174, 647, 73, 122, 335, 530, 442, 853, 695, 249, 445, 515, 909, 545, 703, 919, 874, 474,
            882, 500, 594, 612, 641, 801, 220, 162, 819, 984, 589, 513, 495, 799, 161, 604, 958, 533, 221, 400, 386, 867, 600, 782,
            382, 596, 414, 171, 516, 375, 682, 485, 911, 276, 98, 553, 163, 354, 666, 933, 424, 341, 533, 870, 227, 730, 475, 186,
            263, 647, 537, 686, 600, 224, 469, 68, 770, 919, 190, 373, 294, 822, 808, 206, 184, 943, 795, 384, 383, 461, 404, 758,
            839, 887, 715, 67, 618, 276, 204, 918, 873, 777, 604, 560, 951, 160, 578, 722, 79, 804, 96, 409, 713, 940, 652, 934,
            970, 447, 318, 353, 859, 672, 112, 785, 645, 863, 803, 350, 139, 93, 354, 99, 820, 908, 609, 772, 154, 274, 580, 184,
            79, 626, 630, 742, 653, 282, 762, 623, 680, 81, 927, 626, 789, 125, 411, 521, 938, 300, 821, 78, 343, 175, 128, 250,
            170, 774, 972, 275, 999, 639, 495, 78, 352, 126, 857, 956, 358, 619, 580, 124, 737, 594, 701, 612, 669, 112, 134, 694,
            363, 992, 809, 743, 168, 974, 944, 375, 748, 52, 600, 747, 642, 182, 862, 81, 344, 805, 988, 739, 511, 655, 814, 334,
            249, 515, 897, 955, 664, 981, 649, 113, 974, 459, 893, 228, 433, 837, 553, 268, 926, 240, 102, 654, 459, 51, 686, 754,
            806, 760, 493, 403, 415, 394, 687, 700, 946, 670, 656, 610, 738, 392, 760, 799, 887, 653, 978, 321, 576, 617, 626, 502,
            894, 679, 243, 440, 680, 879, 194, 572, 640, 724, 926, 56, 204, 700, 707, 151, 457, 449, 797, 195, 791, 558, 945, 679,
            297, 59, 87, 824, 713, 663, 412, 693, 342, 606, 134, 108, 571, 364, 631, 212, 174, 643, 304, 329, 343, 97, 430, 751,
            497, 314, 983, 374, 822, 928, 140, 206, 73, 263, 980, 736, 876, 478, 430, 305, 170, 514, 364, 692, 829, 82, 855, 953,
            676, 246, 369, 970, 294, 750, 807, 827, 150, 790, 288, 923, 804, 378, 215, 828, 592, 281, 565, 555, 710, 82, 896, 831,
            547, 261, 524, 462, 293, 465, 502, 56, 661, 821, 976, 991, 658, 869, 905, 758, 745, 193, 768, 550, 608, 933, 378, 286,
            215, 979, 792, 961, 61, 688, 793, 644, 986, 403, 106, 366, 905, 644, 372, 567, 466, 434, 645, 210, 389, 550, 919, 135,
            780, 773, 635, 389, 707, 100, 626, 958, 165, 504, 920, 176, 193, 713, 857, 265, 203, 50, 668, 108, 645, 990, 626, 197,
            510, 357, 358, 850, 858, 364, 936, 638
    };

    private static final int @NotNull [] CRC_TABLE = createCrcTable();

    private static int @NotNull [] createCrcTable() {
        int[] table = new int[BYTE_VALUES];

        for (int index = 0; index < BYTE_VALUES; index++) {
            int crc = index << CRC_TOP_BYTE_SHIFT;

            for (int bit = 0; bit < Byte.SIZE; bit++) {
                crc = (crc & CRC_TOP_BIT) != 0 ? (crc << 1) ^ CRC_POLYNOMIAL : crc << 1;
            }

            table[index] = crc;
        }

        return table;
    }

    /**
     * Decompresses the first bzip2 stream of the input.
     *
     * @param input    the compressed data after the method mask byte
     * @param capacity the output capacity
     * @return the decompressed bytes, or {@code null} if the stream is malformed, truncated or exceeds the capacity
     */
    static byte @Nullable [] decompress(byte @NotNull [] input, int capacity) {
        try {
            return new MpqBzip2(input, capacity).decodeStream();
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private static int initializeDecodeTables(int @NotNull [] lengths,
                                              int minimumLength, int maximumLength,
                                              int @NotNull [] limit,
                                              int @NotNull [] base,
                                              int @NotNull [] permutation) {
        int permutationCount = 0;

        for (int length = minimumLength; length <= maximumLength; length++) {
            for (int symbol = 0; symbol < lengths.length; symbol++) {
                if (lengths[symbol] == length) {
                    permutation[permutationCount++] = symbol;
                }
            }
        }

        for (int length : lengths) {
            base[length + 1]++;
        }

        for (int index = 1; index < CODE_LENGTH_TABLE_SIZE; index++) {
            base[index] += base[index - 1];
        }

        int vector = 0;

        for (int length = minimumLength; length <= maximumLength; length++) {
            vector += base[length + 1] - base[length];
            limit[length] = vector - 1;
            vector <<= 1;
        }

        for (int length = minimumLength + 1; length <= maximumLength; length++) {
            base[length] = ((limit[length - 1] + 1) << 1) - base[length];
        }

        return permutationCount;
    }


    private final byte @NotNull [] input;
    private int inputPosition;
    private long bitBuffer;
    private int bitCount;

    private final byte @NotNull [] output;
    private int outputSize;

    /**
     * Creates a {@link MpqBzip2} instance.
     *
     * @param input    the compressed data after the method mask byte
     * @param capacity the output capacity
     */
    private MpqBzip2(byte @NotNull [] input, int capacity) {
        this.input         = input;
        this.inputPosition = 0;
        this.bitBuffer     = 0;
        this.bitCount      = 0;

        this.output     = new byte[capacity];
        this.outputSize = 0;
    }

    private byte @NotNull [] decodeStream() {
        if (this.readBits(Byte.SIZE) != 'B' || this.readBits(Byte.SIZE) != 'Z' || this.readBits(Byte.SIZE) != 'h') {
            throw Exceptions.newIllegalStateException("bzip2 magic");
        }

        int blockSizeDigit = this.readBits(Byte.SIZE) - '0';

        if (blockSizeDigit < 1 || blockSizeDigit > MAXIMUM_BLOCK_SIZE_DIGIT) {
            throw Exceptions.newIllegalStateException("bzip2 block size");
        }

        int blockLimit = blockSizeDigit * BLOCK_UNIT;
        int combinedCrc = 0;

        while (true) {
            int magicHigh = this.readBits(MAGIC_HALF_BITS);
            int magicLow = this.readBits(MAGIC_HALF_BITS);
            int storedCrc = this.readBits(Integer.SIZE);

            if (magicHigh == END_MAGIC_HIGH && magicLow == END_MAGIC_LOW) {
                if (storedCrc != combinedCrc) {
                    throw Exceptions.newIllegalStateException("bzip2 combined CRC");
                }

                return Arrays.copyOf(this.output, this.outputSize);
            }

            if (magicHigh != BLOCK_MAGIC_HIGH || magicLow != BLOCK_MAGIC_LOW) {
                throw Exceptions.newIllegalStateException("bzip2 block magic");
            }

            int blockCrc = this.decodeBlock(blockLimit);

            if (blockCrc != storedCrc) {
                throw Exceptions.newIllegalStateException("bzip2 block CRC");
            }

            combinedCrc = Integer.rotateLeft(combinedCrc, 1) ^ blockCrc;
        }
    }

    private int decodeBlock(int blockLimit) {
        boolean randomized = this.readBits(1) != 0;
        int originPointer = this.readBits(ORIGIN_POINTER_BITS);

        if (originPointer > ORIGIN_POINTER_SLACK + blockLimit) {
            throw Exceptions.newIllegalStateException("bzip2 origin pointer");
        }

        boolean[] used = new boolean[BYTE_VALUES];
        int usedGroups = this.readBits(Short.SIZE);

        for (int group = 0; group < MAPPING_GROUPS; group++) {
            if ((usedGroups & (MAPPING_TOP_BIT >>> group)) != 0) {
                int groupBits = this.readBits(Short.SIZE);

                for (int bit = 0; bit < MAPPING_GROUP_SIZE; bit++) {
                    used[group * MAPPING_GROUP_SIZE + bit] = (groupBits & (MAPPING_TOP_BIT >>> bit)) != 0;
                }
            }
        }

        int[] sequenceToSymbol = new int[BYTE_VALUES];
        int usedCount = 0;

        for (int symbol = 0; symbol < BYTE_VALUES; symbol++) {
            if (used[symbol]) {
                sequenceToSymbol[usedCount++] = symbol;
            }
        }

        if (usedCount == 0) {
            throw Exceptions.newIllegalStateException("bzip2 empty mapping");
        }

        int alphabetSize = usedCount + ALPHABET_EXTRA_SYMBOLS;
        int groupCount = this.readBits(GROUP_COUNT_BITS);

        if (groupCount < MINIMUM_GROUPS || groupCount > MAXIMUM_GROUPS) {
            throw Exceptions.newIllegalStateException("bzip2 group count");
        }

        int selectorCount = this.readBits(SELECTOR_COUNT_BITS);

        if (selectorCount < 1 || selectorCount > MAXIMUM_SELECTORS) {
            throw Exceptions.newIllegalStateException("bzip2 selector count");
        }

        int[] selectorMoveToFrontIndices = new int[selectorCount];

        for (int selector = 0; selector < selectorCount; selector++) {
            int index = 0;

            while (this.readBits(1) != 0) {
                if (++index >= groupCount) {
                    throw Exceptions.newIllegalStateException("bzip2 selector");
                }
            }

            selectorMoveToFrontIndices[selector] = index;
        }

        int[] groupOrder = new int[MAXIMUM_GROUPS];

        for (int group = 0; group < groupCount; group++) {
            groupOrder[group] = group;
        }

        int[] selectors = new int[selectorCount];

        for (int selector = 0; selector < selectorCount; selector++) {
            int index = selectorMoveToFrontIndices[selector];
            int group = groupOrder[index];

            System.arraycopy(groupOrder, 0, groupOrder, 1, index);
            groupOrder[0]       = group;
            selectors[selector] = group;
        }

        int[][] limits = new int[groupCount][CODE_LENGTH_TABLE_SIZE];
        int[][] bases = new int[groupCount][CODE_LENGTH_TABLE_SIZE];
        int[][] permutations = new int[groupCount][MAXIMUM_ALPHABET_SIZE];
        int[] permutationCounts = new int[groupCount];
        int[] minimumLengths = new int[groupCount];

        for (int group = 0; group < groupCount; group++) {
            int[] lengths = new int[alphabetSize];
            int current = this.readBits(CODE_LENGTH_BITS);

            for (int symbol = 0; symbol < alphabetSize; symbol++) {
                while (true) {
                    if (current < 1 || current > MAXIMUM_CODE_LENGTH) {
                        throw Exceptions.newIllegalStateException("bzip2 code length");
                    }

                    if (this.readBits(1) == 0) {
                        break;
                    }

                    current += this.readBits(1) == 0 ? 1 : -1;
                }

                lengths[symbol] = current;
            }

            int minimumLength = INITIAL_MINIMUM_CODE_LENGTH;
            int maximumLength = 0;

            for (int length : lengths) {
                minimumLength = Math.min(minimumLength, length);
                maximumLength = Math.max(maximumLength, length);
            }

            minimumLengths[group]    = minimumLength;
            permutationCounts[group] = initializeDecodeTables(lengths, minimumLength, maximumLength,
                                                              limits[group], bases[group], permutations[group]);
        }

        // Entropy, run-length and move-to-front decoding into the block.
        int[] block = new int[blockLimit];
        int[] symbolCounts = new int[BYTE_VALUES];
        int[] frontOrder = new int[BYTE_VALUES];

        for (int index = 0; index < BYTE_VALUES; index++) {
            frontOrder[index] = index;
        }

        int endOfBlock = usedCount + 1;
        int blockSize = 0;
        int groupIndex = -1;
        int groupPosition = 0;
        int[] selection = {groupIndex, groupPosition};
        int symbol = this.decodeSymbol(selection, selectors, limits, bases, permutations, permutationCounts, minimumLengths);

        while (symbol != endOfBlock) {
            if (symbol == RUN_A || symbol == RUN_B) {
                int runLength = -1;
                int weight = 1;

                do {
                    if (weight >= MAXIMUM_RUN_WEIGHT) {
                        throw Exceptions.newIllegalStateException("bzip2 run");
                    }

                    runLength += (symbol == RUN_A ? 1 : RUN_B_WEIGHT) * weight;
                    weight <<= 1;
                    symbol = this.decodeSymbol(selection, selectors, limits, bases, permutations, permutationCounts, minimumLengths);
                } while (symbol == RUN_A || symbol == RUN_B);

                runLength++;

                int value = sequenceToSymbol[frontOrder[0]];

                symbolCounts[value] += runLength;

                while (runLength-- > 0) {
                    if (blockSize >= blockLimit) {
                        throw Exceptions.newIllegalStateException("bzip2 block overflow");
                    }

                    block[blockSize++] = value;
                }

                continue;
            }

            if (blockSize >= blockLimit) {
                throw Exceptions.newIllegalStateException("bzip2 block overflow");
            }

            int frontIndex = symbol - 1;
            int sequence = frontOrder[frontIndex];

            System.arraycopy(frontOrder, 0, frontOrder, 1, frontIndex);
            frontOrder[0] = sequence;

            int value = sequenceToSymbol[sequence];

            symbolCounts[value]++;
            block[blockSize++] = value;

            symbol = this.decodeSymbol(selection, selectors, limits, bases, permutations, permutationCounts, minimumLengths);
        }

        if (originPointer >= blockSize) {
            throw Exceptions.newIllegalStateException("bzip2 origin pointer");
        }

        // Inverse Burrows-Wheeler transform: each block entry keeps its successor position above its byte value.
        int[] cumulative = new int[BYTE_VALUES + 1];

        for (int value = 0; value < BYTE_VALUES; value++) {
            cumulative[value + 1] = cumulative[value] + symbolCounts[value];
        }

        for (int index = 0; index < blockSize; index++) {
            int value = block[index] & BYTE_MASK;

            block[cumulative[value]++] |= index << SUCCESSOR_SHIFT;
        }

        return this.writeBlock(block, blockSize, block[originPointer] >>> SUCCESSOR_SHIFT, randomized, blockLimit);
    }

    private int writeBlock(int @NotNull [] block, int blockSize, int startPosition, boolean randomized, int blockLimit) {
        int crc = CRC_INITIAL;
        int[] position = {startPosition};
        int[] random = {0, 0};
        int fetched = 0;
        int current = this.fetch(block, position, randomized, random, blockLimit);

        fetched++;

        // The fetched count includes every read symbol, also the one past the last run.
        while (true) {
            if (fetched == blockSize + 1) {
                break;
            }

            if (fetched > blockSize + 1) {
                throw Exceptions.newIllegalStateException("bzip2 run at block end");
            }

            int runLength = 1;
            int runValue = current;
            int next = this.fetch(block, position, randomized, random, blockLimit);

            fetched++;

            if (fetched != blockSize + 1 && next == current) {
                runLength = RUN_OF_TWO;
                next      = this.fetch(block, position, randomized, random, blockLimit);
                fetched++;

                if (fetched != blockSize + 1 && next == current) {
                    runLength = RUN_OF_THREE;
                    next      = this.fetch(block, position, randomized, random, blockLimit);
                    fetched++;

                    if (fetched != blockSize + 1 && next == current) {
                        runLength = this.fetch(block, position, randomized, random, blockLimit) + RUN_OF_FOUR;
                        fetched++;
                        next = this.fetch(block, position, randomized, random, blockLimit);
                        fetched++;
                    }
                }
            }

            current = next;

            for (int index = 0; index < runLength; index++) {
                if (this.outputSize == this.output.length) {
                    throw Exceptions.newIllegalStateException("bzip2 output full");
                }

                this.output[this.outputSize++] = (byte) runValue;
                crc                            = crc << Byte.SIZE ^ CRC_TABLE[(crc >>> CRC_TOP_BYTE_SHIFT ^ runValue) & BYTE_MASK];
            }
        }

        return ~crc;
    }

    private int fetch(int @NotNull [] block, int @NotNull [] position, boolean randomized, int @NotNull [] random, int blockLimit) {
        if (Integer.compareUnsigned(position[0], blockLimit) >= 0) {
            throw Exceptions.newIllegalStateException("bzip2 position");
        }

        int entry = block[position[0]];
        int value = entry & BYTE_MASK;

        position[0] = entry >>> SUCCESSOR_SHIFT;

        if (randomized) {
            // The randomization mask is 1 when the countdown reaches 1.
            if (random[0] == 0) {
                random[0] = RANDOM_NUMBERS[random[1]];
                random[1] = (random[1] + 1) % RANDOM_NUMBERS.length;
            }

            random[0]--;
            value ^= random[0] == 1 ? 1 : 0;
        }

        return value;
    }

    private int decodeSymbol(int @NotNull [] selection, int @NotNull [] selectors,
                             int @NotNull [] @NotNull [] limits, int @NotNull [] @NotNull [] bases, int @NotNull [] @NotNull [] permutations,
                             int @NotNull [] permutationCounts, int @NotNull [] minimumLengths) {
        if (selection[1] == 0) {
            if (++selection[0] >= selectors.length) {
                throw Exceptions.newIllegalStateException("bzip2 selector overrun");
            }

            selection[1] = GROUP_SIZE;
        }

        selection[1]--;

        int group = selectors[selection[0]];
        int length = minimumLengths[group];
        int code = this.readBits(length);

        while (true) {
            if (length > MAXIMUM_CODE_LENGTH) {
                throw Exceptions.newIllegalStateException("bzip2 code");
            }

            if (code <= limits[group][length]) {
                break;
            }

            length++;
            code = code << 1 | this.readBits(1);
        }

        int index = code - bases[group][length];

        if (index < 0 || index >= permutationCounts[group]) {
            throw Exceptions.newIllegalStateException("bzip2 permutation");
        }

        return permutations[group][index];
    }

    private int readBits(int count) {
        while (this.bitCount < count) {
            if (this.inputPosition >= this.input.length) {
                throw Exceptions.newIllegalStateException("bzip2 input end");
            }

            this.bitBuffer = this.bitBuffer << Byte.SIZE | this.input[this.inputPosition++] & BYTE_MASK;
            this.bitCount += Byte.SIZE;
        }

        int value = (int) (this.bitBuffer >>> (this.bitCount - count) & ((1L << count) - 1));

        this.bitCount -= count;

        return value;
    }
}
