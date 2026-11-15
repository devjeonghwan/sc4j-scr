package org.fogofwarlabs.sc4j.scr.net.proxy;

import org.fogofwarlabs.sc4j.scr.net.provider.ProviderChannel;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Checks the receive gate, the probe answers and the endpoint mapping of {@link GameProxyProviderReceiver}.
 */
final class GameProxyProviderReceiverTest {
    private static final @NotNull String PROVIDER_ENVELOPE = "0801121300000000913b0f003412cdab02000700153412";
    /**
     * The answer to a probe with timestamp {@code 1} and no address information.
     */
    private static final @NotNull String PROBE_ANSWER = "08011800200048016009";

    private static final GameProxyProviderReceiver.@NotNull DatagramSender UNEXPECTED_ANSWER = (address, port, datagram) -> Assertions.fail();
    private static final GameProxyProviderReceiver.@NotNull PacketReceiver UNEXPECTED_PACKET = (address, port, packet) -> Assertions.fail();
    private static final GameProxyProviderReceiver.@NotNull PacketReceiver IGNORED_PACKET = (address, port, packet) -> {
    };

    /**
     * The address {@code 10.0.0.1} as the receiver takes it: a private address.
     */
    private static final int PRIVATE_ADDRESS = 0x0100000A;
    /**
     * The address {@code 127.0.0.1} as the receiver takes it: a private address.
     */
    private static final int LOOPBACK_ADDRESS = 0x0100007F;
    /**
     * The address {@code 8.8.8.8} as the receiver takes it: a public address.
     */
    private static final int PUBLIC_ADDRESS = 0x08080808;
    private static final int GAME_PORT = 6112;
    private static final int OTHER_PORT = 6114;

    private static final int PAYLOAD_HASH_FIELD = 5;

