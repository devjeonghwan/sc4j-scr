package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.data.type.ScrTechTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUpgradeTypeView;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Computes the mineral and gas costs of computer-player upgrades and research.
 */
public final class ComputerTechnology {
    /**
     * Truncates a cost to a WORD.
     */
    private static final int WORD_MASK = 0xFFFF;


    private final @NotNull Game game;

    /**
     * Creates a {@link ComputerTechnology} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public ComputerTechnology(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Returns the mineral and gas cost of the owner's next level of an upgrade, each truncated to a WORD.
     *
     * @param owner   the owner
     * @param upgrade the upgrade type
     * @return the mineral cost and the gas cost
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the player upgrade storage is not initialized
     */
    int @NotNull [] getUpgradeCost(int owner, int upgrade) {
        ScrUpgradeTypeView upgrades = this.game.requireGameData().getUpgradeTypes();
        int level = this.game.getPlayerTable().requirePlayerUpgrades().getLevel(owner, upgrade);

        return new int[]{upgrades.getMineralCost(upgrade) + upgrades.getMineralCostFactor(upgrade) * level & WORD_MASK,
                         upgrades.getGasCost(upgrade) + upgrades.getGasCostFactor(upgrade) * level & WORD_MASK};
    }

    /**
     * Returns the mineral and gas cost of a research.
     *
     * @param technology the technology type
     * @return the mineral cost and the gas cost
     * @throws NullPointerException if the world was built without game data
     */
    int @NotNull [] getResearchCost(int technology) {
        ScrTechTypeView technologies = this.game.requireGameData().getTechTypes();

        return new int[]{technologies.getMineralCost(technology), technologies.getGasCost(technology)};
    }
}
