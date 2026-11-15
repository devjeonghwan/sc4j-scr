package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.combat;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code attkshiftproj}: attacks with the ground weapon at the forward offset of the operand.
 */
public final class ScrAttackShiftedProjectile extends ScrInstruction {
    /**
     * The weapon number of the ground weapon, which {@code attkshiftproj} launches.
     */
    private static final int GROUND_WEAPON_NUMBER = 1;

    private final int forwardOffset;

    /**
     * Creates a {@link ScrAttackShiftedProjectile} instance.
     *
     * @param decoded the decoded {@code attkshiftproj} instruction
     */
    public ScrAttackShiftedProjectile(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.forwardOffset = decoded.getOperands()[0];
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

        if (unit != null) {
            target.getGame().getUnitAttack().attackWith(unit, GROUND_WEAPON_NUMBER, this.forwardOffset, getRemainingInstructions(context));
        }

        return AnimationStep.CONTINUE;
    }
}