    @Test
    void matches_native_receive_hash_cases() throws Exception {
        String[] fields = {"2800", "2801", "28ffffffffffffffffff01"};
        long[] expected = {0xCB798E03DF694E23L, 0xB1A1FECBEF5CC75AL, 0xDF5E94859FDBCC91L};

        for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
            GameProxyDatagram datagram = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + fields[fieldIndex]));

            Assertions.assertEquals(expected[fieldIndex], datagram.calculateReceiveHash());
        }
    }

    @Test
    void receive_should_drop_a_hash_match_before_the_turn_queue_or_acknowledgement_changes() throws Exception {
        GameProxyDatagram datagram = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + "2801"));
        ProviderChannel channel = new ProviderChannel(0xABCC, 0x1234);
        ProviderPacket pending = channel.createOutgoingTurn(0, 0, new byte[]{5});
        long firstValue = 0x123456789ABCDEF0L;
        long[] values = {firstValue, 0xB1A1FECBEF5CC75AL ^ firstValue, firstValue, 0xB1A1FECBEF5CC75AL ^ firstValue ^ 1};
        int[] valueIndex = {0};
        GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(() -> values[valueIndex[0]++]);
        GameProxyProviderReceiver.PacketReceiver delivery = (address, port, packet) -> {
            Assertions.assertEquals(PRIVATE_ADDRESS, address);
            Assertions.assertEquals(GAME_PORT, port);
            Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, channel.receive(packet));
        };

        Assertions.assertFalse(receiver.receive(datagram, PRIVATE_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, delivery));
        Assertions.assertSame(pending, channel.getOldestPendingPacket());
        Assertions.assertEquals(1, channel.getPendingOutgoingCount());
        Assertions.assertNull(channel.pollExpectedPacket());
        Assertions.assertEquals(2, valueIndex[0]);

        Assertions.assertTrue(receiver.receive(datagram, PRIVATE_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, delivery));
        Assertions.assertEquals(0, channel.getPendingOutgoingCount());

        ProviderPacket receivedTurn = channel.pollExpectedPacket();

        Assertions.assertNotNull(receivedTurn);
        Assertions.assertArrayEquals(new byte[]{0x15, 0x34, 0x12}, receivedTurn.getPayloadBytes());
        Assertions.assertEquals(4, valueIndex[0]);
    }

    @Test
    void receive_should_call_the_hash_context_only_for_private_addresses_and_a_nonzero_gate() throws Exception {
        int[] addresses = {0x0100000A, 0x0100007F, 0x01000FAC, 0x010010AC, 0x01001FAC, 0x010020AC, 0x0100A8C0, 0x0100A9C0, 0x08080808, 0};
        boolean[] privateAddresses = {true, true, false, true, true, false, true, false, false, false};

        for (int addressIndex = 0; addressIndex < addresses.length; addressIndex++) {
            for (String field : new String[]{"", "2800", "2801", "28ffffffffffffffffff01"}) {
                GameProxyDatagram datagram = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + field));
                int[] calls = {0};
                GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(() -> {
                    calls[0]++;

                    return 0;
                });

                Assertions.assertTrue(receiver.receive(datagram, addresses[addressIndex], GAME_PORT, UNEXPECTED_ANSWER, IGNORED_PACKET));
                Assertions.assertEquals(privateAddresses[addressIndex] && datagram.getScalarField(PAYLOAD_HASH_FIELD) != 0 ? 2 : 0, calls[0]);
                Assertions.assertTrue(new GameProxyProviderReceiver(null).receive(datagram, addresses[addressIndex], GAME_PORT,
                                                                                  UNEXPECTED_ANSWER, IGNORED_PACKET));
            }
        }
    }

    @Test
    void receive_should_replace_the_inbound_endpoint_only_when_both_route_parts_are_nonzero() throws Exception {
        GameProxyDatagram datagram = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE));
        GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(null);
        int[][] routes = {{0x04030201, 6113}, {0, 6113}, {0x04030201, 0}, {0xF4030201, 65535}};

        for (int[] route : routes) {
            boolean mapped = route[0] != 0 && route[1] != 0;

            receiver.recordInboundRoute(PRIVATE_ADDRESS, GAME_PORT, route[0], route[1]);

            Assertions.assertTrue(receiver.receive(datagram, PRIVATE_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, (address, port, packet) -> {
                Assertions.assertEquals(mapped ? route[0] : PRIVATE_ADDRESS, address);
                Assertions.assertEquals(mapped ? route[1] : GAME_PORT, port);
            }));
            Assertions.assertTrue(receiver.receive(datagram, PRIVATE_ADDRESS, OTHER_PORT, UNEXPECTED_ANSWER, (address, port, packet) -> {
                Assertions.assertEquals(PRIVATE_ADDRESS, address);
                Assertions.assertEquals(OTHER_PORT, port);
            }));
        }
    }

    @Test
    void receive_should_merge_the_nested_relay_flag_before_choosing_the_ordinary_path() throws Exception {
        GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(null);

        for (String fields : new String[]{"5a00", "5a022800", "5a0228015a022800", "5a022a00"}) {
            GameProxyDatagram datagram = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + fields));

            Assertions.assertFalse(datagram.isRelayed());
            Assertions.assertTrue(receiver.receive(datagram, PUBLIC_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, IGNORED_PACKET));
        }

        for (String fields : new String[]{"5a022801", "5a022802", "5a0228015a00", "5a06288080808010"}) {
            GameProxyDatagram datagram = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + fields));

            Assertions.assertTrue(datagram.isRelayed());
            Assertions.assertThrows(UnsupportedOperationException.class,
                                    () -> receiver.receive(datagram, PUBLIC_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, UNEXPECTED_PACKET));
        }
    }

    @Test
    void receive_should_reject_unimplemented_control_and_malformed_nesting() throws Exception {
        GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(null);
        GameProxyDatagram control = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + "0802"));

        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> receiver.receive(control, PUBLIC_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, UNEXPECTED_PACKET));

        for (String fields : new String[]{"5a01ff", "5a01ff5a022800", "5a0100", "5a022b2c", "5a031202ff"}) {
            byte[] encoded = HexFormat.of().parseHex(PROVIDER_ENVELOPE + fields);
            GameProxyDatagram datagram = GameProxyDatagram.parse(encoded);

            Assertions.assertArrayEquals(encoded, datagram.getEncodedBytes());
            Assertions.assertThrows(IOException.class, datagram::isRelayed);
            Assertions.assertThrows(IOException.class,
                                    () -> receiver.receive(datagram, PUBLIC_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, UNEXPECTED_PACKET));
        }
    }

    @Test
    void receive_should_answer_a_probe_of_every_type_but_a_probe_answer_at_its_source() throws Exception {
        GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(null);
        List<String> answers = new ArrayList<>();
        int[] deliveries = {0};
        GameProxyProviderReceiver.DatagramSender sender = (address, port, datagram) -> {
            Assertions.assertEquals(PUBLIC_ADDRESS, address);
            Assertions.assertEquals(GAME_PORT, port);
            answers.add(HexFormat.of().formatHex(datagram.getEncodedBytes()));
        };
        GameProxyDatagram probe = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + "4801"));
        GameProxyDatagram relayedProbe = GameProxyDatagram.parse(HexFormat.of().parseHex("08084801"));
        GameProxyDatagram probeAnswer = GameProxyDatagram.parse(HexFormat.of().parseHex("48016009"));
        GameProxyDatagram controlProbe = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + "08024801"));

        Assertions.assertTrue(receiver.receive(probe, PUBLIC_ADDRESS, GAME_PORT, sender, (address, port, packet) -> deliveries[0]++));
        Assertions.assertFalse(receiver.receive(relayedProbe, PUBLIC_ADDRESS, GAME_PORT, sender, UNEXPECTED_PACKET));
        Assertions.assertFalse(receiver.receive(probeAnswer, PUBLIC_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, UNEXPECTED_PACKET));
        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> receiver.receive(controlProbe, PUBLIC_ADDRESS, GAME_PORT, sender, UNEXPECTED_PACKET));

        Assertions.assertEquals(List.of(PROBE_ANSWER, PROBE_ANSWER, PROBE_ANSWER), answers);
        Assertions.assertEquals(1, deliveries[0]);
    }

    @Test
    void receive_should_not_answer_a_probe_the_hash_gate_drops() throws Exception {
        GameProxyDatagram datagram = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + "28014801"));
        long[] values = {0, datagram.calculateReceiveHash()};
        int[] valueIndex = {0};
        GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(() -> values[valueIndex[0]++]);

        Assertions.assertFalse(receiver.receive(datagram, LOOPBACK_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, UNEXPECTED_PACKET));
        Assertions.assertEquals(2, valueIndex[0]);
    }

    @Test
    void receive_should_answer_a_relayed_probe_before_rejecting_the_relay_branch() throws Exception {
        GameProxyDatagram datagram = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + "48015a022801"));
        GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(null);
        List<String> answers = new ArrayList<>();

        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> receiver.receive(datagram, PUBLIC_ADDRESS, GAME_PORT,
                                                       (address, port, answer) -> answers.add(HexFormat.of().formatHex(answer.getEncodedBytes())),
                                                       UNEXPECTED_PACKET));
        Assertions.assertEquals(List.of("08011800200048015a0a080010002801300038006009"), answers);
    }

    @Test
    void receive_should_apply_the_hash_gate_before_unsupported_control_dispatch() throws Exception {
        GameProxyDatagram datagram = GameProxyDatagram.parse(HexFormat.of().parseHex(PROVIDER_ENVELOPE + "28010802"));
        long[] values = {0, datagram.calculateReceiveHash()};
        int[] valueIndex = {0};
        GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(() -> values[valueIndex[0]++]);

        Assertions.assertFalse(receiver.receive(datagram, LOOPBACK_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, UNEXPECTED_PACKET));
        Assertions.assertEquals(2, valueIndex[0]);
    }

    @Test
    void receive_should_validate_provider_bytes_and_port_bounds_before_delivery() throws Exception {
        GameProxyProviderReceiver receiver = new GameProxyProviderReceiver(null);
        GameProxyDatagram invalid = GameProxyDatagram.parse(HexFormat.of().parseHex("0801120400000000"));

        Assertions.assertThrows(IOException.class, () -> receiver.receive(invalid, PUBLIC_ADDRESS, GAME_PORT, UNEXPECTED_ANSWER, UNEXPECTED_PACKET));
        Assertions.assertThrows(IllegalArgumentException.class, () -> receiver.receive(invalid, 0, -1, UNEXPECTED_ANSWER, UNEXPECTED_PACKET));
        Assertions.assertThrows(IllegalArgumentException.class, () -> receiver.recordInboundRoute(0, 65536, 0, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> receiver.recordInboundRoute(0, 0, 0, -1));
    }
}
