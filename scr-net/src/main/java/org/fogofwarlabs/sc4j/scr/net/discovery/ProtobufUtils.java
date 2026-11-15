package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Reads and writes the protobuf wire encoding of the small SDK messages carried by the LAN protocol ({@link LanRecord},
 * the presence record's nested values) and of the game proxy's envelope ({@link org.fogofwarlabs.sc4j.scr.net.proxy.GameProxyDatagram},
 * {@link org.fogofwarlabs.sc4j.scr.net.proxy.BattleNetServiceHeader}). Groups are not supported.
 */
public final class ProtobufUtils {
    /**
     * The wire type of a variable-length integer.
     */
    public static final int WIRE_TYPE_VARIABLE_INTEGER = 0;
    /**
     * The wire type of a fixed 64-bit value.
     */
    public static final int WIRE_TYPE_FIXED_64 = 1;
    /**
     * The wire type of a length-delimited value (bytes, strings, nested messages).
     */
    public static final int WIRE_TYPE_LENGTH_DELIMITED = 2;
    /**
     * The wire type of a fixed 32-bit value.
     */
    public static final int WIRE_TYPE_FIXED_32 = 5;
    /**
     * The number of tag bits that hold the wire type; the field number follows above them.
     */
    public static final int WIRE_TYPE_BITS = 3;
    /**
     * The mask of the wire type in a tag.
     */
    public static final int WIRE_TYPE_MASK = 7;
    /**
     * The most bytes of a variable-length 64-bit integer.
     */
    public static final int MAXIMUM_VARIABLE_INTEGER_BYTES = 10;
    /**
     * The value bits of one variable-length integer byte.
     */
    public static final int VARIABLE_INTEGER_BITS = 7;
    /**
     * The mask of the value bits of one variable-length integer byte.
     */
    public static final int VARIABLE_INTEGER_MASK = 0x7F;
    /**
     * The continuation bit of one variable-length integer byte.
     */
    public static final int VARIABLE_INTEGER_CONTINUATION = 0x80;

    /**
     * Creates the tag of a field.
     *
     * @param fieldNumber the field number
     * @param wireType    the wire type
     * @return the tag
     */
    public static long createTag(int fieldNumber, int wireType) {
        return ((long) fieldNumber << WIRE_TYPE_BITS) | wireType;
    }

    static long readVariableInteger(@NotNull ByteBuffer input) throws IOException {
        long value = 0;

        for (int byteIndex = 0; byteIndex < MAXIMUM_VARIABLE_INTEGER_BYTES; byteIndex++) {
            if (!input.hasRemaining()) {
                throw Exceptions.newIOException("Truncated protobuf variable integer.");
            }

            int current = Byte.toUnsignedInt(input.get());

            value |= (long) (current & VARIABLE_INTEGER_MASK) << (byteIndex * VARIABLE_INTEGER_BITS);

            if ((current & VARIABLE_INTEGER_CONTINUATION) == 0) {
                return value;
            }
        }

        throw Exceptions.newIOException("Protobuf variable integer exceeds 64 bits.");
    }

    static byte @NotNull [] readBytes(@NotNull ByteBuffer input) throws IOException {
        long length = readVariableInteger(input);

        if (length < 0 || length > input.remaining()) {
            throw Exceptions.newIOException("Truncated protobuf field.");
        }

        byte[] value = new byte[(int) length];

        input.get(value);

        return value;
    }

    static void skipField(@NotNull ByteBuffer input, int wireType) throws IOException {
        long length = switch (wireType) {
            case WIRE_TYPE_VARIABLE_INTEGER -> {
                readVariableInteger(input);
                yield 0;
            }
            case WIRE_TYPE_FIXED_64 -> Long.BYTES;
            case WIRE_TYPE_LENGTH_DELIMITED -> readVariableInteger(input);
            case WIRE_TYPE_FIXED_32 -> Integer.BYTES;
            default -> throw Exceptions.newIOException("Unsupported protobuf wire type: {0}", wireType);
        };

        if (length < 0 || length > input.remaining()) {
            throw Exceptions.newIOException("Truncated protobuf field.");
        }

        input.position(input.position() + (int) length);
    }

    static void writeVariableInteger(@NotNull ByteArrayOutputStream output, int fieldNumber, long value) {
        writeVariableInteger(output, createTag(fieldNumber, WIRE_TYPE_VARIABLE_INTEGER));
        writeVariableInteger(output, value);
    }

    /**
     * Writes a variable-length integer.
     *
     * @param output the destination
     * @param value  the value, written as unsigned
     */
    public static void writeVariableInteger(@NotNull ByteArrayOutputStream output, long value) {
        long remaining = value;

        while ((remaining & ~(long) VARIABLE_INTEGER_MASK) != 0) {
            output.write((int) ((remaining & VARIABLE_INTEGER_MASK) | VARIABLE_INTEGER_CONTINUATION));
            remaining >>>= VARIABLE_INTEGER_BITS;
        }

        output.write((int) remaining);
    }

    static void writeBytes(@NotNull ByteArrayOutputStream output, int fieldNumber, byte @NotNull [] value) {
        writeVariableInteger(output, createTag(fieldNumber, WIRE_TYPE_LENGTH_DELIMITED));
        writeVariableInteger(output, value.length);
        output.writeBytes(value);
    }


    private ProtobufUtils() {
    }
}
