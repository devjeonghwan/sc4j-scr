package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.combat;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code domissiledmg} and {@code dogrddamage}: the bullet being updated hits.
 */
public final class ScrMissileDamage extends ScrInstruction {
    /**
     * Creates a {@link ScrMissileDamage} instance.
     *
     * @param decoded the decoded {@code domissiledmg} or {@code dogrddamage} instruction
     */
    public ScrMissileDamage(@NotNull IscriptInstruction decoded) {
        super(decoded);
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

        target.getGame().getProjectilePhase().hit(getRemainingInstructions(context));

        return AnimationStep.CONTINUE;
    }
}
