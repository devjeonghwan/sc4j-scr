package org.fogofwarlabs.sc4j.scr.core.test;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

/**
 * Checks the {@link ExternalInput}s of {@link RequiresInstallation} and {@link CorpusTest} tests.
 * <p>
 * A {@link CorpusTest} class or method is disabled when one of its declared inputs is not configured. A
 * {@link RequiresInstallation} class or method fails before it runs when the installation is not configured or names no
 * directory. The tests read the paths through {@link ExternalInput#getPath()} and {@link ExternalInput#resolveFile}.
 */
public final class ExternalInputExtension implements ExecutionCondition, BeforeAllCallback, BeforeEachCallback {
    private static final @NotNull ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("All declared external inputs are configured.");

    private static void checkInstallation(@NotNull Optional<? extends AnnotatedElement> element) {
        if (element.isPresent() && AnnotationSupport.isAnnotated(element.get(), RequiresInstallation.class)) {
            ExternalInput.INSTALLATION.getPath();
        }
    }

    /**
     * Creates an {@link ExternalInputExtension} instance.
     */
    public ExternalInputExtension() {
    }

    /**
     * Evaluates this condition for the supplied {@link ExtensionContext}.
     * <p>
     * An {@link ConditionEvaluationResult#enabled(String) enabled} result indicates that the container or test should be
     * executed; whereas, a {@link ConditionEvaluationResult#disabled(String) disabled} result indicates that the container or test
     * should not be executed.
     *
     * @param context the current extension context; never {@code null}
     * @return the result of evaluating this condition; never {@code null}
     */
    @Override
    public @NotNull ConditionEvaluationResult evaluateExecutionCondition(@NotNull ExtensionContext context) {
        Optional<AnnotatedElement> element = context.getElement();
        Optional<CorpusTest> corpusTest = element.flatMap(annotated -> AnnotationSupport.findAnnotation(annotated, CorpusTest.class));

        if (corpusTest.isPresent()) {
            for (ExternalInput input : corpusTest.get().value()) {
                if (!input.isConfigured()) {
                    return ConditionEvaluationResult.disabled(input.getMissingMessage());
                }
            }
        }

        return ENABLED;
    }

    /**
     * Callback that is invoked once <em>before</em> all tests in the current container.
     *
     * @param context the current extension context; never {@code null}
     */
    @Override
    public void beforeAll(@NotNull ExtensionContext context) {
        checkInstallation(context.getTestClass());
    }

    /**
     * Callback that is invoked <em>before</em> an individual test and any user-defined setup methods for that test have
     * been executed.
     *
     * @param context the current extension context; never {@code null}
     */
    @Override
    public void beforeEach(@NotNull ExtensionContext context) {
        checkInstallation(context.getTestClass());
        checkInstallation(context.getTestMethod());
    }
}
