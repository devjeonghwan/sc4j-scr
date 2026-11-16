package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.unit;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrPreviewSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code setspawnframe}: sets the direction target of the unit ahead of it in the operand's frame direction
 * ({@link FlingyMotionUtils#faceFrameDirection}), so the unit turns there while it stands on its waypoint.
 */
public final class ScrSetSpawnFrame extends ScrInstruction {
    private final int frameDirection;

    /**
     * Creates a {@link ScrSetSpawnFrame} instance.
     *
     * @param decoded the decoded {@code setspawnframe} instruction
     */
    public ScrSetSpawnFrame(@NotNull IscriptInstruction decoded) {
        super(decoded, ScrPreviewSupport.WITH_UNIT);

        this.frameDirection = decoded.getOperands()[0];
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Fails with an {@link IllegalStateException} without a unit context.
     *
     * @param context the running animation of one object
     * @return how the program continues
     * @throws UnsupportedOperationException if a bounded update without a unit context meets the instruction
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        this.checkPreviewSupport(context, target);

        FlingyMotionUtils.faceFrameDirection(this.requireUnit(target), this.frameDirection);

        return AnimationStep.CONTINUE;
    }
}
