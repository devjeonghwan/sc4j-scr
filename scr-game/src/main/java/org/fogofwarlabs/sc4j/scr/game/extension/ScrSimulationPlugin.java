package org.fogofwarlabs.sc4j.scr.game.extension;

import org.fogofwarlabs.sc4j.scr.game.extension.simulation.SimulationDecision;
import org.fogofwarlabs.sc4j.scr.game.extension.simulation.SimulationEvent;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * The per-game state of a {@link ScrSimulationExtension}. A plugin reads and changes the game through the {@link Game} it was created for; its
 * methods run on the thread that runs the game, inside the routines that call them, in the same order on every peer and in every replay of the
 * game. A plugin keeps the simulation deterministic: it draws no numbers from the game's random generator and keeps its own
 * generator, seeded from the game's seed, for its own draws. Its state is not part of the game's state hash or saves.
 * <p>
 * Decisions ({@link #decide}) run inside the routine that offers them, which continues with the values the plugin leaves in the decision
 * object; a plugin changes only the decision object there. Events ({@link #onEvent}) are reported while their routine runs, so a plugin that
 * changes the game in answer to an event keeps the event and acts on it later, at the next frame start ({@link #onFrameStarted}).
 */
public interface ScrSimulationPlugin {
    /**
     * Called once per frame when the frame's trigger step runs, before the triggers, with the random generator enabled as for the triggers; the
     * trigger step also runs while the game is paused.
     */
    default void onFrameStarted() {
    }

    /**
     * Called when something happens in the simulation, from inside the routine where it happens.
     *
     * @param event the event; its kind is its class
     */
    default void onEvent(@NotNull SimulationEvent event) {
    }

    /**
     * Called at a decision point of a simulation routine, which continues with the values the plugins leave in the decision; the plugins decide
     * in extension order, each on the values the previous one left.
     *
     * @param decision the decision; its kind is its class
     */
    default void decide(@NotNull SimulationDecision decision) {
    }

    /**
     * Called when a mod command of a player runs, at its place in the command stream of the frame.
     *
     * @param player the command player of the issuing record
     * @param data   the command data, which every plugin of the game receives; the plugin does not change it
     */
    default void onModCommand(@NotNull Player player, byte @NotNull [] data) {
    }
}
