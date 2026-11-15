package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs manually requested image script operations of a map placement with isolated random and simulation state: the SC:R preview service of
 * the client's map inspection, which never touches the source game.
 * <p>
 * Uses the common pooled sprite creation and interpreter in an isolated world.
 * Construction immediately executes Init; later animations remain manually requested.
 * Unsupported operations stop inspection at their script offset without consuming them.
 */
public final class ImageScriptPreview {
    /**
     * The most image script instructions one request executes.
     */
    private static final int INSTRUCTION_LIMIT = 4096;
    /**
     * The image pool capacity of the isolated world.
     */
    private static final int IMAGE_CAPACITY = 512;
    /**
     * The sprite pool capacity of the isolated world: the previewed sprite only.
     */
    private static final int SPRITE_CAPACITY = 1;

    /**
     * Describes a failure of the image script for {@link #getStopReason}.
     *
     * @param exception the failure
     * @return the failure message, or the exception's simple class name when it has no message
     */
    private static @NotNull String describeFailure(@NotNull RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private final @NotNull Game game;
    private final @NotNull Sprite sprite;
    private final @NotNull SpriteImage image;
    private final int scriptIdentifier;

    private int invocationCount;
    private boolean ended;
    private @Nullable String stopReason;

    /**
     * Creates an {@link ImageScriptPreview} instance and executes its initial image script.
     *
     * @param sourceState the source simulation whose data, map shape and random seed are copied
     * @param sourceUnit  the placement to preview
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the sprite pool or the image pool is not initialized
     */
    public ImageScriptPreview(@NotNull Game sourceState, @NotNull ChkUnit sourceUnit) {
        GameData gameData = sourceState.requireGameData();
        int movementIndex = gameData.getUnitTypes().getFlingyType(sourceUnit.getUnitId());
        int spriteIndex = gameData.getFlingyTypes().getSprite(movementIndex);
        int imageIndex = gameData.getSpriteTypes().getImage(spriteIndex);

        this.game = Game.create(gameData,
                                new GameMap(sourceState.getMap().getWidth(), sourceState.getMap().getHeight(),
                                            sourceState.getMap().getTileset(),
                                            new int[sourceState.getMap().getWidth() * sourceState.getMap().getHeight()]),
                                new LcgRandom(sourceState.getRandom().getState()));

        this.game.getSpriteSystem().initializeSpritePool(SPRITE_CAPACITY);
        this.game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);

        this.sprite           = this.game.getSpriteSystem().requireSpritePool().getSprite(0);
        this.image            = this.game.getSpriteSystem().requireImagePool().getImage(0);
        this.scriptIdentifier = gameData.getImageTypes().getIscript(imageIndex);

        this.invocationCount = 1;
        this.ended           = false;
        this.stopReason      = null;

        try {
            Sprite created = this.game.getIscriptExecutor().createSprite(spriteIndex, sourceUnit.getX(), sourceUnit.getY(),
                                                                         sourceUnit.getOwner(), null, INSTRUCTION_LIMIT);

            this.ended = created == null || this.sprite.getImages().isEmpty();
        } catch (RuntimeException exception) {
            this.stopReason = describeFailure(exception);
        }
    }

    /**
     * @return the preview sprite image
     */
    public @NotNull SpriteImage getImage() {
        return this.image;
    }

    /**
     * @return the preview sprite
     */
    public @NotNull Sprite getSprite() {
        return this.sprite;
    }

    /**
     * @return the preview image script identifier
     */
    public int getScriptIdentifier() {
        return this.scriptIdentifier;
    }

    /**
     * @return the number of requested script invocations
     */
    public int getInvocationCount() {
        return this.invocationCount;
    }

    /**
     * @return {@code true} if the preview no longer has an active sprite image, {@code false} otherwise
     */
    public boolean isEnded() {
        return this.ended;
    }

    /**
     * @return the script failure description, or {@code null} while no failure was recorded
     */
    public @Nullable String getStopReason() {
        return this.stopReason;
    }


    /**
     * Runs a requested animation on the active preview image.
     *
     * @param animationType the image animation type
     */
    public void requestAnimation(int animationType) {
        if (this.ended || this.stopReason != null || !this.sprite.getImages().contains(this.image)) {
            return;
        }

        try {
            this.game.getInterfaceLink().getSoundEvents().clear();
            this.invocationCount++;

            this.game.getIscriptExecutor().runImagePreviewAnimation(this.image, this.sprite, this.scriptIdentifier, animationType, INSTRUCTION_LIMIT);

            this.ended = this.sprite.getImages().isEmpty();
        } catch (RuntimeException exception) {
            this.stopReason = describeFailure(exception);
        }
    }

    /**
     * Advances the active preview script by one explicit request.
     */
    public void step() {
        if (this.ended || this.stopReason != null || this.invocationCount == 0 ||
            (this.image.getProgramCounter() == 0 && this.sprite.getImages().contains(this.image))) {
            return;
        }

        try {
            this.game.getInterfaceLink().getSoundEvents().clear();
            this.invocationCount++;

            this.ended = !this.game.getIscriptExecutor().executeSpritePreview(this.sprite, INSTRUCTION_LIMIT);
        } catch (RuntimeException exception) {
            this.stopReason = describeFailure(exception);
        }
    }
}
