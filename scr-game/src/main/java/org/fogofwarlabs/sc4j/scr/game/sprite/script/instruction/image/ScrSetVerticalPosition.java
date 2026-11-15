package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code setvertpos}: sets the vertical offset of the image and requests a redraw if it changes, unless the unit is
 * cloaked or requires a detector.
 */
public final class ScrSetVerticalPosition extends ScrInstruction {
    private final int offsetY;

    /**
     * Creates a {@link ScrSetVerticalPosition} instance.
     *
     * @param decoded the decoded {@code setvertpos} instruction
     */
    public ScrSetVerticalPosition(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.offsetY = decoded.getOperands()[0];
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
        Unit unit = target.getUnit();

        if ((unit == null || (unit.getStatus() & (UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED)) == 0) &&
            image.getOffsetY() != (byte) this.offsetY) {
            image.setOffsetY(this.offsetY);
            image.setFlags(image.getFlags() | SpriteImage.FLAG_REDRAW);
        }

        return AnimationStep.CONTINUE;
    }
}
