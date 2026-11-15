package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.text.AsciiCaseUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Encodes and parses LAN discovery DNS messages with message-wide name compression.
 * PTR and SRV records expose their resolved target names; NSEC also retains its trailing bitmap.
 * Other record types retain their raw data bytes.
 * <p>
 * Names are dot-separated text: inside a label a backslash comes before a dot or a backslash, and a byte up to the space is
 * written as a backslash and three decimal digits ({@link #escapeLabel}), so an instance label may hold dots and spaces. Names
 * compare with {@link AsciiCaseUtils}; compression reuses only byte-identical names.
 */
public final class MulticastDnsMessage {
    /**
     * The response flag word of an authoritative answer ({@code QR} and {@code AA}).
     */
    public static final int RESPONSE_FLAGS = 0x8400;
    /**
     * The class of the Internet ({@code IN}).
     */
    public static final int CLASS_INTERNET = 1;
    /**
     * The cache flush bit of a record class, the unicast response bit of a question class.
     */
    public static final int CLASS_FLAG = 0x8000;

    /**
     * The record type of an IPv4 address ({@code A}).
     */
    public static final int TYPE_A = 1;
    /**
     * The record type of a domain name pointer ({@code PTR}).
     */
    public static final int TYPE_PTR = 12;
    /**
     * The record type of a text record ({@code TXT}).
     */
    public static final int TYPE_TXT = 16;
    /**
     * The record type of an IPv6 address ({@code AAAA}).
     */
    public static final int TYPE_AAAA = 28;
    /**
     * The record type of a service location ({@code SRV}).
     */
    public static final int TYPE_SRV = 33;
    /**
     * The record type of a next secure record ({@code NSEC}), which lists the types that exist for a name.
     */
    public static final int TYPE_NSEC = 47;
    /**
     * The record type of LAN data records ({@link LanRecord}).
     */
    public static final int TYPE_BLIZZARD = 66;
    /**
     * The question type that asks for every record type ({@code ANY}).
     */
    public static final int TYPE_ANY = 255;

    /**
     * The most bytes of one label.
     */
    public static final int MAXIMUM_LABEL_LENGTH = 63;

    private static final int HEADER_SIZE = 12;
    /**
     * The query/response bit of the flag word ({@code QR}).
     */
    private static final int RESPONSE_FLAG = 0x8000;
    /**
     * The fixed part of a question after its name: type and class.
     */
    private static final int QUESTION_FIXED_SIZE = 4;
    /**
     * The fixed part of a record after its name: type, class, time to live and data length.
     */
    private static final int RECORD_FIXED_SIZE = 10;
    /**
     * The fixed part of {@code SRV} data before the host name: priority, weight and port.
     */
    private static final int SERVICE_FIXED_SIZE = 6;
    /**
     * The offset of the port in {@code SRV} data, after the priority and the weight.
     */
    private static final int SERVICE_PORT_OFFSET = 4;
    /**
     * The size of the data length field before the record data.
     */
    private static final int DATA_LENGTH_SIZE = Short.BYTES;

    private static final int POINTER_MASK = 0xC0;
    /**
     * The compression pointer marker of a name, as a big-endian word.
     */
    private static final int POINTER_WORD = 0xC000;
    /**
     * The first message offset a compression pointer cannot reach.
     */
    private static final int POINTER_LIMIT = 0x4000;
    private static final int POINTER_SIZE = Short.BYTES;
    private static final int MAXIMUM_POINTER_HOPS = 32;
    /**
     * The most bytes of an encoded name: its labels with their length bytes and the closing zero.
     */
    private static final int MAXIMUM_NAME_LENGTH = 256;

    /**
     * The text form of names: the label separator, the escape byte, the last byte written as a decimal escape, the digits
     * and the largest value of a decimal escape.
     */
    private static final byte LABEL_SEPARATOR = '.';
    private static final byte ESCAPE = '\\';
    private static final int LAST_DECIMAL_ESCAPED = ' ';
    private static final int FIRST_DIGIT = '0';
    private static final int LAST_DIGIT = '9';
    private static final int DECIMAL_BASE = 10;
    private static final int DECIMAL_HUNDREDS = 100;
    private static final int LARGEST_DECIMAL_ESCAPE = 0xFF;
    /**
     * The digits of a decimal escape after its first digit.
     */
    private static final int DECIMAL_ESCAPE_TRAILING_DIGITS = 2;

    /**
     * The {@code NSEC} type bitmap: window block number and bitmap length, then the bits with type {@code 0} at {@code 0x80}.
     */
    private static final int NEXT_SECURE_WINDOW_HEADER_SIZE = 2;
    private static final int NEXT_SECURE_LENGTH_OFFSET = 1;
    private static final int NEXT_SECURE_FIRST_TYPE_BIT = 0x80;

    /**
     * The divisor of an answer's time to live that gives the least time to live of a known answer suppressing it.
     */
    private static final int KNOWN_ANSWER_TIME_TO_LIVE_DIVISOR = 2;

    /**
     * The initial capacity of an encoded message: the classic DNS datagram size.
     */
    private static final int MESSAGE_CAPACITY = 512;

    /**
     * One question of a message.
     */
    public static final class Question {
        private final @NotNull String name;
        private final int type;
        private final int questionClass;

        /**
         * Creates a {@link Question} instance.
         *
         * @param name          the queried name without the trailing dot, for example {@code _pnm._sub._blizzard._udp.local}
         * @param type          the queried type
         * @param questionClass the class, with {@link #CLASS_FLAG} for a unicast response
         */
        public Question(@NotNull String name, int type, int questionClass) {
            this.name          = name;
            this.type          = type;
            this.questionClass = questionClass;
        }

        /**
         * @return the queried name
         */
        public @NotNull String getName() {
            return this.name;
        }

        /**
         * @return the queried type
         */
        public int getType() {
            return this.type;
        }
    }

    /**
     * One resource record of a message: an answer, an authority or an additional record.
     */
    public static final class Record {
        /**
         * Creates a {@link Record} instance with raw data ({@code A}, {@code AAAA}, {@code TXT}, {@link #TYPE_BLIZZARD}).
         *
         * @param name        the owner name
         * @param type        the record type
         * @param recordClass the class, with {@link #CLASS_FLAG} for a unique record
         * @param timeToLive  the time to live in seconds, {@code 0} for a goodbye
         * @param data        the record data
         * @return the created {@link Record} instance
         */
        public static @NotNull Record createData(@NotNull String name, int type, int recordClass, long timeToLive, byte @NotNull [] data) {
            return new Record(name, type, recordClass, timeToLive, data.clone(), "", 0);
        }

        /**
         * Creates a {@link Record} instance of type {@code PTR}.
         *
         * @param name        the owner name
         * @param recordClass the class
         * @param timeToLive  the time to live in seconds
         * @param target      the name pointed to
         * @return the created {@link Record} instance
         */
        public static @NotNull Record createPointer(@NotNull String name, int recordClass, long timeToLive, @NotNull String target) {
            return new Record(name, TYPE_PTR, recordClass, timeToLive, new byte[0], target, 0);
        }

        /**
         * Creates a {@link Record} instance of type {@code SRV} with priority and weight {@code 0}.
         *
         * @param name        the owner name
         * @param recordClass the class
         * @param timeToLive  the time to live in seconds
         * @param port        the port as it goes on the wire
         * @param target      the host name
         * @return the created {@link Record} instance
         */
        public static @NotNull Record createService(@NotNull String name, int recordClass, long timeToLive, int port, @NotNull String target) {
            return new Record(name, TYPE_SRV, recordClass, timeToLive, new byte[SERVICE_PORT_OFFSET], target, port);
        }

        /**
         * Creates a {@link Record} instance of type {@code NSEC} naming its owner and the listed types.
         *
         * @param name        the owner name
         * @param recordClass the class
         * @param timeToLive  the time to live in seconds
         * @param types       the types that exist for the name, each below {@code 256}
         * @return the created {@link Record} instance
         */
        public static @NotNull Record createNextSecure(@NotNull String name, int recordClass, long timeToLive, int @NotNull ... types) {
            int highest = 0;

            for (int type : types) {
                highest = Math.max(highest, type);
            }

            byte[] bitmap = new byte[NEXT_SECURE_WINDOW_HEADER_SIZE + highest / Byte.SIZE + 1];

            bitmap[NEXT_SECURE_LENGTH_OFFSET] = (byte) (bitmap.length - NEXT_SECURE_WINDOW_HEADER_SIZE);

            for (int type : types) {
                bitmap[NEXT_SECURE_WINDOW_HEADER_SIZE + type / Byte.SIZE] |= (byte) (NEXT_SECURE_FIRST_TYPE_BIT >>> (type % Byte.SIZE));
            }

            return new Record(name, TYPE_NSEC, recordClass, timeToLive, bitmap, name, 0);
        }


        private final @NotNull String name;
        private final int type;
        private final int recordClass;
        private final long timeToLive;
        private final byte @NotNull [] data;
        private final @NotNull String target;
        private final int port;

        private Record(@NotNull String name, int type, int recordClass, long timeToLive, byte @NotNull [] data, @NotNull String target, int port) {
            this.name        = name;
            this.type        = type;
            this.recordClass = recordClass;
            this.timeToLive  = timeToLive;
            this.data        = data;
            this.target      = target;
            this.port        = port;
        }

        /**
         * @return the owner name
         */
        public @NotNull String getName() {
            return this.name;
        }

        /**
         * @return the record type
         */
        public int getType() {
            return this.type;
        }

        /**
         * @return the class, with {@link #CLASS_FLAG} on a unique record
         */
        public int getRecordClass() {
            return this.recordClass;
        }

        /**
         * @return the time to live in seconds, {@code 0} for a goodbye
         */
        public long getTimeToLive() {
            return this.timeToLive;
        }

        /**
         * @return a copy of the record data; for {@code PTR} and {@code NSEC} the part after the name, for {@code SRV} the priority and the weight
         */
        public byte @NotNull [] getData() {
            return this.data.clone();
        }

        /**
         * @return the name of a {@code PTR}, the host of an {@code SRV}, the next name of an {@code NSEC}, else empty
         */
        public @NotNull String getTarget() {
            return this.target;
        }

        /**
         * @return the port of an {@code SRV} as it is on the wire, else {@code 0}
         */
        public int getPort() {
            return this.port;
        }

        /**
         * Compares the owner name with {@link #areNamesEqual}.
         *
         * @param otherName a name
         * @return {@code true} if the owner name equals the argument with ASCII-only case folding, {@code false} otherwise
         */
        public boolean isNamed(@NotNull String otherName) {
            return areNamesEqual(this.name, otherName);
        }

        /**
         * Compares this record with another one: the owner names and the names inside the data with {@link #areNamesEqual},
         * the type, the class without {@link #CLASS_FLAG}, the port and the other data bytes.
         *
         * @param other another record
         * @return {@code true} if both records hold the same resource, whatever their times to live, {@code false} otherwise
         */
        public boolean isIdentical(@NotNull Record other) {
            return this.type == other.type && (this.recordClass & ~CLASS_FLAG) == (other.recordClass & ~CLASS_FLAG) &&
                   areNamesEqual(this.name, other.name) && areNamesEqual(this.target, other.target) && this.port == other.port &&
                   Arrays.equals(this.data, other.data);
        }

        /**
         * Applies the known-answer rule to this answer: a known answer of a query suppresses it when the known answer is
         * {@link #isIdentical identical} and its time to live is at least half of this record's (rounded down).
         *
         * @param knownAnswer a known answer of the query
         * @return {@code true} if the answer must not be sent, {@code false} otherwise
         */
        public boolean isSuppressedBy(@NotNull Record knownAnswer) {
            return this.isIdentical(knownAnswer) && knownAnswer.timeToLive >= this.timeToLive / KNOWN_ANSWER_TIME_TO_LIVE_DIVISOR;
        }
    }

    /**
     * Compares two names with {@link AsciiCaseUtils}: only the ASCII letters of the names fold.
     *
     * @param first  a name
     * @param second another name
     * @return {@code true} if the names are equal, {@code false} otherwise
     */
    public static boolean areNamesEqual(@NotNull String first, @NotNull String second) {
        return AsciiCaseUtils.equalsIgnoreCase(first, second);
    }

    /**
     * Escapes one label into the text form of names: a dot or a backslash gets a backslash before it, and a byte up to the
     * space becomes a backslash and three decimal digits.
     *
     * @param label the label, for example an instance name
     * @return the label's text, which a name joins with dots
     */
    public static @NotNull String escapeLabel(@NotNull String label) {
        byte[] bytes = label.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream text = new ByteArrayOutputStream(bytes.length);

        writeEscapedLabel(text, bytes, 0, bytes.length);

        return text.toString(StandardCharsets.UTF_8);
    }

    /**
     * Creates a {@link MulticastDnsMessage} instance.
     *
     * @param flags       the flag word, {@code 0} for a query or {@link #RESPONSE_FLAGS}
     * @param questions   the questions
     * @param answers     the answers (known answers of a query)
     * @param authorities the authority records (the proposed records of a probe)
     * @param additionals the additional records
     * @return the created {@link MulticastDnsMessage} instance
     */
    public static @NotNull MulticastDnsMessage create(int flags,
                                                      @NotNull List<Question> questions,
                                                      @NotNull List<Record> answers,
                                                      @NotNull List<Record> authorities,
                                                      @NotNull List<Record> additionals) {
        return new MulticastDnsMessage(0, flags, List.copyOf(questions), List.copyOf(answers), List.copyOf(authorities), List.copyOf(additionals));
    }

    /**
     * Creates a {@link MulticastDnsMessage} instance from one datagram.
     *
     * @param encoded the datagram payload
     * @return the created {@link MulticastDnsMessage} instance
     * @throws IOException if the message is truncated or a name is malformed
     */
    public static @NotNull MulticastDnsMessage parse(byte @NotNull [] encoded) throws IOException {
        if (encoded.length < HEADER_SIZE) {
            throw Exceptions.newIOException("Truncated DNS header.");
        }

        ByteBuffer input = ByteBuffer.wrap(encoded);
        int identifier = Short.toUnsignedInt(input.getShort());
        int flags = Short.toUnsignedInt(input.getShort());
        int questionCount = Short.toUnsignedInt(input.getShort());
        int answerCount = Short.toUnsignedInt(input.getShort());
        int authorityCount = Short.toUnsignedInt(input.getShort());
        int additionalCount = Short.toUnsignedInt(input.getShort());
        List<Question> questions = new ArrayList<>(questionCount);

        for (int index = 0; index < questionCount; index++) {
            String name = readName(encoded, input);

            requireRemaining(input, QUESTION_FIXED_SIZE);
            questions.add(new Question(name, Short.toUnsignedInt(input.getShort()), Short.toUnsignedInt(input.getShort())));
        }

        List<Record> answers = readRecords(encoded, input, answerCount);
        List<Record> authorities = readRecords(encoded, input, authorityCount);
        List<Record> additionals = readRecords(encoded, input, additionalCount);

        return new MulticastDnsMessage(identifier, flags, questions, answers, authorities, additionals);
    }

    private static @NotNull List<Record> readRecords(byte @NotNull [] encoded, @NotNull ByteBuffer input, int count) throws IOException {
        List<Record> records = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            String name = readName(encoded, input);

            requireRemaining(input, RECORD_FIXED_SIZE);

            int type = Short.toUnsignedInt(input.getShort());
            int recordClass = Short.toUnsignedInt(input.getShort());
            long timeToLive = Integer.toUnsignedLong(input.getInt());
            int length = Short.toUnsignedInt(input.getShort());

            requireRemaining(input, length);

            int start = input.position();
            ByteBuffer data = input.slice(start, length);
            String target = "";
            int port = 0;
            byte[] raw;

            switch (type) {
                case TYPE_PTR -> {
                    target = readName(encoded, ByteBuffer.wrap(encoded).position(start));
                    raw    = new byte[0];
                }
                case TYPE_SRV -> {
                    requireRemaining(data, SERVICE_FIXED_SIZE);
                    port   = Short.toUnsignedInt(data.getShort(SERVICE_PORT_OFFSET));
                    target = readName(encoded, ByteBuffer.wrap(encoded).position(start + SERVICE_FIXED_SIZE));
                    raw    = Arrays.copyOfRange(encoded, start, start + SERVICE_PORT_OFFSET);
                }
                case TYPE_NSEC -> {
                    ByteBuffer next = ByteBuffer.wrap(encoded, 0, start + length).position(start);

                    target = readName(encoded, next);
                    raw    = Arrays.copyOfRange(encoded, next.position(), start + length);
                }
                default -> raw = Arrays.copyOfRange(encoded, start, start + length);
            }

            input.position(start + length);
            records.add(new Record(name, type, recordClass, timeToLive, raw, target, port));
        }

        return records;
    }

    private static @NotNull String readName(byte @NotNull [] encoded, @NotNull ByteBuffer input) throws IOException {
        ByteArrayOutputStream name = new ByteArrayOutputStream();
        int position = input.position();
        int resume = -1;

        for (int hops = 0; ; ) {
            if (position >= encoded.length) {
                throw Exceptions.newIOException("Truncated DNS name.");
            }

            int length = Byte.toUnsignedInt(encoded[position]);

            if ((length & POINTER_MASK) == POINTER_MASK) {
                if (position + 1 >= encoded.length || ++hops > MAXIMUM_POINTER_HOPS) {
                    throw Exceptions.newIOException("Malformed DNS name pointer.");
                }

                if (resume < 0) {
                    resume = position + POINTER_SIZE;
                }

                position = ((length & ~POINTER_MASK) << Byte.SIZE) | Byte.toUnsignedInt(encoded[position + 1]);

                continue;
            }

            if (length > MAXIMUM_LABEL_LENGTH) {
                throw Exceptions.newIOException("Unsupported DNS label type.");
            }

            if (length == 0) {
                input.position(resume < 0 ? position + 1 : resume);

                return name.toString(StandardCharsets.UTF_8);
            }

            if (position + 1 + length > encoded.length) {
                throw Exceptions.newIOException("Truncated DNS label.");
            }

            if (name.size() > 0) {
                name.write(LABEL_SEPARATOR);
            }

            writeEscapedLabel(name, encoded, position + 1, length);
            position += 1 + length;
        }
    }

    private static void writeEscapedLabel(@NotNull ByteArrayOutputStream text, byte @NotNull [] label, int offset, int length) {
        for (int index = offset; index < offset + length; index++) {
            int value = Byte.toUnsignedInt(label[index]);

            if (value == LABEL_SEPARATOR || value == ESCAPE) {
                text.write(ESCAPE);
                text.write(value);
            } else if (value <= LAST_DECIMAL_ESCAPED) {
                text.write(ESCAPE);
                text.write(FIRST_DIGIT + value / DECIMAL_HUNDREDS);
                text.write(FIRST_DIGIT + value / DECIMAL_BASE % DECIMAL_BASE);
                text.write(FIRST_DIGIT + value % DECIMAL_BASE);
            } else {
                text.write(value);
            }
        }
    }

    /**
     * Encodes the labels of a name in text form, each after its length byte; a backslash before three decimal digits up to
     * {@code 255} stands for that byte, before any other character for the character.
     *
     * @param name the name
     * @return the labels without the closing zero
     * @throws IllegalArgumentException if a label is empty or longer than {@link #MAXIMUM_LABEL_LENGTH}, or the name is too long
     */
    private static byte @NotNull [] encodeLabels(@NotNull String name) {
        byte[] text = name.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream labels = new ByteArrayOutputStream(text.length + 1);
        ByteArrayOutputStream label = new ByteArrayOutputStream(MAXIMUM_LABEL_LENGTH);
        int position = 0;

        while (position < text.length) {
            if (text[position] == LABEL_SEPARATOR) {
                throw Exceptions.newIllegalArgumentException("Empty DNS label in name: {0}", name);
            }

            label.reset();

            while (position < text.length && text[position] != LABEL_SEPARATOR) {
                int value = Byte.toUnsignedInt(text[position++]);

                if (value == ESCAPE) {
                    if (position == text.length) {
                        break;
                    }

                    value = Byte.toUnsignedInt(text[position++]);

                    if (isDigit(value) && position + 1 < text.length && isDigit(text[position]) && isDigit(text[position + 1])) {
                        int decimal = (value - FIRST_DIGIT) * DECIMAL_HUNDREDS + (text[position] - FIRST_DIGIT) * DECIMAL_BASE +
                                      (text[position + 1] - FIRST_DIGIT);

                        if (decimal <= LARGEST_DECIMAL_ESCAPE) {
                            value = decimal;
                            position += DECIMAL_ESCAPE_TRAILING_DIGITS;
                        }
                    }
                }

                label.write(value);
            }

            if (position < text.length) {
                position++;
            }

            if (label.size() > MAXIMUM_LABEL_LENGTH) {
                throw Exceptions.newIllegalArgumentException("DNS label longer than {0} bytes in name: {1}", MAXIMUM_LABEL_LENGTH, name);
            }

            labels.write(label.size());
            labels.writeBytes(label.toByteArray());
        }

        if (labels.size() + 1 > MAXIMUM_NAME_LENGTH) {
            throw Exceptions.newIllegalArgumentException("DNS name longer than {0} bytes: {1}", MAXIMUM_NAME_LENGTH, name);
        }

        return labels.toByteArray();
    }

    private static boolean isDigit(int value) {
        return value >= FIRST_DIGIT && value <= LAST_DIGIT;
    }

    private static void requireRemaining(@NotNull ByteBuffer input, int length) throws IOException {
        if (input.remaining() < length) {
            throw Exceptions.newIOException("Truncated DNS message.");
        }
    }

    /**
     * Writes a name, or its longest suffix already in the message as a compression pointer; the offsets are keyed by the
     * encoded labels, so only byte-identical names compress.
     */
    private static void writeName(@NotNull ByteArrayOutputStream output, @NotNull Map<String, Integer> offsets, @NotNull String name, int base) {
        byte[] labels = encodeLabels(name);
        int position = 0;

        while (position < labels.length) {
            String suffix = new String(labels, position, labels.length - position, StandardCharsets.ISO_8859_1);
            Integer known = offsets.get(suffix);

            if (known != null) {
                writeShort(output, POINTER_WORD | known);

                return;
            }

            int offset = base + output.size();

            if (offset < POINTER_LIMIT) {
                offsets.put(suffix, offset);
            }

            int length = 1 + labels[position];

            output.write(labels, position, length);
            position += length;
        }

        output.write(0);
    }

    private static void writeShort(@NotNull ByteArrayOutputStream output, int value) {
        output.write(value >>> Byte.SIZE);
        output.write(value);
    }


    private final int identifier;
    private final int flags;
    private final @NotNull List<Question> questions;
    private final @NotNull List<Record> answers;
    private final @NotNull List<Record> authorities;
    private final @NotNull List<Record> additionals;

    private MulticastDnsMessage(int identifier,
                                int flags,
                                @NotNull List<Question> questions,
                                @NotNull List<Record> answers,
                                @NotNull List<Record> authorities,
                                @NotNull List<Record> additionals) {
        this.identifier  = identifier;
        this.flags       = flags;
        this.questions   = questions;
        this.answers     = answers;
        this.authorities = authorities;
        this.additionals = additionals;
    }

    /**
     * @return the message identifier, {@code 0} in multicast DNS
     */
    public int getIdentifier() {
        return this.identifier;
    }

    /**
     * @return the flag word
     */
    public int getFlags() {
        return this.flags;
    }

    /**
     * @return {@code true} if the message is a response, {@code false} otherwise
     */
    public boolean isResponse() {
        return (this.flags & RESPONSE_FLAG) != 0;
    }

    /**
     * @return the questions
     */
    public @NotNull List<Question> getQuestions() {
        return this.questions;
    }

    /**
     * @return the answers
     */
    public @NotNull List<Record> getAnswers() {
        return this.answers;
    }

    /**
     * @return the authority records
     */
    public @NotNull List<Record> getAuthorities() {
        return this.authorities;
    }

    /**
     * @return the additional records
     */
    public @NotNull List<Record> getAdditionals() {
        return this.additionals;
    }

    /**
     * @return the answers, authorities and additional records in message order
     */
    public @NotNull List<Record> getAllRecords() {
        List<Record> records = new ArrayList<>(this.answers.size() + this.authorities.size() + this.additionals.size());

        records.addAll(this.answers);
        records.addAll(this.authorities);
        records.addAll(this.additionals);

        return records;
    }


    /**
     * Encodes the message with name compression.
     *
     * @return the datagram payload
     */
    public byte @NotNull [] encode() {
        ByteArrayOutputStream output = new ByteArrayOutputStream(MESSAGE_CAPACITY);
        Map<String, Integer> offsets = new HashMap<>();

        writeShort(output, this.identifier);
        writeShort(output, this.flags);
        writeShort(output, this.questions.size());
        writeShort(output, this.answers.size());
        writeShort(output, this.authorities.size());
        writeShort(output, this.additionals.size());

        for (Question question : this.questions) {
            writeName(output, offsets, question.name, 0);
            writeShort(output, question.type);
            writeShort(output, question.questionClass);
        }

        for (Record record : this.getAllRecords()) {
            writeName(output, offsets, record.name, 0);
            writeShort(output, record.type);
            writeShort(output, record.recordClass);
            writeShort(output, (int) (record.timeToLive >>> Short.SIZE));
            writeShort(output, (int) record.timeToLive);

            ByteArrayOutputStream data = new ByteArrayOutputStream();
            // The data's names are compressed against the message; their offsets count from the data start.
            int dataStart = output.size() + DATA_LENGTH_SIZE;

            switch (record.type) {
                case TYPE_PTR -> writeName(data, offsets, record.target, dataStart);
                case TYPE_SRV -> {
                    data.writeBytes(record.data);
                    writeShort(data, record.port);
                    writeName(data, offsets, record.target, dataStart);
                }
                case TYPE_NSEC -> {
                    writeName(data, offsets, record.target, dataStart);
                    data.writeBytes(record.data);
                }
                default -> data.writeBytes(record.data);
            }

            writeShort(output, data.size());
            output.writeBytes(data.toByteArray());
        }

        return output.toByteArray();
    }
}
