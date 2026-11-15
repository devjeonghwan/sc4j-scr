package org.fogofwarlabs.sc4j.scr.game.order;

/**
 * The stages of the per-unit order dispatch of {@link UnitOrders}, in the order they run for a visible unit. Each stage has its own {@link Order} per
 * order id in its {@link ScrOrderBehavior}.
 */
public enum OrderStage {
    /**
     * The main order before the disabled test; when present it replaces the rest of the main-order dispatch.
     */
    MAIN_UNCONDITIONAL,
    /**
     * The main order of an enabled unit, every frame, before the order timer counts down.
     */
    MAIN_EVERY_FRAME,
    /**
     * The main order when its order timer expires; looked up again with the main order after the every-frame stage.
     */
    MAIN_TIMER,
    /**
     * The secondary order before the disabled test; when present it replaces {@link #SECONDARY}.
     */
    SECONDARY_UNCONDITIONAL,
    /**
     * The secondary order of an enabled unit.
     */
    SECONDARY,
    /**
     * The main order of a hidden unit (inside a transport, a bunker or a building).
     */
    HIDDEN_MAIN,
    /**
     * The secondary order of a hidden unit.
     */
    HIDDEN_SECONDARY
}
