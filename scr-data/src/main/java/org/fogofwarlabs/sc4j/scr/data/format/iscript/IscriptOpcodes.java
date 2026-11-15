package org.fogofwarlabs.sc4j.scr.data.format.iscript;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * Provides fixed and count-prefixed iscript opcode operand layouts. The {@code playsndrand} and {@code attackmelee} opcodes carry a count byte
 * followed by that many sound identifiers.
 */
public final class IscriptOpcodes {
    /**
     * An unsigned {@code 8}-bit operand.
     */
    public static final int OPERAND_UNSIGNED_BYTE = 1;
    /**
     * A signed {@code 8}-bit operand.
     */
    public static final int OPERAND_SIGNED_BYTE = 2;
    /**
     * An unsigned {@code 16}-bit operand.
     */
    public static final int OPERAND_UNSIGNED_WORD = 3;
    /**
     * A variable-length operand list.
     */
    public static final int OPERAND_VARIABLE = 4;

    // Opcode mnemonics; unnamed entries use UNKNOWN_* identifiers.
    /**
     * Opcode {@code playfram}.
     */
    public static final int PLAYFRAM = 0x00;
    /**
     * Opcode {@code playframtile}.
     */
    public static final int PLAYFRAMTILE = 0x01;
    /**
     * Opcode {@code sethorpos}.
     */
    public static final int SETHORPOS = 0x02;
    /**
     * Opcode {@code setvertpos}.
     */
    public static final int SETVERTPOS = 0x03;
    /**
     * Opcode {@code setpos}.
     */
    public static final int SETPOS = 0x04;
    /**
     * Opcode {@code wait}.
     */
    public static final int WAIT = 0x05;
    /**
     * Opcode {@code waitrand}.
     */
    public static final int WAITRAND = 0x06;
    /**
     * Opcode {@code goto}.
     */
    public static final int GOTO = 0x07;
    /**
     * Opcode {@code imgol}.
     */
    public static final int IMGOL = 0x08;
    /**
     * Opcode {@code imgul}.
     */
    public static final int IMGUL = 0x09;
    /**
     * Opcode {@code imgolorig}.
     */
    public static final int IMGOLORIG = 0x0A;
    /**
     * Opcode {@code switchul}.
     */
    public static final int SWITCHUL = 0x0B;
    /**
     * Opcode {@code __0c}.
     */
    public static final int UNKNOWN_0C = 0x0C;
    /**
     * Opcode {@code imgoluselo}.
     */
    public static final int IMGOLUSELO = 0x0D;
    /**
     * Opcode {@code imguluselo}.
     */
    public static final int IMGULUSELO = 0x0E;
    /**
     * Opcode {@code sprol}.
     */
    public static final int SPROL = 0x0F;
    /**
     * Opcode {@code highsprol}.
     */
    public static final int HIGHSPROL = 0x10;
    /**
     * Opcode {@code lowsprul}.
     */
    public static final int LOWSPRUL = 0x11;
    /**
     * Opcode {@code uflunstable}.
     */
    public static final int UFLUNSTABLE = 0x12;
    /**
     * Opcode {@code spruluselo}.
     */
    public static final int SPRULUSELO = 0x13;
    /**
     * Opcode {@code sprul}.
     */
    public static final int SPRUL = 0x14;
    /**
     * Opcode {@code sproluselo}.
     */
    public static final int SPROLUSELO = 0x15;
    /**
     * Opcode {@code end}.
     */
    public static final int END = 0x16;
    /**
     * Opcode {@code setflipstate}.
     */
    public static final int SETFLIPSTATE = 0x17;
    /**
     * Opcode {@code playsnd}.
     */
    public static final int PLAYSND = 0x18;
    /**
     * Opcode {@code playsndrand}.
     */
    public static final int PLAYSNDRAND = 0x19;
    /**
     * Opcode {@code playsndbtwn}.
     */
    public static final int PLAYSNDBTWN = 0x1A;
    /**
     * Opcode {@code domissiledmg}.
     */
    public static final int DOMISSILEDMG = 0x1B;
    /**
     * Opcode {@code attackmelee}.
     */
    public static final int ATTACKMELEE = 0x1C;
    /**
     * Opcode {@code followmaingraphic}.
     */
    public static final int FOLLOWMAINGRAPHIC = 0x1D;
    /**
     * Opcode {@code randcondjmp}.
     */
    public static final int RANDCONDJMP = 0x1E;
    /**
     * Opcode {@code turnccwise}.
     */
    public static final int TURNCCWISE = 0x1F;
    /**
     * Opcode {@code turncwise}.
     */
    public static final int TURNCWISE = 0x20;
    /**
     * Opcode {@code turn1cwise}.
     */
    public static final int TURN1CWISE = 0x21;
    /**
     * Opcode {@code turnrand}.
     */
    public static final int TURNRAND = 0x22;
    /**
     * Opcode {@code setspawnframe}.
     */
    public static final int SETSPAWNFRAME = 0x23;
    /**
     * Opcode {@code sigorder}.
     */
    public static final int SIGORDER = 0x24;
    /**
     * Opcode {@code attackwith}.
     */
    public static final int ATTACKWITH = 0x25;
    /**
     * Opcode {@code attack}.
     */
    public static final int ATTACK = 0x26;
    /**
     * Opcode {@code castspell}.
     */
    public static final int CASTSPELL = 0x27;
    /**
     * Opcode {@code useweapon}.
     */
    public static final int USEWEAPON = 0x28;
    /**
     * Opcode {@code move}.
     */
    public static final int MOVE = 0x29;
    /**
     * Opcode {@code gotorepeatattk}.
     */
    public static final int GOTOREPEATATTK = 0x2A;
    /**
     * Opcode {@code engframe}.
     */
    public static final int ENGFRAME = 0x2B;
    /**
     * Opcode {@code engset}.
     */
    public static final int ENGSET = 0x2C;
    /**
     * Opcode {@code __2d}.
     */
    public static final int UNKNOWN_2D = 0x2D;
    /**
     * Opcode {@code nobrkcodestart}.
     */
    public static final int NOBRKCODESTART = 0x2E;
    /**
     * Opcode {@code nobrkcodeend}.
     */
    public static final int NOBRKCODEEND = 0x2F;
    /**
     * Opcode {@code ignorerest}.
     */
    public static final int IGNOREREST = 0x30;
    /**
     * Opcode {@code attkshiftproj}.
     */
    public static final int ATTKSHIFTPROJ = 0x31;
    /**
     * Opcode {@code tmprmgraphicstart}.
     */
    public static final int TMPRMGRAPHICSTART = 0x32;
    /**
     * Opcode {@code tmprmgraphicend}.
     */
    public static final int TMPRMGRAPHICEND = 0x33;
    /**
     * Opcode {@code setfldirect}.
     */
    public static final int SETFLDIRECT = 0x34;
    /**
     * Opcode {@code call}.
     */
    public static final int CALL = 0x35;
    /**
     * Opcode {@code return}.
     */
    public static final int RETURN = 0x36;
    /**
     * Opcode {@code setflspeed}.
     */
    public static final int SETFLSPEED = 0x37;
    /**
     * Opcode {@code creategasoverlays}.
     */
    public static final int CREATEGASOVERLAYS = 0x38;
    /**
     * Opcode {@code pwrupcondjmp}.
     */
    public static final int PWRUPCONDJMP = 0x39;
    /**
     * Opcode {@code trgtrangecondjmp}.
     */
    public static final int TRGTRANGECONDJMP = 0x3A;
    /**
     * Opcode {@code trgtarccondjmp}.
     */
    public static final int TRGTARCCONDJMP = 0x3B;
    /**
     * Opcode {@code curdirectcondjmp}.
     */
    public static final int CURDIRECTCONDJMP = 0x3C;
    /**
     * Opcode {@code imgulnextid}.
     */
    public static final int IMGULNEXTID = 0x3D;
    /**
     * Opcode {@code __3e}.
     */
    public static final int UNKNOWN_3E = 0x3E;
    /**
     * Opcode {@code liftoffcondjmp}.
     */
    public static final int LIFTOFFCONDJMP = 0x3F;
    /**
     * Opcode {@code warpoverlay}.
     */
    public static final int WARPOVERLAY = 0x40;
    /**
     * Opcode {@code orderdone}.
     */
    public static final int ORDERDONE = 0x41;
    /**
     * Opcode {@code grdsprol}.
     */
    public static final int GRDSPROL = 0x42;
    /**
     * Opcode {@code __43}.
     */
    public static final int UNKNOWN_43 = 0x43;
    /**
     * Opcode {@code dogrddamage}.
     */
    public static final int DOGRDDAMAGE = 0x44;

