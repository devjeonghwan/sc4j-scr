package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the game statistics string against the statistics of a captured native host and the field defaults it leaves
 * empty.
 */
final class LanGameStatisticsTest {
    /**
     * The captured statistics of the native host: Top vs Bottom on {@code (2)Bottleneck.scm}, speed {@code 6}, one human of
     * two players, the observer records closed.
     */
    private static final @NotNull String CAPTURED_STATISTICS = ",44,12,6,1,f,,1,18,1,,B\rBottleneck\r\rfeedda7a,0,,18,";
    private static final int CAPTURED_MAP_SIZE = 128;
    private static final int CAPTURED_SUBTYPE = 1;
    private static final int CAPTURED_TURN_RATE = 24;
    private static final int CAPTURED_TILESET = 1;
    private static final int CAPTURED_MAXIMUM_PLAYERS = 2;
    private static final int CAPTURED_SPEED = 6;
    private static final int CAPTURED_MAP_STATUS = 1;
    private static final int CAPTURED_GAME_TYPE = 15;
    /**
     * The slot counts the captured statistics are rewritten with.
     */
    private static final int HUMAN_COUNT = 2;
    private static final int OBSERVER_COUNT = 1;
    private static final int OBSERVER_LIMIT = 3;
    private static final int CLOSED_SLOT_COUNT = 1;
    /**
     * Statistics whose fields all hold their defaults but the saved game identifier and the replay flag.
     */
    private static final int SAVE_GAME_IDENTIFIER = 0x9ABCDEF0;
    private static final int DEFAULT_MAP_SIZE = 256;
    private static final int DEFAULT_MAXIMUM_PLAYERS = 8;
    private static final int DEFAULT_SPEED = 4;
    private static final int DEFAULT_OBSERVER_LIMIT = 4;
    private static final @NotNull String DEFAULT_STATISTICS = "9abcdef0,,,,,,,,,,1,H\rM\r\rfeedda7a,,,,";
    /**
     * The byte limits of the host name and the map name, names at those limits, and a character of three UTF-8 bytes.
     */
    private static final int HOST_NAME_LIMIT = 24;
    private static final int MAP_NAME_LIMIT = 31;
    private static final @NotNull String LONGEST_HOST_NAME = "H".repeat(HOST_NAME_LIMIT);
    private static final @NotNull String LONGEST_MAP_NAME = "M".repeat(MAP_NAME_LIMIT);
    private static final int THREE_BYTE_CODE_POINT = 0xAC00;
    private static final @NotNull String THREE_BYTE_CHARACTER = new String(Character.toChars(THREE_BYTE_CODE_POINT));
    private static final int THREE_BYTE_CHARACTER_SIZE = 3;

    private static @NotNull LanGameStatistics createCapturedStatistics(@NotNull String hostName, @NotNull String mapName) {
        return new LanGameStatistics(0,
                                     CAPTURED_MAP_SIZE, CAPTURED_MAP_SIZE,
                                     1, CAPTURED_MAXIMUM_PLAYERS,
                                     GameSpeed.FASTEST, LanGameStatistics.HOST_MAP_STATUS,
                                     GameTemplate.TYPE_TOP_VS_BOTTOM, CAPTURED_SUBTYPE,
                                     CAPTURED_TURN_RATE, CAPTURED_TILESET, false,
                                     hostName, mapName,
                                     0, 0, 0);
    }


    @Test
    void encode_should_write_the_captured_statistics() {
        Assertions.assertEquals(CAPTURED_STATISTICS, createCapturedStatistics("B", "Bottleneck").encode());
    }

    @Test
    void parse_should_read_every_field_of_the_captured_statistics() {
        LanGameStatistics statistics = LanGameStatistics.parse(CAPTURED_STATISTICS);

        Assertions.assertNotNull(statistics);
        Assertions.assertEquals(0, statistics.getSaveGameId());
        Assertions.assertEquals(CAPTURED_MAP_SIZE, statistics.getMapWidth());
        Assertions.assertEquals(CAPTURED_MAP_SIZE, statistics.getMapHeight());
        Assertions.assertEquals(1, statistics.getHumanPlayers());
        Assertions.assertEquals(CAPTURED_MAXIMUM_PLAYERS, statistics.getMaximumPlayers());
        Assertions.assertEquals(CAPTURED_SPEED, statistics.getSpeed());
        Assertions.assertEquals(CAPTURED_MAP_STATUS, statistics.getMapStatus());
        Assertions.assertEquals(CAPTURED_GAME_TYPE, statistics.getGameType());
        Assertions.assertEquals(CAPTURED_SUBTYPE, statistics.getSubtype());
        Assertions.assertEquals(CAPTURED_TURN_RATE, statistics.getTurnRate());
        Assertions.assertEquals(CAPTURED_TILESET, statistics.getTileset());
        Assertions.assertFalse(statistics.isReplay());
        Assertions.assertEquals("B", statistics.getHostName());
        Assertions.assertEquals("Bottleneck", statistics.getMapName());
        Assertions.assertEquals(0, statistics.getObservers());
        Assertions.assertEquals(0, statistics.getObserverLimit());
        Assertions.assertEquals(0, statistics.getClosedSlots());
        Assertions.assertEquals(CAPTURED_STATISTICS, statistics.encode());
    }

