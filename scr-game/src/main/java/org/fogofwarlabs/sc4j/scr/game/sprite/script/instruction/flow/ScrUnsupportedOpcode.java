package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.flow;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrImageTarget;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrPreviewSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code uflunstable}, whose standalone movement entities the unit and sprite pools do not represent: running it
 * fails. A bounded update does not run it.
 */
public final class ScrUnsupportedOpcode extends ScrInstruction {
    /**
     * Creates a {@link ScrUnsupportedOpcode} instance.
     *
     * @param decoded the decoded {@code uflunstable} instruction
     */
    public ScrUnsupportedOpcode(@NotNull IscriptInstruction decoded) {
        super(decoded, ScrPreviewSupport.NEVER);
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Fails with an {@link UnsupportedOperationException}.
     *
     * @param context the running animation of one object
     * @return how the program continues
     * @throws UnsupportedOperationException if a bounded update meets the instruction and does not run it
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        ScrImageTarget target = getTarget(context);

        this.checkPreviewSupport(context, target);

        throw Exceptions.newUnsupportedOperationException("The uflunstable opcode at offset {0} requires a standalone movement-entity pool.",
                                                          this.getDecoded().getOffset());
    }
}
