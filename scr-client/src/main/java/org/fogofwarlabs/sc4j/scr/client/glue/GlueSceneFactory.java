package org.fogofwarlabs.sc4j.scr.client.glue;

import org.jetbrains.annotations.NotNull;

/**
 * Creates the screen of one glue destination. A factory is registered per {@link GlueNavigator.Destination} in the
 * {@link GlueSceneRegistry} and runs each time the flow enters that destination.
 */
@FunctionalInterface
public interface GlueSceneFactory {
    /**
     * Creates a {@link GlueScreen} instance of the destination; the glue opens it.
     *
     * @param context the glue context: game choice, navigator and shared resources
     * @return the created {@link GlueScreen} instance
     */
    @NotNull GlueScreen create(@NotNull GlueContext context);
}
