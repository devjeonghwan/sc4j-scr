package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.flow;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationContext;
import org.fogofwarlabs.sc4j.engine.animation.AnimationInstruction;
import org.fogofwarlabs.sc4j.engine.animation.AnimationStep;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptFile;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptModeOneInterpreter;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrModeOneStep;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.load.ScrIscriptOffsets;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Holds the position of an iscript offset that does not decode: the operands run past the end of {@code iscript.bin}, or
 * the offset lies after it. Running it fails as decoding the offset fails.
 */
public final class ScrUndecodableInstruction extends AnimationInstruction implements ScrModeOneStep {
    private final @NotNull IscriptFile iscript;
    private final @NotNull ScrIscriptOffsets offsets;

    /**
     * Creates a {@link ScrUndecodableInstruction} instance; one instance serves every undecodable offset of the file.
     *
     * @param iscript the iscript whose offsets the instruction stands for
     * @param offsets the offset map of the program, which gives the offset of a position
     */
    public ScrUndecodableInstruction(@NotNull IscriptFile iscript, @NotNull ScrIscriptOffsets offsets) {
        this.iscript = iscript;
        this.offsets = offsets;
    }

    /**
     * Decodes the offset the instruction stands for again, which throws the decoding failure.
     *
     * @param offset the iscript offset
     * @throws IOException           if the offset does not decode, which is always the case
     * @throws IllegalStateException if the offset decodes after all
     */
    public void throwDecodingFailure(int offset) throws IOException {
        this.iscript.decodeInstruction(offset);
        this.iscript.getInstructionSize(offset);

        throw Exceptions.newIllegalStateException("The iscript offset {0} decodes, but the program holds no instruction for it.", offset);
    }


    /**
     * Runs this instruction for the animated object of the context.
     * <p>
     * Fails with an {@link IllegalStateException} whose cause is the decoding failure of the offset.
     *
     * @param context the running animation of one object
     * @return never
     * @throws IllegalStateException always
     */
    @Override
    public @NotNull AnimationStep execute(@NotNull AnimationContext context) {
        int offset = this.offsets.getOffset(context.getPosition());

        try {
            this.throwDecodingFailure(offset);
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "Failed to decode the iscript instruction at offset {0}", offset);
        }

        throw Exceptions.newIllegalStateException("The iscript offset {0} decodes, but the program holds no instruction for it.", offset);
    }

    /**
     * Runs this instruction in mode one on the scratch state of the interpreter, whose program counter is the offset of the
     * instruction.
     * <p>
     * Throws the decoding failure of the offset.
     *
     * @param interpreter  the mode-one interpreter
     * @param game         the game, whose random generator the instruction may draw
     * @param unit         the unit whose speed modifiers the instruction may apply
     * @param nextPosition the offset after the instruction
     * @throws IOException           if the instruction does not decode, which is always the case
     * @throws IllegalStateException if the offset decodes after all
     */
    @Override
    public void stepModeOne(@NotNull IscriptModeOneInterpreter interpreter, @NotNull Game game, @NotNull Unit unit, int nextPosition) throws IOException {
        this.throwDecodingFailure(interpreter.getProgramCounter());
    }
}
