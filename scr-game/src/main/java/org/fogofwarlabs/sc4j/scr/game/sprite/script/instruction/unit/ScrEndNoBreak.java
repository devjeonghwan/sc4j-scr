package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.unit;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code nobrkcodeend}: clears the no-break marks of the unit and its sprite, and lets a unit whose order is done go
 * to idle and activate its next order.
 */
public final class ScrEndNoBreak extends ScrInstruction {
    /**
     * Creates a {@link ScrEndNoBreak} instance.
     *
     * @param decoded the decoded {@code nobrkcodeend} instruction
     */
    public ScrEndNoBreak(@NotNull IscriptInstruction decoded) {
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
            unit.setStatus(unit.getStatus() & ~UnitStatusFlags.ISCRIPT_NO_BREAK);
            unit.requireSprite().setFlags(unit.requireSprite().getFlags() & ~Sprite.FLAG_ISCRIPT_NO_BREAK);

            if (unit.getFirstOrder() != null && (unit.getUserActionFlags() & Unit.USER_ACTION_ORDER_DONE) != 0) {
                int instructionLimit = getRemainingInstructions(context);

                target.getExecutor().runToIdle(unit, instructionLimit);
                target.getGame().getOrders().activateNext(unit, instructionLimit);
            }
        }

        return AnimationStep.CONTINUE;
    }
}
