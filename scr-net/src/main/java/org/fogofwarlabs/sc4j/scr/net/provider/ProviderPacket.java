package org.fogofwarlabs.sc4j.scr.net.provider;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

/**
 * Encodes and validates the {@code 12}-byte provider packet envelope. The header stores a reverse Fletcher checksum, encoded length, sequence,
 * acknowledgement and packet identity. Parsing validates length, class and checksum, then discards bytes beyond the encoded packet length.
 */
public final class ProviderPacket {
    /**
     * The class of provider control packets (join handshake, keep-alive, player info).
     */
    public static final int CONTROL = 0;
    /**
     * The class of game messages (the lobby's messages).
     */
    public static final int MESSAGE = 1;
    /**
     * The class of turns.
     */
    public static final int TURN = 2;
    /**
     * The class of packets that are neither kept for retransmission nor acknowledged.
     */
    public static final int UNRELIABLE = 3;
    /**
     * The number of classes the receiver accepts.
     */
    public static final int CLASS_COUNT = 4;

    /**
     * Flag of a packet that only carries its acknowledgement.
     */
    public static final int FLAG_ACKNOWLEDGEMENT = 1;
    /**
     * Flag of a header-only request to resend the packet of the given sequence.
     */
    public static final int FLAG_RESEND_REQUEST = 2;
    /**
     * Flag of a packet relayed for another player; it neither carries nor updates acknowledgements.
     */
    public static final int FLAG_RELAYED = 4;

    /**
     * The size of the header.
     */
    public static final int HEADER_SIZE = 12;

    /**
     * The mask of an unsigned 16-bit header field.
     */
    static final int WORD_MASK = 0xFFFF;
    /**
     * The mask of an unsigned 8-bit header field.
     */
    static final int BYTE_MASK = 0xFF;

    /**
     * The offsets of the header fields: checksum, size, sequence, acknowledgement, class, subtype, sender and flags.
     */
    private static final int CHECKSUM_OFFSET = 0;
    private static final int SIZE_OFFSET = 2;
    private static final int SEQUENCE_OFFSET = 4;
    private static final int ACKNOWLEDGEMENT_OFFSET = 6;
    private static final int CLASS_OFFSET = 8;
    private static final int SUBTYPE_OFFSET = 9;
    private static final int SENDER_OFFSET = 10;
    static final int FLAGS_OFFSET = 11;

    /**
     * The largest encoded packet size.
     */
    private static final int MAXIMUM_PACKET_SIZE = 0xFFFF;

    /**
     * The modulus of both Fletcher sums.
     */
    private static final int CHECKSUM_MODULUS = 255;

    /**
     * Creates a {@link ProviderPacket} instance with the original turn type {@code 2}, subtype {@code 0} and flags {@code 0}.
     * <p>
     * The caller supplies the sequence and raw acknowledgement field. This method does not advance participant state.
     * Payloads must be nonempty and fit the 16-bit encoded length.
     *
     * @param sequence         the unsigned 16-bit sequence
     * @param acknowledgement  the unsigned 16-bit acknowledgement field
     * @param senderIdentifier the unsigned 8-bit sender identifier
     * @param payload          the complete turn payload
     * @return the created {@link ProviderPacket} instance
     * @throws IllegalArgumentException if a field or payload length is outside the supported range
     */
    public static @NotNull ProviderPacket createTurn(int sequence, int acknowledgement, int senderIdentifier, byte @NotNull [] payload) {
        if (payload.length == 0) {
            throw Exceptions.newIllegalArgumentException("Turn packet fields or payload length are outside the supported range.");
        }

        return create(sequence, acknowledgement, TURN, 0, senderIdentifier, 0, payload);
    }