    /**
     * The number of opcodes of the table: {@code 0} to {@link #DOGRDDAMAGE}.
     */
    private static final int OPCODE_COUNT = DOGRDDAMAGE + 1;
    /**
     * The mask that keeps the unsigned value of an operand byte.
     */
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    private static final @Nullable String @NotNull [] NAMES = new String[OPCODE_COUNT];
    private static final int @NotNull [] @Nullable [] LAYOUTS = new int[OPCODE_COUNT][];

    static {
        register(PLAYFRAM, "playfram", new int[]{OPERAND_UNSIGNED_WORD});
        register(PLAYFRAMTILE, "playframtile", new int[]{OPERAND_UNSIGNED_WORD});
        register(SETHORPOS, "sethorpos", new int[]{OPERAND_SIGNED_BYTE});
        register(SETVERTPOS, "setvertpos", new int[]{OPERAND_SIGNED_BYTE});
        register(SETPOS, "setpos", new int[]{OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(WAIT, "wait", new int[]{OPERAND_UNSIGNED_BYTE});
        register(WAITRAND, "waitrand", new int[]{OPERAND_UNSIGNED_BYTE, OPERAND_UNSIGNED_BYTE});
        register(GOTO, "goto", new int[]{OPERAND_UNSIGNED_WORD});
        register(IMGOL, "imgol", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(IMGUL, "imgul", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(IMGOLORIG, "imgolorig", new int[]{OPERAND_UNSIGNED_WORD});
        register(SWITCHUL, "switchul", new int[]{OPERAND_UNSIGNED_WORD});
        register(UNKNOWN_0C, "__0c", new int[]{});
        register(IMGOLUSELO, "imgoluselo", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(IMGULUSELO, "imguluselo", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(SPROL, "sprol", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(HIGHSPROL, "highsprol", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(LOWSPRUL, "lowsprul", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(UFLUNSTABLE, "uflunstable", new int[]{OPERAND_UNSIGNED_WORD});
        register(SPRULUSELO, "spruluselo", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(SPRUL, "sprul", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(SPROLUSELO, "sproluselo", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_UNSIGNED_BYTE});
        register(END, "end", new int[]{});
        register(SETFLIPSTATE, "setflipstate", new int[]{OPERAND_UNSIGNED_BYTE});
        register(PLAYSND, "playsnd", new int[]{OPERAND_UNSIGNED_WORD});
        register(PLAYSNDRAND, "playsndrand", new int[]{OPERAND_VARIABLE, OPERAND_UNSIGNED_WORD});
        register(PLAYSNDBTWN, "playsndbtwn", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_UNSIGNED_WORD});
        register(DOMISSILEDMG, "domissiledmg", new int[]{});
        register(ATTACKMELEE, "attackmelee", new int[]{OPERAND_VARIABLE, OPERAND_UNSIGNED_WORD});
        register(FOLLOWMAINGRAPHIC, "followmaingraphic", new int[]{});
        register(RANDCONDJMP, "randcondjmp", new int[]{OPERAND_UNSIGNED_BYTE, OPERAND_UNSIGNED_WORD});
        register(TURNCCWISE, "turnccwise", new int[]{OPERAND_UNSIGNED_BYTE});
        register(TURNCWISE, "turncwise", new int[]{OPERAND_UNSIGNED_BYTE});
        register(TURN1CWISE, "turn1cwise", new int[]{});
        register(TURNRAND, "turnrand", new int[]{OPERAND_UNSIGNED_BYTE});
        register(SETSPAWNFRAME, "setspawnframe", new int[]{OPERAND_UNSIGNED_BYTE});
        register(SIGORDER, "sigorder", new int[]{OPERAND_UNSIGNED_BYTE});
        register(ATTACKWITH, "attackwith", new int[]{OPERAND_UNSIGNED_BYTE});
        register(ATTACK, "attack", new int[]{});
        register(CASTSPELL, "castspell", new int[]{});
        register(USEWEAPON, "useweapon", new int[]{OPERAND_UNSIGNED_BYTE});
        register(MOVE, "move", new int[]{OPERAND_UNSIGNED_BYTE});
        register(GOTOREPEATATTK, "gotorepeatattk", new int[]{});
        register(ENGFRAME, "engframe", new int[]{OPERAND_UNSIGNED_BYTE});
        register(ENGSET, "engset", new int[]{OPERAND_UNSIGNED_BYTE});
        register(UNKNOWN_2D, "__2d", new int[]{});
        register(NOBRKCODESTART, "nobrkcodestart", new int[]{});
        register(NOBRKCODEEND, "nobrkcodeend", new int[]{});
        register(IGNOREREST, "ignorerest", new int[]{});
        register(ATTKSHIFTPROJ, "attkshiftproj", new int[]{OPERAND_UNSIGNED_BYTE});
        register(TMPRMGRAPHICSTART, "tmprmgraphicstart", new int[]{});
        register(TMPRMGRAPHICEND, "tmprmgraphicend", new int[]{});
        register(SETFLDIRECT, "setfldirect", new int[]{OPERAND_UNSIGNED_BYTE});
        register(CALL, "call", new int[]{OPERAND_UNSIGNED_WORD});
        register(RETURN, "return", new int[]{});
        register(SETFLSPEED, "setflspeed", new int[]{OPERAND_UNSIGNED_WORD});
        register(CREATEGASOVERLAYS, "creategasoverlays", new int[]{OPERAND_UNSIGNED_BYTE});
        register(PWRUPCONDJMP, "pwrupcondjmp", new int[]{OPERAND_UNSIGNED_WORD});
        register(TRGTRANGECONDJMP, "trgtrangecondjmp", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_UNSIGNED_WORD});
        register(TRGTARCCONDJMP, "trgtarccondjmp", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_UNSIGNED_WORD, OPERAND_UNSIGNED_WORD});
        register(CURDIRECTCONDJMP, "curdirectcondjmp", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_UNSIGNED_WORD, OPERAND_UNSIGNED_WORD});
        register(IMGULNEXTID, "imgulnextid", new int[]{OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(UNKNOWN_3E, "__3e", new int[]{});
        register(LIFTOFFCONDJMP, "liftoffcondjmp", new int[]{OPERAND_UNSIGNED_WORD});
        register(WARPOVERLAY, "warpoverlay", new int[]{OPERAND_UNSIGNED_WORD});
        register(ORDERDONE, "orderdone", new int[]{OPERAND_UNSIGNED_BYTE});
        register(GRDSPROL, "grdsprol", new int[]{OPERAND_UNSIGNED_WORD, OPERAND_SIGNED_BYTE, OPERAND_SIGNED_BYTE});
        register(UNKNOWN_43, "__43", new int[]{});
        register(DOGRDDAMAGE, "dogrddamage", new int[]{});
    }

    private static void register(int opcode, @NotNull String name, int @NotNull [] layout) {
        NAMES[opcode]   = name;
        LAYOUTS[opcode] = layout;
    }

    /**
     * Returns the name of the given opcode.
     *
     * @param opcode the opcode
     * @return the name, or {@code null} if the opcode is unknown
     */
    public static @Nullable String getName(int opcode) {
        if (opcode < 0 || opcode >= NAMES.length) {
            return null;
        }

        return NAMES[opcode];
    }

    /**
     * Decodes the operands of the given opcode from a range of the data.
     *
     * @param opcode the opcode
     * @param data   the data
     * @param offset the first operand byte
     * @param limit  the end of the readable range, exclusive
     * @return the assembled operand values, or {@code null} if the opcode is unknown or the range is too short
     * @throws IndexOutOfBoundsException if the range is outside the data
     */
    static int @Nullable [] decodeOperands(int opcode, byte @NotNull [] data, int offset, int limit) {
        Objects.checkFromToIndex(offset, limit, data.length);

        if (opcode < 0 || opcode >= LAYOUTS.length) {
            return null;
        }

        int[] layout = LAYOUTS[opcode];

        if (layout == null) {
            return null;
        }

        int[] operands = new int[layout.length];
        int position = offset;

        for (int layoutIndex = 0; layoutIndex < layout.length; layoutIndex++) {
            switch (layout[layoutIndex]) {
                case OPERAND_UNSIGNED_BYTE:
                    if (position + 1 > limit) {
                        return null;
                    }

                    operands[layoutIndex] = data[position] & BYTE_MASK;
                    position += 1;

                    break;

                case OPERAND_SIGNED_BYTE:
                    if (position + 1 > limit) {
                        return null;
                    }

                    operands[layoutIndex] = data[position];
                    position += 1;

                    break;

                case OPERAND_UNSIGNED_WORD:
                    if (limit - position < Short.BYTES) {
                        return null;
                    }

                    operands[layoutIndex] = LittleEndianUtils.readUnsigned16(data, position);
                    position += Short.BYTES;

                    break;

                case OPERAND_VARIABLE:
                    if (position + 1 > limit) {
                        return null;
                    }

                    int count = data[position] & BYTE_MASK;

                    if (limit - position < 1 + count * Short.BYTES) {
                        return null;
                    }

                    int[] variableOperands = new int[1 + count];

                    variableOperands[0] = count;
                    position += 1;

                    for (int soundIndex = 0; soundIndex < count; soundIndex++) {
                        variableOperands[1 + soundIndex] = LittleEndianUtils.readUnsigned16(data, position);
                        position += Short.BYTES;
                    }

                    return variableOperands;

                default:
                    return null;
            }
        }

        return operands;
    }

    /**
     * Returns the byte size of the operands of the instruction whose operands start at an offset.
     *
     * @param opcode the opcode
     * @param data   the data
     * @param offset the first operand byte
     * @param limit  the end of the readable range, exclusive
     * @return the operand size in bytes, or {@code -1} if the opcode is unknown or the range is too short
     * @throws IndexOutOfBoundsException if the range is outside the data
     */
    static int getOperandSize(int opcode, byte @NotNull [] data, int offset, int limit) {
        Objects.checkFromToIndex(offset, limit, data.length);

        if (opcode < 0 || opcode >= LAYOUTS.length) {
            return -1;
        }

        int[] layout = LAYOUTS[opcode];

        if (layout == null) {
            return -1;
        }

        int size = 0;

        for (int operandType : layout) {
            switch (operandType) {
                case OPERAND_UNSIGNED_BYTE:
                case OPERAND_SIGNED_BYTE:
                    size += 1;

                    break;

                case OPERAND_UNSIGNED_WORD:
                    size += Short.BYTES;

                    break;

                case OPERAND_VARIABLE:
                    if (limit - offset <= size) {
                        return -1;
                    }

                    return size + 1 + (data[offset + size] & BYTE_MASK) * Short.BYTES;

                default:
                    return -1;
            }
        }

        return size;
    }

    /**
     * Writes one instruction: the opcode byte and its operands in the opcode's layout. A count-prefixed opcode takes the count as its first
     * operand, followed by that many sound identifiers.
     *
     * @param opcode   the opcode
     * @param operands the operand values; a signed byte takes {@code -128..127}, an unsigned byte {@code 0..255}, a word {@code 0..0xFFFF}
     * @param output   the stream the instruction is appended to
     * @throws IllegalArgumentException if the opcode is unknown, the operand count does not match its layout or a value is out of range
     */
    public static void encodeInstruction(int opcode, int @NotNull [] operands, @NotNull ByteArrayOutputStream output) {
        int[] layout = opcode >= 0 && opcode < LAYOUTS.length ? LAYOUTS[opcode] : null;

        if (layout == null) {
            throw Exceptions.newIllegalArgumentException("Unknown iscript opcode: {0}", opcode);
        }

        boolean counted = layout.length > 0 && layout[0] == OPERAND_VARIABLE;
        int expected = counted && operands.length > 0 ? 1 + operands[0] : layout.length;

        if (operands.length != expected) {
            throw Exceptions.newIllegalArgumentException("The iscript opcode {0} takes {1} operands, not {2}.",
                                                         getName(opcode), expected, operands.length);
        }

        output.write(opcode);

        for (int index = 0; index < operands.length; index++) {
            int kind = counted ? (index == 0 ? OPERAND_UNSIGNED_BYTE : OPERAND_UNSIGNED_WORD) : layout[index];
            int value = operands[index];

            switch (kind) {
                case OPERAND_UNSIGNED_BYTE -> {
                    checkOperand(opcode, value, 0, BYTE_MASK);
                    output.write(value);
                }
                case OPERAND_SIGNED_BYTE -> {
                    checkOperand(opcode, value, Byte.MIN_VALUE, Byte.MAX_VALUE);
                    output.write(value & BYTE_MASK);
                }
                default -> {
                    checkOperand(opcode, value, 0, WORD_MASK);
                    output.write(value & BYTE_MASK);
                    output.write(value >>> Byte.SIZE);
                }
            }
        }
    }

    private static void checkOperand(int opcode, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw Exceptions.newIllegalArgumentException("The iscript opcode {0} operand {1} is outside {2}..{3}.",
                                                         getName(opcode), value, minimum, maximum);
        }
    }


    private IscriptOpcodes() {
    }
}
