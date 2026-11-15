package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

/**
 * Checks the multicast DNS name rules (ASCII-only case folding, byte-exact compression, labels that hold dots or spaces) and
 * the service record's priority and weight against bytes of the recorded game announcement of instance {@code B}.
 */
final class MulticastDnsMessageTest {
    /**
     * The type, class with the cache flush bit, time to live ({@code 4500} seconds) and data (one zero byte) of the captured text record.
     */
    private static final @NotNull String TEXT_RECORD_FIXED_PART = "0010800100001194000100";
    /**
     * The captured owner name {@code B._blizzard._udp.local}, whose {@code local} label starts at offset {@code 29}.
     */
    private static final @NotNull String CAPTURED_OWNER_NAME = "0142095f626c697a7a617264045f756470056c6f63616c00";
    /**
     * The captured text record of instance {@code B}: its owner name and the fixed part.
     */
    private static final @NotNull String CAPTURED_TEXT_RECORD = CAPTURED_OWNER_NAME + TEXT_RECORD_FIXED_PART;
    /**
     * The type, class, time to live ({@code 120} seconds) and data length of the captured service record of instance {@code B}.
     */
    private static final @NotNull String SERVICE_RECORD_HEAD = "0021800100000078" + "0010";
    /**
     * The captured service record's byte-swapped port {@code 6112} and host {@code HOST-PC} with a pointer to {@code local}.
     */
    private static final @NotNull String CAPTURED_SERVICE_TAIL = "e017" + "07484f53542d5043" + "c01d";
    private static final int CAPTURED_PORT = 0xe017;
    /**
     * Response headers with two answers and with one answer.
     */
    private static final @NotNull String RESPONSE_HEADER = "000084000000000200000000";
    private static final @NotNull String SINGLE_ANSWER_HEADER = "000084000000000100000000";
    /**
     * A compression pointer to the {@code _blizzard._udp.local} suffix, which the captured record writes at offset {@code 14}.
     */
    private static final @NotNull String SERVICE_POINTER = "c00e";

    /**
     * The record class of a unique record: the internet class with the cache flush bit.
     */
    private static final int UNIQUE = 0x8001;

    private static final long SERVICE_TIME_TO_LIVE = 4500;
    private static final long HOST_TIME_TO_LIVE = 120;

    private static final int MAXIMUM_LABEL_LENGTH = 63;

    private static MulticastDnsMessage.@NotNull Record createText(@NotNull String name) {
        return MulticastDnsMessage.Record.createData(name, MulticastDnsMessage.TYPE_TXT, UNIQUE, SERVICE_TIME_TO_LIVE, new byte[1]);
    }

    private static byte @NotNull [] encodeAnswers(MulticastDnsMessage.@NotNull Record @NotNull ... answers) {
        return MulticastDnsMessage.create(MulticastDnsMessage.RESPONSE_FLAGS, List.of(), List.of(answers), List.of(), List.of()).encode();
    }

    private static @NotNull String createLabel(@NotNull String label) {
        byte[] bytes = label.getBytes(StandardCharsets.UTF_8);

        return HexFormat.of().toHexDigits((byte) bytes.length) + HexFormat.of().formatHex(bytes);
    }

    @Test
    void are_names_equal_should_fold_ascii_letters_only() {
        Assertions.assertTrue(MulticastDnsMessage.areNamesEqual("Game._BLIZZARD._udp.local", "game._blizzard._UDP.LOCAL"));
        Assertions.assertFalse(MulticastDnsMessage.areNamesEqual("\u00c4._blizzard._udp.local", "\u00e4._blizzard._udp.local"));
        Assertions.assertFalse(MulticastDnsMessage.areNamesEqual("\u212a._blizzard._udp.local", "k._blizzard._udp.local"));
        Assertions.assertFalse(MulticastDnsMessage.areNamesEqual("\u0130._blizzard._udp.local", "i\u0307._blizzard._udp.local"));
    }

