package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.fogofwarlabs.sc4j.scr.net.discovery.LanGameInformation;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

/**
 * Checks the hosted game's advertisement built from the provider session against the captured game records of a native
 * host, the turn rate a host chooses in the native turn rate bytes, and its revisions as the lobby's slot counts change.
 */
final class LanGameAdvertisementTest {
    private static final int CLOCK_BASE = 10_000_000;

    private static final @NotNull String HOST_NAME = "B";
    private static final @NotNull String MAP_MD5 = "725EAA6B3C05137A2ECAF973F1441CA0";

    private static final int MAXIMUM_PLAYERS = 2;
    private static final int JOINER_SLOT = 1;

    /**
     * The captured game statistics before the lobby counts its slots, and the captured game options.
     */
    private static final @NotNull String CREATED_GAME_STATISTICS = ",44,12,6,1,f,,1,18,1,,B\rBottleneck\r\rfeedda7a,,,18,";
    private static final byte @NotNull [] GAME_DATA = HexFormat.of().parseHex("0f00010001000000010101020200010100010000000000320000000000000000");
    /**
     * The captured game records of the native host {@code 192.168.0.88}: revision {@code 0} at the game creation (four observer
     * slots), revision {@code 1} after its lobby counted the slots (the observer records are closed).
     */
    private static final @NotNull String CREATED_GAME_RECORD = "0a014210001a0a0a055f6e616d651201421a0e0a0570726f7879120566616c73651a150a076d61705f637263120a313438343730363136371a110a0c636c6f7365645f736c6f74731201301a160a0d6d61705f66696c655f73697a65120536383337361a160a086d61705f6e616d65120a426f74746c656e65636b1a130a0d6e65745f7475726e5f72617465120232341a2b0a076d61705f6d6435122037323545414136423343303531333741324543414639373346313434314341301a120a0969735f7265706c6179120566616c73651a140a0f706c61796572735f63757272656e741201311a0b0a055f74797065120231331a160a116f62736572766572735f63757272656e741201301a0d0a085f737562747970651201311a0f0a0a67616d655f73706565641201361a110a0c736176655f67616d655f69641201301a220a0d6d61705f66696c655f6e616d651211283229426f74746c656e65636b2e73636d1a0a0a05666c6167731201301a0f0a0a706c61796572735f61691201301a100a0b706c61796572735f6d61781201321a0e0a09686f73745f6e616d651201421a110a0c6d61705f74696c655f7365741201311a090a0472616e6b1201311a120a0d6f62736572766572735f6d61781201341a110a0a6d61705f68656967687412033132381a0b0a065f666c6167731201301a100a096d61705f77696474681203313238";
    private static final @NotNull String LOBBY_GAME_RECORD = "0a014210011a0a0a055f6e616d651201421a0e0a0570726f7879120566616c73651a150a076d61705f637263120a313438343730363136371a110a0c636c6f7365645f736c6f74731201301a160a0d6d61705f66696c655f73697a65120536383337361a160a086d61705f6e616d65120a426f74746c656e65636b1a130a0d6e65745f7475726e5f72617465120232341a2b0a076d61705f6d6435122037323545414136423343303531333741324543414639373346313434314341301a120a0969735f7265706c6179120566616c73651a140a0f706c61796572735f63757272656e741201311a0b0a055f74797065120231331a160a116f62736572766572735f63757272656e741201301a0d0a085f737562747970651201311a0f0a0a67616d655f73706565641201361a110a0c736176655f67616d655f69641201301a220a0d6d61705f66696c655f6e616d651211283229426f74746c656e65636b2e73636d1a0a0a05666c6167731201301a0f0a0a706c61796572735f61691201301a100a0b706c61796572735f6d61781201321a0e0a09686f73745f6e616d651201421a110a0c6d61705f74696c655f7365741201311a090a0472616e6b1201311a120a0d6f62736572766572735f6d61781201301a110a0a6d61705f68656967687412033132381a0b0a065f666c6167731201301a100a096d61705f77696474681203313238";
    /**
     * A turn rate the host chooses instead of the captured {@code 24} ({@code 0x18}): the created game's statistics and record with its
     * turn rate bytes, {@code c} in both statistics fields and {@code "12"} in the record's {@code net_turn_rate}, otherwise the captured ones.
     */
    private static final int CHOSEN_TURN_RATE = 12;
    private static final @NotNull String CHOSEN_TURN_RATE_STATISTICS = ",44,12,6,1,f,,1,c,1,,B\rBottleneck\r\rfeedda7a,,,c,";
    private static final @NotNull String CAPTURED_TURN_RATE_ENTRY = "0a0d6e65745f7475726e5f726174651202" + "3234";
    private static final @NotNull String CHOSEN_TURN_RATE_ENTRY = "0a0d6e65745f7475726e5f726174651202" + "3132";
    private static final int CAPTURED_MAP_SIZE = 128;
    private static final int CAPTURED_SUBTYPE = 1;
    private static final int CAPTURED_TILESET = 1;
    private static final int OBSERVER_LIMIT = 4;
    /**
     * The longest wait for the host's own slot change to come back in its turns.
     */
    private static final int SLOT_CHANGE_MILLISECONDS = 1000;

    private static final int EITHER_PREFER_COMPUTER_TYPE = 5;
    private static final int CLOSED_TYPE = 8;

    private static final class Clock {
        private int now;

        private Clock() {
            this.now = CLOCK_BASE;
        }
    }

    private static @NotNull ProviderSession createGame(@NotNull Clock clock, @NotNull String statistics) {
        ProviderSession session = new ProviderSession((address, packet) -> {
        }, () -> clock.now);

        session.createGame(HOST_NAME, statistics, "", MAXIMUM_PLAYERS, PlayerTable.OBSERVER_SLOTS, 0, 0, GAME_DATA, HOST_NAME, "");

        return session;
    }

