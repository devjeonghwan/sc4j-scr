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
 * Runs {@code imgol} and {@code imgul}: creates an image above or below the image at an offset from it and runs its Init
 * script.
 */
public final class ScrCreateImage extends ScrInstruction {
    private final int order;
    private final int imageType;
    private final int offsetX;
    private final int offsetY;

    /**
     * Creates a {@link ScrCreateImage} instance.
     *
     * @param decoded the decoded {@code imgol} or {@code imgul} instruction
     * @param order   {@link IscriptExecutor#ORDER_ABOVE} for {@code imgol}, {@link IscriptExecutor#ORDER_BELOW} for {@code imgul}
     */
    public ScrCreateImage(@NotNull IscriptInstruction decoded, int order) {
        super(decoded);

        this.order     = order;
        this.imageType = decoded.getOperands()[0];
        this.offsetX   = decoded.getOperands()[1];
        this.offsetY   = decoded.getOperands()[2];
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
                                               this.imageType,
                                               image.getOffsetX() + this.offsetX, image.getOffsetY() + this.offsetY,
                                               this.order,
                                               target.getUnit());

        return AnimationStep.CONTINUE;
    }
}
