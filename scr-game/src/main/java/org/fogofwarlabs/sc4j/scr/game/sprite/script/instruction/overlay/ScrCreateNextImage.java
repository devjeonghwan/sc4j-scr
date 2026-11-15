package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.overlay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecution;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code imgulnextid}: creates the image of the next image type below the image at an offset from it and runs its
 * Init script.
 */
public final class ScrCreateNextImage extends ScrInstruction {
    private final int offsetX;
    private final int offsetY;

    /**
     * Creates a {@link ScrCreateNextImage} instance.
     *
     * @param decoded the decoded {@code imgulnextid} instruction
     */
    public ScrCreateNextImage(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.offsetX = decoded.getOperands()[0];
        this.offsetY = decoded.getOperands()[1];
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * An unbounded update fails with an {@link UnsupportedOperationException}.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        IscriptExecution execution = target.getExecution();

        if (execution == null) {
            throw Exceptions.newUnsupportedOperationException("Image creation requires bounded pooled execution.");
        }

        SpriteImage image = target.getImage();

        target.getExecutor().createScriptImage(execution,
                                               image,
                                               target.getSprite(),
                                               image.getImageTypeId() + 1,
                                               image.getOffsetX() + this.offsetX, image.getOffsetY() + this.offsetY,
                                               IscriptExecutor.ORDER_BELOW,
                                               target.getUnit());

        return AnimationStep.CONTINUE;
    }
}
