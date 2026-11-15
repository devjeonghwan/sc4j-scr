package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code warpoverlay}: sets the warp texture frame as the modifier data and requests a redraw.
 */
public final class ScrWarpOverlay extends ScrInstruction {
    /**
     * The mask retaining an unsigned WORD.
     */
    private static final int WORD_MASK = 0xFFFF;

    private final int frameIndex;

    /**
     * Creates a {@link ScrWarpOverlay} instance.
     *
     * @param decoded the decoded {@code warpoverlay} instruction
     */
    public ScrWarpOverlay(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.frameIndex = decoded.getOperands()[0];
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

        image.setModifierData1(this.frameIndex & WORD_MASK);
        image.setFlags(image.getFlags() | SpriteImage.FLAG_REDRAW);

        return AnimationStep.CONTINUE;
    }
}
