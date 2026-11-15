package org.fogofwarlabs.sc4j.scr.net.proxy;

import org.fogofwarlabs.sc4j.scr.net.provider.ProviderChannel;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPacket;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderTransportMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Checks the encoding, the parsing and the probe answers of {@link GameProxyDatagram}.
 */
final class GameProxyDatagramTest {
    private static final int TYPE_DATA = 1;
    private static final int TYPE_RELAYED_PROBE = 8;
    private static final int TYPE_PROBE_ANSWER = 9;
    /**
     * The type value the type enum does not define; the values {@link #TYPE_DATA}..{@link #LAST_TYPE} otherwise are.
     */
    private static final int UNDEFINED_TYPE = 4;
    private static final int LAST_TYPE = 9;

    @Test
    void create_data_should_wrap_the_provider_fixture_in_native_type_and_payload_tags() throws Exception {
        byte[] expected = HexFormat.of().parseHex("0801121300000000913b0f003412cdab02000700153412");
        ProviderPacket packet = ProviderPacket.createTurn(0x1234, 0xABCD, 7, new byte[]{0x15, 0x34, 0x12});
        GameProxyDatagram datagram = GameProxyDatagram.createData(ProviderTransportMessage.create(packet));

        Assertions.assertArrayEquals(expected, datagram.getEncodedBytes());
        Assertions.assertEquals(TYPE_DATA, datagram.getMessageType());
        Assertions.assertEquals(1, datagram.getScalarField(GameProxyDatagram.ALTERNATE_TYPE_FIELD));
        Assertions.assertTrue(datagram.hasScalarField(GameProxyDatagram.TYPE_FIELD));
        Assertions.assertFalse(datagram.hasScalarField(GameProxyDatagram.ALTERNATE_TYPE_FIELD));
        Assertions.assertArrayEquals(packet.getEncodedBytes(),
                                     GameProxyDatagram.parse(expected).parseProviderMessage().getPacket().getEncodedBytes());
    }

    @Test
    void create_data_should_encode_a_multibyte_payload_length_beyond_the_inner_limit() throws Exception {
        ProviderPacket packet = ProviderPacket.createTurn(0, 0, 0, new byte[500]);
        GameProxyDatagram datagram = GameProxyDatagram.createData(ProviderTransportMessage.create(packet));
        byte[] encoded = datagram.getEncodedBytes();

        Assertions.assertEquals(521, encoded.length);
        Assertions.assertArrayEquals(HexFormat.of().parseHex("0801128404"), Arrays.copyOf(encoded, 5));
        Assertions.assertEquals(516, GameProxyDatagram.parse(encoded).getPayloadBytes().length);
        Assertions.assertArrayEquals(packet.getEncodedBytes(), GameProxyDatagram.parse(encoded).parseProviderMessage().getPacket().getEncodedBytes());
    }

    @Test
    void create_probe_answer_should_copy_the_probe_scalars_and_the_destination_the_probe_addressed() throws Exception {
        String[][] probesAndAnswers = {
                {"4801", "08011800200048016009"},
                {"48015a00", "08011800200048015a062800300038006009"},
                {"18c0d1821020e02f48015a0630c0d182c0055a0338e02f", "080118c0d1821020e02f48015a0b280030c0d182c00538e02f6009"},
                {"18c0d1821020e02f48015a0b280130c0d182c00538e02f", "080118c0d1821020e02f48015a1308c0d1821010e02f280130c0d182c00538e02f6009"}
        };

        for (String[] probeAndAnswer : probesAndAnswers) {
            GameProxyDatagram answer = GameProxyDatagram.createProbeAnswer(GameProxyDatagram.parse(HexFormat.of().parseHex(probeAndAnswer[0])));

            Assertions.assertEquals(probeAndAnswer[1], HexFormat.of().formatHex(answer.getEncodedBytes()));
            Assertions.assertEquals(TYPE_PROBE_ANSWER, answer.getMessageType());
        }

        GameProxyDatagram malformed = GameProxyDatagram.parse(HexFormat.of().parseHex("48015a01ff"));

        Assertions.assertThrows(IOException.class, () -> GameProxyDatagram.createProbeAnswer(malformed));
    }

