package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommand;
import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the client's own {@code /console} command of the replay viewer: which player's recorded selection the game console shows, whether the
 * command card shows the native replay controls, and following the recorded selection.
 */
public final class ConsoleCommands {
    /**
     * The number of player slots the console can show.
     */
    static final int PLAYER_COUNT = PlayerTable.PLAYABLE_PLAYER_SLOTS;

    /**
     * Receives the effect of a console command.
     */
    public interface Target {
        /**
         * @return {@code true} if a live game runs and refuses this console command, {@code false} otherwise
         */
        boolean isLiveGame();

        /**
         * @return the player whose recorded selection the console shows
         */
        int getConsolePlayer();

        /**
         * Sets the player whose recorded selection the console shows.
         *
         * @param player the player whose recorded selection the console shows, {@code 0..7}
         */
        void setConsolePlayer(int player);

        /**
         * @return {@code true} if the command card shows replay controls, {@code false} otherwise
         */
        boolean isReplayControls();

        /**
         * Sets whether the command card shows replay controls.
         *
         * @param shown whether the command card shows the native replay controls
         */
        void setReplayControls(boolean shown);

        /**
         * Returns the console to the console player's recorded selection, dropping the viewer's own local selection.
         *
         * @return {@code true} if a console is shown, {@code false} otherwise
         */
        boolean followRecordedSelection();
    }

    /**
     * Registers {@code /console}.
     *
     * @param registry the command registry
     * @param target   where the command acts
     */
    public static void register(@NotNull ConsoleCommandRegistry registry, @NotNull Target target) {
        registry.register(ConsoleCommand.of("/console", "player [<0-7>]|controls [on|off]|follow", "the replay console's player and controls")
                                        .describe("The replay viewer's game console shows one player's recorded selection " +
                                                  "(--console player[:replay] sets it at launch).")
                                        .subcommand("player",
                                                    "[<0-7>]",
                                                    "show that player's selection; no number selects the next player",
                                                    arguments -> {
                                                        int player;

                                                        if (arguments.length == 0) {
                                                            player = (target.getConsolePlayer() + 1) % PLAYER_COUNT;
                                                        } else {
                                                            player = DebugCommands.parsePlayer(arguments[0]);

                                                            if (player < 0) {
                                                                return "Use /console player 0-7.";
                                                            }
                                                        }

                                                        target.setConsolePlayer(player);

                                                        return "Console player " + player + ".";
                                                    })
                                        .subcommand("controls",
                                                    "[on|off]",
                                                    "show or hide the native replay controls in the command card; no argument toggles them",
                                                    arguments -> {
                                                        Boolean shown = arguments.length == 0
                                                                        ? Boolean.valueOf(!target.isReplayControls())
                                                                        : ConsoleCommand.parseSwitch(arguments[0]);

                                                        if (shown == null) {
                                                            return "Use /console controls [on|off].";
                                                        }

                                                        target.setReplayControls(shown);

                                                        return "Replay controls " + (target.isReplayControls() ? "shown." : "hidden.");
                                                    })
                                        .subcommand("follow",
                                                    "",
                                                    "show the recorded selection again after a click selected units locally",
                                                    arguments -> target.followRecordedSelection()
                                                                 ? "Console follows player " + target.getConsolePlayer() + "'s recorded selection."
                                                                 : "No console shown.")
                                        .requires(() -> !target.isLiveGame(), DebugCommands.LIVE_GAME_REFUSAL)
                                        .availability("the replay viewer; refused in a live game.")
                                        .example("/console player 2")
                                        .example("/console controls off"));
    }

    /**
     * Creates a {@link ConsoleCommands} instance.
     */
    private ConsoleCommands() {
    }
}
