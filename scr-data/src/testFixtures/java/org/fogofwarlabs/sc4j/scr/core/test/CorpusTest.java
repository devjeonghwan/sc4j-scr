package org.fogofwarlabs.sc4j.scr.core.test;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * Marks a test class or method of the corpus tier: long runs and tests over input folders outside the repository other
 * than the installation. It runs in the Gradle task {@code corpusTest} only.
 * <p>
 * The test is skipped when one of the declared inputs is not configured. The test reads the paths through
 * {@link ExternalInput#getPath()} and {@link ExternalInput#resolveFile}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("corpus")
@ExtendWith(ExternalInputExtension.class)
public @interface CorpusTest {
    /**
     * @return the inputs the test needs; the test is skipped when one of them is not configured
     */
    @NotNull ExternalInput @NotNull [] value() default {};
}