    @Test
    void parse_should_keep_absent_and_unknown_enum_values_at_their_defaults() throws Exception {
        GameProxyDatagram empty = GameProxyDatagram.parse(new byte[0]);

        Assertions.assertEquals(TYPE_DATA, empty.getMessageType());
        Assertions.assertFalse(empty.hasScalarField(GameProxyDatagram.TYPE_FIELD));
        Assertions.assertFalse(empty.hasScalarField(GameProxyDatagram.ALTERNATE_TYPE_FIELD));

        for (int messageType = 0; messageType <= LAST_TYPE + 1; messageType++) {
            boolean recognized = messageType >= TYPE_DATA && messageType <= LAST_TYPE && messageType != UNDEFINED_TYPE;
            GameProxyDatagram datagram = GameProxyDatagram.parse(new byte[]{8, (byte) messageType, 18, 1, 0, 96, (byte) messageType});

            Assertions.assertEquals(recognized ? messageType : TYPE_DATA, datagram.getMessageType());
            Assertions.assertEquals(recognized, datagram.hasScalarField(GameProxyDatagram.TYPE_FIELD));
            Assertions.assertEquals(recognized, datagram.hasScalarField(GameProxyDatagram.ALTERNATE_TYPE_FIELD));
        }

        GameProxyDatagram duplicate = GameProxyDatagram.parse(HexFormat.of().parseHex("080308040800"));

        Assertions.assertEquals(3, duplicate.getMessageType());
    }

    @Test
    void parse_should_substitute_the_alternate_type_only_for_empty_type_one_payloads() throws Exception {
        Assertions.assertEquals(TYPE_PROBE_ANSWER, GameProxyDatagram.parse(HexFormat.of().parseHex("08016009")).getMessageType());
        Assertions.assertEquals(TYPE_RELAYED_PROBE, GameProxyDatagram.parse(HexFormat.of().parseHex("12006008")).getMessageType());
        Assertions.assertEquals(TYPE_DATA, GameProxyDatagram.parse(HexFormat.of().parseHex("08011201006009")).getMessageType());
        Assertions.assertEquals(2, GameProxyDatagram.parse(HexFormat.of().parseHex("08026009")).getMessageType());
        Assertions.assertThrows(IOException.class, () -> GameProxyDatagram.parse(HexFormat.of().parseHex("08016009")).parseProviderMessage());
    }

    @Test
    void parse_should_truncate_oversized_enums_before_validation_like_the_native_parser() throws Exception {
        GameProxyDatagram recognized = GameProxyDatagram.parse(HexFormat.of().parseHex("088180808010608980808010"));

        Assertions.assertEquals(1, recognized.getScalarField(GameProxyDatagram.TYPE_FIELD));
        Assertions.assertEquals(9, recognized.getScalarField(GameProxyDatagram.ALTERNATE_TYPE_FIELD));
        Assertions.assertEquals(TYPE_PROBE_ANSWER, recognized.getMessageType());
        Assertions.assertTrue(recognized.hasScalarField(GameProxyDatagram.TYPE_FIELD));
        Assertions.assertTrue(recognized.hasScalarField(GameProxyDatagram.ALTERNATE_TYPE_FIELD));

        byte[] unknown = HexFormat.of().parseHex("08848080801060ffffffffffffffffff01");
        GameProxyDatagram unrecognized = GameProxyDatagram.parse(unknown);

        Assertions.assertEquals(TYPE_DATA, unrecognized.getMessageType());
        Assertions.assertFalse(unrecognized.hasScalarField(GameProxyDatagram.TYPE_FIELD));
        Assertions.assertFalse(unrecognized.hasScalarField(GameProxyDatagram.ALTERNATE_TYPE_FIELD));
        Assertions.assertArrayEquals(unknown, unrecognized.getEncodedBytes());
    }

    @Test
    void parse_should_read_the_verified_scalar_slots_and_keep_unsigned_bits() throws Exception {
        GameProxyDatagram datagram = GameProxyDatagram.parse(
                HexFormat.of().parseHex("18ffffffff0f20ffff0328ffffffffffffffffff0148808080808080808080016008")
        );

        Assertions.assertEquals(0xFFFFFFFFL, datagram.getScalarField(GameProxyDatagram.SCALAR_FIELD_3));
        Assertions.assertEquals(65535, datagram.getScalarField(GameProxyDatagram.SCALAR_FIELD_4));
        Assertions.assertEquals(-1L, datagram.getScalarField(GameProxyDatagram.PAYLOAD_HASH_FIELD));
        Assertions.assertEquals(Long.MIN_VALUE, datagram.getScalarField(GameProxyDatagram.PROBE_TIMESTAMP_FIELD));
        Assertions.assertEquals(8, datagram.getScalarField(GameProxyDatagram.ALTERNATE_TYPE_FIELD));

        for (int fieldNumber : new int[]{GameProxyDatagram.SCALAR_FIELD_3, GameProxyDatagram.SCALAR_FIELD_4, GameProxyDatagram.PAYLOAD_HASH_FIELD,
                                         GameProxyDatagram.PROBE_TIMESTAMP_FIELD, GameProxyDatagram.ALTERNATE_TYPE_FIELD}) {
            Assertions.assertTrue(datagram.hasScalarField(fieldNumber));
        }

        Assertions.assertFalse(datagram.hasScalarField(GameProxyDatagram.TYPE_FIELD));
        Assertions.assertThrows(IllegalArgumentException.class, () -> datagram.getScalarField(2));
        Assertions.assertThrows(IllegalArgumentException.class, () -> datagram.hasScalarField(11));
    }

