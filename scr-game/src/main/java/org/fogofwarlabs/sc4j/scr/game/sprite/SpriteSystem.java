package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the sprite storage of one game: the image, sprite and sprite-effect pools and the sprite effect that startup creates.
 * <p>
 * The pools are created by world initialization with the established capacities and stay {@code null} until then.
 */
public final class SpriteSystem {
    private @Nullable SpriteImagePool imagePool;
    private @Nullable SpritePool spritePool;
    private @Nullable SpriteEffectPool spriteEffectPool;

    private @Nullable SpriteEffect startupSpriteEffect;

    /**
     * Creates a {@link SpriteSystem} instance without storage.
     */
    public SpriteSystem() {
        this.imagePool        = null;
        this.spritePool       = null;
        this.spriteEffectPool = null;

        this.startupSpriteEffect = null;
    }

    /**
     * @return the image pool, or {@code null} before {@link #initializeImagePool(int)} creates it
     */
    public @Nullable SpriteImagePool getImagePool() {
        return this.imagePool;
    }

    /**
     * Returns the image pool of an initialized world.
     *
     * @return the image pool
     * @throws UnsupportedOperationException if the image pool is not initialized
     */
    public @NotNull SpriteImagePool requireImagePool() {
        SpriteImagePool imagePool = this.imagePool;

        if (imagePool == null) {
            throw Exceptions.newUnsupportedOperationException("The image pool is not initialized.");
        }

        return imagePool;
    }

    /**
     * @return the sprite pool, or {@code null} before {@link #initializeSpritePool(int)} creates it
     */
    public @Nullable SpritePool getSpritePool() {
        return this.spritePool;
    }

    /**
     * Returns the sprite pool of an initialized world.
     *
     * @return the sprite pool
     * @throws UnsupportedOperationException if the sprite pool is not initialized
     */
    public @NotNull SpritePool requireSpritePool() {
        SpritePool spritePool = this.spritePool;

        if (spritePool == null) {
            throw Exceptions.newUnsupportedOperationException("The sprite pool is not initialized.");
        }

        return spritePool;
    }

    /**
     * @return the sprite effect pool, or {@code null} before {@link #initializeSpriteEffectPool(int)} creates it
     */
    public @Nullable SpriteEffectPool getSpriteEffectPool() {
        return this.spriteEffectPool;
    }

    /**
     * @return the sprite effect that startup created, or {@code null} before {@link #setStartupSpriteEffect(SpriteEffect)} retains it
     */
    public @Nullable SpriteEffect getStartupSpriteEffect() {
        return this.startupSpriteEffect;
    }

    /**
     * Sets the sprite effect that startup created, which is retained once.
     *
     * @param spriteEffect the created startup sprite effect
     * @throws IllegalStateException if a startup sprite effect is already retained
     */
    public void setStartupSpriteEffect(@NotNull SpriteEffect spriteEffect) {
        if (this.startupSpriteEffect != null) {
            throw Exceptions.newIllegalStateException("The startup sprite effect is already initialized.");
        }

        this.startupSpriteEffect = spriteEffect;
    }


    /**
     * Initializes the image pool with the established capacity.
     *
     * @param capacity the established runtime image capacity
     * @throws IllegalStateException if the image pool is already initialized
     */
    public void initializeImagePool(int capacity) {
        if (this.imagePool != null) {
            throw Exceptions.newIllegalStateException("Native image storage is already initialized.");
        }

        this.imagePool = new SpriteImagePool(capacity);
    }

    /**
     * Initializes the sprite pool with the established capacity.
     *
     * @param capacity the established runtime sprite capacity
     * @throws IllegalStateException if the sprite pool is already initialized
     */
    public void initializeSpritePool(int capacity) {
        if (this.spritePool != null) {
            throw Exceptions.newIllegalStateException("Native sprite storage is already initialized.");
        }

        this.spritePool = new SpritePool(capacity);
    }

    /**
     * Initializes the sprite effect pool with the established capacity.
     *
     * @param capacity the established runtime sprite effect capacity
     * @throws IllegalStateException if the sprite effect pool is already initialized
     */
    public void initializeSpriteEffectPool(int capacity) {
        if (this.spriteEffectPool != null) {
            throw Exceptions.newIllegalStateException("Native sprite effect storage is already initialized.");
        }

        this.spriteEffectPool = new SpriteEffectPool(capacity);
    }
}
