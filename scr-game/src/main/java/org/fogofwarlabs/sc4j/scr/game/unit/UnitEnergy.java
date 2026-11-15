package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameCheats;
import org.jetbrains.annotations.NotNull;

/**
 * Tests and spends unit energy, honoring {@link GameCheats#THE_GATHERING}.
 * <p>
 * The cheat bypasses the sufficiency comparison and energy subtraction. Without an active frame the normal energy check and subtraction apply.
 */
public final class UnitEnergy {
    private final @NotNull Game game;

    /**
     * Creates a {@link UnitEnergy} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitEnergy(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Tests whether a unit may spend an energy cost: always under the cheat, otherwise when its energy reaches the cost.
     *
     * @param unit the caster
     * @param cost the energy cost with eight fractional bits
     * @return {@code true} if the unit may spend the cost, {@code false} otherwise
     */
    public boolean hasEnergy(@NotNull Unit unit, int cost) {
        return (this.game.getCheatFlags() & GameCheats.THE_GATHERING) != 0 || unit.getEnergy() >= cost;
    }

    /**
     * Subtracts the cost unless the cheat bit is set.
     *
     * @param unit the caster
     * @param cost the energy cost with eight fractional bits
     */
    public void spend(@NotNull Unit unit, int cost) {
        if ((this.game.getCheatFlags() & GameCheats.THE_GATHERING) == 0) {
            unit.setEnergy(unit.getEnergy() - cost);
        }
    }
}
