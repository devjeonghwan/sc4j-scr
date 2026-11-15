package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates the games of the replay command tests: a one-tile map whose player records carry network ids that match no issuer.
 */
final class ReplayCommandScenario {
    /**
     * The first of the network ids the fixture's player records carry, which no issuer byte matches.
     */
    private static final int UNMATCHED_ISSUER_ID_BASE = 0x10000;

    /**
     * Creates a {@link Game} instance for the explicit fixture whose player records match no issuer.
     *
     * @param gameData the installed game data
     * @return the created {@link Game} instance
     */
    static @NotNull Game createGame(@Nullable GameData gameData) {
        Game state = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(1));

        for (int recordIndex = 0; recordIndex < PlayerTable.PLAYER_SLOTS; recordIndex++) {
            state.getPlayerTable().setPlayer(recordIndex, new Player(recordIndex, UNMATCHED_ISSUER_ID_BASE + recordIndex, PlayerTypes.NONE, 0, 0));
        }

        return state;
    }


    /**
     * Creates a {@link ReplayCommandScenario} instance.
     */
    private ReplayCommandScenario() {
    }
}
