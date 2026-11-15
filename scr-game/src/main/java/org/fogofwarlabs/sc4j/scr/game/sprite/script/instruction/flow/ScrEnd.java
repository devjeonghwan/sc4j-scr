package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.flow;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code end}: removes the image from its sprite, returns it to the image pool and ends its program.
 */
public final class ScrEnd extends ScrInstruction {
    /**
     * Creates a {@link ScrEnd} instance.
     *
     * @param decoded the decoded {@code end} instruction
     */
    public ScrEnd(@NotNull IscriptInstruction decoded) {
        super(decoded);
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Fails with an {@link IllegalStateException} if the game's image pool does not own the image.
     *
     * @param context the running animation of one object
     * @return how the program continues
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        SpriteImage image = target.getImage();
        SpriteImagePool pool = target.getGame().getSpriteSystem().getImagePool();

        if (pool == null || !pool.isAllocated(image.getIndex()) || pool.getImage(image.getIndex()) != image) {
            throw Exceptions.newIllegalStateException("The image has no owning pool.");
        }

        target.getSprite().removeImage(image);
        pool.recycle(image);

        return AnimationStep.END;
    }
}
