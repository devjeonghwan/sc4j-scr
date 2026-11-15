package org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction;

import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptModeOneInterpreter;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Represents an iscript instruction with its own meaning in mode one, the path of the interpreter that the Walking speed
 * sampler and the movement prediction run on a scratch script state ({@link IscriptModeOneInterpreter}). In mode one an
 * instruction without this interface only goes on at the next offset; the engine {@code wait} ends the step and the engine
 * {@code goto} jumps.
 */
public interface ScrModeOneStep {
    /**
     * Runs this instruction in mode one on the scratch state of the interpreter, whose program counter is the offset of the
     * instruction.
     *
     * @param interpreter  the mode-one interpreter
     * @param game         the game, whose random generator the instruction may draw
     * @param unit         the unit whose speed modifiers the instruction may apply
     * @param nextPosition the offset after the instruction
     * @throws IOException if the instruction does not decode
     */
    void stepModeOne(@NotNull IscriptModeOneInterpreter interpreter, @NotNull Game game, @NotNull Unit unit, int nextPosition) throws IOException;
}
