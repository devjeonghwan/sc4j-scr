package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommand;
import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the client's own replay viewer commands: {@code /replay open}, {@code /replay restart} and {@code /replay fit}. The native replay
 * controls (the command card, Space, the speeds) are unchanged.
 */
public final class ReplayCommands {
    /**
     * Receives the effect of a replay viewer command.
     */
    public interface Target {
        /**
         * @return {@code true} if a live game runs and refuses viewer commands, {@code false} otherwise
         */
        boolean isLiveGame();

        /**
         * Opens the file dialog that chooses a replay to load; a hidden run shows no dialog.
         *
         * @return {@code true} if the dialog was shown, {@code false} otherwise
         */
        boolean openReplay();

        /**
         * Restarts the session from its first frame; nothing restarts without a session or when the restart fails.
         *
         * @return {@code true} if the session restarted, {@code false} otherwise
         */
        boolean restart();

        /**
         * Zooms and centers the camera so that the whole map shows; the camera stays without a session.
         *
         * @return {@code true} if the camera moved, {@code false} otherwise
         */
        boolean fitMap();
    }

    /**
     * Registers {@code /replay}.
     *
     * @param registry the command registry
     * @param target   where the command acts
     */
    public static void register(@NotNull ConsoleCommandRegistry registry, @NotNull Target target) {
        registry.register(ConsoleCommand.of("/replay", "open|restart|fit", "open, restart or fit the replay in the replay viewer")
                                        .subcommand("open",
                                                    "",
                                                    "choose a replay file in a file dialog and load it; dropping a " +
                                                    "file on the window also loads it",
                                                    arguments -> target.openReplay() ? "Choose a replay to open." : "No file dialog in a hidden run.")
                                        .subcommand("restart",
                                                    "",
                                                    "load the replay again and pause at its first frame",
                                                    arguments -> target.restart() ? "Replay restarted."
                                                                                  : "Nothing restarted: no replay loaded, or the reset failed.")
                                        .subcommand("fit",
                                                    "",
                                                    "zoom out and centre the camera so that the whole map shows; " +
                                                    "/zoom reset returns to the native scale",
                                                    arguments -> target.fitMap() ? "Map fitted." : "No map to fit.")
                                        .requires(() -> !target.isLiveGame(), DebugCommands.LIVE_GAME_REFUSAL)
                                        .availability("the replay viewer, map inspection and the empty viewer before a replay " +
                                                      "loads (open only); refused in a live game.")
                                        .example("/replay open")
                                        .example("/replay fit"));
    }

    /**
     * Creates a {@link ReplayCommands} instance.
     */
    private ReplayCommands() {
    }
}
