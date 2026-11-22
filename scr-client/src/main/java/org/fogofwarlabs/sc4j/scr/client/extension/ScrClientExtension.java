package org.fogofwarlabs.sc4j.scr.client.extension;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Extends the client of the SC:R mod. A mod built from the SC:R mod's sources adds an implementation with a public no-argument constructor and
 * names it in the resource {@code META-INF/services/org.fogofwarlabs.sc4j.scr.client.extension.ScrClientExtension} of its jar;
 * {@link ScrClientExtensionUtils} calls the named implementations in the order of that file. The SC:R mod itself names none.
 */
public interface ScrClientExtension {
    /**
     * Registers the extension's commands in the client's command registry, once, when the client starts, after the client's own commands. The
     * commands run on the client's thread, between game frames, and {@code /help} lists them with the client's own.
     *
     * @param registry the client's command registry
     */
    default void registerCommands(@NotNull ConsoleCommandRegistry registry) {
    }
}
