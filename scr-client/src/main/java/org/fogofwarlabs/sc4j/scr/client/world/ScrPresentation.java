package org.fogofwarlabs.sc4j.scr.client.world;

import org.fogofwarlabs.sc4j.scr.game.player.PlayerDeparture;
import org.fogofwarlabs.sc4j.scr.game.presentation.InterfaceLink;
import org.fogofwarlabs.sc4j.scr.game.session.LocalInterfaceEffects;
import org.fogofwarlabs.sc4j.scr.game.sprite.SelectionImages;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.RuntimeRandom;
import org.jetbrains.annotations.NotNull;

/**
 * The SC:R presentation state of a running game that the client writes: the interface link (local interface, local view, message lines and
 * the event queues the client drains), the selection images of the local selection, the local interface effects, the C runtime generator
 * the presentation draws from and the player departure rules of the vision dialog. It is kept apart from {@link ScrGameView}, which reads
 * the game only; a session holds one of each per game state.
 */
public final class ScrPresentation {
    private final @NotNull Game game;

    /**
     * Creates a {@link ScrPresentation} instance over a game's presentation state.
     *
     * @param game the game
     */
    public ScrPresentation(@NotNull Game game) {
        this.game = game;
    }

    /**
     * @return the presentation state the game shares with the client: interface, message lines and effects
     */
    public @NotNull InterfaceLink getInterfaceLink() {
        return this.game.getInterfaceLink();
    }

    /**
     * @return the C runtime generator that the creep tile variants, the player-list orders, the cheat input and the unit responses share
     */
    public @NotNull RuntimeRandom getRuntimeRandom() {
        return this.game.getRuntimeRandom();
    }

    /**
     * @return the departure state of the players: who left and with whom vision may still be shared
     */
    public @NotNull PlayerDeparture getPlayerDeparture() {
        return this.game.getPlayerDeparture();
    }

    /**
     * @return the selection images of the game: the health bars and selection circles the local selection changes
     */
    public @NotNull SelectionImages getSelectionImages() {
        return this.game.getSelectionImages();
    }

    /**
     * @return the local interface effects of the game (alert areas, unit speech timers)
     */
    public @NotNull LocalInterfaceEffects getLocalInterfaceEffects() {
        return this.game.getLocalInterfaceEffects();
    }
}
