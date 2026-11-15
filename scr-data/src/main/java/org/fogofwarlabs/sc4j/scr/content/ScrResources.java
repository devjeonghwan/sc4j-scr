package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.engine.content.economy.ResourceKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds the resource kinds of the SC:R mod, in the order the mod registers them: minerals, then gas. The cost columns of
 * the engine tables hold the amounts in this order.
 */
public final class ScrResources {
    /**
     * Minerals (resource kind {@code 0}).
     */
    public static final @NotNull ResourceKind MINERALS = new ResourceKind("Minerals");
    /**
     * Vespene gas (resource kind {@code 1}).
     */
    public static final @NotNull ResourceKind GAS = new ResourceKind("Vespene Gas");

    private static final @NotNull List<@NotNull ResourceKind> TYPE_LIST = List.of(MINERALS, GAS);

    /**
     * @return every resource kind in registration order
     */
    public static @NotNull List<@NotNull ResourceKind> getTypes() {
        return TYPE_LIST;
    }


    private ScrResources() {
    }
}
