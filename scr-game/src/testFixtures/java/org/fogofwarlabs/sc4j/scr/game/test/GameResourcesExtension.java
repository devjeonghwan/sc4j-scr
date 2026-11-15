package org.fogofwarlabs.sc4j.scr.game.test;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Injects one {@link InstallationTestResources} instance per test JVM into {@link InstallationTestResources} parameters of test, test factory and
 * lifecycle methods, so that the installed data is loaded once and shared replay runs ({@link SharedReplayRunScenario}) are shared between test
 * classes. The instance lives in the root extension context and is closed when the test run ends.
 */
public final class GameResourcesExtension implements ParameterResolver {
    private static final @NotNull Namespace NAMESPACE = Namespace.create(GameResourcesExtension.class);

    /**
     * Creates a {@link GameResourcesExtension} instance.
     */
    public GameResourcesExtension() {
    }

    /**
     * Determines if this resolver supports resolution of an argument for the {@link java.lang.reflect.Parameter} in the supplied
     * {@link ParameterContext} for the supplied {@link ExtensionContext}.
     *
     * @param parameterContext the context for the parameter for which an argument should be resolved; never {@code null}
     * @param extensionContext the extension context for the {@code Executable} about to be invoked; never {@code null}
     * @return {@code true} if this resolver can resolve an argument for the parameter, {@code false} otherwise
     */
    @Override
    public boolean supportsParameter(@NotNull ParameterContext parameterContext, @NotNull ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == InstallationTestResources.class;
    }

    /**
     * Resolves an argument for the {@link java.lang.reflect.Parameter} in the supplied {@link ParameterContext} for the supplied
     * {@link ExtensionContext}.
     *
     * @param parameterContext the context for the parameter for which an argument should be resolved; never {@code null}
     * @param extensionContext the extension context for the {@code Executable} about to be invoked; never {@code null}
     * @return the resolved argument for the parameter; may only be {@code null} if the parameter type is not a primitive
     */
    @Override
    public @NotNull Object resolveParameter(@NotNull ParameterContext parameterContext, @NotNull ExtensionContext extensionContext) {
        Store store = extensionContext.getRoot().getStore(NAMESPACE);

        return store.getOrComputeIfAbsent(InstallationTestResources.class, type -> new InstallationTestResources(), InstallationTestResources.class);
    }
}
