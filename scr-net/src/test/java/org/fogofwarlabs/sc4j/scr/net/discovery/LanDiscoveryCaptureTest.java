package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

/**
 * Checks LAN discovery codecs against recorded multicast DNS fixtures, redacted with lengths and structure kept (host names
 * {@code HOST-PC} and {@code WORKSTATION-PC}, the EDNS0 owner option's MAC address, IPv6 link-local addresses, BattleTag
 * characters {@code X...#0...} and the presence guids).
 */
final class LanDiscoveryCaptureTest {
    /**
     * The game record of the host {@code 192.168.0.88}, revision {@code 0}: {@code (2)Bottleneck.scm}, Top vs Bottom, speed
     * {@code 6}, {@code 2} players.
     */
    private static final @NotNull String GAME_RECORD = "0a014210001a0a0a055f6e616d651201421a0e0a0570726f7879120566616c73651a150a076d61705f637263120a313438343730363136371a110a0c636c6f7365645f736c6f74731201301a160a0d6d61705f66696c655f73697a65120536383337361a160a086d61705f6e616d65120a426f74746c656e65636b1a130a0d6e65745f7475726e5f72617465120232341a2b0a076d61705f6d6435122037323545414136423343303531333741324543414639373346313434314341301a120a0969735f7265706c6179120566616c73651a140a0f706c61796572735f63757272656e741201311a0b0a055f74797065120231331a160a116f62736572766572735f63757272656e741201301a0d0a085f737562747970651201311a0f0a0a67616d655f73706565641201361a110a0c736176655f67616d655f69641201301a220a0d6d61705f66696c655f6e616d651211283229426f74746c656e65636b2e73636d1a0a0a05666c6167731201301a0f0a0a706c61796572735f61691201301a100a0b706c61796572735f6d61781201321a0e0a09686f73745f6e616d651201421a110a0c6d61705f74696c655f7365741201311a090a0472616e6b1201311a120a0d6f62736572766572735f6d61781201341a110a0a6d61705f68656967687412033132381a0b0a065f666c6167731201301a100a096d61705f77696474681203313238";

    /**
     * {@code 07:48:22.655}, {@code 192.168.0.2}: the daemon's probe of the presence instance (question {@code ANY}, proposed
     * {@code SRV}, {@code OPT}).
     */
    private static final @NotNull String PRESENCE_PROBE = "00000000000100000001000105504e4d3a41095f626c697a7a617264045f756470056c6f63616c0000ff0001c00c002100010000007800170000000001000e574f524b53544154494f4e2d5043c02100002905a000001194000c000400080000020000000001";

    /**
     * {@code 07:48:23.467}, {@code 192.168.0.2}: the first announcement of presence {@code PNM:A} (revision {@code 1}).
     */
    private static final @NotNull String PRESENCE_ANNOUNCEMENT = "00008400000000060000000505504e4d3a41095f626c697a7a617264045f756470056c6f63616c000010800100001194000100095f7365727669636573075f646e732d7364c01c000c0001000011940002c012045f706e6d045f737562c012000c0001000011940002c00cc012000c0001000011940002c00cc00c004280010000119400ae0a05504e4d3a4110011a4b0a0a61747472696275746573123d0a0c0a06526567696f6e12023f3f0a100a0b4c616e546f6f6e4e616d651201410a1b0a09426174746c65546167120e58585858585858585823303030301a180a0a70726f6772616d5f6964120a313339373035333532301a0e0a09746f6f6e5f6e616d651201411a200a04677569641218414141414141414141414141414141414141414141413d3d1a0a0a0666696c7465721200c00c002180010000007800170000000001000e574f524b53544154494f4e2d5043c021c14500018001000000780004c0a80002c145001c8001000000780010fe800000000000000000000000000001c145002f8001000000780008c145000440000008c00c002f800100001194000dc00c000900008000400000002000002905a000001194000c000400080000020000000001";

