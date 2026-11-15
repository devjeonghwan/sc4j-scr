package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code tmprmgraphicend}: shows a hidden image again and requests a redraw.
 */
public final class ScrShowImage extends ScrInstruction {
    /**
     * Creates a {@link ScrShowImage} instance.
     *
     * @param decoded the decoded {@code tmprmgraphicend} instruction
     */
    public ScrShowImage(@NotNull IscriptInstruction decoded) {
        super(decoded);
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

        if ((image.getFlags() & SpriteImage.FLAG_HIDDEN) != 0) {
            image.setFlags((image.getFlags() & ~SpriteImage.FLAG_HIDDEN) | SpriteImage.FLAG_REDRAW);
        }

        return AnimationStep.CONTINUE;
    }
}
