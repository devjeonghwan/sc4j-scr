package org.fogofwarlabs.sc4j.scr.game.sprite.script.load;

import org.fogofwarlabs.sc4j.engine.animation.*;
import org.fogofwarlabs.sc4j.engine.animation.instruction.Jump;
import org.fogofwarlabs.sc4j.engine.animation.instruction.ShowFrame;
import org.fogofwarlabs.sc4j.engine.animation.instruction.Wait;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptFile;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptOpcodes;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.ScrInstruction;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.branch.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.combat.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.flow.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.image.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.overlay.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sound.ScrPlayRandomSound;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sound.ScrPlaySound;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sound.ScrPlaySoundBetween;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.sprite.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.instruction.unit.*;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Checks that the iscript library holds, at the position of every WORD offset, the instruction class, operands and
 * successor that {@link IscriptFile#decodeInstruction} decodes there, that the offset map is a bijection with the reachable
 * code first, and that the sets start the declared animations at the positions of their offsets.
 */
@RequiresInstallation
final class ScrIscriptImporterTest {
    /**
     * The ticks of the engine wait of {@code wait 0}.
     */
    private static final int WAIT_ZERO_TICKS = 256;
    /**
     * The operand of each branch opcode that holds its target offset.
     */
    private static final @NotNull Map<Integer, Integer> TARGET_OPERANDS = Map.of(IscriptOpcodes.GOTO, 0,
                                                                                 IscriptOpcodes.CALL, 0,
                                                                                 IscriptOpcodes.PWRUPCONDJMP, 0,
                                                                                 IscriptOpcodes.LIFTOFFCONDJMP, 0,
                                                                                 IscriptOpcodes.RANDCONDJMP, 1,
                                                                                 IscriptOpcodes.TRGTRANGECONDJMP, 1,
                                                                                 IscriptOpcodes.TRGTARCCONDJMP, 2,
                                                                                 IscriptOpcodes.CURDIRECTCONDJMP, 2);
    /**
     * The instruction class of each opcode.
     */
    private static final @NotNull Map<Integer, Class<?>> INSTRUCTION_CLASSES = Map.ofEntries(
            Map.entry(IscriptOpcodes.PLAYFRAM, ShowFrame.class),
            Map.entry(IscriptOpcodes.PLAYFRAMTILE, ScrPlayFrameTile.class),
            Map.entry(IscriptOpcodes.SETHORPOS, ScrSetHorizontalPosition.class),
            Map.entry(IscriptOpcodes.SETVERTPOS, ScrSetVerticalPosition.class),
            Map.entry(IscriptOpcodes.SETPOS, ScrSetPosition.class),
            Map.entry(IscriptOpcodes.WAIT, Wait.class),
            Map.entry(IscriptOpcodes.WAITRAND, ScrWaitRandom.class),
            Map.entry(IscriptOpcodes.GOTO, Jump.class),
            Map.entry(IscriptOpcodes.IMGOL, ScrCreateImage.class),
            Map.entry(IscriptOpcodes.IMGUL, ScrCreateImage.class),
            Map.entry(IscriptOpcodes.IMGOLORIG, ScrCreateOriginImage.class),
            Map.entry(IscriptOpcodes.SWITCHUL, ScrCreateOriginImage.class),
            Map.entry(IscriptOpcodes.UNKNOWN_0C, ScrNoOperation.class),
            Map.entry(IscriptOpcodes.IMGOLUSELO, ScrCreateImageAtOverlay.class),
            Map.entry(IscriptOpcodes.IMGULUSELO, ScrCreateImageAtOverlay.class),
            Map.entry(IscriptOpcodes.SPROL, ScrCreateSpriteOverlay.class),
            Map.entry(IscriptOpcodes.HIGHSPROL, ScrCreateHighSprite.class),
            Map.entry(IscriptOpcodes.LOWSPRUL, ScrCreateLowSprite.class),
            Map.entry(IscriptOpcodes.UFLUNSTABLE, ScrUnsupportedOpcode.class),
            Map.entry(IscriptOpcodes.SPRULUSELO, ScrCreateSpriteUnderlay.class),
            Map.entry(IscriptOpcodes.SPRUL, ScrCreateSpriteUnderlay.class),
            Map.entry(IscriptOpcodes.SPROLUSELO, ScrCreateSpriteAtOverlay.class),
            Map.entry(IscriptOpcodes.END, ScrEnd.class),
            Map.entry(IscriptOpcodes.SETFLIPSTATE, ScrSetFlipState.class),
            Map.entry(IscriptOpcodes.PLAYSND, ScrPlaySound.class),
            Map.entry(IscriptOpcodes.PLAYSNDRAND, ScrPlayRandomSound.class),
            Map.entry(IscriptOpcodes.PLAYSNDBTWN, ScrPlaySoundBetween.class),
            Map.entry(IscriptOpcodes.DOMISSILEDMG, ScrMissileDamage.class),
            Map.entry(IscriptOpcodes.ATTACKMELEE, ScrMeleeAttack.class),
            Map.entry(IscriptOpcodes.FOLLOWMAINGRAPHIC, ScrFollowMainGraphic.class),
            Map.entry(IscriptOpcodes.RANDCONDJMP, ScrRandomJump.class),
            Map.entry(IscriptOpcodes.TURNCCWISE, ScrTurn.class),
            Map.entry(IscriptOpcodes.TURNCWISE, ScrTurn.class),
            Map.entry(IscriptOpcodes.TURN1CWISE, ScrTurnOnce.class),
            Map.entry(IscriptOpcodes.TURNRAND, ScrTurnRandom.class),
            Map.entry(IscriptOpcodes.SETSPAWNFRAME, ScrSetSpawnFrame.class),
            Map.entry(IscriptOpcodes.SIGORDER, ScrSignalOrder.class),
            Map.entry(IscriptOpcodes.ATTACKWITH, ScrAttackWith.class),
            Map.entry(IscriptOpcodes.ATTACK, ScrAttack.class),
            Map.entry(IscriptOpcodes.CASTSPELL, ScrCastSpell.class),
            Map.entry(IscriptOpcodes.USEWEAPON, ScrUseWeapon.class),
            Map.entry(IscriptOpcodes.MOVE, ScrMove.class),
            Map.entry(IscriptOpcodes.GOTOREPEATATTK, ScrClearRepeatAttack.class),
            Map.entry(IscriptOpcodes.ENGFRAME, ScrEngineFrame.class),
            Map.entry(IscriptOpcodes.ENGSET, ScrEngineSet.class),
            Map.entry(IscriptOpcodes.UNKNOWN_2D, ScrUnhandledOpcode.class),
            Map.entry(IscriptOpcodes.NOBRKCODESTART, ScrStartNoBreak.class),
            Map.entry(IscriptOpcodes.NOBRKCODEEND, ScrEndNoBreak.class),
            Map.entry(IscriptOpcodes.IGNOREREST, ScrIgnoreRest.class),
            Map.entry(IscriptOpcodes.ATTKSHIFTPROJ, ScrAttackShiftedProjectile.class),
            Map.entry(IscriptOpcodes.TMPRMGRAPHICSTART, ScrHideImage.class),
            Map.entry(IscriptOpcodes.TMPRMGRAPHICEND, ScrShowImage.class),
            Map.entry(IscriptOpcodes.SETFLDIRECT, ScrSetDirection.class),
            Map.entry(IscriptOpcodes.CALL, ScrCall.class),
            Map.entry(IscriptOpcodes.RETURN, ScrReturn.class),
            Map.entry(IscriptOpcodes.SETFLSPEED, ScrSetTopSpeed.class),
            Map.entry(IscriptOpcodes.CREATEGASOVERLAYS, ScrCreateGasOverlays.class),
            Map.entry(IscriptOpcodes.PWRUPCONDJMP, ScrPowerupJump.class),
            Map.entry(IscriptOpcodes.TRGTRANGECONDJMP, ScrTargetRangeJump.class),
            Map.entry(IscriptOpcodes.TRGTARCCONDJMP, ScrTargetArcJump.class),
            Map.entry(IscriptOpcodes.CURDIRECTCONDJMP, ScrDirectionJump.class),
            Map.entry(IscriptOpcodes.IMGULNEXTID, ScrCreateNextImage.class),
            Map.entry(IscriptOpcodes.UNKNOWN_3E, ScrNoOperation.class),
            Map.entry(IscriptOpcodes.LIFTOFFCONDJMP, ScrLiftOffJump.class),
            Map.entry(IscriptOpcodes.WARPOVERLAY, ScrWarpOverlay.class),
            Map.entry(IscriptOpcodes.ORDERDONE, ScrOrderDone.class),
            Map.entry(IscriptOpcodes.GRDSPROL, ScrCreateGroundSprite.class),
            Map.entry(IscriptOpcodes.UNKNOWN_43, ScrNoOperation.class),
            Map.entry(IscriptOpcodes.DOGRDDAMAGE, ScrMissileDamage.class)
    );

    @Test
    void import_library_should_hold_the_decoded_instruction_of_every_iscript_offset() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            IscriptFile iscript = gameData.getIscript();
            AnimationProgram program = gameData.getIscriptProgram();
            ScrIscriptOffsets offsets = gameData.getIscriptOffsets();

            Assertions.assertEquals(ScrIscriptOffsets.OFFSET_COUNT, program.getLength());

            for (int offset = 0; offset < ScrIscriptOffsets.OFFSET_COUNT; offset++) {
                int position = offsets.getPosition(offset);
                AnimationInstruction instruction = program.get(position);

                Assertions.assertEquals(offset, offsets.getOffset(position), "offset " + offset);
                IscriptInstruction decoded;
                int size;

                try {
                    decoded = iscript.decodeInstruction(offset);
                    size    = iscript.getInstructionSize(offset);
                } catch (IOException exception) {
                    Assertions.assertInstanceOf(ScrUndecodableInstruction.class, instruction, "offset " + offset);

                    continue;
                }

                int opcode = decoded.getOpcode();
                int[] operands = decoded.getOperands();
                String context = "offset " + offset + ", opcode 0x" + Integer.toHexString(opcode);

                int next = offset + size;

                Assertions.assertEquals(next < ScrIscriptOffsets.OFFSET_COUNT ? offsets.getPosition(next) : ScrIscriptOffsets.OFFSET_COUNT,
                                        program.getNextPosition(position), context);

                if (opcode > IscriptFile.MAXIMUM_OPCODE) {
                    Assertions.assertSame(ScrSkipByte.INSTANCE, instruction, context);

                    continue;
                }

                Assertions.assertEquals(INSTRUCTION_CLASSES.get(opcode), instruction.getClass(), context);

                if (instruction instanceof ScrInstruction scriptInstruction) {
                    Assertions.assertEquals(offset, scriptInstruction.getDecoded().getOffset(), context);
                    Assertions.assertEquals(opcode, scriptInstruction.getDecoded().getOpcode(), context);
                    Assertions.assertArrayEquals(operands, scriptInstruction.getDecoded().getOperands(), context);
                } else if (instruction instanceof ShowFrame showFrame) {
                    Assertions.assertEquals(operands[0], showFrame.getFrame(), context);
                } else if (instruction instanceof Wait wait) {
                    Assertions.assertEquals(operands[0] == 0 ? WAIT_ZERO_TICKS : operands[0], wait.getTicks(), context);
                }

                Integer targetOperand = TARGET_OPERANDS.get(opcode);

                if (targetOperand != null) {
                    BranchInstruction branch = Assertions.assertInstanceOf(BranchInstruction.class, instruction, context);

                    Assertions.assertEquals(offsets.getPosition(operands[targetOperand]), branch.getTargetPosition(), context);
                } else {
                    Assertions.assertFalse(instruction instanceof BranchInstruction, context);
                }
            }
        }
    }

    @Test
    void create_should_place_the_reachable_offsets_first_in_ascending_order() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ScrIscriptOffsets offsets = gameData.getIscriptOffsets();
            int reachable = offsets.getReachableCount();

            for (IscriptHeader header : gameData.getIscript().getHeaders()) {
                for (IscriptHeader.Animation animation : header.getAnimations()) {
                    Assertions.assertTrue(offsets.getPosition(animation.getOffset()) < reachable, "offset " + animation.getOffset());
                }
            }

            for (int position = 1; position < ScrIscriptOffsets.OFFSET_COUNT; position++) {
                if (position != reachable) {
                    Assertions.assertTrue(offsets.getOffset(position - 1) < offsets.getOffset(position), "position " + position);
                }
            }

            Assertions.assertEquals(ScrIscriptOffsets.OFFSET_COUNT, offsets.getOffset(ScrIscriptOffsets.OFFSET_COUNT));
        }
    }

    @Test
    void import_library_should_start_each_declared_animation_at_its_native_offset() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            IscriptFile iscript = gameData.getIscript();
            AnimationLibrary library = gameData.getIscriptLibrary();

            Assertions.assertEquals(IscriptHeader.ANIMATION_TYPE_NAMES.length, library.getKinds().size());

            for (int scriptIdentifier = 0; scriptIdentifier < library.getSets().size(); scriptIdentifier++) {
                AnimationSet set = library.getSet(scriptIdentifier);
                IscriptHeader header = iscript.findHeader(scriptIdentifier);

                for (AnimationKind kind : library.getKinds()) {
                    int offset = header == null || kind.getIndex() > header.getType() ? 0 : header.getAnimationOffset(kind.getIndex());
                    String context = "script " + scriptIdentifier + ", " + kind;

                    if (offset == 0) {
                        Assertions.assertFalse(set.contains(kind), context);
                    } else {
                        Assertions.assertSame(gameData.getIscriptProgram(), set.getProgram(kind), context);
                        Assertions.assertEquals(gameData.getIscriptOffsets().getPosition(offset), set.getStartPosition(kind), context);
                    }
                }
            }

            for (IscriptHeader header : iscript.getHeaders()) {
                Assertions.assertTrue(header.getId() < library.getSets().size(), "script " + header.getId());
            }
        }
    }
}