    /**
     * {@code 07:48:32.257}, {@code 192.168.0.88}: the first announcement of game {@code B} (revision {@code 0}).
     */
    private static final @NotNull String GAME_ANNOUNCEMENT = "0000840000000005000000010142095f626c697a7a617264045f756470056c6f63616c000010800100001194000100075f736578706539045f737562c00e000c0001000011940002c00cc00e000c0001000011940002c00cc00c004280010000119402060a014210001a0a0a055f6e616d651201421a0e0a0570726f7879120566616c73651a150a076d61705f637263120a313438343730363136371a110a0c636c6f7365645f736c6f74731201301a160a0d6d61705f66696c655f73697a65120536383337361a160a086d61705f6e616d65120a426f74746c656e65636b1a130a0d6e65745f7475726e5f72617465120232341a2b0a076d61705f6d6435122037323545414136423343303531333741324543414639373346313434314341301a120a0969735f7265706c6179120566616c73651a140a0f706c61796572735f63757272656e741201311a0b0a055f74797065120231331a160a116f62736572766572735f63757272656e741201301a0d0a085f737562747970651201311a0f0a0a67616d655f73706565641201361a110a0c736176655f67616d655f69641201301a220a0d6d61705f66696c655f6e616d651211283229426f74746c656e65636b2e73636d1a0a0a05666c6167731201301a0f0a0a706c61796572735f61691201301a100a0b706c61796572735f6d61781201321a0e0a09686f73745f6e616d651201421a110a0c6d61705f74696c655f7365741201311a090a0472616e6b1201311a120a0d6f62736572766572735f6d61781201341a110a0a6d61705f68656967687412033132381a0b0a065f666c6167731201301a100a096d61705f77696474681203313238c00c0021800100000078001000000000e01707484f53542d5043c01dc00c002f800100001194000dc00c0009000080004000000020";

    /**
     * {@code 07:48:33.279}, {@code 192.168.0.88}: presence {@code PNM:B} revision {@code 3} with {@code lobby_info}, and game
     * {@code B} revision {@code 1}.
     */
    private static final @NotNull String PRESENCE_AND_GAME = "00008400000000060000000505504e4d3a42095f626c697a7a617264045f756470056c6f63616c00004280010000119400da0a05504e4d3a4210031a4c0a0a61747472696275746573123e0a0c0a06526567696f6e12023f3f0a100a0b4c616e546f6f6e4e616d651201420a1c0a09426174746c65546167120f5858585858585858582330303030301a180a0a70726f6772616d5f6964120a313339373035333532301a0e0a09746f6f6e5f6e616d651201421a290a0a6c6f6262795f696e666f121b08d0b0959a0510001a014220012a0c302e302e302e303a363131321a200a04677569641218415145424151454241514542415145424151454241513d3d1a0a0a0666696c74657212000142c0120010800100001194000100075f736578706539045f737562c012000c0001000011940002c10cc012000c0001000011940002c10cc10c004280010000119402060a014210011a0a0a055f6e616d651201421a0e0a0570726f7879120566616c73651a150a076d61705f637263120a313438343730363136371a110a0c636c6f7365645f736c6f74731201301a160a0d6d61705f66696c655f73697a65120536383337361a160a086d61705f6e616d65120a426f74746c656e65636b1a130a0d6e65745f7475726e5f72617465120232341a2b0a076d61705f6d6435122037323545414136423343303531333741324543414639373346313434314341301a120a0969735f7265706c6179120566616c73651a140a0f706c61796572735f63757272656e741201311a0b0a055f74797065120231331a160a116f62736572766572735f63757272656e741201301a0d0a085f737562747970651201311a0f0a0a67616d655f73706565641201361a110a0c736176655f67616d655f69641201301a220a0d6d61705f66696c655f6e616d651211283229426f74746c656e65636b2e73636d1a0a0a05666c6167731201301a0f0a0a706c61796572735f61691201301a100a0b706c61796572735f6d61781201321a0e0a09686f73745f6e616d651201421a110a0c6d61705f74696c655f7365741201311a090a0472616e6b1201311a120a0d6f62736572766572735f6d61781201301a110a0a6d61705f68656967687412033132381a0b0a065f666c6167731201301a100a096d61705f77696474681203313238c10c0021800100000078001000000000e01707484f53542d5043c021c36800018001000000780004c0a80058c368001c8001000000780010fe800000000000000000000000000002c368002f8001000000780008c368000440000008c00c002f800100001194000dc00c0009000080004000000020c10c002f800100001194000dc10c0009000080004000000020";

    /**
     * {@code 07:48:53.488}, {@code 192.168.0.88}: after the game start, presence revision {@code 6} without {@code lobby_info}
     * and the game's goodbyes.
     */
    private static final @NotNull String GAME_GOODBYE = "00008400000000030000000105504e4d3a42095f626c697a7a617264045f756470056c6f63616c00004280010000119400af0a05504e4d3a4210061a4c0a0a61747472696275746573123e0a0c0a06526567696f6e12023f3f0a100a0b4c616e546f6f6e4e616d651201420a1c0a09426174746c65546167120f5858585858585858582330303030301a180a0a70726f6772616d5f6964120a313339373035333532301a0e0a09746f6f6e5f6e616d651201421a200a04677569641218415145424151454241514542415145424151454241513d3d1a0a0a0666696c7465721200075f736578706539045f737562c012000c00010000000000040142c012c012000c0001000000000002c0fac00c002f800100001194000dc00c0009000080004000000020";

