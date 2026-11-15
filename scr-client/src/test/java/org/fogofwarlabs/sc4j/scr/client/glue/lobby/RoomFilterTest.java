package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.engine.net.beaconlink.*;
import org.fogofwarlabs.sc4j.engine.net.lobby.JoinRefusal;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.fogofwarlabs.sc4j.scr.net.beaconlink.ScrRoomData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests the room list's filter: the order of the listed rooms, the search text, the game type, the latency, the largest player count and the
 * rooms of another version, and that reset passes every room again.
 */
final class RoomFilterTest {
    private static final long GOOD_MILLISECONDS = 40;
    private static final long FAIR_MILLISECONDS = 150;
    private static final long POOR_MILLISECONDS = 320;
    private static final int SPEED = 6;
    private static final int MAP_SIZE = 128;
    private static final int TWO_PLAYERS = 2;
    private static final int FOUR_PLAYERS = 4;
    private static final int EIGHT_PLAYERS = 8;

    /**
     * Names game types by their identifier and hosts by their room's name.
     */
    private static final RoomFilter.@NotNull Names NAMES = new RoomFilter.Names() {
        @Override
        public @NotNull String getGameTypeName(int gameType) {
            return gameType == GameTemplate.TYPE_USE_MAP_SETTINGS ? "Use Map Settings" : "Melee";
        }

        @Override
        public @NotNull String getHostName(@NotNull FoundRoom room) {
            return room.getSettings().getRoomName().startsWith("Duel") ? "Kerrigan#1a2b3c" : "";
        }
    };

    private static final @NotNull List<@NotNull FoundRoom> ROOMS = List.of(
            createRoom(1, "beta melee", EIGHT_PLAYERS, GameTemplate.TYPE_MELEE, null, LatencyEstimate.of(POOR_MILLISECONDS)),
            createRoom(2, "Duel", TWO_PLAYERS, GameTemplate.TYPE_MELEE, null, LatencyEstimate.UNKNOWN),
            createRoom(3, "Old version", FOUR_PLAYERS, GameTemplate.TYPE_MELEE, JoinRefusal.MOD_VERSION, LatencyEstimate.of(GOOD_MILLISECONDS)),
            createRoom(4, "Alpha UMS", FOUR_PLAYERS, GameTemplate.TYPE_USE_MAP_SETTINGS, null, LatencyEstimate.of(FAIR_MILLISECONDS)),
            createRoom(5, "alpha melee", FOUR_PLAYERS, GameTemplate.TYPE_MELEE, null, LatencyEstimate.of(POOR_MILLISECONDS))
    );

    private static @NotNull FoundRoom createRoom(int key, @NotNull String name, int capacity, int gameType, @Nullable JoinRefusal refusal,
                                                 @NotNull LatencyEstimate latency) {
        byte[] bytes = new byte[LinkPlayer.KEY_LENGTH];

        Arrays.fill(bytes, (byte) key);

        return new FoundRoom(LinkParticipant.of(LinkPlayer.of(bytes), 1L), 1,
                             new RoomSettings(name, "Lost Temple", 1, capacity, new ScrRoomData(gameType, 1, SPEED, MAP_SIZE, MAP_SIZE).encode()),
                             refusal, latency);
    }

    private static @NotNull List<String> select(@NotNull RoomFilter filter) {
        return filter.select(ROOMS, NAMES).stream().map(entry -> entry.getRoom().getSettings().getRoomName()).toList();
    }

    @Test
    void select_should_list_joinable_rooms_by_latency_and_name_before_other_versions() {
        Assertions.assertEquals(List.of("Alpha UMS", "alpha melee", "beta melee", "Duel", "Old version"), select(new RoomFilter()));
    }

    @Test
    void select_should_find_the_search_text_in_name_type_or_host() {
        RoomFilter filter = new RoomFilter();

        filter.setQuery("ALPHA");
        Assertions.assertEquals(List.of("Alpha UMS", "alpha melee"), select(filter));

        filter.setQuery("map settings");
        Assertions.assertEquals(List.of("Alpha UMS"), select(filter));

        filter.setQuery("kerrigan");
        Assertions.assertEquals(List.of("Duel"), select(filter));

        filter.setQuery("temple");
        Assertions.assertEquals(5, select(filter).size(), "every room's map");
    }

    @Test
    void select_should_filter_by_type_latency_players_and_version() {
        RoomFilter filter = new RoomFilter();

        filter.setGameType(GameTemplate.TYPE_USE_MAP_SETTINGS);
        Assertions.assertEquals(List.of("Alpha UMS"), select(filter));

        filter.setGameType(RoomFilter.ANY_GAME_TYPE);
        filter.setWorstLatency(LatencyClass.FAIR);
        Assertions.assertEquals(List.of("Alpha UMS", "Duel", "Old version"), select(filter), "an unknown latency passes");

        filter.setWorstLatency(null);
        filter.setMaximumPlayers(FOUR_PLAYERS);
        Assertions.assertEquals(List.of("Alpha UMS", "alpha melee", "Duel", "Old version"), select(filter));

        filter.setClosedRoomsShown(false);
        Assertions.assertEquals(List.of("Alpha UMS", "alpha melee", "Duel"), select(filter));

        filter.setQuery("duel");
        filter.reset();
        Assertions.assertEquals(List.of("Duel"), select(filter), "reset keeps the search text");
        Assertions.assertThrows(IllegalArgumentException.class, () -> filter.setMaximumPlayers(1));
    }
}
