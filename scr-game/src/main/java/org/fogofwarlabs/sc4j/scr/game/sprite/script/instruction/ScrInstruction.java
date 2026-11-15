package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationInstruction;
import org.fogofwarlabs.sc4j.engine.animation.InstructionBudget;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an iscript opcode of {@code iscript.bin} as an instruction of the SC:R iscript program. It keeps the decoded
 * instruction it was created from (offset, opcode and operands) for diagnostics, and runs on the image, sprite and unit
 * of the {@link ScrImageTarget} of its context ({@link #getTarget}).
 * <p>
 * A bounded update (one with an instruction budget) runs only the opcodes that support it ({@link ScrPreviewSupport});
 * an instruction whose support is not {@link ScrPreviewSupport#ALWAYS} checks it first ({@link #checkPreviewSupport})
 * and fails before it changes anything. The bytes above the last opcode are not opcodes and are not represented
 * by this class.
 */
public abstract class ScrInstruction extends AnimationInstruction {
    /**
     * Returns the SC:R target of a running iscript program.
     *
     * @param context the running animation of the image
     * @return the image, sprite, unit and game of the update
     */
    protected static @NotNull ScrImageTarget getTarget(@NotNull AnimationContext context) {
        return (ScrImageTarget) context.getTarget();
    }

    /**
     * Returns the number of instructions a part of the game that this instruction calls may run, as the interpreter passes
     * it on: what the budget of a bounded update still allows, or no bound.
     *
     * @param context the running animation of the image
     * @return the remaining budget, or {@link Integer#MAX_VALUE} for an unbounded update
     */
    protected static int getRemainingInstructions(@NotNull AnimationContext context) {
        InstructionBudget budget = context.getInstructionBudget();

        return budget == null ? Integer.MAX_VALUE : budget.getRemaining();
    }


    private final @NotNull IscriptInstruction decoded;
    private final @NotNull ScrPreviewSupport previewSupport;

    /**
     * Creates a {@link ScrInstruction} instance that every bounded update runs.
     *
     * @param decoded the decoded instruction
     */
    protected ScrInstruction(@NotNull IscriptInstruction decoded) {
        this(decoded, ScrPreviewSupport.ALWAYS);
    }

    /**
     * Creates a {@link ScrInstruction} instance.
     *
     * @param decoded        the decoded instruction
     * @param previewSupport whether a bounded update runs the instruction
     */
    protected ScrInstruction(@NotNull IscriptInstruction decoded, @NotNull ScrPreviewSupport previewSupport) {
        this.decoded        = decoded;
        this.previewSupport = previewSupport;
    }

    /**
     * @return the decoded instruction this instruction was created from
     */
    public @NotNull IscriptInstruction getDecoded() {
        return this.decoded;
    }


    /**
     * Fails if a bounded update meets this instruction and does not run it ({@link ScrPreviewSupport}).
     *
     * @param context the running animation of the image
     * @param target  the image, sprite, unit and game of the update
     * @throws UnsupportedOperationException if the update is bounded and does not run the instruction
     */
    protected final void checkPreviewSupport(@NotNull AnimationContext context, @NotNull ScrImageTarget target) {
        if (context.getInstructionBudget() != null && !this.previewSupport.isSupported(target.getUnit() != null)) {
            throw Exceptions.newUnsupportedOperationException("Image preview does not support {0} for image {1} at offset {2}",
                                                              this.decoded.getName(),
                                                              target.getImage().getImageTypeId(),
                                                              this.decoded.getOffset());
        }
    }

    /**
     * Returns the unit context of an opcode that needs one.
     *
     * @param target the image, sprite, unit and game of the update
     * @return the unit context
     * @throws IllegalStateException if the update has no unit context
     */
    protected final @NotNull Unit requireUnit(@NotNull ScrImageTarget target) {
        Unit unit = target.getUnit();

        if (unit == null) {
            throw Exceptions.newIllegalStateException("The opcode needs a unit context: {0} at offset {1}",
                                                      this.decoded.getName(),
                                                      this.decoded.getOffset());
        }

        return unit;
    }
}