    /**
     * Creates a {@link ProviderPacket} instance from caller-supplied header fields and payload bytes.
     *
     * @param sequence         the unsigned 16-bit sequence
     * @param acknowledgement  the unsigned 16-bit acknowledgement field
     * @param packetClass      the class: {@link #CONTROL}, {@link #MESSAGE}, {@link #TURN} or {@link #UNRELIABLE}
     * @param subtype          the unsigned 8-bit subtype
     * @param senderIdentifier the unsigned 8-bit sender identifier
     * @param flags            the unsigned 8-bit flags: {@link #FLAG_ACKNOWLEDGEMENT}, {@link #FLAG_RESEND_REQUEST}, {@link #FLAG_RELAYED}
     * @param payload          the payload, possibly empty
     * @return the created {@link ProviderPacket} instance
     * @throws IllegalArgumentException if a field or the payload length is outside the supported range
     */
    public static @NotNull ProviderPacket create(int sequence, int acknowledgement,
                                                 int packetClass, int subtype,
                                                 int senderIdentifier,
                                                 int flags,
                                                 byte @NotNull [] payload) {
        if ((sequence & ~WORD_MASK) != 0 || (acknowledgement & ~WORD_MASK) != 0 || (packetClass & ~BYTE_MASK) != 0 || (subtype & ~BYTE_MASK) != 0 ||
            (senderIdentifier & ~BYTE_MASK) != 0 || (flags & ~BYTE_MASK) != 0 || payload.length > MAXIMUM_PACKET_SIZE - HEADER_SIZE) {
            throw Exceptions.newIllegalArgumentException("Provider packet fields or payload length are outside the supported range.");
        }

        byte[] encoded = new byte[HEADER_SIZE + payload.length];

        LittleEndianUtils.writeInt16(encoded, SIZE_OFFSET, encoded.length);
        LittleEndianUtils.writeInt16(encoded, SEQUENCE_OFFSET, sequence);
        LittleEndianUtils.writeInt16(encoded, ACKNOWLEDGEMENT_OFFSET, acknowledgement);

        encoded[CLASS_OFFSET]   = (byte) packetClass;
        encoded[SUBTYPE_OFFSET] = (byte) subtype;
        encoded[SENDER_OFFSET]  = (byte) senderIdentifier;
        encoded[FLAGS_OFFSET]   = (byte) flags;

        System.arraycopy(payload, 0, encoded, HEADER_SIZE, payload.length);

        writeChecksum(encoded);

        return new ProviderPacket(encoded);
    }

    /**
     * Creates a {@link ProviderPacket} instance from bytes supplied by a provider.
     * <p>
     * The original receiver accepts an encoded length smaller than the provider's byte count. Bytes after that length are
     * excluded from checksum validation and the retained packet. A zero checksum residue is required, preserving the
     * original acceptance of equivalent {@code 0} and {@code 255} byte values.
     *
     * @param encoded the provider bytes beginning with the envelope
     * @return the created {@link ProviderPacket} instance
     * @throws IOException if length, packet type or checksum validation fails
     */
    public static @NotNull ProviderPacket parse(byte @NotNull [] encoded) throws IOException {
        byte[] retainedBytes = encoded.clone();

        if (retainedBytes.length < HEADER_SIZE) {
            throw Exceptions.newIOException("Provider packet is shorter than its header.");
        }

        int packetSize = LittleEndianUtils.readUnsigned16(retainedBytes, SIZE_OFFSET);

        if (packetSize < HEADER_SIZE || packetSize > retainedBytes.length) {
            throw Exceptions.newIOException("Provider packet length is outside the supplied bytes.");
        }

        if ((retainedBytes[CLASS_OFFSET] & BYTE_MASK) >= CLASS_COUNT) {
            throw Exceptions.newIOException("Provider packet type is outside the original accepted range.");
        }

        if (calculateResidue(retainedBytes, CHECKSUM_OFFSET, packetSize) != 0) {
            throw Exceptions.newIOException("Provider packet checksum is invalid.");
        }

        return new ProviderPacket(packetSize == retainedBytes.length ? retainedBytes : Arrays.copyOf(retainedBytes, packetSize));
    }

