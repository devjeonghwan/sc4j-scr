package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

/**
 * Checks the known-answer suppression of a response: identical records only, the half time to live, the class without the
 * cache flush bit, and the additional records of suppressed answers.
 */
final class MulticastDnsResponseTest {
    /**
     * {@code 07:48:24.913}, {@code 192.168.0.88}: the browse query of the presence subtype (header, question) with the known
     * answers {@code PNM:A} and {@code PNM:B}.
     */
    private static final @NotNull String PRESENCE_BROWSE_QUERY = "000000000001000200000000" +
                                                                 "045f706e6d045f737562095f626c697a7a617264045f756470056c6f63616c00" + "000c0001" +
                                                                 "c00c000c000100001194000805504e4d3a41c016" +
                                                                 "c00c000c000100001194000805504e4d3a42c016";

    private static final @NotNull String SERVICE = "_blizzard._udp.local";
    private static final @NotNull String PRESENCE_SUBTYPE = "_pnm._sub." + SERVICE;
    private static final @NotNull String INSTANCE = "PNM:A." + SERVICE;
    private static final @NotNull String HOST = "HOST-PC.local";
    /**
     * The instance and host names with other ASCII letter cases, which name the same records.
     */
    private static final @NotNull String OTHER_CASE_INSTANCE = "pnm:a._BLIZZARD._udp.local";
    private static final @NotNull String OTHER_CASE_HOST = "host-pc.LOCAL";

    /**
     * The record class of a unique record: the internet class with the cache flush bit.
     */
    private static final int UNIQUE = 0x8001;
    /**
     * The record class of a shared record: the internet class without the cache flush bit.
     */
    private static final int SHARED = 1;

    private static final long SERVICE_TIME_TO_LIVE = 4500;
    private static final long HOST_TIME_TO_LIVE = 120;

    private static final int PRESENCE_PORT = 256;

    private static final byte @NotNull [] CURRENT_DATA = {0x0a, 0x05, 'P', 'N', 'M', ':', 'A', 0x10, 0x03};
    private static final byte @NotNull [] STALE_DATA = {0x0a, 0x05, 'P', 'N', 'M', ':', 'A', 0x10, 0x01};

    private static MulticastDnsMessage.@NotNull Record createText() {
        return MulticastDnsMessage.Record.createData(INSTANCE, MulticastDnsMessage.TYPE_TXT, UNIQUE, SERVICE_TIME_TO_LIVE, new byte[1]);
    }

    private static MulticastDnsMessage.@NotNull Record createData(byte @NotNull [] data, int recordClass, long timeToLive) {
        return MulticastDnsMessage.Record.createData(INSTANCE, MulticastDnsMessage.TYPE_BLIZZARD, recordClass, timeToLive, data);
    }

    private static MulticastDnsMessage.@NotNull Record createService() {
        return MulticastDnsMessage.Record.createService(INSTANCE, UNIQUE, HOST_TIME_TO_LIVE, PRESENCE_PORT, HOST);
    }

    private static MulticastDnsMessage.@NotNull Record createPointer(@NotNull String name) {
        return MulticastDnsMessage.Record.createPointer(name, SHARED, SERVICE_TIME_TO_LIVE, INSTANCE);
    }

    private static @NotNull List<MulticastDnsMessage.Record> createInstanceRecords() {
        return List.of(createText(), createData(CURRENT_DATA, UNIQUE, SERVICE_TIME_TO_LIVE), createService());
    }

    @Test
    void suppress_known_answers_should_drop_the_captured_known_pointer_and_its_additional_records() throws Exception {
        MulticastDnsMessage query = MulticastDnsMessage.parse(HexFormat.of().parseHex(PRESENCE_BROWSE_QUERY));
        MulticastDnsResponse response = new MulticastDnsResponse();

        Assertions.assertEquals(PRESENCE_SUBTYPE, query.getQuestions().get(0).getName());

        response.addAnswer(createPointer(query.getQuestions().get(0).getName()), createInstanceRecords());
        response.suppressKnownAnswers(query.getAnswers());

        Assertions.assertTrue(response.getAnswers().isEmpty());
        Assertions.assertTrue(response.getAdditionals().isEmpty());
    }

    @Test
    void suppress_known_answers_should_answer_when_the_known_data_differs() {
        MulticastDnsResponse response = new MulticastDnsResponse();
        MulticastDnsMessage.Record current = createData(CURRENT_DATA, UNIQUE, SERVICE_TIME_TO_LIVE);

        response.addAnswer(current, List.of());
        response.suppressKnownAnswers(List.of(createData(STALE_DATA, SHARED, SERVICE_TIME_TO_LIVE)));

        Assertions.assertEquals(List.of(current), response.getAnswers());

        response.suppressKnownAnswers(List.of(createData(CURRENT_DATA, SHARED, SERVICE_TIME_TO_LIVE)));

        Assertions.assertTrue(response.getAnswers().isEmpty());
    }

    @Test
    void suppress_known_answers_should_need_at_least_half_the_time_to_live() {
        MulticastDnsResponse response = new MulticastDnsResponse();
        MulticastDnsMessage.Record service = createService();

        response.addAnswer(service, List.of());
        response.suppressKnownAnswers(List.of(MulticastDnsMessage.Record.createService(OTHER_CASE_INSTANCE, SHARED, HOST_TIME_TO_LIVE / 2 - 1,
                                                                                       PRESENCE_PORT, OTHER_CASE_HOST)));

        Assertions.assertEquals(List.of(service), response.getAnswers());

        response.suppressKnownAnswers(List.of(MulticastDnsMessage.Record.createService(OTHER_CASE_INSTANCE, SHARED, HOST_TIME_TO_LIVE / 2,
                                                                                       PRESENCE_PORT, OTHER_CASE_HOST)));

        Assertions.assertTrue(response.getAnswers().isEmpty());
    }

    @Test
    void suppress_known_answers_should_drop_a_known_additional_record_only() {
        MulticastDnsResponse response = new MulticastDnsResponse();
        MulticastDnsMessage.Record pointer = createPointer(PRESENCE_SUBTYPE);
        List<MulticastDnsMessage.Record> instanceRecords = createInstanceRecords();

        response.addAnswer(pointer, instanceRecords);
        response.suppressKnownAnswers(List.of(createText()));

        Assertions.assertEquals(List.of(pointer), response.getAnswers());
        Assertions.assertEquals(instanceRecords.subList(1, instanceRecords.size()), response.getAdditionals());
    }

    @Test
    void add_answer_should_send_each_record_once_and_keep_an_additional_with_its_first_answer() {
        MulticastDnsResponse response = new MulticastDnsResponse();
        MulticastDnsMessage.Record subtypePointer = createPointer(PRESENCE_SUBTYPE);
        MulticastDnsMessage.Record servicePointer = createPointer(SERVICE);
        MulticastDnsMessage.Record text = createText();

        response.addAnswer(subtypePointer, createInstanceRecords());
        response.addAnswer(servicePointer, createInstanceRecords());
        response.addAnswer(createPointer(PRESENCE_SUBTYPE), createInstanceRecords());
        response.addAnswer(text, List.of());

        Assertions.assertEquals(List.of(subtypePointer, servicePointer, text), response.getAnswers());
        Assertions.assertEquals(2, response.getAdditionals().size());

        response.suppressKnownAnswers(List.of(subtypePointer));

        Assertions.assertEquals(List.of(servicePointer, text), response.getAnswers());
        Assertions.assertTrue(response.getAdditionals().isEmpty());
    }
}
