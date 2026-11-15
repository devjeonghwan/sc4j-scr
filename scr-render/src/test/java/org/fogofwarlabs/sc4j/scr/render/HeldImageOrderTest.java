package org.fogofwarlabs.sc4j.scr.render;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/**
 * Checks that {@link HeldImageOrder} orders held quads by level, texture, mode and effect value and keeps equal quads in hold order.
 */
final class HeldImageOrderTest {
    /**
     * The seed of the random held quads.
     */
    private static final long SEED = 0x5C4AL;
    /**
     * The largest held quad count of the random cases.
     */
    private static final int MAXIMUM_COUNT = 300;
    /**
     * The value range of each random key: small, so that many quads share keys.
     */
    private static final int KEY_RANGE = 3;
    /**
     * The index buffer capacity the random cases start with, so that the buffers grow.
     */
    private static final int INITIAL_CAPACITY = 4;

    /**
     * Orders held quads as an index array boxed into {@link Integer} values and sorted with a comparator that breaks ties by the quad index.
     *
     * @param count    the number of held quads
     * @param levels   the draw level of each held quad
     * @param textures the texture of each held quad
     * @param modes    the mode of each held quad
     * @param uniforms the effect value of each held quad
     * @return the held quad indices in draw order
     */
    private static int @NotNull [] sortBoxed(int count,
                                             int @NotNull [] levels, int @NotNull [] textures, int @NotNull [] modes, int @NotNull [] uniforms) {
        Integer[] order = new Integer[count];

        for (int index = 0; index < count; index++) {
            order[index] = index;
        }

        Comparator<Integer> comparator = (first, second) -> {
            if (levels[first] != levels[second]) {
                return Integer.compare(levels[first], levels[second]);
            }

            if (textures[first] != textures[second]) {
                return Integer.compare(textures[first], textures[second]);
            }

            if (modes[first] != modes[second]) {
                return Integer.compare(modes[first], modes[second]);
            }

            if (uniforms[first] != uniforms[second]) {
                return Integer.compare(uniforms[first], uniforms[second]);
            }

            return Integer.compare(first, second);
        };

        Arrays.sort(order, comparator);

        int[] result = new int[count];

        for (int index = 0; index < count; index++) {
            result[index] = order[index];
        }

        return result;
    }

    private static int @NotNull [] createKeys(@NotNull Random random, int length) {
        int[] keys = new int[length];

        for (int index = 0; index < length; index++) {
            keys[index] = random.nextInt(KEY_RANGE);
        }

        return keys;
    }

    @Test
    void sort_should_keep_hold_order_for_equal_keys() {
        int[] levels = {1, 0, 1, 0, 0, 1};
        int[] textures = {7, 7, 7, 5, 7, 7};
        int[] modes = {2, 2, 2, 2, 2, 1};
        int[] uniforms = {0, 0, 0, 0, 0, 0};

        int[] order = new HeldImageOrder(levels.length).sort(levels.length, levels, textures, modes, uniforms);

        Assertions.assertArrayEquals(new int[]{3, 1, 4, 5, 0, 2}, Arrays.copyOf(order, levels.length));
    }

    @Test
    void sort_should_match_the_boxed_comparator_order() {
        Random random = new Random(SEED);
        HeldImageOrder heldOrder = new HeldImageOrder(INITIAL_CAPACITY);

        for (int count = 0; count <= MAXIMUM_COUNT; count++) {
            int capacity = count + random.nextInt(KEY_RANGE);
            int[] levels = createKeys(random, capacity);
            int[] textures = createKeys(random, capacity);
            int[] modes = createKeys(random, capacity);
            int[] uniforms = createKeys(random, capacity);

            int[] expected = sortBoxed(count, levels, textures, modes, uniforms);
            int[] actual = heldOrder.sort(count, levels, textures, modes, uniforms);

            Assertions.assertArrayEquals(expected, Arrays.copyOf(actual, count), "count " + count);
        }
    }
}
