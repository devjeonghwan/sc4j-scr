package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the issuer scan of replay commands against explicit player-record fixtures.
 */
@RequiresInstallation
final class ReplayCommandIssuerTest {
    /**
     * The issuer byte the tested commands carry.
     */
    private static final int ISSUER = 7;
    /**
     * A recorded id whose low byte is the issuer but whose high bits differ.
     */
    private static final int ALIASED_ISSUER_ID = 0x1000007;
    /**
     * A stored command player index with the sign bit set.
     */
    private static final int HIGH_BIT_COMMAND_PLAYER_INDEX = 0x80000007;
    /**
     * The largest issuer byte.
     */
    private static final int MAXIMUM_ISSUER = 0xFF;
    /**
     * A player type, race and team byte outside every defined value.
     */
    private static final int UNDEFINED_FIELD = 0xFF;
    /**
     * The last of the eight playable player records.
     */
    private static final int LAST_PLAYABLE_SLOT = 7;

    private static @Nullable GameData gameData;

    /**
     * Loads the installed game resources.
     *
     * @throws IOException if the installed game resources cannot be read
     */
    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    /**
     * Closes the installed game resources when they were loaded.
     */
    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    @Test
    void resolve_should_use_first_full_dword_match_without_type_or_stored_index_gate() {
        Game state = ReplayCommandScenario.createGame(gameData);

        state.getPlayerTable().setPlayer(0, new Player(2, ALIASED_ISSUER_ID, PlayerTypes.HUMAN, 1, 0));
        state.getPlayerTable().setPlayer(1, new Player(HIGH_BIT_COMMAND_PLAYER_INDEX, ISSUER, PlayerTypes.NONE, 0, 0));
        state.getPlayerTable().setPlayer(2, new Player(3, ISSUER, PlayerTypes.HUMAN, 1, 0));

        long previousHash = state.computeStateHash();

        ReplayCommandIssuer issuer = ReplayCommandIssuer.resolve(state, ISSUER);

        Assertions.assertEquals(ReplayCommandIssuer.Status.RESOLVED, issuer.getStatus());
        Assertions.assertEquals(1, issuer.getRecordIndex());
        Assertions.assertEquals(HIGH_BIT_COMMAND_PLAYER_INDEX, issuer.getCommandPlayerIndex());
        Assertions.assertEquals(previousHash, state.computeStateHash());
    }

    @Test
    void resolve_should_report_unresolved_extended_records_after_skipping_ineligible_base_records() {
        Game state = ReplayCommandScenario.createGame(gameData);

        for (int recordIndex = PlayerTable.PLAYABLE_PLAYER_SLOTS; recordIndex < PlayerTable.PLAYER_SLOTS; recordIndex++) {
            state.getPlayerTable().setPlayer(recordIndex, new Player(recordIndex, ISSUER, PlayerTypes.HUMAN, 1, 0));
        }

        ReplayCommandIssuer issuer = ReplayCommandIssuer.resolve(state, ISSUER);

        Assertions.assertEquals(ReplayCommandIssuer.Status.UNRESOLVED_EXTENDED_RECORDS, issuer.getStatus());
        Assertions.assertThrows(IllegalStateException.class, issuer::getRecordIndex);
        Assertions.assertThrows(IllegalStateException.class, issuer::getCommandPlayerIndex);
    }

    @Test
    void resolve_should_not_alias_high_bits_or_require_active_player_type() {
        Game state = ReplayCommandScenario.createGame(gameData);

        state.getPlayerTable().setPlayer(LAST_PLAYABLE_SLOT, new Player(-1, MAXIMUM_ISSUER, UNDEFINED_FIELD, UNDEFINED_FIELD, UNDEFINED_FIELD));

        ReplayCommandIssuer issuer = ReplayCommandIssuer.resolve(state, MAXIMUM_ISSUER);

        Assertions.assertEquals(ReplayCommandIssuer.Status.RESOLVED, issuer.getStatus());
        Assertions.assertEquals(LAST_PLAYABLE_SLOT, issuer.getRecordIndex());
        Assertions.assertEquals(-1, issuer.getCommandPlayerIndex());

        state.getPlayerTable().setPlayer(LAST_PLAYABLE_SLOT, new Player(LAST_PLAYABLE_SLOT, -1, PlayerTypes.HUMAN, 1, 0));

        Assertions.assertEquals(ReplayCommandIssuer.Status.UNRESOLVED_EXTENDED_RECORDS,
                                ReplayCommandIssuer.resolve(state, MAXIMUM_ISSUER).getStatus());
    }

    @Test
    void resolve_should_leave_issuer_unresolved_when_an_earlier_record_is_missing() {
        Game state = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(1));

        state.getPlayerTable().setPlayer(1, new Player(1, ISSUER, PlayerTypes.HUMAN, 1, 0));

        Assertions.assertEquals(ReplayCommandIssuer.Status.UNRESOLVED_PLAYER_RECORD, ReplayCommandIssuer.resolve(state, ISSUER).getStatus());

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 1, 0));

        Assertions.assertEquals(0, ReplayCommandIssuer.resolve(state, ISSUER).getRecordIndex());
    }

    @Test
    void resolve_should_reject_values_outside_the_issuer_byte_range() {
        Game state = ReplayCommandScenario.createGame(gameData);

        Assertions.assertThrows(IllegalArgumentException.class, () -> ReplayCommandIssuer.resolve(state, -1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ReplayCommandIssuer.resolve(state, MAXIMUM_ISSUER + 1));
    }
}
