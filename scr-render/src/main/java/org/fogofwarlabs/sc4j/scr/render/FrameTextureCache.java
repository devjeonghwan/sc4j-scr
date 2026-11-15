package org.fogofwarlabs.sc4j.scr.render;

import org.jetbrains.annotations.NotNull;

import static org.lwjgl.opengl.GL33C.glDeleteTextures;

/**
 * Caches frame textures by a {@code long} frame key without boxing the key: an open-addressing table with linear probing. OpenGL texture names are
 * never {@code 0}, so {@code 0} marks an empty slot and a key without a cached texture.
 */
final class FrameTextureCache {
    /**
     * The initial number of slots, a power of two.
     */
    private static final int INITIAL_CAPACITY = 64;
    /**
     * The table doubles its slots once more than one slot in {@link #LOAD_DIVISOR} holds a texture.
     */
    private static final int CAPACITY_GROWTH = 2;
    private static final int LOAD_DIVISOR = 2;
    /**
     * The shift that folds the upper half of a key hash into its lower half, as {@link java.util.HashMap} spreads its hashes.
     */
    private static final int HASH_SPREAD_SHIFT = 16;

    private static int getHash(long key) {
        int hash = Long.hashCode(key);

        return hash ^ (hash >>> HASH_SPREAD_SHIFT);
    }

    private long @NotNull [] keys;
    private int @NotNull [] textures;
    private int size;

    /**
     * Creates an empty {@link FrameTextureCache} instance.
     */
    FrameTextureCache() {
        this.keys     = new long[INITIAL_CAPACITY];
        this.textures = new int[INITIAL_CAPACITY];
        this.size     = 0;
    }

    /**
     * @param key the frame key
     * @return the cached texture of the key, or {@code 0} if none is cached
     */
    int getTexture(long key) {
        int mask = this.keys.length - 1;

        for (int slot = getHash(key) & mask; this.textures[slot] != 0; slot = (slot + 1) & mask) {
            if (this.keys[slot] == key) {
                return this.textures[slot];
            }
        }

        return 0;
    }

    /**
     * Caches the texture of a key that has no cached texture yet.
     *
     * @param key     the frame key
     * @param texture the texture name, never {@code 0}
     */
    void putTexture(long key, int texture) {
        if ((this.size + 1) * LOAD_DIVISOR > this.keys.length) {
            this.grow();
        }

        this.insert(key, texture);
        this.size++;
    }

    private void grow() {
        long[] oldKeys = this.keys;
        int[] oldTextures = this.textures;

        this.keys     = new long[oldKeys.length * CAPACITY_GROWTH];
        this.textures = new int[oldTextures.length * CAPACITY_GROWTH];

        for (int slot = 0; slot < oldTextures.length; slot++) {
            if (oldTextures[slot] != 0) {
                this.insert(oldKeys[slot], oldTextures[slot]);
            }
        }
    }

    private void insert(long key, int texture) {
        int mask = this.keys.length - 1;
        int slot = getHash(key) & mask;

        while (this.textures[slot] != 0) {
            slot = (slot + 1) & mask;
        }

        this.keys[slot]     = key;
        this.textures[slot] = texture;
    }

    /**
     * Deletes every cached texture on the current OpenGL context. The cache is not used afterwards.
     */
    void deleteTextures() {
        for (int texture : this.textures) {
            if (texture != 0) {
                glDeleteTextures(texture);
            }
        }
    }
}