    @Test
    void parse_should_retain_opaque_fields_and_use_the_last_correctly_typed_payload_and_scalar() throws Exception {
        byte[] encoded = HexFormat.of().parseHex("1201aa08031202bbcc080112010012020000180118025a01ff690102030405060708");
        byte[] original = encoded.clone();
        GameProxyDatagram datagram = GameProxyDatagram.parse(encoded);

        encoded[0]                    = 0;
        datagram.getEncodedBytes()[0] = 0;
        datagram.getPayloadBytes()[0] = 1;

        Assertions.assertEquals(TYPE_DATA, datagram.getMessageType());
        Assertions.assertEquals(2, datagram.getScalarField(GameProxyDatagram.SCALAR_FIELD_3));
        Assertions.assertArrayEquals(new byte[]{0, 0}, datagram.getPayloadBytes());
        Assertions.assertArrayEquals(original, datagram.getEncodedBytes());

        GameProxyDatagram wrongWireType = GameProxyDatagram.parse(HexFormat.of().parseHex("0d0100000010001a01006004"));

        Assertions.assertFalse(wrongWireType.hasScalarField(GameProxyDatagram.TYPE_FIELD));
        Assertions.assertFalse(wrongWireType.hasScalarField(GameProxyDatagram.SCALAR_FIELD_3));
        Assertions.assertFalse(wrongWireType.hasScalarField(GameProxyDatagram.ALTERNATE_TYPE_FIELD));
        Assertions.assertArrayEquals(new byte[0], wrongWireType.getPayloadBytes());
    }

    @Test
    void parse_should_reject_malformed_outer_encoding_and_other_envelopes() {
        for (String encoded : new String[]{"00", "01", "0b", "0c", "0e", "0f", "08", "0880", "1202ff",
                                           "090102", "0d0102", "8080808010", "0880808080808080808002"}) {
            Assertions.assertThrows(IOException.class, () -> GameProxyDatagram.parse(HexFormat.of().parseHex(encoded)));
        }

        Assertions.assertThrows(IOException.class, () -> GameProxyDatagram.parse(HexFormat.of().parseHex("00000000913b0f003412cdab02000700153412")));
        Assertions.assertThrows(IOException.class, () -> GameProxyDatagram.parse(new byte[0]).parseProviderMessage());
        Assertions.assertThrows(IOException.class, () -> GameProxyDatagram.parse(HexFormat.of().parseHex("0801120403000000")).parseProviderMessage());
    }

    @Test
    void datagram_framed_turns_reach_the_turn_queue_and_acknowledge_exclusively() throws Exception {
        ProviderChannel local = new ProviderChannel(0xFFFF, 42);
        ProviderChannel remote = new ProviderChannel(42, 0xFFFF);
        ProviderPacket outgoing = local.createOutgoingTurn(42, 7, new byte[0]);
        byte[] encoded = GameProxyDatagram.createData(ProviderTransportMessage.create(outgoing)).getEncodedBytes();
        ProviderPacket received = GameProxyDatagram.parse(encoded).parseProviderMessage().getPacket();

        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, remote.receive(received));

        ProviderPacket remoteTurn = remote.pollExpectedPacket();

        Assertions.assertNotNull(remoteTurn);
        Assertions.assertArrayEquals(new byte[]{5}, remoteTurn.getPayloadBytes());

        ProviderPacket reply = remote.createOutgoingTurn(0, 3, new byte[]{0x55});
        byte[] returned = GameProxyDatagram.createData(ProviderTransportMessage.create(reply)).getEncodedBytes();

        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED,
                                local.receive(GameProxyDatagram.parse(returned).parseProviderMessage().getPacket()));
        Assertions.assertEquals(0, local.getPendingOutgoingCount());

        ProviderPacket localTurn = local.pollExpectedPacket();

        Assertions.assertNotNull(localTurn);
        Assertions.assertArrayEquals(new byte[]{0x55}, localTurn.getPayloadBytes());
    }
}
