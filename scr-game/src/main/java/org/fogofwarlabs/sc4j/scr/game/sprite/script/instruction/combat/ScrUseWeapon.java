package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.combat;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code useweapon}: fires the weapon of the operand at the order target of the unit.
 */
public final class ScrUseWeapon extends ScrInstruction {
    private final int weapon;

    /**
     * Creates a {@link ScrUseWeapon} instance.
     *
     * @param decoded the decoded {@code useweapon} instruction
     */
    public ScrUseWeapon(@NotNull IscriptInstruction decoded) {
        super(decoded);

        this.weapon = decoded.getOperands()[0];
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
            target.getGame().getProjectilePhase().useWeapon(unit, this.weapon, getRemainingInstructions(context));
        }

        return AnimationStep.CONTINUE;
    }
}