    /**
     * Writes the reverse Fletcher checksum over the bytes after the checksum into the checksum field.
     *
     * @param encoded the complete packet bytes
     */
    private static void writeChecksum(byte @NotNull [] encoded) {
        int remainder = calculateResidue(encoded, SIZE_OFFSET, encoded.length);
        int firstSum = remainder >>> Byte.SIZE;
        int highByte = CHECKSUM_MODULUS - (firstSum + (remainder & BYTE_MASK)) % CHECKSUM_MODULUS;
        int lowByte = CHECKSUM_MODULUS - (firstSum + highByte) % CHECKSUM_MODULUS;

        LittleEndianUtils.writeInt16(encoded, CHECKSUM_OFFSET, lowByte | highByte << Byte.SIZE);
    }

    private static int calculateResidue(byte @NotNull [] encoded, int firstByte, int endByte) {
        int firstSum = 0;
        int secondSum = 0;

        for (int byteIndex = endByte - 1; byteIndex >= firstByte; byteIndex--) {
            firstSum += encoded[byteIndex] & BYTE_MASK;

            if (firstSum >= CHECKSUM_MODULUS) {
                firstSum -= CHECKSUM_MODULUS;
            }

            secondSum += firstSum;
        }

        return firstSum << Byte.SIZE | secondSum % CHECKSUM_MODULUS;
    }


    private final byte @NotNull [] encoded;

    private ProviderPacket(byte @NotNull [] encoded) {
        this.encoded = encoded;
    }

    /**
     * @return the unsigned packet size of the size field, including the header
     */
    public int getSize() {
        return this.encoded.length;
    }

    /**
     * @return the unsigned sequence field
     */
    public int getSequence() {
        return LittleEndianUtils.readUnsigned16(this.encoded, SEQUENCE_OFFSET);
    }

    /**
     * @return the unsigned acknowledgement field
     */
    public int getAcknowledgement() {
        return LittleEndianUtils.readUnsigned16(this.encoded, ACKNOWLEDGEMENT_OFFSET);
    }

    /**
     * @return the unsigned packet type (the packet class)
     */
    public int getType() {
        return this.encoded[CLASS_OFFSET] & BYTE_MASK;
    }

    /**
     * @return the unsigned packet subtype
     */
    public int getSubtype() {
        return this.encoded[SUBTYPE_OFFSET] & BYTE_MASK;
    }

    /**
     * @return the unsigned sender identifier
     */
    public int getSenderIdentifier() {
        return this.encoded[SENDER_OFFSET] & BYTE_MASK;
    }

    /**
     * @return the unsigned flags byte
     */
    public int getFlags() {
        return this.encoded[FLAGS_OFFSET] & BYTE_MASK;
    }

    /**
     * Returns a copy of the payload after the {@code 12}-byte header.
     *
     * @return the payload bytes
     */
    public byte @NotNull [] getPayloadBytes() {
        return Arrays.copyOfRange(this.encoded, HEADER_SIZE, this.encoded.length);
    }

    /**
     * Returns a copy of the complete encoded packet, excluding any trailing provider bytes.
     *
     * @return the encoded packet bytes
     */
    public byte @NotNull [] getEncodedBytes() {
        return this.encoded.clone();
    }


    /**
     * Creates a {@link ProviderPacket} instance that copies this packet with another acknowledgement field and a recomputed checksum.
     *
     * @param acknowledgement the unsigned 16-bit acknowledgement
     * @return the created {@link ProviderPacket} instance
     */
    public @NotNull ProviderPacket copyWithAcknowledgement(int acknowledgement) {
        byte[] copy = this.encoded.clone();

        LittleEndianUtils.writeInt16(copy, ACKNOWLEDGEMENT_OFFSET, acknowledgement);
        writeChecksum(copy);

        return new ProviderPacket(copy);
    }
}
