package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.branch;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.engine.animation.BranchInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code curdirectcondjmp}: jumps when the heading of the unit lies within the arc around the direction operand.
 */
public final class ScrDirectionJump extends ScrInstruction implements BranchInstruction {
    private final int direction;
    private final int arc;
    private final int targetPosition;

    /**
     * Creates a {@link ScrDirectionJump} instance.
     *
     * @param decoded        the decoded {@code curdirectcondjmp} instruction
     * @param targetPosition the program position of the branch target
     */
    public ScrDirectionJump(@NotNull IscriptInstruction decoded, int targetPosition) {
        super(decoded);

        this.direction      = decoded.getOperands()[0];
        this.arc            = decoded.getOperands()[1];
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

        Unit unit = target.getUnit();

        if (unit != null && Math.abs((byte) (unit.getHeading() - this.direction)) < this.arc) {
            context.jump(this.targetPosition);
        }

        return AnimationStep.CONTINUE;
    }
}
