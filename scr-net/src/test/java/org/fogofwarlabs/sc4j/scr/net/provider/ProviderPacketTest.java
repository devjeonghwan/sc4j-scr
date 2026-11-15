package org.fogofwarlabs.sc4j.scr.net.provider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Verifies that provider packets encode the native envelope fixtures and that parsing applies the native length, type and
 * checksum checks.
 */
final class ProviderPacketTest {
    private static final int FIRST_PAYLOAD_BYTE = 12;
    private static final int HEADER_SIZE = 12;
    private static final int MAXIMUM_PACKET_SIZE = 0xFFFF;
    private static final int MAXIMUM_PAYLOAD_SIZE = MAXIMUM_PACKET_SIZE - HEADER_SIZE;

    private static final int BYTE_MASK = 0xFF;

    private static final int TURN_CLASS = 2;

    @Test
    void create_turn_should_encode_the_native_fixture_and_parse_it_back() throws Exception {
        byte[] expected = HexFormat.of().parseHex("913b0f003412cdab02000700153412");
        ProviderPacket packet = ProviderPacket.createTurn(0x1234, 0xABCD, 7, HexFormat.of().parseHex("153412"));

        Assertions.assertArrayEquals(expected, packet.getEncodedBytes());

        ProviderPacket parsed = ProviderPacket.parse(expected);

        Assertions.assertEquals(0x1234, parsed.getSequence());
        Assertions.assertEquals(0xABCD, parsed.getAcknowledgement());
        Assertions.assertEquals(TURN_CLASS, parsed.getType());
        Assertions.assertEquals(0, parsed.getSubtype());
        Assertions.assertEquals(7, parsed.getSenderIdentifier());
        Assertions.assertEquals(0, parsed.getFlags());
        Assertions.assertArrayEquals(HexFormat.of().parseHex("153412"), parsed.getPayloadBytes());
    }

    @Test
    void create_turn_should_keep_unsigned_fields_and_match_the_reverse_checksum_fixture() throws Exception {
        byte[] expected = HexFormat.of().parseHex("22c71400ffffffff0200ff00ffffffffffffffff");
        byte[] payload = new byte[8];

        Arrays.fill(payload, (byte) 0xFF);

        ProviderPacket packet = ProviderPacket.createTurn(0xFFFF, 0xFFFF, 0xFF, payload);

        Assertions.assertArrayEquals(expected, packet.getEncodedBytes());

        ProviderPacket parsed = ProviderPacket.parse(expected);

        Assertions.assertEquals(0xFFFF, parsed.getSequence());
        Assertions.assertEquals(0xFFFF, parsed.getAcknowledgement());
        Assertions.assertEquals(0xFF, parsed.getSenderIdentifier());
        Assertions.assertArrayEquals(payload, parsed.getPayloadBytes());
    }

    @Test
    void parse_should_accept_the_native_zero_residue_checksum_that_differs_from_canonical() throws Exception {
        byte[] equivalent = HexFormat.of().parseHex("c9000d00000000000200000027");
        ProviderPacket packet = ProviderPacket.parse(equivalent);

        Assertions.assertArrayEquals(equivalent, packet.getEncodedBytes());
        Assertions.assertArrayEquals(HexFormat.of().parseHex("c9ff0d00000000000200000027"),
                                     ProviderPacket.createTurn(0, 0, 0, new byte[]{0x27}).getEncodedBytes());
    }

    @Test
    void parse_should_ignore_bytes_after_the_encoded_length_and_keep_a_snapshot() throws Exception {
        byte[] fixture = HexFormat.of().parseHex("52990d00000000000200000005");
        byte[] supplied = Arrays.copyOf(fixture, fixture.length + 3);

        Arrays.fill(supplied, fixture.length, supplied.length, (byte) 0xFF);

        ProviderPacket packet = ProviderPacket.parse(supplied);

        supplied[FIRST_PAYLOAD_BYTE] = 0;

        Assertions.assertArrayEquals(fixture, packet.getEncodedBytes());

        byte[] returned = packet.getEncodedBytes();

        returned[0] = 0;

        byte[] payload = packet.getPayloadBytes();

        payload[0] = 0;

        Assertions.assertArrayEquals(fixture, packet.getEncodedBytes());
        Assertions.assertArrayEquals(new byte[]{5}, packet.getPayloadBytes());
    }