    private static @NotNull LanGameAdvertisement createAdvertisement() {
        return new LanGameAdvertisement(LanTestResources.CAPTURED_MAP_FILE_NAME, LanTestResources.CAPTURED_MAP_FILE_SIZE,
                                        LanTestResources.CAPTURED_MAP_CHECKSUM, MAP_MD5,
                                        0);
    }

    private static void changeSlot(@NotNull Clock clock, @NotNull ProviderSession session, @NotNull LanLobby lobby, int state, int type) {
        Assertions.assertTrue(lobby.setSlotState(JOINER_SLOT, state));

        for (int time = 0; time < SLOT_CHANGE_MILLISECONDS && lobby.getRecord(JOINER_SLOT).getType() != type; time++) {
            clock.now++;
            session.update();
            lobby.update();
        }

        Assertions.assertEquals(type, lobby.getRecord(JOINER_SLOT).getType());
    }


    @Test
    void update_should_advertise_the_captured_records_of_the_created_game_and_of_its_lobby() {
        Clock clock = new Clock();
        ProviderSession session = createGame(clock, CREATED_GAME_STATISTICS);
        LanGameAdvertisement advertisement = createAdvertisement();

        Assertions.assertNull(advertisement.getGameInformation());
        Assertions.assertTrue(advertisement.update(session));
        Assertions.assertEquals(CREATED_GAME_RECORD, HexFormat.of().formatHex(advertisement.toRecord().encode()));

        LanLobby lobby = new LanLobby(session, () -> clock.now, LanTestResources.createCapturedLobbySetup(HOST_NAME), HOST_NAME,
                                      LanTestResources.createCapturedHostBlock());

        // The lobby counted its slots: its observer records are closed.
        Assertions.assertEquals(CLOSED_TYPE, lobby.getRecord(PlayerTable.PLAYABLE_PLAYER_SLOTS).getType());
        Assertions.assertTrue(advertisement.update(session));
        Assertions.assertEquals(LOBBY_GAME_RECORD, HexFormat.of().formatHex(advertisement.toRecord().encode()));
        Assertions.assertFalse(advertisement.update(session));
        Assertions.assertEquals(1, advertisement.getRevision());
    }

    @Test
    void update_should_advertise_a_chosen_turn_rate_in_the_native_turn_rate_bytes() {
        LanGameStatistics statistics = new LanGameStatistics(0,
                                                             CAPTURED_MAP_SIZE, CAPTURED_MAP_SIZE,
                                                             1, MAXIMUM_PLAYERS,
                                                             GameSpeed.FASTEST, LanGameStatistics.HOST_MAP_STATUS,
                                                             GameTemplate.TYPE_TOP_VS_BOTTOM, CAPTURED_SUBTYPE,
                                                             CHOSEN_TURN_RATE, CAPTURED_TILESET, false,
                                                             HOST_NAME, "Bottleneck",
                                                             0, OBSERVER_LIMIT, 0);

        Assertions.assertEquals(CHOSEN_TURN_RATE_STATISTICS, statistics.encode());

        ProviderSession session = createGame(new Clock(), statistics.encode());
        LanGameAdvertisement advertisement = createAdvertisement();

        Assertions.assertTrue(advertisement.update(session));
        Assertions.assertEquals(CREATED_GAME_RECORD.replace(CAPTURED_TURN_RATE_ENTRY, CHOSEN_TURN_RATE_ENTRY),
                                HexFormat.of().formatHex(advertisement.toRecord().encode()));

        LanGameInformation information = advertisement.getGameInformation();

        Assertions.assertNotNull(information);
        Assertions.assertEquals(CHOSEN_TURN_RATE, information.getNetworkTurnRate());
    }

    @Test
    void update_should_follow_the_computer_and_closed_slots_of_the_lobby() {
        Clock clock = new Clock();
        ProviderSession session = createGame(clock, CREATED_GAME_STATISTICS);
        LanGameAdvertisement advertisement = createAdvertisement();
        LanLobby lobby = new LanLobby(session, () -> clock.now, LanTestResources.createCapturedLobbySetup(HOST_NAME), HOST_NAME,
                                      LanTestResources.createCapturedHostBlock());

        advertisement.update(session);
        changeSlot(clock, session, lobby, LanLobby.SLOT_COMPUTER, EITHER_PREFER_COMPUTER_TYPE);

        // The computer slot counts as a player.
        Assertions.assertTrue(advertisement.update(session));

        LanGameInformation computer = advertisement.getGameInformation();

        Assertions.assertNotNull(computer);
        Assertions.assertEquals(MAXIMUM_PLAYERS, computer.getPlayersCurrent());
        Assertions.assertEquals(MAXIMUM_PLAYERS, computer.getPlayersMaximum());
        Assertions.assertEquals(0, computer.getClosedSlots());

        changeSlot(clock, session, lobby, LanLobby.SLOT_CLOSED, CLOSED_TYPE);

        Assertions.assertTrue(advertisement.update(session));

        LanGameInformation closed = advertisement.getGameInformation();

        Assertions.assertNotNull(closed);
        Assertions.assertEquals(1, closed.getPlayersCurrent());
        Assertions.assertEquals(MAXIMUM_PLAYERS, closed.getPlayersMaximum());
        Assertions.assertEquals(1, closed.getClosedSlots());
        Assertions.assertEquals(0, closed.getObserversMaximum());
        Assertions.assertEquals(2, advertisement.getRevision());
    }

    @Test
    void update_should_reject_game_statistics_that_cannot_be_parsed() {
        ProviderSession session = createGame(new Clock(), "statistics");

        Assertions.assertThrows(IllegalStateException.class, () -> createAdvertisement().update(session));
    }
}