    /**
     * The record class of a unique record: the internet class with the cache flush bit.
     */
    private static final int UNIQUE = 0x8001;
    private static final int CLASS_INTERNET = 1;

    private static final int RESPONSE_FLAGS = 0x8400;

    private static final int TYPE_TXT = 16;
    private static final int TYPE_SRV = 33;
    private static final int TYPE_BLIZZARD = 66;

    private static final int GAME_PORT = 6112;

    @Test
    void every_captured_message_reencodes_byte_identical() throws Exception {
        for (String encodedHexadecimal : List.of(PRESENCE_PROBE, PRESENCE_ANNOUNCEMENT, GAME_ANNOUNCEMENT, PRESENCE_AND_GAME, GAME_GOODBYE)) {
            byte[] captured = HexFormat.of().parseHex(encodedHexadecimal);
            MulticastDnsMessage message = MulticastDnsMessage.parse(captured);

            Assertions.assertEquals(0, message.getIdentifier());

            MulticastDnsMessage copy = MulticastDnsMessage.create(message.getFlags(),
                                                                  message.getQuestions(),
                                                                  message.getAnswers(),
                                                                  message.getAuthorities(),
                                                                  message.getAdditionals());

            Assertions.assertEquals(encodedHexadecimal, HexFormat.of().formatHex(copy.encode()));
        }
    }

    @Test
    void game_announcement_decodes_its_text_pointer_game_service_and_next_secure_records() throws Exception {
        MulticastDnsMessage message = MulticastDnsMessage.parse(HexFormat.of().parseHex(GAME_ANNOUNCEMENT));
        List<MulticastDnsMessage.Record> answers = message.getAnswers();

        Assertions.assertEquals(RESPONSE_FLAGS, message.getFlags());
        Assertions.assertEquals(5, answers.size());

        MulticastDnsMessage.Record text = answers.get(0);

        Assertions.assertEquals("B._blizzard._udp.local", text.getName());
        Assertions.assertEquals(TYPE_TXT, text.getType());
        Assertions.assertEquals(UNIQUE, text.getRecordClass());
        Assertions.assertEquals(4500, text.getTimeToLive());
        Assertions.assertArrayEquals(new byte[]{0}, text.getData());

        Assertions.assertEquals("_sexpe9._sub._blizzard._udp.local", answers.get(1).getName());
        Assertions.assertEquals("B._blizzard._udp.local", answers.get(1).getTarget());
        Assertions.assertEquals(CLASS_INTERNET, answers.get(1).getRecordClass());
        Assertions.assertEquals("_blizzard._udp.local", answers.get(2).getName());
        Assertions.assertEquals("B._blizzard._udp.local", answers.get(2).getTarget());

        MulticastDnsMessage.Record data = answers.get(3);

        Assertions.assertEquals(TYPE_BLIZZARD, data.getType());
        Assertions.assertEquals(GAME_RECORD, HexFormat.of().formatHex(data.getData()));

        MulticastDnsMessage.Record service = answers.get(4);

        Assertions.assertEquals(TYPE_SRV, service.getType());
        Assertions.assertEquals(120, service.getTimeToLive());
        // 6112 goes on the wire byte-swapped.
        Assertions.assertEquals(57367, service.getPort());
        Assertions.assertEquals(GAME_PORT, Short.toUnsignedInt(Short.reverseBytes((short) service.getPort())));
        Assertions.assertEquals("HOST-PC.local", service.getTarget());

        MulticastDnsMessage.Record nextSecure = message.getAdditionals().get(0);
        MulticastDnsMessage.Record expected = MulticastDnsMessage.Record.createNextSecure("B._blizzard._udp.local",
                                                                                          UNIQUE,
                                                                                          4500,
                                                                                          TYPE_TXT, TYPE_SRV, TYPE_BLIZZARD);

        Assertions.assertEquals(expected.getTarget(), nextSecure.getTarget());
        Assertions.assertArrayEquals(expected.getData(), nextSecure.getData());
    }

