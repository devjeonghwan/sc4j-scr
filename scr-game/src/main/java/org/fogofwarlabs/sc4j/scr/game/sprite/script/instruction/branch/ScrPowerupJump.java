package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.branch;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.engine.animation.BranchInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code pwrupcondjmp}: jumps when the image is not the main image of its sprite.
 */
public final class ScrPowerupJump extends ScrInstruction implements BranchInstruction {
    private final int targetPosition;

    /**
     * Creates a {@link ScrPowerupJump} instance.
     *
     * @param decoded        the decoded {@code pwrupcondjmp} instruction
     * @param targetPosition the program position of the branch target
     */
    public ScrPowerupJump(@NotNull IscriptInstruction decoded, int targetPosition) {
        super(decoded);

        this.targetPosition = targetPosition;
    }

    /**
     * @return the position the instruction may continue at, {@code 0} up to the program length minus one
     */
    @Override
    public int getTargetPosition() {
        return this.targetPosition;
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

        if (target.getSprite().getMainImage() != target.getImage()) {
            context.jump(this.targetPosition);
        }

        return AnimationStep.CONTINUE;
    }
}
