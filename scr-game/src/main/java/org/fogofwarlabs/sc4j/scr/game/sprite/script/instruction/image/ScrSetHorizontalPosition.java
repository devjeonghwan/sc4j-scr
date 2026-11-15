package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code sethorpos}: sets the horizontal offset of the image and requests a redraw if it changes.
 */
public final class ScrSetHorizontalPosition extends ScrInstruction {
    private final int offsetX;

    /**
     * Creates a {@link ScrSetHorizontalPosition} instance.
     *
     * @param decoded the decoded {@code sethorpos} instruction
     */
    public ScrSetHorizontalPosition(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.offsetX = decoded.getOperands()[0];
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

        if (image.getOffsetX() != (byte) this.offsetX) {
            image.setOffsetX(this.offsetX);
            image.setFlags(image.getFlags() | SpriteImage.FLAG_REDRAW);
        }

        return AnimationStep.CONTINUE;
    }
}