    @Test
    void parse_should_reject_single_bit_changes_across_header_and_payload() {
        byte[] fixture = HexFormat.of().parseHex("52990d00000000000200000005");

        for (int byteIndex = 0; byteIndex < fixture.length; byteIndex++) {
            for (int bitIndex = 0; bitIndex < Byte.SIZE; bitIndex++) {
                byte[] corrupted = fixture.clone();

                corrupted[byteIndex] ^= (byte) (1 << bitIndex);

                Assertions.assertThrows(IOException.class, () -> ProviderPacket.parse(corrupted));
            }
        }
    }

    @Test
    void parse_should_reject_lengths_and_types_outside_the_envelope_checks() {
        Assertions.assertThrows(IOException.class, () -> ProviderPacket.parse(new byte[HEADER_SIZE - 1]));
        Assertions.assertThrows(IOException.class, () -> ProviderPacket.parse(new byte[HEADER_SIZE]));
        Assertions.assertThrows(IOException.class, () -> ProviderPacket.parse(HexFormat.of().parseHex("00000e00000000000200000005")));
        Assertions.assertThrows(IOException.class, () -> ProviderPacket.parse(HexFormat.of().parseHex("00000b00000000000200000005")));
        Assertions.assertThrows(IOException.class, () -> ProviderPacket.parse(HexFormat.of().parseHex("3aa20c000700090004a5035a")));
    }

    @Test
    void parse_should_keep_raw_subtype_flags_and_empty_payload_for_each_accepted_type() throws Exception {
        String[] fixtures = {"1ec20c000700090000a5035a", "25ba0c000700090001a5035a", "2cb20c000700090002a5035a", "33aa0c000700090003a5035a"};

        for (int packetType = 0; packetType < fixtures.length; packetType++) {
            ProviderPacket packet = ProviderPacket.parse(HexFormat.of().parseHex(fixtures[packetType]));

            Assertions.assertEquals(packetType, packet.getType());
            Assertions.assertEquals(0xA5, packet.getSubtype());
            Assertions.assertEquals(0x5A, packet.getFlags());
            Assertions.assertEquals(7, packet.getSequence());
            Assertions.assertEquals(9, packet.getAcknowledgement());
            Assertions.assertEquals(3, packet.getSenderIdentifier());
            Assertions.assertEquals(0, packet.getPayloadBytes().length);
        }
    }

    @Test
    void create_turn_should_support_the_maximum_encoded_length_with_the_native_checksum() throws Exception {
        byte[] payload = new byte[MAXIMUM_PAYLOAD_SIZE];

        Arrays.fill(payload, (byte) 0xFF);

        ProviderPacket packet = ProviderPacket.createTurn(0xFFFF, 0xFFFF, 0xFF, payload);
        byte[] encoded = packet.getEncodedBytes();

        Assertions.assertEquals(MAXIMUM_PACKET_SIZE, encoded.length);
        Assertions.assertEquals(0x0E, encoded[0] & BYTE_MASK);
        Assertions.assertEquals(0xEF, encoded[1] & BYTE_MASK);
        Assertions.assertArrayEquals(payload, ProviderPacket.parse(encoded).getPayloadBytes());
    }

    @Test
    void create_turn_should_reject_out_of_range_fields_and_copy_the_payload() {
        byte[] payload = new byte[]{5};

        Assertions.assertThrows(IllegalArgumentException.class, () -> ProviderPacket.createTurn(-1, 0, 0, payload));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProviderPacket.createTurn(0x10000, 0, 0, payload));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProviderPacket.createTurn(0, -1, 0, payload));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProviderPacket.createTurn(0, 0x10000, 0, payload));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProviderPacket.createTurn(0, 0, -1, payload));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProviderPacket.createTurn(0, 0, 0x100, payload));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProviderPacket.createTurn(0, 0, 0, new byte[0]));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ProviderPacket.createTurn(0, 0, 0, new byte[MAXIMUM_PAYLOAD_SIZE + 1]));

        ProviderPacket packet = ProviderPacket.createTurn(0, 0, 0, payload);

        payload[0] = 0;

        Assertions.assertArrayEquals(new byte[]{5}, packet.getPayloadBytes());
    }
}
