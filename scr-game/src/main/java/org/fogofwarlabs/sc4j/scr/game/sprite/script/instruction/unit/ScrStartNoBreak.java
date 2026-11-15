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
 * Runs {@code nobrkcodestart}: marks the unit and its sprite as running a script part that orders must not break.
 */
public final class ScrStartNoBreak extends ScrInstruction {
    /**
     * Creates a {@link ScrStartNoBreak} instance.
     *
     * @param decoded the decoded {@code nobrkcodestart} instruction
     */
    public ScrStartNoBreak(@NotNull IscriptInstruction decoded) {
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
            unit.setStatus(unit.getStatus() | UnitStatusFlags.ISCRIPT_NO_BREAK);
            unit.requireSprite().setFlags(unit.requireSprite().getFlags() | Sprite.FLAG_ISCRIPT_NO_BREAK);
        }

        return AnimationStep.CONTINUE;
    }
}
