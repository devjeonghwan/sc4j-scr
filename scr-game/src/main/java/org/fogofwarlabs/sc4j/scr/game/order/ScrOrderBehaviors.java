package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.order.OrderRegistry;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrOrders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the {@link ScrOrderBehavior} of every order id a unit's order byte may hold: the engine {@link OrderRegistry} over the order types of
 * {@link ScrOrders} (slot = orders.dat entry), and the behavior of the {@link ScrOrderIds#NONE} id, which names no order type.
 * <p>
 * The native dispatch looks a behavior up by the order byte ({@link OrderRegistry#find(int)}); an id outside the order ids is rejected like an
 * invalid order type. A lookup is two array reads at most and does not allocate.
 */
public final class ScrOrderBehaviors {
    /**
     * The number of order ids: the {@code orders.dat} entries and the {@link ScrOrderIds#NONE} id.
     */
    public static final int ORDER_COUNT = ScrOrderIds.NONE + 1;

    private final @NotNull OrderRegistry<ScrOrderBehavior> registry;
    private final @NotNull ScrOrderBehavior noneBehavior;

    /**
     * Creates a {@link ScrOrderBehaviors} instance.
     *
     * @param registry     the frozen registry of the order types, every one with a behavior
     * @param noneBehavior the behavior of the {@link ScrOrderIds#NONE} id
     * @throws IllegalArgumentException if the registry is not over the order types of {@link ScrOrders}
     */
    public ScrOrderBehaviors(@NotNull OrderRegistry<ScrOrderBehavior> registry, @NotNull ScrOrderBehavior noneBehavior) {
        if (!registry.getTypes().equals(ScrOrders.getTypes())) {
            throw Exceptions.newIllegalArgumentException("The order registry is not over the SC:R order types.");
        }

        this.registry     = registry;
        this.noneBehavior = noneBehavior;
    }

    /**
     * @return the engine registry of the order types
     */
    public @NotNull OrderRegistry<ScrOrderBehavior> getRegistry() {
        return this.registry;
    }

    /**
     * @return the number of order ids, {@link #ORDER_COUNT}
     */
    public int getOrderCount() {
        return ORDER_COUNT;
    }

    /**
     * Returns the behavior of an order id.
     *
     * @param orderId the order id
     * @return the behavior
     * @throws IllegalArgumentException if the id is outside the order ids
     */
    public @NotNull ScrOrderBehavior get(int orderId) {
        ScrOrderBehavior behavior = this.find(orderId);

        if (behavior == null) {
            throw Exceptions.newIllegalArgumentException("Invalid order type index: {0}", orderId);
        }

        return behavior;
    }

    /**
     * Returns the behavior of an order id without rejecting ids outside the order ids.
     *
     * @param orderId the order id, any value
     * @return the behavior, or {@code null} if the id is outside the order ids
     */
    public @Nullable ScrOrderBehavior find(int orderId) {
        return orderId == ScrOrderIds.NONE ? this.noneBehavior : this.registry.find(orderId);
    }

    /**
     * Returns the step of an order id at a stage.
     *
     * @param stage   the dispatch stage
     * @param orderId the order id
     * @return the step, or {@code null} if the id has none at this stage
     * @throws IllegalArgumentException if the id is outside the order ids
     */
    public @Nullable Order getStep(@NotNull OrderStage stage, int orderId) {
        return this.get(orderId).getStep(stage);
    }

    /**
     * Returns the step of an order id at a stage without rejecting ids outside the order ids.
     *
     * @param stage   the dispatch stage
     * @param orderId the order id, any value
     * @return the step, or {@code null} if the id has none at this stage or is outside the order ids
     */
    public @Nullable Order findStep(@NotNull OrderStage stage, int orderId) {
        ScrOrderBehavior behavior = this.find(orderId);

        return behavior != null ? behavior.getStep(stage) : null;
    }

    /**
     * Checks whether the main-order dispatch represents an id ({@link ScrOrderBehavior#isRepresented()}).
     *
     * @param orderId the order id
     * @return {@code true} if the main-order dispatch represents the id, {@code false} otherwise
     * @throws IllegalArgumentException if the id is outside the order ids
     */
    public boolean isRepresented(int orderId) {
        return this.get(orderId).isRepresented();
    }
}