    @Test
    void game_goodbye_expires_the_game_records_and_clears_the_lobby_name() throws Exception {
        MulticastDnsMessage message = MulticastDnsMessage.parse(HexFormat.of().parseHex(GAME_GOODBYE));
        List<MulticastDnsMessage.Record> answers = message.getAnswers();

        Assertions.assertEquals(3, answers.size());
        Assertions.assertEquals(0, answers.get(1).getTimeToLive());
        Assertions.assertEquals("B._blizzard._udp.local", answers.get(1).getTarget());
        Assertions.assertEquals(0, answers.get(2).getTimeToLive());
        Assertions.assertEquals("_blizzard._udp.local", answers.get(2).getName());

        LanPresenceInformation presence = LanPresenceInformation.of(LanRecord.parse(answers.get(0).getData()));

        Assertions.assertNotNull(presence);
        Assertions.assertNull(presence.getLobbyName());
    }

    @Test
    void game_record_decodes_to_game_information_and_reencodes_byte_identical() throws Exception {
        byte[] captured = HexFormat.of().parseHex(GAME_RECORD);
        LanRecord record = LanRecord.parse(captured);

        Assertions.assertEquals("B", record.getName());
        Assertions.assertEquals(0, record.getRevision());
        Assertions.assertEquals(26, record.getAttributes().size());

        LanGameInformation information = LanGameInformation.of(record);

        Assertions.assertNotNull(information);
        Assertions.assertEquals("B", information.getName());
        Assertions.assertEquals("B", information.getHostName());
        Assertions.assertEquals(15, information.getGameType());
        Assertions.assertEquals(6, information.getGameSpeed());
        Assertions.assertEquals(24, information.getNetworkTurnRate());
        Assertions.assertEquals(1, information.getPlayersCurrent());
        Assertions.assertEquals(2, information.getPlayersMaximum());
        Assertions.assertEquals(4, information.getObserversMaximum());
        Assertions.assertEquals("Bottleneck", information.getMapName());
        Assertions.assertEquals("(2)Bottleneck.scm", information.getMapFileName());
        Assertions.assertEquals(68376, information.getMapFileSize());
        Assertions.assertEquals(1484706167L, information.getMapCrc());
        Assertions.assertEquals("725EAA6B3C05137A2ECAF973F1441CA0", information.getMapMd5());
        Assertions.assertEquals(1, information.getMapTileSet());
        Assertions.assertEquals(128, information.getMapWidth());
        Assertions.assertEquals(128, information.getMapHeight());

        Assertions.assertArrayEquals(captured, information.toRecord(0).encode());
        Assertions.assertArrayEquals(captured, record.encode());
    }

    @Test
    void presence_records_decode_and_reencode_and_own_presence_omits_the_battle_tag() throws Exception {
        MulticastDnsMessage first = MulticastDnsMessage.parse(HexFormat.of().parseHex(PRESENCE_ANNOUNCEMENT));
        byte[] captured = first.getAnswers().get(4).getData();
        LanPresenceInformation presence = LanPresenceInformation.of(LanRecord.parse(captured));

        Assertions.assertNotNull(presence);
        Assertions.assertEquals("A", presence.getToonName());
        Assertions.assertEquals("A", presence.getLanToonName());
        Assertions.assertEquals("XXXXXXXXX#0000", presence.getBattleTag());
        Assertions.assertNull(presence.getLobbyName());
        Assertions.assertArrayEquals(new byte[16], presence.getGuid());
        Assertions.assertArrayEquals(captured, presence.toRecord(1).encode());

        MulticastDnsMessage hosting = MulticastDnsMessage.parse(HexFormat.of().parseHex(PRESENCE_AND_GAME));
        byte[] lobby = hosting.getAnswers().get(0).getData();
        LanPresenceInformation host = LanPresenceInformation.of(LanRecord.parse(lobby));

        Assertions.assertNotNull(host);
        Assertions.assertEquals("B", host.getLobbyName());
        Assertions.assertArrayEquals(lobby, host.toRecord(3).encode());

        // The presence sc4j publishes: the same layout without the BattleTag attribute.
        LanPresenceInformation own = LanPresenceInformation.create("A", null, new byte[16]);
        LanRecord ownRecord = own.toRecord(1);

        Assertions.assertEquals("PNM:A", ownRecord.getName());

        LanPresenceInformation reread = LanPresenceInformation.of(LanRecord.parse(ownRecord.encode()));

        Assertions.assertNotNull(reread);
        Assertions.assertEquals("A", reread.getLanToonName());
        Assertions.assertNull(reread.getBattleTag());
        Assertions.assertEquals(List.of("attributes", "program_id", "toon_name", "guid", "filter"),
                                ownRecord.getAttributes().stream().map(LanRecord.Attribute::getKey).toList());
    }
}
