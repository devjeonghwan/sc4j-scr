package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Retains a standalone animated sprite and its pooled effect lifetime storage.
 */
public final class SpriteEffect {
    private final int poolIndex;
    private @Nullable Sprite sprite;
    private int life;

    /**
     * Creates a {@link SpriteEffect} instance for one zeroed pool slot without a sprite.
     *
     * @param poolIndex the storage index the owning pool assigns
     */
    SpriteEffect(int poolIndex) {
        this.poolIndex = poolIndex;
        this.sprite    = null;
        this.life      = 0;
    }

    /**
     * @return the storage index assigned by its owning pool
     */
    public int getPoolIndex() {
        return this.poolIndex;
    }

    /**
     * @return the retained sprite, or {@code null} if the slot has none
     */
    public @Nullable Sprite getSpriteOrNull() {
        return this.sprite;
    }

    /**
     * @return the sprite
     * @throws IllegalStateException if the slot has no sprite
     */
    public @NotNull Sprite getSprite() {
        if (this.sprite == null) {
            throw Exceptions.newIllegalStateException("The sprite effect storage has no sprite.");
        }

        return this.sprite;
    }

    /**
     * @return the retained life DWORD
     */
    public int getLife() {
        return this.life;
    }


    /**
     * Sets the sprite; only the owning pool attaches and clears it.
     *
     * @param sprite the sprite, or {@code null}
     */
    void setSprite(@Nullable Sprite sprite) {
        this.sprite = sprite;
    }

    /**
     * Sets the life DWORD; a remembered fog sprite keeps its unit type in the low WORD.
     *
     * @param life the life DWORD
     */
    public void setLife(int life) {
        this.life = life;
    }
}
