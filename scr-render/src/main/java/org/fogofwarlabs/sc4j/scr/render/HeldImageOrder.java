package org.fogofwarlabs.sc4j.scr.render;

import org.jetbrains.annotations.NotNull;

/**
 * Orders the held image quads of {@link WorldRenderer} for drawing: by draw level, then by texture, mode and effect value, while quads with equal
 * keys keep the order they were held in. The index buffers are reused, so ordering allocates only when the held quads outgrow them.
 */
final class HeldImageOrder {
    /**
     * The sorted runs merged into one run in each pass of the merge sort.
     */
    private static final int RUNS_PER_MERGE = 2;

    private int @NotNull [] order;
    private int @NotNull [] merged;

    private int @NotNull [] levels;
    private int @NotNull [] textures;
    private int @NotNull [] modes;
    private int @NotNull [] uniforms;

    /**
     * Creates a {@link HeldImageOrder} instance with index buffers for a number of held quads.
     *
     * @param capacity the held quads the index buffers hold before they grow
     */
    HeldImageOrder(int capacity) {
        this.order  = new int[capacity];
        this.merged = new int[capacity];

        this.levels   = new int[0];
        this.textures = new int[0];
        this.modes    = new int[0];
        this.uniforms = new int[0];
    }

    /**
     * Orders held quads {@code 0} through {@code count - 1} with a stable merge sort. The index buffers grow to the length of the key arrays when
     * they hold fewer than {@code count} quads.
     *
     * @param count    the number of held quads
     * @param levels   the draw level of each held quad
     * @param textures the texture of each held quad
     * @param modes    the mode of each held quad
     * @param uniforms the effect value of each held quad
     * @return the held quad indices in draw order, valid until the next call; the entries from {@code count} on are unspecified
     */
    int @NotNull [] sort(int count, int @NotNull [] levels, int @NotNull [] textures, int @NotNull [] modes, int @NotNull [] uniforms) {
        if (this.order.length < count) {
            this.order  = new int[levels.length];
            this.merged = new int[levels.length];
        }

        this.levels   = levels;
        this.textures = textures;
        this.modes    = modes;
        this.uniforms = uniforms;

        int[] source = this.order;
        int[] target = this.merged;

        for (int index = 0; index < count; index++) {
            source[index] = index;
        }

        for (int width = 1; width < count; width *= RUNS_PER_MERGE) {
            for (int start = 0; start < count; start += RUNS_PER_MERGE * width) {
                this.merge(source, target, start, Math.min(start + width, count), Math.min(start + RUNS_PER_MERGE * width, count));
            }

            int[] sorted = target;

            target = source;
            source = sorted;
        }

        this.order  = source;
        this.merged = target;

        return source;
    }

    /**
     * Merges two sorted runs of held quad indices; on equal keys the quad of the first run comes first.
     *
     * @param source the indices holding both runs
     * @param target the indices receiving the merged run at the same positions
     * @param start  the first position of the first run
     * @param middle the first position of the second run
     * @param end    the position after the second run
     */
    private void merge(int @NotNull [] source, int @NotNull [] target, int start, int middle, int end) {
        int first = start;
        int second = middle;

        for (int position = start; position < end; position++) {
            if (second >= end || (first < middle && this.compare(source[first], source[second]) <= 0)) {
                target[position] = source[first++];
            } else {
                target[position] = source[second++];
            }
        }
    }

    /**
     * Compares two held quads by draw level, then by texture, mode and effect value.
     *
     * @param first  the index of the first held quad
     * @param second the index of the second held quad
     * @return a negative value, {@code 0} or a positive value if the first quad is drawn before, with or after the second
     */
    private int compare(int first, int second) {
        if (this.levels[first] != this.levels[second]) {
            return Integer.compare(this.levels[first], this.levels[second]);
        }

        if (this.textures[first] != this.textures[second]) {
            return Integer.compare(this.textures[first], this.textures[second]);
        }

        if (this.modes[first] != this.modes[second]) {
            return Integer.compare(this.modes[first], this.modes[second]);
        }

        return Integer.compare(this.uniforms[first], this.uniforms[second]);
    }
}
