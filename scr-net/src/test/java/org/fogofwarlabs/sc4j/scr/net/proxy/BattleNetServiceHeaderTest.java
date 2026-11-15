package org.fogofwarlabs.sc4j.scr.net.proxy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HexFormat;

/**
 * Checks the request encoding and the parsing of {@link BattleNetServiceHeader} against the descriptor fixtures.
 */
final class BattleNetServiceHeaderTest {
    @Test
    void create_request_should_match_the_original_descriptor_fixture() throws Exception {
        byte[] expected = HexFormat.of().parseHex("0801100218f8acd1910128ac025defcdab89");
        BattleNetServiceHeader header = BattleNetServiceHeader.createRequest(1, 2, 0x12345678L, 300, 0x89ABCDEFL);

        Assertions.assertArrayEquals(expected, header.getEncodedBytes());

        BattleNetServiceHeader decoded = BattleNetServiceHeader.parse(expected);

        Assertions.assertEquals(1, decoded.getServiceIdentifier());
        Assertions.assertEquals(2, decoded.getMethodIdentifier());
        Assertions.assertEquals(0x12345678L, decoded.getToken());
        Assertions.assertEquals(300, decoded.getPayloadSize());
        Assertions.assertEquals(0x89ABCDEFL, decoded.getServiceHash());
    }

    @Test
    void parse_should_preserve_the_unsigned_descriptor_fixture() throws Exception {
        byte[] encoded = HexFormat.of().parseHex("08ffffffff0f10ffffffff0f18ffffffff0f20ffffffffffffffffff0128ffffffff0f40808080808080808080015dffffffff");
        BattleNetServiceHeader header = BattleNetServiceHeader.parse(encoded);

        Assertions.assertEquals(0xFFFFFFFFL, header.getServiceIdentifier());
        Assertions.assertEquals(0xFFFFFFFFL, header.getMethodIdentifier());
        Assertions.assertEquals(0xFFFFFFFFL, header.getToken());
        Assertions.assertEquals(0xFFFFFFFFL, header.getPayloadSize());
        Assertions.assertEquals(0xFFFFFFFFL, header.getServiceHash());
        Assertions.assertEquals(-1L, header.getObjectIdentifier());
        Assertions.assertEquals(Long.MIN_VALUE, header.getTimeout());
        Assertions.assertArrayEquals(encoded, header.getEncodedBytes());
    }

    @Test
    void parse_should_read_a_response_without_inferring_service_policy() throws Exception {
        BattleNetServiceHeader header = BattleNetServiceHeader.parse(HexFormat.of().parseHex("08fe0118113081808080084801"));

        Assertions.assertEquals(254, header.getServiceIdentifier());
        Assertions.assertEquals(17, header.getToken());
        Assertions.assertEquals(0x80000001L, header.getStatus());
        Assertions.assertTrue(header.isResponse());
        Assertions.assertTrue(header.hasScalarField(9));
        Assertions.assertFalse(header.hasScalarField(2));
        Assertions.assertFalse(header.hasScalarField(5));
    }

    @Test
    void parse_should_distinguish_absent_fields_from_present_zero() throws Exception {
        BattleNetServiceHeader header = BattleNetServiceHeader.parse(HexFormat.of().parseHex("08001800"));

        Assertions.assertTrue(header.hasScalarField(1));
        Assertions.assertTrue(header.hasScalarField(3));
        Assertions.assertFalse(header.hasScalarField(2));
        Assertions.assertFalse(header.hasScalarField(11));
        Assertions.assertEquals(0, header.getMethodIdentifier());
        Assertions.assertEquals(0, header.getServiceHash());
        Assertions.assertFalse(header.isResponse());
        Assertions.assertThrows(IllegalArgumentException.class, () -> header.hasScalarField(7));
        Assertions.assertThrows(IllegalArgumentException.class, () -> header.hasScalarField(32));
    }

    @Test
    void parse_should_preserve_nested_messages_from_the_descriptor_fixture() throws Exception {
        byte[] encoded = HexFormat.of().parseHex("080118033a0e0a060a040804100510061807200852040809100a");
        BattleNetServiceHeader header = BattleNetServiceHeader.parse(encoded);

        Assertions.assertEquals(1, header.getServiceIdentifier());
        Assertions.assertEquals(3, header.getToken());
        Assertions.assertArrayEquals(encoded, header.getEncodedBytes());
    }

    @Test
    void parse_should_retain_unknown_fields_and_use_the_last_correctly_typed_scalar() throws Exception {
        byte[] encoded = HexFormat.of().parseHex("1804080108050d7856341260016901020304050607087203abcdef7d123456785001");
        BattleNetServiceHeader header = BattleNetServiceHeader.parse(encoded);

        Assertions.assertEquals(5, header.getServiceIdentifier());
        Assertions.assertEquals(4, header.getToken());
        Assertions.assertArrayEquals(encoded, header.getEncodedBytes());

        encoded[0] = 0;

        byte[] returned = header.getEncodedBytes();

        returned[0] = 0;

        Assertions.assertEquals(0x18, header.getEncodedBytes()[0]);
    }

    @Test
    void parse_should_handle_scalar_overflow_and_noncanonical_variable_integers() throws Exception {
        BattleNetServiceHeader header = BattleNetServiceHeader.parse(HexFormat.of().parseHex("0881800018ffffffffffffffffff014802"));

        Assertions.assertEquals(1, header.getServiceIdentifier());
        Assertions.assertEquals(0xFFFFFFFFL, header.getToken());
        Assertions.assertTrue(header.isResponse());
    }

    @Test
    void parse_should_reject_missing_required_and_malformed_outer_fields() {
        for (String encoded : new String[]{"", "0801", "1801", "0a001801", "0801180100", "0801180180",
                                           "0801180161", "080118017203aa", "080118011880808080808080808002",
                                           "0801180163", "0801180164", "0801180166", "0801180167",
                                           "0801180172ffffffffffffffffff01", "08011801808080801000"}) {
            Assertions.assertThrows(IOException.class, () -> BattleNetServiceHeader.parse(HexFormat.of().parseHex(encoded)), encoded);
        }
    }

    @Test
    void create_request_should_reject_out_of_range_fields() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> BattleNetServiceHeader.createRequest(-1, 0, 0, 0, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> BattleNetServiceHeader.createRequest(0, 0, 0x100000000L, 0, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> BattleNetServiceHeader.createRequest(0, 0, 0, 0, -1));
    }
}
