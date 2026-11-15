package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.engine.computer.ComputerController;
import org.fogofwarlabs.sc4j.engine.game.Game;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Plays one playable slot of an SC:R game as a computer player: the {@link ComputerController} of the slot in its
 * {@link ComputerPlayerPhase}. The phase holds one controller per playable slot (the computer records exist for every slot, as a trigger
 * can run a script for a human player); its scheduler runs the per-player update of the slot through {@link #update} when its player
 * cursor reaches a computer slot whose town has started.
 */
public final class ScrComputerController implements ComputerController {
    private final @NotNull ComputerPlayerPhase phase;
    private final @NotNull Player player;

    /**
     * Creates a {@link ScrComputerController} instance.
     *
     * @param phase  the computer-player phase that runs the controller
     * @param player the player of the slot
     */
    ScrComputerController(@NotNull ComputerPlayerPhase phase, @NotNull Player player) {
        this.phase  = phase;
        this.player = player;
    }

    /**
     * @return the player this controller plays
     */
    @Override
    public @NotNull Player getPlayer() {
        return this.player;
    }


    /**
     * Runs the decisions of this computer player at the point of the frame where its system's schedule reaches it: the player issues its
     * orders and uses the other systems of the game.
     * <p>
     * Runs the computer branch of the player update of the running scheduler step: the budgets, the pending construction, the supply budget,
     * the military records, the region defense and the town requests, with the elapsed trigger clock of that step.
     *
     * @param game the game
     */
    @Override
    public void update(@NotNull Game game) {
        this.phase.updatePlayer(this.player.getIndex());
    }
}
