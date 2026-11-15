package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.game.production.ScrRequirementSystem;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Maps requirement-script results to enabled, hidden or disabled command-card states.
 * Every evaluation retains its failure code for the subsequent button refresh.
 */
public final class RequirementEvaluator {
    /**
     * The result of an enabled button.
     */
    public static final int ENABLED = 1;

    /**
     * The result of a hidden button.
     */
    public static final int HIDDEN = 0;

    /**
     * The result of a disabled, grayed button.
     */
    public static final int DISABLED = -1;

    /**
     * The failure code that selects the replacement disabled string {@code 0x2fa} during the button refresh.
     */
    public static final int UNAVAILABLE_FAILURE = 0x15;

    private final @NotNull ScrGameView view;

    private int failureCode;

    /**
     * Creates a {@link RequirementEvaluator} instance reading the given game state.
     *
     * @param view the game view whose players, units and settings are evaluated
     */
    public RequirementEvaluator(@NotNull ScrGameView view) {
        this.view = view;

        this.failureCode = 0;
    }

    /**
     * @return the failure code of the last evaluation
     */
    public int getFailureCode() {
        return this.failureCode;
    }


    /**
     * Evaluates whether a unit may produce, morph into or build a unit type.
     *
     * @param unitType the requested {@code units.dat} type
     * @param player   the requesting player
     * @param producer the producing unit
     * @return {@link #ENABLED}, {@link #DISABLED} or {@link #HIDDEN}
     */
    public int evaluateUnit(int unitType, int player, @NotNull Unit producer) {
        return this.adapt(this.view.getRequirements().checkBuild(producer, unitType, player));
    }

    /**
     * Evaluates whether a unit may use a technology.
     *
     * @param technology the {@code techdata.dat} identifier
     * @param player     the requesting player
     * @param unit       the unit
     * @return {@link #ENABLED}, {@link #DISABLED} or {@link #HIDDEN}
     */
    public int evaluateTechnologyUse(int technology, int player, @NotNull Unit unit) {
        return this.adapt(this.view.getRequirements().checkTechnologyUse(unit, technology, player));
    }

    /**
     * Evaluates whether a unit may research a technology.
     *
     * @param technology the {@code techdata.dat} identifier
     * @param player     the requesting player
     * @param unit       the researching building
     * @return {@link #ENABLED}, {@link #DISABLED} or {@link #HIDDEN}
     */
    public int evaluateResearch(int technology, int player, @NotNull Unit unit) {
        return this.adapt(this.view.getRequirements().checkResearch(unit, technology, player));
    }

    /**
     * Evaluates whether a unit may start the next level of an upgrade.
     *
     * @param upgrade the {@code upgrades.dat} identifier
     * @param player  the requesting player
     * @param unit    the upgrading building
     * @return {@link #ENABLED}, {@link #DISABLED} or {@link #HIDDEN}
     */
    public int evaluateUpgrade(int upgrade, int player, @NotNull Unit unit) {
        return this.adapt(this.view.getRequirements().checkUpgrade(unit, upgrade, player));
    }

    /**
     * Evaluates whether a unit may receive an order.
     *
     * @param order  the {@code orders.dat} identifier
     * @param player the requesting player
     * @param unit   the unit
     * @return {@link #ENABLED}, {@link #DISABLED} or {@link #HIDDEN}
     */
    public int evaluateOrder(int order, int player, @NotNull Unit unit) {
        return this.adapt(this.view.getRequirements().checkOrder(unit, order, player));
    }

    private int adapt(int result) {
        this.failureCode = ScrRequirementSystem.getFailureCode(result);

        if (ScrRequirementSystem.isAccepted(result)) {
            return ENABLED;
        }

        return ScrRequirementSystem.isDisabled(result) ? DISABLED : HIDDEN;
    }
}
