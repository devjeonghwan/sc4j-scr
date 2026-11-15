package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sprite;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code sproluselo}: creates a sprite with the heading of the image one elevation above the sprite of the image,
 * at an overlay offset of its current frame.
 */
public final class ScrCreateSpriteAtOverlay extends ScrInstruction {
    private final int spriteType;
    private final int overlayType;

    /**
     * Creates a {@link ScrCreateSpriteAtOverlay} instance.
     *
     * @param decoded the decoded {@code sproluselo} instruction
     */
    public ScrCreateSpriteAtOverlay(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.spriteType  = decoded.getOperands()[0];
        this.overlayType = decoded.getOperands()[1];
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
        Sprite sprite = target.getSprite();
        IscriptExecutor executor = target.getExecutor();
        int spriteOffset = executor.getImageOverlayOffset(image, this.overlayType, 0);

        executor.createSpriteEffectAtImage(image,
                                           sprite,
                                           this.spriteType,
                                           (short) spriteOffset, (short) (spriteOffset >>> Short.SIZE),
                                           sprite.getElevationLevel() + 1,
                                           target.getUnit(),
                                           true,
                                           getRemainingInstructions(context));

        return AnimationStep.CONTINUE;
    }
}
