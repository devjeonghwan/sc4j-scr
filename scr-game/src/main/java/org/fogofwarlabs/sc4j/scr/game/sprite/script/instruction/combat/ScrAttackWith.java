package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.combat;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code attackwith}: attacks with the weapon of the operand at its own forward offset.
 */
public final class ScrAttackWith extends ScrInstruction {
    /**
     * The forward offset with which {@code attackwith} launches at the weapon's own offset.
     */
    private static final int WEAPON_OWN_FORWARD_OFFSET = -1;

    private final int weaponNumber;

    /**
     * Creates a {@link ScrAttackWith} instance.
     *
     * @param decoded the decoded {@code attackwith} instruction
     */
    public ScrAttackWith(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.weaponNumber = decoded.getOperands()[0];
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
            target.getGame().getUnitAttack().attackWith(unit, this.weaponNumber, WEAPON_OWN_FORWARD_OFFSET, getRemainingInstructions(context));
        }

        return AnimationStep.CONTINUE;
    }
}
