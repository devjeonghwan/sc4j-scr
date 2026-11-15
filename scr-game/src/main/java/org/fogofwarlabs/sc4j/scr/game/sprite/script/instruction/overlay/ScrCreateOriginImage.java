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
 * Runs {@code imgolorig} and {@code switchul}: creates an image above or below the image, runs its Init script and
 * places it at the special overlay offset of the main image.
 */
public final class ScrCreateOriginImage extends ScrInstruction {
    private final int order;
    private final int imageType;

    /**
     * Creates a {@link ScrCreateOriginImage} instance.
     *
     * @param decoded the decoded {@code imgolorig} or {@code switchul} instruction
     * @param order   {@link IscriptExecutor#ORDER_ABOVE} for {@code imgolorig}, {@link IscriptExecutor#ORDER_BELOW} for {@code switchul}
     */
    public ScrCreateOriginImage(@NotNull IscriptInstruction decoded, int order) {
        super(decoded);

        this.order     = order;
        this.imageType = decoded.getOperands()[0];
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

        IscriptExecutor executor = target.getExecutor();
        SpriteImage overlay = executor.createScriptImage(execution,
                                                         target.getImage(),
                                                         target.getSprite(),
                                                         this.imageType,
                                                         0, 0,
                                                         this.order,
                                                         target.getUnit());

        if (overlay != null && (overlay.getFlags() & SpriteImage.FLAG_USES_SPECIAL_OFFSET) == 0) {
            overlay.setFlags(overlay.getFlags() | SpriteImage.FLAG_USES_SPECIAL_OFFSET);
            executor.updateSpecialOffset(overlay);
        }

        return AnimationStep.CONTINUE;
    }
}
