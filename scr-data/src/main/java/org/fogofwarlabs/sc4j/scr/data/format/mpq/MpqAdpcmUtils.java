package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Decompresses MPQ mono and stereo IMA ADPCM streams with bounded sample output.
 * Missing initial sample bytes read as zero.
 */
final class MpqAdpcmUtils {
    private static final int BYTE_MASK = 0xFF;
    private static final int INITIAL_STEP_INDEX = 0x2C;
    private static final int MAXIMUM_STEP_INDEX = 0x58;
    private static final int STEP_INDEX_JUMP = 8;
    private static final int HEADER_SIZE = 2;
    private static final int SHIFT_OFFSET = 1;
    private static final int SHIFT_MASK = 0x1F;
    private static final int SAMPLE_SIZE = 2;
    private static final int STEREO = 2;
    private static final int COMMAND_FLAG = 0x80;
    private static final int COMMAND_MASK = 0x7F;
    private static final int COMMAND_REPEAT = 0;
    private static final int COMMAND_RAISE_STEP = 1;
    private static final int COMMAND_LOWER_STEP = 2;
    private static final int MAGNITUDE_BITS = 6;
    private static final int SIGN_FLAG = 0x40;
    private static final int STEP_INDEX_CHANGE_MASK = 0x1F;

    private static final int @NotNull [] STEP_SIZES = {
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130,
            143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282,
            1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630,
            9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    };

    private static final int @NotNull [] STEP_INDEX_CHANGES = {
            -1, 0, -1, 4, -1, 2, -1, 6, -1, 1, -1, 5, -1, 3, -1, 7, -1, 1, -1, 5, -1, 3, -1, 7, -1, 2, -1, 4, -1, 6, -1, 8
    };

    /**
     * Decompresses one ADPCM stream.
     *
     * @param input        the compressed data after the method mask byte
     * @param capacity     the output capacity in bytes
     * @param channelCount {@code 1} for mono or {@code 2} for stereo
     * @return the decompressed 16-bit little-endian samples
     */
    static byte @NotNull [] decompress(byte @NotNull [] input, int capacity, int channelCount) {
        byte[] output = new byte[capacity];
        int remaining = capacity;
        int outputSize = 0;
        int inputPosition = HEADER_SIZE;
        int shift = readUnsignedByteOrZero(input, SHIFT_OFFSET) & SHIFT_MASK;
        int[] stepIndexes = {INITIAL_STEP_INDEX, INITIAL_STEP_INDEX};
        int[] samples = new int[channelCount];

        for (int channel = 0; channel < channelCount; channel++) {
            samples[channel] = (short) (readUnsignedByteOrZero(input, inputPosition) | readUnsignedByteOrZero(input, inputPosition + 1) << Byte.SIZE);
            inputPosition += SAMPLE_SIZE;

            if (remaining < SAMPLE_SIZE) {
                return Arrays.copyOf(output, outputSize);
            }

            outputSize = writeSample(output, outputSize, samples[channel]);
            remaining -= SAMPLE_SIZE;
        }

        int channel = channelCount - 1;

        while (inputPosition < input.length) {
            if (channelCount == STEREO) {
                channel ^= 1;
            }

            int encoded = input[inputPosition++] & BYTE_MASK;

            if ((encoded & COMMAND_FLAG) != 0) {
                int command = encoded & COMMAND_MASK;

                if (command == COMMAND_REPEAT) {
                    if (stepIndexes[channel] != 0) {
                        stepIndexes[channel]--;
                    }

                    if (remaining < SAMPLE_SIZE) {
                        break;
                    }

                    outputSize = writeSample(output, outputSize, samples[channel]);
                    remaining -= SAMPLE_SIZE;
                } else if (command == COMMAND_RAISE_STEP || command == COMMAND_LOWER_STEP) {
                    int stepIndex = stepIndexes[channel] + (command == COMMAND_RAISE_STEP ? STEP_INDEX_JUMP : -STEP_INDEX_JUMP);

                    stepIndexes[channel] = Math.clamp(stepIndex, 0, MAXIMUM_STEP_INDEX);

                    if (channelCount == STEREO) {
                        channel ^= 1;
                    }
                }

                continue;
            }

            int stepIndex = stepIndexes[channel];
            int stepSize = STEP_SIZES[stepIndex];
            int difference = stepSize >>> shift;

            for (int bit = 0; bit < MAGNITUDE_BITS; bit++) {
                if ((encoded & (1 << bit)) != 0) {
                    difference += stepSize >>> bit;
                }
            }

            int sample = (encoded & SIGN_FLAG) != 0 ? Math.max(samples[channel] - difference, Short.MIN_VALUE)
                                                    : Math.min(samples[channel] + difference, Short.MAX_VALUE);

            samples[channel] = sample;

            if (remaining < SAMPLE_SIZE) {
                break;
            }

            outputSize = writeSample(output, outputSize, sample);
            remaining -= SAMPLE_SIZE;

            stepIndexes[channel] = Math.clamp(stepIndex + STEP_INDEX_CHANGES[encoded & STEP_INDEX_CHANGE_MASK], 0, MAXIMUM_STEP_INDEX);
        }

        return Arrays.copyOf(output, outputSize);
    }

    private static int writeSample(byte @NotNull [] output, int outputSize, int sample) {
        output[outputSize]     = (byte) sample;
        output[outputSize + 1] = (byte) (sample >> Byte.SIZE);

        return outputSize + SAMPLE_SIZE;
    }

    private static int readUnsignedByteOrZero(byte @NotNull [] input, int position) {
        return position < input.length ? input[position] & BYTE_MASK : 0;
    }


    private MpqAdpcmUtils() {
    }
}
