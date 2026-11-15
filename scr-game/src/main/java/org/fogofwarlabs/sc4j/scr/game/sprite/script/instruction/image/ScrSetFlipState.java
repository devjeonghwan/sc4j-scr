package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code setflipstate}: sets the horizontal flip of the image when it changes, with a redraw, the warp flash restart
 * and the special overlay offset.
 */
public final class ScrSetFlipState extends ScrInstruction {
    /**
     * The bit position of the horizontal-flip flag in the image flags.
     */
    private static final int HORIZONTAL_FLIP_FLAG_SHIFT = 1;
    /**
     * The mask retaining the lowest bit of the operand.
     */
    private static final int FLAG_BIT_MASK = 1;
    /**
     * The mask retaining the high word of the modifier data.
     */
    private static final int HIGH_WORD_MASK = 0xFFFF0000;

    private final int flipState;

    /**
     * Creates a {@link ScrSetFlipState} instance.
     *
     * @param decoded the decoded {@code setflipstate} instruction
     */
    public ScrSetFlipState(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.flipState = decoded.getOperands()[0];
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

        if (((image.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) >>> HORIZONTAL_FLIP_FLAG_SHIFT) != this.flipState) {
            if (image.getModifier() == SpriteImage.MODIFIER_WARP_FLASH) {
                image.setModifierData1(image.getModifierData1() & HIGH_WORD_MASK | SpriteImage.WARP_FLASH_START_DATA);
            }

            image.setFlags((image.getFlags() & ~SpriteImage.FLAG_HORIZONTALLY_FLIPPED) |
                           ((this.flipState & FLAG_BIT_MASK) << HORIZONTAL_FLIP_FLAG_SHIFT) | SpriteImage.FLAG_REDRAW);

            if ((image.getFlags() & SpriteImage.FLAG_USES_SPECIAL_OFFSET) != 0) {
                target.getExecutor().updateSpecialOffset(image);
            }
        }

        return AnimationStep.CONTINUE;
    }
}
