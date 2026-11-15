package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.jetbrains.annotations.NotNull;

/**
 * Creates player records for trigger fixtures.
 */
final class TriggerPlayerScenario {
    /**
     * Creates player records in slot order.
     *
     * @param types    the player types
     * @param outcomes the initial victory states
     * @return the created player records
     */
    static @NotNull Player @NotNull [] createPlayers(int @NotNull [] types, byte @NotNull [] outcomes) {
        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int index = 0; index < players.length; index++) {
            players[index] = new Player(index, index, types[index], 0, 0);
            players[index].setVictoryState(outcomes[index]);
        }

        return players;
    }


    /**
     * Creates a {@link TriggerPlayerScenario} instance.
     */
    private TriggerPlayerScenario() {
    }
}
