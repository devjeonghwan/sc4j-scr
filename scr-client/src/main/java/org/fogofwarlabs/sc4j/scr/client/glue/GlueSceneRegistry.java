package org.fogofwarlabs.sc4j.scr.client.glue;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;

/**
 * Holds the {@link GlueSceneFactory} of every glue destination. The glue creates the screen of a destination through it
 * ({@link GlueNavigator#show}); the game's screens register themselves, and registering again replaces a screen.
 */
public final class GlueSceneRegistry {
    private final @NotNull EnumMap<GlueNavigator.Destination, GlueSceneFactory> factories;

    /**
     * Creates a {@link GlueSceneRegistry} instance with no scenes.
     */
    public GlueSceneRegistry() {
        this.factories = new EnumMap<>(GlueNavigator.Destination.class);
    }

    /**
     * Registers the screen of a destination, replacing a previous one.
     *
     * @param destination the destination
     * @param factory     the factory of its screen
     */
    public void register(GlueNavigator.@NotNull Destination destination, @NotNull GlueSceneFactory factory) {
        this.factories.put(destination, factory);
    }

    /**
     * Creates a {@link GlueScreen} instance of a destination.
     *
     * @param context     the glue context
     * @param destination the destination
     * @return the created {@link GlueScreen} instance
     * @throws IllegalStateException if no screen is registered for the destination
     */
    public @NotNull GlueScreen create(@NotNull GlueContext context, GlueNavigator.@NotNull Destination destination) {
        GlueSceneFactory factory = this.factories.get(destination);

        if (factory == null) {
            throw Exceptions.newIllegalStateException("No glue screen is registered for {0}.", destination);
        }

        return factory.create(context);
    }
}
