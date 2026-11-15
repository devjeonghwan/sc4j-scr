package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code setpos}: sets both offsets of the image and requests a redraw if one changes.
 */
public final class ScrSetPosition extends ScrInstruction {
    private final int offsetX;
    private final int offsetY;

    /**
     * Creates a {@link ScrSetPosition} instance.
     *
     * @param decoded the decoded {@code setpos} instruction
     */
    public ScrSetPosition(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.offsetX = decoded.getOperands()[0];
        this.offsetY = decoded.getOperands()[1];
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

        if (image.getOffsetX() != (byte) this.offsetX || image.getOffsetY() != (byte) this.offsetY) {
            image.setOffsetX(this.offsetX);
            image.setOffsetY(this.offsetY);
            image.setFlags(image.getFlags() | SpriteImage.FLAG_REDRAW);
        }

        return AnimationStep.CONTINUE;
    }
}
