package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Evaluates where a building or an add-on can be placed without placing it: the placement preview of the interface. Unit positions and
 * orders are unchanged by an evaluation. The placement rules are {@link UnitPlacement}'s.
 */
public interface PlacementEvaluator {
    /**
     * Evaluates a building footprint with its per-tile codes.
     *
     * @param builder         the placing unit, or {@code null}
     * @param owner           the player whose exploration, visibility and psi fields apply
     * @param unitType        the building type
     * @param tileX           the left footprint tile
     * @param tileY           the top footprint tile
     * @param rememberedState whether fogged tiles are judged from remembered state (the placement preview)
     * @param checkUndetected whether undetected units obstruct with {@link PlacementResult#UNDETECTED}
     * @param checkFogged     whether fogged tiles take part in the creep, occupancy and unit checks
     * @param memory          the remembered images for {@code rememberedState}, or {@code null} for none
     * @return the result with the overall and per-tile codes
     * @throws NullPointerException if the world was built without game data
     */
    @NotNull PlacementResult evaluateBuilding(@Nullable Unit builder,
                                              int owner,
                                              int unitType,
                                              int tileX, int tileY,
                                              boolean rememberedState,
                                              boolean checkUndetected,
                                              boolean checkFogged,
                                              PlacementResult.@Nullable Memory memory);

    /**
     * Evaluates an add-on footprint and then the footprint of its parent building placed at the add-on's offset: always with {@code rememberedState}
     * (the placement preview), otherwise only when the add-on footprint is valid.
     *
     * @param builder         the parent building placing the add-on
     * @param owner           the player whose exploration, visibility and psi fields apply
     * @param addonType       the add-on type
     * @param tileX           the add-on's left footprint tile
     * @param tileY           the add-on's top footprint tile
     * @param rememberedState whether both checks judge fogged tiles from remembered state
     * @param checkUndetected whether undetected units obstruct in both checks
     * @param checkFogged     whether fogged tiles take part in both checks
     * @param memory          the remembered images for {@code rememberedState}, or {@code null} for none
     * @return the add-on result, carrying the parent's result when it was evaluated
     * @throws NullPointerException if the world was built without game data
     */
    @NotNull PlacementResult evaluateAddon(@NotNull Unit builder,
                                           int owner,
                                           int addonType,
                                           int tileX, int tileY,
                                           boolean rememberedState,
                                           boolean checkUndetected,
                                           boolean checkFogged,
                                           PlacementResult.@Nullable Memory memory);
}
