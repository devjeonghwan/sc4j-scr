package org.fogofwarlabs.sc4j.scr.game.sprite.script.load;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.*;
import org.fogofwarlabs.sc4j.engine.animation.instruction.Jump;
import org.fogofwarlabs.sc4j.engine.animation.instruction.ShowFrame;
import org.fogofwarlabs.sc4j.engine.animation.instruction.Wait;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptFile;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptOpcodes;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rebuilds {@code iscript.bin} as an {@link AnimationLibrary}: one program, the 28 iscript animation kinds and one
 * animation set per script identifier.
 * <p>
 * The program has a position for every WORD offset of the file, {@code 0} through {@code 0xFFFF}, in the order of the
 * {@link ScrIscriptOffsets} map: the reachable code first, so that it stays cache-local. Its successor table names the
 * position of the offset after each instruction, and branch targets are positions. The image state keeps positions; an
 * image's program counter, return address, saved bytes and state hash convert them to native offsets through the map, and
 * every offset an image can hold, also one in the middle of an instruction or after the file, has its position. An offset that does not decode holds a
 * {@link ScrUndecodableInstruction}, and a byte above the last opcode a {@link ScrSkipByte}.
 * <p>
 * The kinds have the native animation bytes as indices ({@code Init} {@code 0} through {@code Enable} {@code 27}), and the
 * sets the script identifiers: a set holds the animations its header declares, and an identifier without a header has an
 * empty set. The interpreter resolves the start offset of a requested animation through the native header table
 * ({@link IscriptFile#getAnimationOffset}), which also reads entries past the declared count.
 */
public final class ScrIscriptImporter {
    /**
     * The name of the one program.
     */
    public static final @NotNull String PROGRAM_NAME = "iscript";
    /**
     * The CASC path of the animation scripts.
     */
    public static final @NotNull String ISCRIPT_PATH = "scripts/iscript.bin";
    /**
     * The ticks of {@code wait 0}: the wait timer becomes {@code 255}, as the native timer wraps.
     */
    private static final int WAIT_ZERO_TICKS = 256;
    /**
     * The sign of a counterclockwise turn.
     */
    private static final int COUNTERCLOCKWISE = -1;
    /**
     * The sign of a clockwise turn.
     */
    private static final int CLOCKWISE = 1;
    /**
     * The mask of a WORD offset, which a return address keeps.
     */
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Creates the instruction of a decoded opcode.
     *
     * @param decoded the decoded instruction
     * @param size    the size of the instruction in bytes
     * @param offsets the offset map, which gives the positions of branch targets and return addresses
     * @return the created instruction
     * @throws IllegalStateException if the opcode has no instruction class
     */
    private static @NotNull AnimationInstruction createInstruction(@NotNull IscriptInstruction decoded, int size, @NotNull ScrIscriptOffsets offsets) {
        int opcode = decoded.getOpcode();

        if (opcode > IscriptFile.MAXIMUM_OPCODE) {
            return ScrSkipByte.INSTANCE;
        }

        int[] operands = decoded.getOperands();
        int target = ScrIscriptOffsets.findBranchTarget(decoded);
        int targetPosition = target < 0 ? -1 : offsets.getPosition(target);

        return switch (opcode) {
            case IscriptOpcodes.PLAYFRAM -> new ShowFrame(operands[0]);
            case IscriptOpcodes.PLAYFRAMTILE -> new ScrPlayFrameTile(decoded);
            case IscriptOpcodes.SETHORPOS -> new ScrSetHorizontalPosition(decoded);
            case IscriptOpcodes.SETVERTPOS -> new ScrSetVerticalPosition(decoded);
            case IscriptOpcodes.SETPOS -> new ScrSetPosition(decoded);
            case IscriptOpcodes.WAIT -> new Wait(operands[0] == 0 ? WAIT_ZERO_TICKS : operands[0]);
            case IscriptOpcodes.WAITRAND -> new ScrWaitRandom(decoded);
            case IscriptOpcodes.GOTO -> new Jump(targetPosition);
            case IscriptOpcodes.IMGOL -> new ScrCreateImage(decoded, IscriptExecutor.ORDER_ABOVE);
            case IscriptOpcodes.IMGUL -> new ScrCreateImage(decoded, IscriptExecutor.ORDER_BELOW);
            case IscriptOpcodes.IMGOLORIG -> new ScrCreateOriginImage(decoded, IscriptExecutor.ORDER_ABOVE);
            case IscriptOpcodes.SWITCHUL -> new ScrCreateOriginImage(decoded, IscriptExecutor.ORDER_BELOW);
            case IscriptOpcodes.UNKNOWN_0C, IscriptOpcodes.UNKNOWN_3E, IscriptOpcodes.UNKNOWN_43 -> new ScrNoOperation(decoded);
            case IscriptOpcodes.IMGOLUSELO -> new ScrCreateImageAtOverlay(decoded, IscriptExecutor.ORDER_ABOVE);
            case IscriptOpcodes.IMGULUSELO -> new ScrCreateImageAtOverlay(decoded, IscriptExecutor.ORDER_BELOW);
            case IscriptOpcodes.SPROL -> new ScrCreateSpriteOverlay(decoded);
            case IscriptOpcodes.HIGHSPROL -> new ScrCreateHighSprite(decoded);
            case IscriptOpcodes.LOWSPRUL -> new ScrCreateLowSprite(decoded);
            case IscriptOpcodes.UFLUNSTABLE -> new ScrUnsupportedOpcode(decoded);
            case IscriptOpcodes.SPRULUSELO -> new ScrCreateSpriteUnderlay(decoded, true);
            case IscriptOpcodes.SPRUL -> new ScrCreateSpriteUnderlay(decoded, false);
            case IscriptOpcodes.SPROLUSELO -> new ScrCreateSpriteAtOverlay(decoded);
            case IscriptOpcodes.END -> new ScrEnd(decoded);
            case IscriptOpcodes.SETFLIPSTATE -> new ScrSetFlipState(decoded);
            case IscriptOpcodes.PLAYSND -> new ScrPlaySound(decoded);
            case IscriptOpcodes.PLAYSNDRAND -> new ScrPlayRandomSound(decoded);
            case IscriptOpcodes.PLAYSNDBTWN -> new ScrPlaySoundBetween(decoded);
            case IscriptOpcodes.DOMISSILEDMG, IscriptOpcodes.DOGRDDAMAGE -> new ScrMissileDamage(decoded);
            case IscriptOpcodes.ATTACKMELEE -> new ScrMeleeAttack(decoded);
            case IscriptOpcodes.FOLLOWMAINGRAPHIC -> new ScrFollowMainGraphic(decoded);
            case IscriptOpcodes.RANDCONDJMP -> new ScrRandomJump(decoded, targetPosition);
            case IscriptOpcodes.TURNCCWISE -> new ScrTurn(decoded, COUNTERCLOCKWISE);
            case IscriptOpcodes.TURNCWISE -> new ScrTurn(decoded, CLOCKWISE);
            case IscriptOpcodes.TURN1CWISE -> new ScrTurnOnce(decoded);
            case IscriptOpcodes.TURNRAND -> new ScrTurnRandom(decoded);
            case IscriptOpcodes.SETSPAWNFRAME -> new ScrSetSpawnFrame(decoded);
            case IscriptOpcodes.SIGORDER -> new ScrSignalOrder(decoded);
            case IscriptOpcodes.ATTACKWITH -> new ScrAttackWith(decoded);
            case IscriptOpcodes.ATTACK -> new ScrAttack(decoded);
            case IscriptOpcodes.CASTSPELL -> new ScrCastSpell(decoded);
            case IscriptOpcodes.USEWEAPON -> new ScrUseWeapon(decoded);
            case IscriptOpcodes.MOVE -> new ScrMove(decoded);
            case IscriptOpcodes.GOTOREPEATATTK -> new ScrClearRepeatAttack(decoded);
            case IscriptOpcodes.ENGFRAME -> new ScrEngineFrame(decoded);
            case IscriptOpcodes.ENGSET -> new ScrEngineSet(decoded);
            case IscriptOpcodes.UNKNOWN_2D -> new ScrUnhandledOpcode(decoded);
            case IscriptOpcodes.NOBRKCODESTART -> new ScrStartNoBreak(decoded);
            case IscriptOpcodes.NOBRKCODEEND -> new ScrEndNoBreak(decoded);
            case IscriptOpcodes.IGNOREREST -> new ScrIgnoreRest(decoded);
            case IscriptOpcodes.ATTKSHIFTPROJ -> new ScrAttackShiftedProjectile(decoded);
            case IscriptOpcodes.TMPRMGRAPHICSTART -> new ScrHideImage(decoded);
            case IscriptOpcodes.TMPRMGRAPHICEND -> new ScrShowImage(decoded);
            case IscriptOpcodes.SETFLDIRECT -> new ScrSetDirection(decoded);
            case IscriptOpcodes.CALL -> new ScrCall(decoded, targetPosition, offsets.getPosition((decoded.getOffset() + size) & WORD_MASK));
            case IscriptOpcodes.RETURN -> new ScrReturn(decoded);
            case IscriptOpcodes.SETFLSPEED -> new ScrSetTopSpeed(decoded);
            case IscriptOpcodes.CREATEGASOVERLAYS -> new ScrCreateGasOverlays(decoded);
            case IscriptOpcodes.PWRUPCONDJMP -> new ScrPowerupJump(decoded, targetPosition);
            case IscriptOpcodes.TRGTRANGECONDJMP -> new ScrTargetRangeJump(decoded, targetPosition);
            case IscriptOpcodes.TRGTARCCONDJMP -> new ScrTargetArcJump(decoded, targetPosition);
            case IscriptOpcodes.CURDIRECTCONDJMP -> new ScrDirectionJump(decoded, targetPosition);
            case IscriptOpcodes.IMGULNEXTID -> new ScrCreateNextImage(decoded);
            case IscriptOpcodes.LIFTOFFCONDJMP -> new ScrLiftOffJump(decoded, targetPosition);
            case IscriptOpcodes.WARPOVERLAY -> new ScrWarpOverlay(decoded);
            case IscriptOpcodes.ORDERDONE -> new ScrOrderDone(decoded);
            case IscriptOpcodes.GRDSPROL -> new ScrCreateGroundSprite(decoded);
            default -> throw Exceptions.newIllegalStateException("The iscript opcode 0x{0} has no instruction.", Integer.toHexString(opcode));
        };
    }

    /**
     * @param scriptIdentifier the script identifier
     * @return the name of the animation set of the script identifier
     */
    private static @NotNull String getSetName(int scriptIdentifier) {
        return "iscript " + scriptIdentifier;
    }

    /**
     * Reads the animation scripts of a storage and creates their animation library, as the game data does.
     *
     * @param storage the open storage
     * @return the created library
     * @throws IOException if {@code iscript.bin} cannot be read or is malformed
     */
    public static @NotNull AnimationLibrary readLibrary(@NotNull CascStorage storage) throws IOException {
        IscriptFile iscript = IscriptFile.parse(storage.readFile(ISCRIPT_PATH));

        return new ScrIscriptImporter(iscript, ScrIscriptOffsets.create(iscript)).importLibrary();
    }


    private final @NotNull IscriptFile iscript;
    private final @NotNull ScrIscriptOffsets offsets;

    /**
     * Creates a {@link ScrIscriptImporter} instance.
     *
     * @param iscript the parsed {@code iscript.bin}
     * @param offsets the offset map of the iscript, which orders the program positions
     */
    public ScrIscriptImporter(@NotNull IscriptFile iscript, @NotNull ScrIscriptOffsets offsets) {
        this.iscript = iscript;
        this.offsets = offsets;
    }

    /**
     * Creates the animation library of the iscript.
     *
     * @return the created library
     */
    public @NotNull AnimationLibrary importLibrary() {
        IscriptFile iscript = this.iscript;
        List<AnimationKind> kinds = new ArrayList<>(IscriptHeader.ANIMATION_TYPE_NAMES.length);

        for (int kindIndex = 0; kindIndex < IscriptHeader.ANIMATION_TYPE_NAMES.length; kindIndex++) {
            kinds.add(AnimationKind.of(kindIndex, IscriptHeader.ANIMATION_TYPE_NAMES[kindIndex]));
        }

        AnimationProgram program = this.createProgram();
        int setCount = 0;

        for (IscriptHeader header : iscript.getHeaders()) {
            setCount = Math.max(setCount, header.getId() + 1);
        }

        List<AnimationSet> sets = new ArrayList<>(setCount);

        for (int scriptIdentifier = 0; scriptIdentifier < setCount; scriptIdentifier++) {
            AnimationSet.Builder builder = new AnimationSet.Builder(scriptIdentifier, getSetName(scriptIdentifier));
            IscriptHeader header = iscript.findHeader(scriptIdentifier);

            if (header != null) {
                for (int kindIndex = 0; kindIndex <= Math.min(header.getType(), kinds.size() - 1); kindIndex++) {
                    int offset = header.getAnimationOffset(kindIndex);

                    if (offset != 0) {
                        builder.setAnimation(kinds.get(kindIndex), program, this.offsets.getPosition(offset));
                    }
                }
            }

            sets.add(builder.build());
        }

        return new AnimationLibrary(kinds, List.of(program), sets);
    }

    /**
     * Creates the program: the instruction decoded at every WORD offset in the order of the offset map, the successor table
     * and a label at each declared animation start.
     *
     * @return the created program
     */
    private @NotNull AnimationProgram createProgram() {
        IscriptFile iscript = this.iscript;
        ScrUndecodableInstruction undecodable = new ScrUndecodableInstruction(iscript, this.offsets);
        AnimationInstruction[] instructions = new AnimationInstruction[ScrIscriptOffsets.OFFSET_COUNT];
        int[] successors = new int[ScrIscriptOffsets.OFFSET_COUNT];

        for (int position = 0; position < ScrIscriptOffsets.OFFSET_COUNT; position++) {
            instructions[position] = this.createInstructionAt(position, undecodable, successors);
        }

        List<AnimationLabel> labels = new ArrayList<>();

        for (IscriptHeader header : iscript.getHeaders()) {
            for (IscriptHeader.Animation animation : header.getAnimations()) {
                String kindName = animation.getTypeIndex() < IscriptHeader.ANIMATION_TYPE_NAMES.length
                                  ? IscriptHeader.ANIMATION_TYPE_NAMES[animation.getTypeIndex()]
                                  : Integer.toString(animation.getTypeIndex());

                labels.add(new AnimationLabel(getSetName(header.getId()) + " " + kindName, this.offsets.getPosition(animation.getOffset())));
            }
        }

        return new AnimationProgram(0, PROGRAM_NAME, Arrays.asList(instructions), labels, successors);
    }

    /**
     * Creates the instruction at a position and stores the position after it in the successor table.
     *
     * @param position    the program position
     * @param undecodable the instruction of every offset that does not decode
     * @param successors  the successor table
     * @return the created instruction
     */
    private @NotNull AnimationInstruction createInstructionAt(int position, @NotNull ScrUndecodableInstruction undecodable, int @NotNull [] successors) {
        int offset = this.offsets.getOffset(position);
        IscriptInstruction decoded;
        int size;

        try {
            decoded = this.iscript.decodeInstruction(offset);
            size    = this.iscript.getInstructionSize(offset);
        } catch (IOException exception) {
            successors[position] = position + 1;

            return undecodable;
        }

        int next = offset + size;

        successors[position] = next < ScrIscriptOffsets.OFFSET_COUNT ? this.offsets.getPosition(next) : ScrIscriptOffsets.OFFSET_COUNT;

        return createInstruction(decoded, size, this.offsets);
    }
}
