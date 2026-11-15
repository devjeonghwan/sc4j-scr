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
 * Runs {@code trgtrangecondjmp}: jumps when the order target of the unit is within the script range.
 */
public final class ScrTargetRangeJump extends ScrInstruction implements BranchInstruction {
    private final int range;
    private final int targetPosition;

    /**
     * Creates a {@link ScrTargetRangeJump} instance.
     *
     * @param decoded        the decoded {@code trgtrangecondjmp} instruction
     * @param targetPosition the program position of the branch target
     */
    public ScrTargetRangeJump(@NotNull IscriptInstruction decoded, int targetPosition) {
        super(decoded);

        this.range          = decoded.getOperands()[0];
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

        if (unit != null && target.getGame().getUnitAttack().isTargetWithinScriptRange(unit, this.range)) {
            context.jump(this.targetPosition);
        }

        return AnimationStep.CONTINUE;
    }
}