    @Test
    void encode_should_compress_only_byte_identical_names() throws Exception {
        byte[] encoded = encodeAnswers(createText("B._blizzard._udp.local"), createText("b._blizzard._udp.local"));

        Assertions.assertEquals(RESPONSE_HEADER + CAPTURED_TEXT_RECORD + createLabel("b") + SERVICE_POINTER + TEXT_RECORD_FIXED_PART,
                                HexFormat.of().formatHex(encoded));
        Assertions.assertEquals("b._blizzard._udp.local", MulticastDnsMessage.parse(encoded).getAnswers().get(1).getName());

        byte[] unicode = encodeAnswers(createText("\u00c4._blizzard._udp.local"), createText("\u00e4._blizzard._udp.local"));

        Assertions.assertEquals("\u00e4._blizzard._udp.local", MulticastDnsMessage.parse(unicode).getAnswers().get(1).getName());
    }

    @Test
    void encode_should_write_an_escaped_dot_inside_one_label() {
        byte[] encoded = encodeAnswers(createText("B._blizzard._udp.local"), createText("v1\\.2._blizzard._udp.local"));

        Assertions.assertEquals(RESPONSE_HEADER + CAPTURED_TEXT_RECORD + createLabel("v1.2") + SERVICE_POINTER + TEXT_RECORD_FIXED_PART,
                                HexFormat.of().formatHex(encoded));
    }

    @Test
    void parse_should_escape_dots_spaces_backslashes_and_control_bytes_of_a_label() throws Exception {
        String encoded = RESPONSE_HEADER + CAPTURED_TEXT_RECORD + createLabel("v1.2") + createLabel("My Game") + createLabel("a\\b\u0001") +
                         SERVICE_POINTER + TEXT_RECORD_FIXED_PART;
        String name = MulticastDnsMessage.parse(HexFormat.of().parseHex(encoded)).getAnswers().get(1).getName();

        Assertions.assertEquals("v1\\.2.My\\032Game.a\\\\b\\001._blizzard._udp.local", name);
        Assertions.assertEquals(encoded, HexFormat.of().formatHex(encodeAnswers(createText("B._blizzard._udp.local"), createText(name))));
    }

    @Test
    void escape_label_should_escape_dots_backslashes_and_bytes_up_to_the_space() {
        Assertions.assertEquals("v1\\.2\\032a\\\\b\\009\u00c4", MulticastDnsMessage.escapeLabel("v1.2 a\\b\t\u00c4"));
        Assertions.assertEquals("PNM:A", MulticastDnsMessage.escapeLabel("PNM:A"));
    }

    @Test
    void parse_should_keep_the_priority_and_the_weight_of_a_service() throws Exception {
        String encoded = SINGLE_ANSWER_HEADER + CAPTURED_OWNER_NAME + SERVICE_RECORD_HEAD + "0001" + "0002" + CAPTURED_SERVICE_TAIL;
        MulticastDnsMessage.Record service = MulticastDnsMessage.parse(HexFormat.of().parseHex(encoded)).getAnswers().get(0);

        Assertions.assertArrayEquals(new byte[]{0, 1, 0, 2}, service.getData());
        Assertions.assertEquals(encoded, HexFormat.of().formatHex(encodeAnswers(service)));
        Assertions.assertFalse(service.isIdentical(MulticastDnsMessage.Record.createService("B._blizzard._udp.local", UNIQUE,
                                                                                            HOST_TIME_TO_LIVE, CAPTURED_PORT,
                                                                                            "HOST-PC.local")));

        String unweighted = SINGLE_ANSWER_HEADER + CAPTURED_OWNER_NAME + SERVICE_RECORD_HEAD + "0000" + "0000" + CAPTURED_SERVICE_TAIL;

        Assertions.assertTrue(MulticastDnsMessage.parse(HexFormat.of().parseHex(unweighted)).getAnswers().get(0)
                                                 .isIdentical(MulticastDnsMessage.Record.createService("b._blizzard._udp.local", UNIQUE,
                                                                                                       HOST_TIME_TO_LIVE, CAPTURED_PORT,
                                                                                                       "host-pc.local")));
    }

    @Test
    void encode_should_reject_a_label_longer_than_63_bytes() {
        String longest = "x".repeat(MAXIMUM_LABEL_LENGTH);

        Assertions.assertDoesNotThrow(() -> encodeAnswers(createText(longest + "._blizzard._udp.local")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> encodeAnswers(createText(longest + "x._blizzard._udp.local")));
    }
}
