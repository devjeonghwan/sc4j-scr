package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A LAN discovery record with a name, revision and ordered key/value attributes.
 * Nested messages remain raw bytes; text attributes are decoded as UTF-8.
 * Parsing accepts unknown fields and preserves repeated attributes in message order.
 */
public final class LanRecord {
    /**
     * The record fields: the instance name, the revision and the repeated attributes.
     */
    private static final int NAME_FIELD = 1;
    private static final int REVISION_FIELD = 2;
    private static final int ATTRIBUTE_FIELD = 3;

    /**
     * The fields of one attribute message.
     */
    private static final int KEY_FIELD = 1;
    private static final int VALUE_FIELD = 2;

    /**
     * The initial capacity of the encoded record.
     */
    private static final int RECORD_CAPACITY = 256;

    /**
     * The room of an attribute message past its key and value bytes: two tags and two one-byte lengths.
     */
    private static final int ATTRIBUTE_OVERHEAD = 4;

    /**
     * One attribute: a key and its value.
     */
    public static final class Attribute {
        private final @NotNull String key;
        private final byte @NotNull [] value;

        private Attribute(@NotNull String key, byte @NotNull [] value) {
            this.key   = key;
            this.value = value;
        }

        /**
         * @return the key
         */
        public @NotNull String getKey() {
            return this.key;
        }

        /**
         * @return a copy of the value bytes
         */
        public byte @NotNull [] getValue() {
            return this.value.clone();
        }

        /**
         * @return the value as UTF-8 text
         */
        public @NotNull String getText() {
            return new String(this.value, StandardCharsets.UTF_8);
        }
    }

    /**
     * Builds {@link LanRecord} instances, keeping the attribute order.
     */
    public static final class Builder {
        private final @NotNull String name;
        private final long revision;

        private final @NotNull List<Attribute> attributes;

        private Builder(@NotNull String name, long revision) {
            this.name     = name;
            this.revision = revision;

            this.attributes = new ArrayList<>();
        }

