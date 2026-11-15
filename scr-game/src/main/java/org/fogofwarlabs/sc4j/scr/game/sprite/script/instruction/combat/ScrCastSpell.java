package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.combat;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code castspell}: casts the spell of the order of the unit.
 */
public final class ScrCastSpell extends ScrInstruction {
    /**
     * Creates a {@link ScrCastSpell} instance.
     *
     * @param decoded the decoded {@code castspell} instruction
     */
    public ScrCastSpell(@NotNull IscriptInstruction decoded) {
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

        Unit unit = target.getUnit();

        if (unit != null) {
            target.getGame().getUnitSpells().castSpell(unit, getRemainingInstructions(context));
        }

        return AnimationStep.CONTINUE;
    }
}
