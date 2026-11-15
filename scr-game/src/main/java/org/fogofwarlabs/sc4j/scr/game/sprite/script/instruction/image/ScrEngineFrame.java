package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code engframe}: shows a frame with the direction and the horizontal flip of the main image of the sprite.
 */
public final class ScrEngineFrame extends ScrInstruction {
    private final int frameIndex;

    /**
     * Creates a {@link ScrEngineFrame} instance.
     *
     * @param decoded the decoded {@code engframe} instruction
     */
    public ScrEngineFrame(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.frameIndex = decoded.getOperands()[0];
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

        if (mainImage != null) {
            image.setFrameIndex(this.frameIndex);
            image.setFrameIndexOffset(mainImage.getFrameIndexOffset());
            image.setFlags((image.getFlags() & ~SpriteImage.FLAG_HORIZONTALLY_FLIPPED) |
                           (mainImage.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED));
        }

        return AnimationStep.CONTINUE;
    }
}
