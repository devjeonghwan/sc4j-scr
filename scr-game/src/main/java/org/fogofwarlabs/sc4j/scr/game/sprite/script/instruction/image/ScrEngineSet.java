package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code engset}: shows the frame set of the operand after the frames of the main image of the sprite, with its
 * direction and horizontal flip.
 */
public final class ScrEngineSet extends ScrInstruction {
    /**
     * The mask of the frame count of a GRP (its high bit is a flag).
     */
    private static final int FRAME_COUNT_MASK = 0x7FFF;

    private final int frameSet;

    /**
     * Creates a {@link ScrEngineSet} instance.
     *
     * @param decoded the decoded {@code engset} instruction
     */
    public ScrEngineSet(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.frameSet = decoded.getOperands()[0];
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
            GrpFile mainGraphics = target.getExecutor().getGrpFile(mainImage);

            image.setFrameIndex(mainImage.getFrameIndex() + (mainGraphics.getFrameCount() & FRAME_COUNT_MASK) * this.frameSet);
            image.setFrameIndexOffset(mainImage.getFrameIndexOffset());
            image.setFlags((image.getFlags() & ~SpriteImage.FLAG_HORIZONTALLY_FLIPPED) |
                           (mainImage.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED));
        }

        return AnimationStep.CONTINUE;
    }
}
