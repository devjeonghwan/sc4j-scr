package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.flow;

import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrPreviewSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code setspawnframe}, which has no effect here: its directional frame update applies at spawn time only. A
 * bounded update does not run it.
 */
public final class ScrSetSpawnFrame extends ScrInstruction {
    /**
     * Creates a {@link ScrSetSpawnFrame} instance.
     *
     * @param decoded the decoded {@code setspawnframe} instruction
     */
    public ScrSetSpawnFrame(@NotNull IscriptInstruction decoded) {
        super(decoded, ScrPreviewSupport.NEVER);
    }


    /**
     * Runs this instruction for the animated object of the context.
     *
     * @param context the running animation of one object
     * @return how the program continues
     * @throws UnsupportedOperationException if a bounded update meets the instruction and does not run it
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        this.checkPreviewSupport(context, target);

        return AnimationStep.CONTINUE;
    }
}
