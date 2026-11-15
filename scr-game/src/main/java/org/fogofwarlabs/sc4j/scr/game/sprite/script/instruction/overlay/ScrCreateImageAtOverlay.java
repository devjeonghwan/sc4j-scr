package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.overlay;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code imgoluselo} and {@code imguluselo}: creates an image above or below the image at an overlay offset of its
 * current frame and runs its Init script.
 */
public final class ScrCreateImageAtOverlay extends ScrInstruction {
    private final int order;
    private final int imageType;
    private final int overlayType;
    private final int overlayPosition;

    /**
     * Creates a {@link ScrCreateImageAtOverlay} instance.
     *
     * @param decoded the decoded {@code imgoluselo} or {@code imguluselo} instruction
     * @param order   {@link IscriptExecutor#ORDER_ABOVE} for {@code imgoluselo}, {@link IscriptExecutor#ORDER_BELOW} for {@code imguluselo}
     */
    public ScrCreateImageAtOverlay(@NotNull IscriptInstruction decoded, int order) {
        super(decoded);

        this.order           = order;
        this.imageType       = decoded.getOperands()[0];
        this.overlayType     = decoded.getOperands()[1];
        this.overlayPosition = decoded.getOperands()[2];
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * An unbounded update fails with a {@link NullPointerException} unless the image pool is exhausted.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        SpriteImage image = target.getImage();
        IscriptExecutor executor = target.getExecutor();
        int offset = executor.getImageOverlayOffset(image, this.overlayType, this.overlayPosition);

        executor.createScriptImage(target.getExecution(),
                                   image,
                                   target.getSprite(),
                                   this.imageType,
                                   (short) offset, (short) (offset >>> Short.SIZE),
                                   this.order,
                                   target.getUnit());

        return AnimationStep.CONTINUE;
    }
}
