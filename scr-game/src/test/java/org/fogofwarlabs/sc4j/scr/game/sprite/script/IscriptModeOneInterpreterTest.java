package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptFile;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptOpcodes;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks mode-one operand consumption and conditional random jumps.
 */
@RequiresInstallation
final class IscriptModeOneInterpreterTest {
    /**
     * The instruction budget of every mode-one run.
     */
    private static final int INSTRUCTION_LIMIT = 4096;
    /**
     * The random seed of the fall-through runs and the factor of the per-offset seeds of the {@code randcondjmp} runs.
     */
    private static final int RANDOM_SEED = 0x1234567;
    /**
     * The random source of {@code randcondjmp}.
     */
    private static final int CONDITION_JUMP_RANDOM_SOURCE = 7;
    /**
     * The mask retaining the low random byte {@code randcondjmp} compares with its chance.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The number of random numbers one {@code randcondjmp} draws.
     */
    private static final int JUMP_DRAW_COUNT = 1;
    /**
     * The pool slot of the scratch unit.
     */
    private static final int UNIT_POOL_INDEX = 1;

    /**
     * The opcodes that consume their operands and fall through in mode one.
     */
    private static final int @NotNull [] FALL_THROUGH_OPCODES = {
            IscriptOpcodes.DOMISSILEDMG, IscriptOpcodes.ATTACKMELEE, IscriptOpcodes.TURNRAND, IscriptOpcodes.GOTOREPEATATTK,
            IscriptOpcodes.ENGFRAME, IscriptOpcodes.ENGSET, IscriptOpcodes.UNKNOWN_2D, IscriptOpcodes.NOBRKCODESTART,
            IscriptOpcodes.NOBRKCODEEND, IscriptOpcodes.IGNOREREST, IscriptOpcodes.ATTKSHIFTPROJ, IscriptOpcodes.TMPRMGRAPHICSTART,
            IscriptOpcodes.TMPRMGRAPHICEND, IscriptOpcodes.SETFLDIRECT, IscriptOpcodes.TRGTRANGECONDJMP, IscriptOpcodes.TRGTARCCONDJMP,
            IscriptOpcodes.CURDIRECTCONDJMP, IscriptOpcodes.LIFTOFFCONDJMP, IscriptOpcodes.DOGRDDAMAGE
    };

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    /**
     * Runs a mode-one step after earlier random draws and returns its outcome.
     *
     * @param programCounter the iscript offset the step starts at
     * @param seed           the random seed
     * @param draws          the random numbers drawn before the step
     * @return the resulting script state, speed and random state, or the simple name of the exception the step throws
     */
    private static @NotNull String runModeOneStep(int programCounter, int seed, int draws) {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(seed));

        for (int draw = 0; draw < draws; draw++) {
            game.getRandom().next(CONDITION_JUMP_RANDOM_SOURCE);
        }

        IscriptModeOneInterpreter interpreter = new IscriptModeOneInterpreter();

        interpreter.start(programCounter, 0, 0, INSTRUCTION_LIMIT);

        try {
            int speed = interpreter.step(game, UnitTestUtils.createUnit(UNIT_POOL_INDEX), 0);

            return "pc " + interpreter.getProgramCounter() + " return " + interpreter.getReturnAddress() + " wait " +
                   interpreter.getWait() + " speed " + speed + " random " + game.getRandom().getState();
        } catch (IOException | RuntimeException exception) {
            return exception.getClass().getSimpleName();
        }
    }


    @Test
    void fall_through_opcodes_only_consume_their_operands() throws IOException {
        Assertions.assertNotNull(gameData);

        IscriptFile script = gameData.getIscript();
        Set<Integer> checked = new HashSet<>();

        for (IscriptHeader header : script.getHeaders()) {
            for (IscriptHeader.Animation animation : header.getAnimations()) {
                for (IscriptInstruction instruction : animation.getInstructions()) {
                    int offset = instruction.getOffset();
                    int opcode = instruction.getOpcode();
                    boolean fallsThrough = false;

                    for (int fallThroughOpcode : FALL_THROUGH_OPCODES) {
                        if (fallThroughOpcode == opcode) {
                            fallsThrough = true;

                            break;
                        }
                    }

                    if (!fallsThrough || !checked.add(offset)) {
                        continue;
                    }

                    int next = offset + script.getInstructionSize(offset);

                    Assertions.assertEquals(runModeOneStep(offset, RANDOM_SEED, 0), runModeOneStep(next, RANDOM_SEED, 0),
                                            "Opcode 0x" + Integer.toHexString(instruction.getOpcode()) + " at " + offset);
                }
            }
        }

        Assertions.assertFalse(checked.isEmpty(), "The installed iscript.bin uses the opcodes.");
    }

    @Test
    void random_conditional_jump_draws_source_seven_and_jumps() throws IOException {
        Assertions.assertNotNull(gameData);

        IscriptFile script = gameData.getIscript();
        Set<Integer> checked = new HashSet<>();
        int jumps = 0;

        for (IscriptHeader header : script.getHeaders()) {
            for (IscriptHeader.Animation animation : header.getAnimations()) {
                for (IscriptInstruction instruction : animation.getInstructions()) {
                    int offset = instruction.getOffset();

                    if (instruction.getOpcode() != IscriptOpcodes.RANDCONDJMP || !checked.add(offset)) {
                        continue;
                    }

                    int[] operands = instruction.getOperands();
                    int seed = RANDOM_SEED * offset;
                    int draw = new LcgRandom(seed).next(CONDITION_JUMP_RANDOM_SOURCE) & BYTE_MASK;
                    boolean jump = draw <= operands[0];
                    int continuation = jump ? operands[1] : offset + script.getInstructionSize(offset);

                    if (jump) {
                        jumps++;
                    }

                    Assertions.assertEquals(runModeOneStep(continuation, seed, JUMP_DRAW_COUNT), runModeOneStep(offset, seed, 0),
                                            "randcondjmp at " + offset);
                }
            }
        }

        Assertions.assertFalse(checked.isEmpty(), "The installed iscript.bin uses randcondjmp.");
        Assertions.assertTrue(jumps > 0 && jumps < checked.size(), "Both outcomes are covered.");
    }
}