    @Test
    void create_with_slot_counts_should_rewrite_the_human_players_and_the_slot_count_tail() {
        LanGameStatistics captured = createCapturedStatistics("B", "Bottleneck");
        LanGameStatistics statistics = captured.createWithSlotCounts(HUMAN_COUNT, OBSERVER_COUNT, OBSERVER_LIMIT, CLOSED_SLOT_COUNT);

        Assertions.assertEquals(",44,22,6,1,f,,1,18,1,,B\rBottleneck\r\rfeedda7a,13,1,18,", statistics.encode());
    }

    @Test
    void encode_should_leave_every_default_field_empty_and_parse_should_restore_the_defaults() {
        LanGameStatistics statistics = new LanGameStatistics(SAVE_GAME_IDENTIFIER,
                                                             DEFAULT_MAP_SIZE, DEFAULT_MAP_SIZE,
                                                             1, DEFAULT_MAXIMUM_PLAYERS,
                                                             DEFAULT_SPEED, 0,
                                                             0, 0,
                                                             0, 0, true,
                                                             "H", "M",
                                                             0, DEFAULT_OBSERVER_LIMIT, 0);

        Assertions.assertEquals(DEFAULT_STATISTICS, statistics.encode());

        LanGameStatistics parsed = LanGameStatistics.parse(DEFAULT_STATISTICS);

        Assertions.assertNotNull(parsed);
        Assertions.assertEquals(SAVE_GAME_IDENTIFIER, parsed.getSaveGameId());
        Assertions.assertEquals(DEFAULT_MAP_SIZE, parsed.getMapWidth());
        Assertions.assertEquals(DEFAULT_MAXIMUM_PLAYERS, parsed.getMaximumPlayers());
        Assertions.assertEquals(DEFAULT_SPEED, parsed.getSpeed());
        Assertions.assertTrue(parsed.isReplay());
        Assertions.assertEquals(DEFAULT_OBSERVER_LIMIT, parsed.getObserverLimit());
    }

    @Test
    void parse_should_reject_a_field_that_is_not_hexadecimal_and_a_name_that_is_too_long_or_not_ended() {
        Assertions.assertNull(LanGameStatistics.parse(",4g,12,6,1,f,,1,18,1,,B\rBottleneck\r\rfeedda7a,0,,18,"));
        Assertions.assertNull(LanGameStatistics.parse(",44,12,6,1,f,,1,18,1,," + LONGEST_HOST_NAME + "H\rBottleneck\r"));
        Assertions.assertNull(LanGameStatistics.parse(",44,12,6,1,f,,1,18,1,,B\r" + LONGEST_MAP_NAME + "M\r"));
        Assertions.assertNull(LanGameStatistics.parse(",44,12,6,1,f,,1,18,1,,B\rBottleneck"));
        Assertions.assertNotNull(LanGameStatistics.parse(",44,12,6,1,f,,1,18,1,," + LONGEST_HOST_NAME + "\r" + LONGEST_MAP_NAME + "\r"));
    }

    @Test
    void parse_should_count_no_slots_without_the_slot_count_tail() {
        LanGameStatistics statistics = LanGameStatistics.parse(",44,12,6,1,f,,1,18,1,,B\rBottleneck\r");

        Assertions.assertNotNull(statistics);
        Assertions.assertEquals(0, statistics.getObserverLimit());
        Assertions.assertEquals(0, statistics.getClosedSlots());
    }

    @Test
    void names_keep_their_byte_limits_and_lose_their_carriage_returns() {
        int fitting = HOST_NAME_LIMIT / THREE_BYTE_CHARACTER_SIZE;
        LanGameStatistics statistics = createCapturedStatistics(LONGEST_HOST_NAME + "H", "A\rB" + LONGEST_MAP_NAME);
        LanGameStatistics multibyte = createCapturedStatistics(THREE_BYTE_CHARACTER.repeat(fitting + 1), "B");

        Assertions.assertEquals(LONGEST_HOST_NAME, statistics.getHostName());
        Assertions.assertEquals(("A\nB" + LONGEST_MAP_NAME).substring(0, MAP_NAME_LIMIT), statistics.getMapName());

        LanGameStatistics parsed = LanGameStatistics.parse(statistics.encode());

        Assertions.assertNotNull(parsed);
        Assertions.assertEquals(statistics.getMapName(), parsed.getMapName());
        // A character past the byte limit is left out whole.
        Assertions.assertEquals(THREE_BYTE_CHARACTER.repeat(fitting), multibyte.getHostName());
    }
}
