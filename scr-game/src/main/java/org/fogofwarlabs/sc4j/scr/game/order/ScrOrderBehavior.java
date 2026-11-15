package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.engine.order.OrderBehavior;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The behavior of one SC:R order type: its {@link Order} step at each stage of the native per-unit order dispatch ({@link OrderStage}), run by
 * {@link UnitOrders} in the native stage order. The engine {@link #update} runs the every-frame step of the main order
 * ({@link OrderStage#MAIN_EVERY_FRAME}).
 * <p>
 * A behavior is immutable once built; several order types share the steps of one handler.
 */
public final class ScrOrderBehavior implements OrderBehavior<ScrOrderContext, Unit> {
    /**
     * Builds {@link ScrOrderBehavior} instances.
     */
    public static final class Builder {
        private final @Nullable Order @NotNull [] steps;
        private boolean idle;

        /**
         * Creates a {@link Builder} instance without steps.
         */
        public Builder() {
            this.steps = new Order[OrderStage.values().length];
            this.idle  = false;
        }

        /**
         * Returns the step set at a stage.
         *
         * @param stage the dispatch stage
         * @return the step, or {@code null} if none is set
         */
        public @Nullable Order getStep(@NotNull OrderStage stage) {
            return this.steps[stage.ordinal()];
        }

        /**
         * Sets the step at a stage, replacing a previous one.
         *
         * @param stage the dispatch stage
         * @param step  the step
         * @return this instance for chaining
         */
        public @NotNull Builder setStep(@NotNull OrderStage stage, @NotNull Order step) {
            this.steps[stage.ordinal()] = step;

            return this;
        }

        /**
         * Marks the type as one the main-order dispatch accepts without a step of its own: only its order timer counts down.
         *
         * @return this instance for chaining
         */
        public @NotNull Builder setIdle() {
            this.idle = true;

            return this;
        }

        /**
         * Builds a {@link ScrOrderBehavior} instance with the steps set so far.
         *
         * @return the built {@link ScrOrderBehavior} instance
         */
        public @NotNull ScrOrderBehavior build() {
            boolean represented = this.idle ||
                                  this.steps[OrderStage.MAIN_EVERY_FRAME.ordinal()] != null ||
                                  this.steps[OrderStage.MAIN_TIMER.ordinal()] != null;

            return new ScrOrderBehavior(this.steps.clone(), represented);
        }
    }

    private final @Nullable Order @NotNull [] steps;
    private final boolean represented;

    private ScrOrderBehavior(@Nullable Order @NotNull [] steps, boolean represented) {
        this.steps       = steps;
        this.represented = represented;
    }

    /**
     * Returns the step at a stage.
     *
     * @param stage the dispatch stage
     * @return the step, or {@code null} if the type has none at this stage
     */
    public @Nullable Order getStep(@NotNull OrderStage stage) {
        return this.steps[stage.ordinal()];
    }

    /**
     * Checks whether the main-order dispatch represents the type: it has a step at {@link OrderStage#MAIN_EVERY_FRAME} or
     * {@link OrderStage#MAIN_TIMER}, or was built as idle ({@link Builder#setIdle}).
     *
     * @return {@code true} if the main-order dispatch represents the type, {@code false} otherwise
     */
    public boolean isRepresented() {
        return this.represented;
    }

    /**
     * Runs the every-frame step of the main order, if the type has one.
     *
     * @param context the context of the dispatch
     * @param unit    the unit whose order runs
     */
    @Override
    public void update(@NotNull ScrOrderContext context, @NotNull Unit unit) {
        Order step = this.steps[OrderStage.MAIN_EVERY_FRAME.ordinal()];

        if (step != null) {
            step.update(context, unit);
        }
    }
}
