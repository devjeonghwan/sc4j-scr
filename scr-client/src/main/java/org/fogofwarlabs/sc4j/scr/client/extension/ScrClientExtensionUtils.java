package org.fogofwarlabs.sc4j.scr.client.extension;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Calls the {@link ScrClientExtension} implementations that the jar of the running mod names. They are found once per class loader, with the
 * class loader of {@link ScrClientExtension}, so a mod jar in its own class loader finds its own implementations.
 */
public final class ScrClientExtensionUtils {
    private static final @NotNull List<@NotNull ScrClientExtension> EXTENSIONS = ServiceLoader.load(ScrClientExtension.class, ScrClientExtension.class.getClassLoader())
                                                                                              .stream()
                                                                                              .map(ServiceLoader.Provider::get)
                                                                                              .toList();

    /**
     * Lets every extension register its commands ({@link ScrClientExtension#registerCommands}), in extension order.
     *
     * @param registry the client's command registry
     */
    public static void registerCommands(@NotNull ConsoleCommandRegistry registry) {
        for (ScrClientExtension extension : EXTENSIONS) {
            extension.registerCommands(registry);
        }
    }

    private ScrClientExtensionUtils() {
    }
}
