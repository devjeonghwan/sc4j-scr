package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code followmaingraphic}: takes the frame and the horizontal flip of the main image of the sprite when they differ.
 */
public final class ScrFollowMainGraphic extends ScrInstruction {
    /**
     * The mask retaining the high word of the modifier data.
     */
    private static final int HIGH_WORD_MASK = 0xFFFF0000;

    /**
     * Creates a {@link ScrFollowMainGraphic} instance.
     *
     * @param decoded the decoded {@code followmaingraphic} instruction
     */
    public ScrFollowMainGraphic(@NotNull IscriptInstruction decoded) {
        super(decoded);
    }


    /**
     * Runs this instruction for the animated object of the context.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        SpriteImage image = target.getImage();
        SpriteImage mainImage = target.getSprite().getMainImage();

        if (mainImage != null &&
            (image.getDrawnFrameIndex() != mainImage.getDrawnFrameIndex() ||
             ((image.getFlags() ^ mainImage.getFlags()) & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) != 0)) {
            image.setFrameIndex(mainImage.getFrameIndex());
            image.setFrameIndexOffset(mainImage.getFrameIndexOffset());

            if (image.getModifier() == SpriteImage.MODIFIER_WARP_FLASH) {
                image.setModifierData1(image.getModifierData1() & HIGH_WORD_MASK | SpriteImage.WARP_FLASH_START_DATA);
            }

            image.setFlags((image.getFlags() & ~SpriteImage.FLAG_HORIZONTALLY_FLIPPED) |
                           (mainImage.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) | SpriteImage.FLAG_REDRAW);
        }

        return AnimationStep.CONTINUE;
    }
}