        /**
         * Appends a text attribute.
         *
         * @param key   the key
         * @param value the value
         * @return this instance for chaining
         */
        public @NotNull Builder add(@NotNull String key, @NotNull String value) {
            return this.add(key, value.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Appends an attribute whose value is raw bytes (a nested message).
         *
         * @param key   the key
         * @param value the value bytes
         * @return this instance for chaining
         */
        public @NotNull Builder add(@NotNull String key, byte @NotNull [] value) {
            this.attributes.add(new Attribute(key, value.clone()));

            return this;
        }

        /**
         * Builds a {@link LanRecord} instance of the appended attributes.
         *
         * @return the created {@link LanRecord} instance
         */
        public @NotNull LanRecord build() {
            return new LanRecord(this.name, this.revision, this.attributes);
        }
    }

    /**
     * Creates a {@link Builder} instance.
     *
     * @param name     the instance name, for example {@code PNM:A}
     * @param revision the revision
     * @return the created {@link Builder} instance
     */
    public static @NotNull Builder createBuilder(@NotNull String name, long revision) {
        return new Builder(name, revision);
    }

    /**
     * Creates a {@link LanRecord} instance from record data.
     *
     * @param encoded the record data
     * @return the created {@link LanRecord} instance
     * @throws IOException if the data is not such a message
     */
    public static @NotNull LanRecord parse(byte @NotNull [] encoded) throws IOException {
        ByteBuffer input = ByteBuffer.wrap(encoded);
        String name = "";
        long revision = 0;
        List<Attribute> attributes = new ArrayList<>();

        while (input.hasRemaining()) {
            long tag = ProtobufUtils.readVariableInteger(input);
            int fieldNumber = (int) (tag >>> ProtobufUtils.WIRE_TYPE_BITS);
            int wireType = (int) (tag & ProtobufUtils.WIRE_TYPE_MASK);

            if (fieldNumber == REVISION_FIELD && wireType == ProtobufUtils.WIRE_TYPE_VARIABLE_INTEGER) {
                revision = ProtobufUtils.readVariableInteger(input);
            } else if (wireType == ProtobufUtils.WIRE_TYPE_LENGTH_DELIMITED) {
                byte[] value = ProtobufUtils.readBytes(input);

                if (fieldNumber == NAME_FIELD) {
                    name = new String(value, StandardCharsets.UTF_8);
                } else if (fieldNumber == ATTRIBUTE_FIELD) {
                    attributes.add(parseAttribute(value));
                }
            } else {
                ProtobufUtils.skipField(input, wireType);
            }
        }

        return new LanRecord(name, revision, attributes);
    }

    /**
     * Creates an {@link Attribute} instance from a key and value message in a record or nested attribute list.
     *
     * @param encoded the message
     * @return the created {@link Attribute} instance
     * @throws IOException if the message is malformed
     */
    static @NotNull Attribute parseAttribute(byte @NotNull [] encoded) throws IOException {
        ByteBuffer input = ByteBuffer.wrap(encoded);
        String key = "";
        byte[] value = new byte[0];

        while (input.hasRemaining()) {
            long tag = ProtobufUtils.readVariableInteger(input);
            int wireType = (int) (tag & ProtobufUtils.WIRE_TYPE_MASK);

            if (wireType != ProtobufUtils.WIRE_TYPE_LENGTH_DELIMITED) {
                ProtobufUtils.skipField(input, wireType);

                continue;
            }

            byte[] bytes = ProtobufUtils.readBytes(input);

            if ((tag >>> ProtobufUtils.WIRE_TYPE_BITS) == KEY_FIELD) {
                key = new String(bytes, StandardCharsets.UTF_8);
            } else if ((tag >>> ProtobufUtils.WIRE_TYPE_BITS) == VALUE_FIELD) {
                value = bytes;
            }
        }

        return new Attribute(key, value);
    }

    /**
     * Encodes one key and value message.
     *
     * @param key   the key
     * @param value the value bytes
     * @return the message
     */
    static byte @NotNull [] encodeAttribute(@NotNull String key, byte @NotNull [] value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream entry = new ByteArrayOutputStream(keyBytes.length + value.length + ATTRIBUTE_OVERHEAD);

        ProtobufUtils.writeBytes(entry, KEY_FIELD, keyBytes);
        ProtobufUtils.writeBytes(entry, VALUE_FIELD, value);

        return entry.toByteArray();
    }


    private final @NotNull String name;
    private final long revision;

    private final @NotNull List<Attribute> attributes;

    private LanRecord(@NotNull String name, long revision, @NotNull List<Attribute> attributes) {
        this.name     = name;
        this.revision = revision;

        this.attributes = List.copyOf(attributes);
    }

    /**
     * @return the instance name
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the revision
     */
    public long getRevision() {
        return this.revision;
    }

    /**
     * @return the unmodifiable attributes in record order
     */
    public @NotNull List<Attribute> getAttributes() {
        return this.attributes;
    }


    /**
     * Finds an attribute's value as text.
     *
     * @param key the key
     * @return the value of the last attribute of that key, or {@code null} if there is none
     */
    public @Nullable String findText(@NotNull String key) {
        String found = null;

        for (Attribute attribute : this.attributes) {
            if (attribute.key.equals(key)) {
                found = attribute.getText();
            }
        }

        return found;
    }

    /**
     * Finds an attribute's raw value.
     *
     * @param key the key
     * @return a copy of the value of the last attribute of that key, or {@code null} if there is none
     */
    public byte @Nullable [] findBytes(@NotNull String key) {
        byte[] found = null;

        for (Attribute attribute : this.attributes) {
            if (attribute.key.equals(key)) {
                found = attribute.getValue();
            }
        }

        return found;
    }

    /**
     * Encodes the record data in the SDK's field order: name, revision, attributes.
     *
     * @return the record data
     */
    public byte @NotNull [] encode() {
        ByteArrayOutputStream output = new ByteArrayOutputStream(RECORD_CAPACITY);

        ProtobufUtils.writeBytes(output, NAME_FIELD, this.name.getBytes(StandardCharsets.UTF_8));
        ProtobufUtils.writeVariableInteger(output, REVISION_FIELD, this.revision);

        for (Attribute attribute : this.attributes) {
            ProtobufUtils.writeBytes(output, ATTRIBUTE_FIELD, encodeAttribute(attribute.key, attribute.value));
        }

        return output.toByteArray();
    }
}
